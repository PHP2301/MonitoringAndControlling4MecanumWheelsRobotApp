package com.robotcamera.app

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Động cơ ước tính khoảng cách & bám mục tiêu thị giác (Vision Distance Detector)
 * Tối ưu hóa cho thiết lập camera điện thoại / tablet đơn trên Gimbal RS 3 Mini
 * - Phương pháp 1 (M1): QR / ArUco Fiducial Tag (độ chính xác ±1cm, thích hợp gắn thắt lưng hoặc cầm tay)
 * - Phương pháp 2 (M2): Dáng người MediaPipe / ML Kit Pose (không cần đeo tag, bám trực tiếp thân và bàn chân)
 * - Tích hợp Bộ lọc Kalman 1D (1D Kalman Filter) chống rung nhiễu và nhảy số
 */
class VisualDistanceDetector {

    companion object {
        private const val TAG = "VisualDistanceDetector"

        // Kích thước chuẩn của nhãn QR / ArUco Tag in trên giấy (mặc định 12.0 cm = 0.12 m)
        const val DEFAULT_MARKER_WIDTH_M = 0.12f

        // Chiều cao trung bình từ vai đến hông người lớn (khoảng 0.50m) dùng để ước tính khoảng cách khi không có tag
        const val AVERAGE_TORSO_HEIGHT_M = 0.50f

        // Góc nhìn ngang trung bình của camera điện thoại (HFOV ~ 70 độ)
        // Hệ số tiêu cự chuẩn hóa fx_norm = fx / width = 1 / (2 * tan(HFOV / 2)) ≈ 0.714
        var focalLengthNorm: Float = 0.714f
    }

    enum class DetectionType {
        NONE,
        MARKER_TAG,   // Phát hiện nhãn QR / Tag
        HUMAN_POSE    // Phát hiện người bằng dáng (Pose)
    }

    data class LandmarkPoint(
        val name: String,
        val xNorm: Float,
        val yNorm: Float,
        val isReference: Boolean = false // Có phải điểm mốc đang dùng để đo khoảng cách hay không
    )

    data class TargetResult(
        val type: DetectionType,
        val rawDistanceM: Float,
        val filteredDistanceM: Float,
        val azimuthNorm: Float,       // Độ lệch tâm ngang [-1.0 .. +1.0], 0 là chính giữa ống kính
        val elevationNorm: Float,     // Độ lệch tâm dọc   [-1.0 .. +1.0], 0 là chính giữa
        val boundingBox: RectF,       // Khung bao quanh mục tiêu chuẩn hóa [0..1, 0..1]
        val confidence: Float,
        val timestampMs: Long,
        val referenceDescription: String = "",
        val landmarks: List<LandmarkPoint> = emptyList(),
        val refStartPoint: PointF? = null,
        val refEndPoint: PointF? = null
    ) {
        val distanceCm: Float get() = filteredDistanceM * 100.0f
        val isSafeZone: Boolean get() = filteredDistanceM in 0.40f..0.60f
        val isTooClose: Boolean get() = filteredDistanceM < 0.38f
    }

    // Callbacks
    var onTargetDetected: ((TargetResult) -> Unit)? = null
    var onTargetLost: (() -> Unit)? = null

    // Cấu hình
    var markerRealWidthM: Float = DEFAULT_MARKER_WIDTH_M
    var isEnabled: Boolean = true
    var preferMarker: Boolean = true // Ưu tiên tìm Marker trước, nếu không có mới chuyển sang Pose

    // ML Kit Detectors (nullable & an toàn chống crash)
    private var barcodeScanner: BarcodeScanner? = null
    private var poseDetector: PoseDetector? = null

    @Volatile
    private var isProcessingFrame = false
    @Volatile
    private var lastProcessedTimeMs: Long = 0L

    // Bộ lọc Kalman 1 chiều cho khoảng cách (1D Kalman Filter)
    private var kalmanDistance: Float = 0.50f
    private var kalmanCovariance: Float = 1.0f
    private val processNoiseQ = 0.008f   // Nhiễu quá trình (độ mượt di chuyển)
    private val measurementNoiseR = 0.04f // Nhiễu đo lường camera
    private var isKalmanInitialized = false

    init {
        // Khởi tạo Barcode / QR Tag Scanner an toàn
        try {
            val barcodeOptions = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC, Barcode.FORMAT_DATA_MATRIX)
                .build()
            barcodeScanner = BarcodeScanning.getClient(barcodeOptions)
        } catch (e: Throwable) {
            Log.e(TAG, "Không thể khởi tạo BarcodeScanner: ${e.message}")
        }

        // Khởi tạo Pose Detector bằng CPU an toàn tuyệt đối trên mọi chip/thiết bị
        try {
            val poseOptions = PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .setPreferredHardwareConfigs(PoseDetectorOptions.CPU)
                .build()
            poseDetector = PoseDetection.getClient(poseOptions)
        } catch (e: Throwable) {
            Log.e(TAG, "Không thể khởi tạo PoseDetector CPU: ${e.message}")
        }
    }

    /**
     * Xử lý khung hình dạng Bitmap (được chia sẻ từ MjpegServer hoặc bộ giải mã)
     */
    fun processBitmap(bitmap: Bitmap) {
        if (!isEnabled || isProcessingFrame) return
        if (bitmap.isRecycled) return

        val now = System.currentTimeMillis()
        if (now - lastProcessedTimeMs < 75) return // Throttling: giới hạn ~13 FPS để CPU mát, camera mượt
        lastProcessedTimeMs = now

        val scanner = barcodeScanner
        val pose = poseDetector
        if (scanner == null && pose == null) return

        isProcessingFrame = true
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val frameWidth = bitmap.width
            val frameHeight = bitmap.height

            if (scanner != null) {
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        if (bitmap.isRecycled) {
                            isProcessingFrame = false
                            return@addOnSuccessListener
                        }
                        val primaryBarcode = barcodes.firstOrNull { it.cornerPoints != null && it.cornerPoints!!.size >= 4 }
                        if (primaryBarcode != null) {
                            handleMarkerDetected(primaryBarcode, frameWidth, frameHeight)
                            isProcessingFrame = false
                        } else if (!preferMarker || barcodes.isEmpty()) {
                            scanPose(inputImage, frameWidth, frameHeight, null)
                        } else {
                            notifyTargetLost()
                            isProcessingFrame = false
                        }
                    }
                    .addOnFailureListener {
                        scanPose(inputImage, frameWidth, frameHeight, null)
                    }
            } else {
                scanPose(inputImage, frameWidth, frameHeight, null)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Lỗi nạp ảnh Bitmap vào ML Kit: ${t.message}")
            isProcessingFrame = false
        }
    }

    /**
     * Xử lý khung hình nhận được từ CameraX ImageAnalysis (YUV_420_888)
     */
    @OptIn(ExperimentalGetImage::class)
    fun processImageProxy(imageProxy: ImageProxy) {
        if (!isEnabled || isProcessingFrame) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastProcessedTimeMs < 75) {
            imageProxy.close()
            return
        }
        lastProcessedTimeMs = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val scanner = barcodeScanner
        val pose = poseDetector
        if (scanner == null && pose == null) {
            imageProxy.close()
            return
        }

        isProcessingFrame = true
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        try {
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            val frameWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
            val frameHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height

            if (scanner != null) {
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val primaryBarcode = barcodes.firstOrNull { it.cornerPoints != null && it.cornerPoints!!.size >= 4 }
                        if (primaryBarcode != null) {
                            handleMarkerDetected(primaryBarcode, frameWidth, frameHeight)
                            imageProxy.close()
                            isProcessingFrame = false
                        } else if (!preferMarker || barcodes.isEmpty()) {
                            scanPose(inputImage, frameWidth, frameHeight, imageProxy)
                        } else {
                            notifyTargetLost()
                            imageProxy.close()
                            isProcessingFrame = false
                        }
                    }
                    .addOnFailureListener {
                        scanPose(inputImage, frameWidth, frameHeight, imageProxy)
                    }
            } else {
                scanPose(inputImage, frameWidth, frameHeight, imageProxy)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Lỗi processImageProxy: ${t.message}")
            imageProxy.close()
            isProcessingFrame = false
        }
    }

    private fun scanPose(inputImage: InputImage, frameWidth: Int, frameHeight: Int, imageProxy: ImageProxy?) {
        val pose = poseDetector
        if (pose == null) {
            imageProxy?.close()
            isProcessingFrame = false
            return
        }

        try {
            pose.process(inputImage)
                .addOnSuccessListener { poseResult ->
                    handlePoseDetected(poseResult, frameWidth, frameHeight)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Lỗi phân tích Pose: ${e.message}")
                    notifyTargetLost()
                }
                .addOnCompleteListener {
                    imageProxy?.close()
                    isProcessingFrame = false
                }
        } catch (t: Throwable) {
            Log.w(TAG, "Lỗi gọi pose.process: ${t.message}")
            imageProxy?.close()
            isProcessingFrame = false
        }
    }

    /**
     * Xử lý khi phát hiện nhãn QR / ArUco Tag (Độ chính xác cao nhất: sai số ±1cm)
     */
    private fun handleMarkerDetected(barcode: Barcode, frameWidth: Int, frameHeight: Int) {
        val corners = barcode.cornerPoints ?: return
        if (corners.size < 4) return

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (pt in corners) {
            val x = pt.x.toFloat()
            val y = pt.y.toFloat()
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        val tagPixelWidth = max(10.0f, maxX - minX)
        val tagPixelHeight = max(10.0f, maxY - minY)
        val tagPixelSize = (tagPixelWidth + tagPixelHeight) / 2.0f

        // Tiêu cự tính bằng pixel: fx = frameWidth * focalLengthNorm
        val fxPixels = frameWidth * focalLengthNorm

        // Công thức lỗ kim (Pinhole Model): d = (fx * W_real) / w_pixel
        val rawDistance = (fxPixels * markerRealWidthM) / tagPixelSize

        // Lọc Kalman
        val filteredDistance = filterKalman(rawDistance)

        // Tính độ lệch góc tâm mục tiêu đối với tâm khung hình
        val centerX = (minX + maxX) / 2.0f
        val centerY = (minY + maxY) / 2.0f
        val azimuthNorm = ((centerX - (frameWidth / 2.0f)) / (frameWidth / 2.0f)).coerceIn(-1.0f, 1.0f)
        val elevationNorm = (((frameHeight / 2.0f) - centerY) / (frameHeight / 2.0f)).coerceIn(-1.0f, 1.0f)

        val normRect = RectF(
            minX / frameWidth,
            minY / frameHeight,
            maxX / frameWidth,
            maxY / frameHeight
        )

        val result = TargetResult(
            type = DetectionType.MARKER_TAG,
            rawDistanceM = rawDistance,
            filteredDistanceM = filteredDistance,
            azimuthNorm = azimuthNorm,
            elevationNorm = elevationNorm,
            boundingBox = normRect,
            confidence = 0.95f,
            timestampMs = System.currentTimeMillis(),
            referenceDescription = "Mốc: Nhãn ArUco Tag (12cm)",
            landmarks = emptyList(),
            refStartPoint = PointF(minX / frameWidth, (minY + maxY) / 2f / frameHeight),
            refEndPoint = PointF(maxX / frameWidth, (minY + maxY) / 2f / frameHeight)
        )

        onTargetDetected?.invoke(result)
    }

    /**
     * Xử lý khi phát hiện Dáng người (Phương pháp M2 không cần nhãn)
     * - Tối ưu hóa đặc biệt cho bám người cự ly gần 50cm và xoay 360 độ:
     *   Ưu tiên mốc dọc (Nose-to-Shoulder 24cm hoặc Torso-Y 50cm) để bất biến khi người xoay góc.
     */
    private fun handlePoseDetected(pose: Pose, frameWidth: Int, frameHeight: Int) {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)

        val hasShoulders = leftShoulder != null && rightShoulder != null &&
                leftShoulder.inFrameLikelihood > 0.35f && rightShoulder.inFrameLikelihood > 0.35f
        val hasHips = leftHip != null && rightHip != null &&
                leftHip.inFrameLikelihood > 0.35f && rightHip.inFrameLikelihood > 0.35f
        val hasNose = nose != null && nose.inFrameLikelihood > 0.35f

        if (!hasShoulders && !hasHips && !hasNose) {
            notifyTargetLost()
            return
        }

        val fxPixels = frameWidth * focalLengthNorm
        val rawDistance: Float
        val referenceDescription: String
        var refStart: PointF? = null
        var refEnd: PointF? = null
        var refMethodIsNose = false
        var refMethodIsHip = false

        // 1. Tính toán điểm chuẩn để đo khoảng cách (Ưu tiên mốc đứng để bất biến khi xoay 360°)
        if (hasShoulders && hasNose) {
            val shoulderMidX = (leftShoulder!!.position.x + rightShoulder!!.position.x) / 2.0f
            val shoulderMidY = (leftShoulder.position.y + rightShoulder.position.y) / 2.0f
            val headToShoulderPixelY = abs(shoulderMidY - nose!!.position.y)

            if (headToShoulderPixelY >= 15f) {
                // ƯU TIÊN SỐ 1 (Tối ưu cho 50cm & xoay 360 độ): Mốc dọc Mũi -> Đường vai (Chuẩn 24cm)
                rawDistance = (fxPixels * 0.24f) / headToShoulderPixelY
                referenceDescription = "Mốc dọc: Mũi ➔ Vai (24cm)"
                refStart = PointF(nose.position.x / frameWidth, nose.position.y / frameHeight)
                refEnd = PointF(shoulderMidX / frameWidth, shoulderMidY / frameHeight)
                refMethodIsNose = true
            } else if (hasHips) {
                // ƯU TIÊN SỐ 2: Mốc dọc Thân người (Vai -> Hông: Chuẩn 50cm)
                val hipMidX = (leftHip!!.position.x + rightHip!!.position.x) / 2.0f
                val hipMidY = (leftHip.position.y + rightHip!!.position.y) / 2.0f
                val torsoPixelHeight = max(20f, abs(hipMidY - shoulderMidY))
                rawDistance = (fxPixels * AVERAGE_TORSO_HEIGHT_M) / torsoPixelHeight
                referenceDescription = "Mốc dọc: Vai ➔ Hông (50cm)"
                refStart = PointF(shoulderMidX / frameWidth, shoulderMidY / frameHeight)
                refEnd = PointF(hipMidX / frameWidth, hipMidY / frameHeight)
                refMethodIsHip = true
            } else {
                val shoulderPixelWidth = max(20.0f, abs(leftShoulder.position.x - rightShoulder.position.x))
                rawDistance = (fxPixels * 0.40f) / shoulderPixelWidth
                referenceDescription = "Mốc ngang: Bề rộng 2 Vai (40cm)"
                refStart = PointF(leftShoulder.position.x / frameWidth, leftShoulder.position.y / frameHeight)
                refEnd = PointF(rightShoulder.position.x / frameWidth, rightShoulder.position.y / frameHeight)
            }
        } else if (hasShoulders && hasHips) {
            val shoulderMidX = (leftShoulder!!.position.x + rightShoulder!!.position.x) / 2.0f
            val shoulderMidY = (leftShoulder.position.y + rightShoulder.position.y) / 2.0f
            val hipMidX = (leftHip!!.position.x + rightHip!!.position.x) / 2.0f
            val hipMidY = (leftHip.position.y + rightHip.position.y) / 2.0f
            val torsoPixelHeight = max(20f, abs(hipMidY - shoulderMidY))
            rawDistance = (fxPixels * AVERAGE_TORSO_HEIGHT_M) / torsoPixelHeight
            referenceDescription = "Mốc dọc: Vai ➔ Hông (50cm)"
            refStart = PointF(shoulderMidX / frameWidth, shoulderMidY / frameHeight)
            refEnd = PointF(hipMidX / frameWidth, hipMidY / frameHeight)
            refMethodIsHip = true
        } else if (hasShoulders) {
            val shoulderPixelWidth = max(20.0f, abs(leftShoulder!!.position.x - rightShoulder!!.position.x))
            rawDistance = (fxPixels * 0.40f) / shoulderPixelWidth
            referenceDescription = "Mốc ngang: Bề rộng 2 Vai (40cm)"
            refStart = PointF(leftShoulder.position.x / frameWidth, leftShoulder.position.y / frameHeight)
            refEnd = PointF(rightShoulder.position.x / frameWidth, rightShoulder.position.y / frameHeight)
        } else if (hasHips) {
            val hipPixelWidth = max(20.0f, abs(leftHip!!.position.x - rightHip!!.position.x))
            rawDistance = (fxPixels * 0.32f) / hipPixelWidth
            referenceDescription = "Mốc ngang: Bề rộng 2 Hông (32cm)"
            refStart = PointF(leftHip.position.x / frameWidth, leftHip.position.y / frameHeight)
            refEnd = PointF(rightHip.position.x / frameWidth, rightHip.position.y / frameHeight)
            refMethodIsHip = true
        } else {
            notifyTargetLost()
            return
        }

        // 2. Thu thập danh sách các điểm mốc (Landmarks) hiển thị lên HUD
        val landmarks = mutableListOf<LandmarkPoint>()
        if (nose != null && nose.inFrameLikelihood > 0.25f) {
            landmarks.add(LandmarkPoint("Mũi", nose.position.x / frameWidth, nose.position.y / frameHeight, isReference = refMethodIsNose))
        }
        if (leftShoulder != null && leftShoulder.inFrameLikelihood > 0.25f) {
            landmarks.add(LandmarkPoint("Vai T", leftShoulder.position.x / frameWidth, leftShoulder.position.y / frameHeight, isReference = true))
        }
        if (rightShoulder != null && rightShoulder.inFrameLikelihood > 0.25f) {
            landmarks.add(LandmarkPoint("Vai P", rightShoulder.position.x / frameWidth, rightShoulder.position.y / frameHeight, isReference = true))
        }
        if (leftHip != null && leftHip.inFrameLikelihood > 0.25f) {
            landmarks.add(LandmarkPoint("Hông T", leftHip.position.x / frameWidth, leftHip.position.y / frameHeight, isReference = refMethodIsHip))
        }
        if (rightHip != null && rightHip.inFrameLikelihood > 0.25f) {
            landmarks.add(LandmarkPoint("Hông P", rightHip.position.x / frameWidth, rightHip.position.y / frameHeight, isReference = refMethodIsHip))
        }

        // 3. Khung bao quanh cơ thể (Bounding Box)
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (lm in landmarks) {
            val px = lm.xNorm * frameWidth
            val py = lm.yNorm * frameHeight
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py
        }

        val padX = max(20f, (maxX - minX) * 0.25f)
        val padY = max(20f, (maxY - minY) * 0.20f)
        val bbox = RectF(
            ((minX - padX) / frameWidth).coerceIn(0f, 1f),
            ((minY - padY * 1.5f) / frameHeight).coerceIn(0f, 1f),
            ((maxX + padX) / frameWidth).coerceIn(0f, 1f),
            ((maxY + padY) / frameHeight).coerceIn(0f, 1f)
        )

        val filteredDistance = filterKalman(rawDistance)
        val centerX = (minX + maxX) / 2.0f
        val centerY = (minY + maxY) / 2.0f
        val azimuthNorm = ((centerX - (frameWidth / 2.0f)) / (frameWidth / 2.0f)).coerceIn(-1.0f, 1.0f)
        val elevationNorm = (((frameHeight / 2.0f) - centerY) / (frameHeight / 2.0f)).coerceIn(-1.0f, 1.0f)

        val result = TargetResult(
            type = DetectionType.HUMAN_POSE,
            rawDistanceM = rawDistance,
            filteredDistanceM = filteredDistance,
            azimuthNorm = azimuthNorm,
            elevationNorm = elevationNorm,
            boundingBox = bbox,
            confidence = 0.85f,
            timestampMs = System.currentTimeMillis(),
            referenceDescription = referenceDescription,
            landmarks = landmarks,
            refStartPoint = refStart,
            refEndPoint = refEnd
        )

        onTargetDetected?.invoke(result)
    }

    private fun notifyTargetLost() {
        onTargetLost?.invoke()
    }

    /**
     * Bộ lọc Kalman 1 chiều: Cập nhật ước lượng khoảng cách mượt mà, loại bỏ jitter
     */
    private fun filterKalman(measured: Float): Float {
        // Loại bỏ ngoại lai phi lý (khoảng cách âm hoặc > 5 mét)
        val clamped = measured.coerceIn(0.15f, 5.0f)

        if (!isKalmanInitialized) {
            kalmanDistance = clamped
            kalmanCovariance = 0.5f
            isKalmanInitialized = true
            return kalmanDistance
        }

        // 1. Dự đoán (Prediction)
        val predDistance = kalmanDistance
        val predCov = kalmanCovariance + processNoiseQ

        // 2. Cập nhật (Measurement Update)
        val kalmanGain = predCov / (predCov + measurementNoiseR)
        kalmanDistance = predDistance + kalmanGain * (clamped - predDistance)
        kalmanCovariance = (1.0f - kalmanGain) * predCov

        return kalmanDistance
    }

    /**
     * Đặt lại bộ lọc Kalman khi đổi mục tiêu hoặc sau khi mất dấu
     */
    fun resetFilter() {
        isKalmanInitialized = false
        kalmanDistance = 0.50f
        kalmanCovariance = 1.0f
    }

    fun close() {
        try { barcodeScanner?.close() } catch (_: Exception) {}
        try { poseDetector?.close() } catch (_: Exception) {}
    }
}
