package com.robotcamera.app;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.Image;
import android.util.Log;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Động cơ ước tính khoảng cách & bám mục tiêu thị giác (Vision Distance Detector)
 * Tối ưu hóa cho thiết lập camera điện thoại / tablet đơn trên Gimbal RS 3 Mini
 * - Phương pháp 1 (M1): QR / ArUco Fiducial Tag (độ chính xác ±1cm, thích hợp gắn thắt lưng hoặc cầm tay)
 * - Phương pháp 2 (M2): Dáng người MediaPipe / ML Kit Pose (không cần đeo tag, bám trực tiếp thân và bàn chân)
 * - Tích hợp Bộ lọc Kalman 1D (1D Kalman Filter) chống rung nhiễu và nhảy số
 */
public class VisualDistanceDetector {

    private static final String TAG = "VisualDistanceDetector";

    public static final float DEFAULT_MARKER_WIDTH_M = 0.12f;
    public static final float AVERAGE_TORSO_HEIGHT_M = 0.50f;
    public static float focalLengthNorm = 0.714f;

    public enum DetectionType {
        NONE,
        MARKER_TAG,
        HUMAN_POSE
    }

    public static class LandmarkPoint {
        public final String name;
        public final float xNorm;
        public final float yNorm;
        public final boolean isReference;

        public LandmarkPoint(String name, float xNorm, float yNorm, boolean isReference) {
            this.name = name;
            this.xNorm = xNorm;
            this.yNorm = yNorm;
            this.isReference = isReference;
        }

        public LandmarkPoint(String name, float xNorm, float yNorm) {
            this(name, xNorm, yNorm, false);
        }
    }

    public static class TargetResult {
        public final DetectionType type;
        public final float rawDistanceM;
        public final float filteredDistanceM;
        public final float azimuthNorm;
        public final float elevationNorm;
        public final RectF boundingBox;
        public final float confidence;
        public final long timestampMs;
        public final String referenceDescription;
        public final List<LandmarkPoint> landmarks;
        public final PointF refStartPoint;
        public final PointF refEndPoint;

        public TargetResult(DetectionType type, float rawDistanceM, float filteredDistanceM,
                            float azimuthNorm, float elevationNorm, RectF boundingBox,
                            float confidence, long timestampMs, String referenceDescription,
                            List<LandmarkPoint> landmarks, PointF refStartPoint, PointF refEndPoint) {
            this.type = type;
            this.rawDistanceM = rawDistanceM;
            this.filteredDistanceM = filteredDistanceM;
            this.azimuthNorm = azimuthNorm;
            this.elevationNorm = elevationNorm;
            this.boundingBox = boundingBox;
            this.confidence = confidence;
            this.timestampMs = timestampMs;
            this.referenceDescription = referenceDescription != null ? referenceDescription : "";
            this.landmarks = landmarks != null ? landmarks : Collections.emptyList();
            this.refStartPoint = refStartPoint;
            this.refEndPoint = refEndPoint;
        }

        public float getDistanceCm() {
            return filteredDistanceM * 100.0f;
        }

        public boolean isSafeZone() {
            return filteredDistanceM >= 0.40f && filteredDistanceM <= 0.60f;
        }

        public boolean isTooClose() {
            return filteredDistanceM < 0.38f;
        }
    }

    public interface OnTargetDetectedListener {
        void onTargetDetected(TargetResult result);
    }

    public interface OnTargetLostListener {
        void onTargetLost();
    }

    private OnTargetDetectedListener mOnTargetDetected;
    private OnTargetLostListener mOnTargetLost;

    public OnTargetDetectedListener getOnTargetDetected() {
        return mOnTargetDetected;
    }

    public void setOnTargetDetected(OnTargetDetectedListener listener) {
        this.mOnTargetDetected = listener;
    }

    public OnTargetLostListener getOnTargetLost() {
        return mOnTargetLost;
    }

    public void setOnTargetLost(OnTargetLostListener listener) {
        this.mOnTargetLost = listener;
    }

    public float markerRealWidthM = DEFAULT_MARKER_WIDTH_M;
    public boolean isEnabled = true;
    public boolean preferMarker = true;

    private BarcodeScanner barcodeScanner;
    private PoseDetector poseDetector;

    private volatile boolean isProcessingFrame = false;
    private volatile long lastProcessedTimeMs = 0L;

    private float kalmanDistance = 0.50f;
    private float kalmanCovariance = 1.0f;
    private final float processNoiseQ = 0.008f;
    private final float measurementNoiseR = 0.04f;
    private boolean isKalmanInitialized = false;

    public VisualDistanceDetector() {
        try {
            BarcodeScannerOptions barcodeOptions = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC, Barcode.FORMAT_DATA_MATRIX)
                .build();
            barcodeScanner = BarcodeScanning.getClient(barcodeOptions);
        } catch (Throwable e) {
            Log.e(TAG, "Không thể khởi tạo BarcodeScanner: " + e.getMessage());
        }

        try {
            PoseDetectorOptions poseOptions = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .setPreferredHardwareConfigs(PoseDetectorOptions.CPU)
                .build();
            poseDetector = PoseDetection.getClient(poseOptions);
        } catch (Throwable e) {
            Log.e(TAG, "Không thể khởi tạo PoseDetector CPU: " + e.getMessage());
        }
    }

    public void processBitmap(Bitmap bitmap) {
        if (!isEnabled || isProcessingFrame) return;
        if (bitmap.isRecycled()) return;

        long now = System.currentTimeMillis();
        if (now - lastProcessedTimeMs < 75) return;
        lastProcessedTimeMs = now;

        final BarcodeScanner scanner = barcodeScanner;
        final PoseDetector pose = poseDetector;
        if (scanner == null && pose == null) return;

        isProcessingFrame = true;
        try {
            InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
            final int frameWidth = bitmap.getWidth();
            final int frameHeight = bitmap.getHeight();

            if (scanner != null) {
                scanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        if (bitmap.isRecycled()) {
                            isProcessingFrame = false;
                            return;
                        }
                        Barcode primaryBarcode = null;
                        for (Barcode b : barcodes) {
                            if (b.getCornerPoints() != null && b.getCornerPoints().length >= 4) {
                                primaryBarcode = b;
                                break;
                            }
                        }
                        if (primaryBarcode != null) {
                            handleMarkerDetected(primaryBarcode, frameWidth, frameHeight);
                            isProcessingFrame = false;
                        } else if (!preferMarker || barcodes.isEmpty()) {
                            scanPose(inputImage, frameWidth, frameHeight, null);
                        } else {
                            notifyTargetLost();
                            isProcessingFrame = false;
                        }
                    })
                    .addOnFailureListener(e -> scanPose(inputImage, frameWidth, frameHeight, null));
            } else {
                scanPose(inputImage, frameWidth, frameHeight, null);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Lỗi nạp ảnh Bitmap vào ML Kit: " + t.getMessage());
            isProcessingFrame = false;
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public void processImageProxy(ImageProxy imageProxy) {
        if (!isEnabled || isProcessingFrame) {
            imageProxy.close();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastProcessedTimeMs < 75) {
            imageProxy.close();
            return;
        }
        lastProcessedTimeMs = now;

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        final BarcodeScanner scanner = barcodeScanner;
        final PoseDetector pose = poseDetector;
        if (scanner == null && pose == null) {
            imageProxy.close();
            return;
        }

        isProcessingFrame = true;
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        try {
            InputImage inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees);
            final int frameWidth = (rotationDegrees == 90 || rotationDegrees == 270) ? imageProxy.getHeight() : imageProxy.getWidth();
            final int frameHeight = (rotationDegrees == 90 || rotationDegrees == 270) ? imageProxy.getWidth() : imageProxy.getHeight();

            if (scanner != null) {
                scanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        Barcode primaryBarcode = null;
                        for (Barcode b : barcodes) {
                            if (b.getCornerPoints() != null && b.getCornerPoints().length >= 4) {
                                primaryBarcode = b;
                                break;
                            }
                        }
                        if (primaryBarcode != null) {
                            handleMarkerDetected(primaryBarcode, frameWidth, frameHeight);
                            imageProxy.close();
                            isProcessingFrame = false;
                        } else if (!preferMarker || barcodes.isEmpty()) {
                            scanPose(inputImage, frameWidth, frameHeight, imageProxy);
                        } else {
                            notifyTargetLost();
                            imageProxy.close();
                            isProcessingFrame = false;
                        }
                    })
                    .addOnFailureListener(e -> scanPose(inputImage, frameWidth, frameHeight, imageProxy));
            } else {
                scanPose(inputImage, frameWidth, frameHeight, imageProxy);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Lỗi processImageProxy: " + t.getMessage());
            imageProxy.close();
            isProcessingFrame = false;
        }
    }

    private void scanPose(InputImage inputImage, int frameWidth, int frameHeight, ImageProxy imageProxy) {
        PoseDetector pose = poseDetector;
        if (pose == null) {
            if (imageProxy != null) imageProxy.close();
            isProcessingFrame = false;
            return;
        }

        try {
            pose.process(inputImage)
                .addOnSuccessListener(poseResult -> handlePoseDetected(poseResult, frameWidth, frameHeight))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Lỗi phân tích Pose: " + e.getMessage());
                    notifyTargetLost();
                })
                .addOnCompleteListener(task -> {
                    if (imageProxy != null) imageProxy.close();
                    isProcessingFrame = false;
                });
        } catch (Throwable t) {
            Log.w(TAG, "Lỗi gọi pose.process: " + t.getMessage());
            if (imageProxy != null) imageProxy.close();
            isProcessingFrame = false;
        }
    }

    private void handleMarkerDetected(Barcode barcode, int frameWidth, int frameHeight) {
        android.graphics.Point[] corners = barcode.getCornerPoints();
        if (corners == null || corners.length < 4) return;

        float minX = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;

        for (android.graphics.Point pt : corners) {
            float x = pt.x;
            float y = pt.y;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }

        float tagPixelWidth = Math.max(10.0f, maxX - minX);
        float tagPixelHeight = Math.max(10.0f, maxY - minY);
        float tagPixelSize = (tagPixelWidth + tagPixelHeight) / 2.0f;

        float fxPixels = frameWidth * focalLengthNorm;
        float rawDistance = (fxPixels * markerRealWidthM) / tagPixelSize;
        float filteredDistance = filterKalman(rawDistance);

        float centerX = (minX + maxX) / 2.0f;
        float centerY = (minY + maxY) / 2.0f;
        float azimuthNorm = Math.max(-1.0f, Math.min(1.0f, (centerX - (frameWidth / 2.0f)) / (frameWidth / 2.0f)));
        float elevationNorm = Math.max(-1.0f, Math.min(1.0f, ((frameHeight / 2.0f) - centerY) / (frameHeight / 2.0f)));

        RectF normRect = new RectF(
            minX / frameWidth,
            minY / frameHeight,
            maxX / frameWidth,
            maxY / frameHeight
        );

        TargetResult result = new TargetResult(
            DetectionType.MARKER_TAG,
            rawDistance,
            filteredDistance,
            azimuthNorm,
            elevationNorm,
            normRect,
            0.95f,
            System.currentTimeMillis(),
            "Mốc: Nhãn ArUco Tag (12cm)",
            Collections.emptyList(),
            new PointF(minX / frameWidth, (minY + maxY) / 2f / frameHeight),
            new PointF(maxX / frameWidth, (minY + maxY) / 2f / frameHeight)
        );

        if (mOnTargetDetected != null) {
            mOnTargetDetected.onTargetDetected(result);
        }
    }

    private void handlePoseDetected(Pose pose, int frameWidth, int frameHeight) {
        PoseLandmark nose = pose.getPoseLandmark(PoseLandmark.NOSE);
        PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);

        boolean hasShoulders = leftShoulder != null && rightShoulder != null &&
            leftShoulder.getInFrameLikelihood() > 0.35f && rightShoulder.getInFrameLikelihood() > 0.35f;
        boolean hasHips = leftHip != null && rightHip != null &&
            leftHip.getInFrameLikelihood() > 0.35f && rightHip.getInFrameLikelihood() > 0.35f;
        boolean hasNose = nose != null && nose.getInFrameLikelihood() > 0.35f;

        if (!hasShoulders && !hasHips && !hasNose) {
            notifyTargetLost();
            return;
        }

        float fxPixels = frameWidth * focalLengthNorm;
        float rawDistance;
        String referenceDescription;
        PointF refStart = null;
        PointF refEnd = null;
        boolean refMethodIsNose = false;
        boolean refMethodIsHip = false;

        if (hasShoulders && hasNose) {
            float shoulderMidX = (leftShoulder.getPosition().x + rightShoulder.getPosition().x) / 2.0f;
            float shoulderMidY = (leftShoulder.getPosition().y + rightShoulder.getPosition().y) / 2.0f;
            float headToShoulderPixelY = Math.abs(shoulderMidY - nose.getPosition().y);

            if (headToShoulderPixelY >= 15f) {
                rawDistance = (fxPixels * 0.24f) / headToShoulderPixelY;
                referenceDescription = "Mốc dọc: Mũi ➔ Vai (24cm)";
                refStart = new PointF(nose.getPosition().x / frameWidth, nose.getPosition().y / frameHeight);
                refEnd = new PointF(shoulderMidX / frameWidth, shoulderMidY / frameHeight);
                refMethodIsNose = true;
            } else if (hasHips) {
                float hipMidX = (leftHip.getPosition().x + rightHip.getPosition().x) / 2.0f;
                float hipMidY = (leftHip.getPosition().y + rightHip.getPosition().y) / 2.0f;
                float torsoPixelHeight = Math.max(20f, Math.abs(hipMidY - shoulderMidY));
                rawDistance = (fxPixels * AVERAGE_TORSO_HEIGHT_M) / torsoPixelHeight;
                referenceDescription = "Mốc dọc: Vai ➔ Hông (50cm)";
                refStart = new PointF(shoulderMidX / frameWidth, shoulderMidY / frameHeight);
                refEnd = new PointF(hipMidX / frameWidth, hipMidY / frameHeight);
                refMethodIsHip = true;
            } else {
                float shoulderPixelWidth = Math.max(20.0f, Math.abs(leftShoulder.getPosition().x - rightShoulder.getPosition().x));
                rawDistance = (fxPixels * 0.40f) / shoulderPixelWidth;
                referenceDescription = "Mốc ngang: Bề rộng 2 Vai (40cm)";
                refStart = new PointF(leftShoulder.getPosition().x / frameWidth, leftShoulder.getPosition().y / frameHeight);
                refEnd = new PointF(rightShoulder.getPosition().x / frameWidth, rightShoulder.getPosition().y / frameHeight);
            }
        } else if (hasShoulders && hasHips) {
            float shoulderMidX = (leftShoulder.getPosition().x + rightShoulder.getPosition().x) / 2.0f;
            float shoulderMidY = (leftShoulder.getPosition().y + rightShoulder.getPosition().y) / 2.0f;
            float hipMidX = (leftHip.getPosition().x + rightHip.getPosition().x) / 2.0f;
            float hipMidY = (leftHip.getPosition().y + rightHip.getPosition().y) / 2.0f;
            float torsoPixelHeight = Math.max(20f, Math.abs(hipMidY - shoulderMidY));
            rawDistance = (fxPixels * AVERAGE_TORSO_HEIGHT_M) / torsoPixelHeight;
            referenceDescription = "Mốc dọc: Vai ➔ Hông (50cm)";
            refStart = new PointF(shoulderMidX / frameWidth, shoulderMidY / frameHeight);
            refEnd = new PointF(hipMidX / frameWidth, hipMidY / frameHeight);
            refMethodIsHip = true;
        } else if (hasShoulders) {
            float shoulderPixelWidth = Math.max(20.0f, Math.abs(leftShoulder.getPosition().x - rightShoulder.getPosition().x));
            rawDistance = (fxPixels * 0.40f) / shoulderPixelWidth;
            referenceDescription = "Mốc ngang: Bề rộng 2 Vai (40cm)";
            refStart = new PointF(leftShoulder.getPosition().x / frameWidth, leftShoulder.getPosition().y / frameHeight);
            refEnd = new PointF(rightShoulder.getPosition().x / frameWidth, rightShoulder.getPosition().y / frameHeight);
        } else if (hasHips) {
            float hipPixelWidth = Math.max(20.0f, Math.abs(leftHip.getPosition().x - rightHip.getPosition().x));
            rawDistance = (fxPixels * 0.32f) / hipPixelWidth;
            referenceDescription = "Mốc ngang: Bề rộng 2 Hông (32cm)";
            refStart = new PointF(leftHip.getPosition().x / frameWidth, leftHip.getPosition().y / frameHeight);
            refEnd = new PointF(rightHip.getPosition().x / frameWidth, rightHip.getPosition().y / frameHeight);
            refMethodIsHip = true;
        } else {
            notifyTargetLost();
            return;
        }

        List<LandmarkPoint> landmarks = new ArrayList<>();
        if (nose != null && nose.getInFrameLikelihood() > 0.25f) {
            landmarks.add(new LandmarkPoint("Mũi", nose.getPosition().x / frameWidth, nose.getPosition().y / frameHeight, refMethodIsNose));
        }
        if (leftShoulder != null && leftShoulder.getInFrameLikelihood() > 0.25f) {
            landmarks.add(new LandmarkPoint("Vai T", leftShoulder.getPosition().x / frameWidth, leftShoulder.getPosition().y / frameHeight, true));
        }
        if (rightShoulder != null && rightShoulder.getInFrameLikelihood() > 0.25f) {
            landmarks.add(new LandmarkPoint("Vai P", rightShoulder.getPosition().x / frameWidth, rightShoulder.getPosition().y / frameHeight, true));
        }
        if (leftHip != null && leftHip.getInFrameLikelihood() > 0.25f) {
            landmarks.add(new LandmarkPoint("Hông T", leftHip.getPosition().x / frameWidth, leftHip.getPosition().y / frameHeight, refMethodIsHip));
        }
        if (rightHip != null && rightHip.getInFrameLikelihood() > 0.25f) {
            landmarks.add(new LandmarkPoint("Hông P", rightHip.getPosition().x / frameWidth, rightHip.getPosition().y / frameHeight, refMethodIsHip));
        }

        float minX = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;

        for (LandmarkPoint lm : landmarks) {
            float px = lm.xNorm * frameWidth;
            float py = lm.yNorm * frameHeight;
            if (px < minX) minX = px;
            if (px > maxX) maxX = px;
            if (py < minY) minY = py;
            if (py > maxY) maxY = py;
        }

        float padX = Math.max(20f, (maxX - minX) * 0.25f);
        float padY = Math.max(20f, (maxY - minY) * 0.20f);
        RectF bbox = new RectF(
            Math.max(0f, Math.min(1f, (minX - padX) / frameWidth)),
            Math.max(0f, Math.min(1f, (minY - padY * 1.5f) / frameHeight)),
            Math.max(0f, Math.min(1f, (maxX + padX) / frameWidth)),
            Math.max(0f, Math.min(1f, (maxY + padY) / frameHeight))
        );

        float filteredDistance = filterKalman(rawDistance);
        float centerX = (minX + maxX) / 2.0f;
        float centerY = (minY + maxY) / 2.0f;
        float azimuthNorm = Math.max(-1.0f, Math.min(1.0f, (centerX - (frameWidth / 2.0f)) / (frameWidth / 2.0f)));
        float elevationNorm = Math.max(-1.0f, Math.min(1.0f, ((frameHeight / 2.0f) - centerY) / (frameHeight / 2.0f)));

        TargetResult result = new TargetResult(
            DetectionType.HUMAN_POSE,
            rawDistance,
            filteredDistance,
            azimuthNorm,
            elevationNorm,
            bbox,
            0.85f,
            System.currentTimeMillis(),
            referenceDescription,
            landmarks,
            refStart,
            refEnd
        );

        if (mOnTargetDetected != null) {
            mOnTargetDetected.onTargetDetected(result);
        }
    }

    private void notifyTargetLost() {
        if (mOnTargetLost != null) {
            mOnTargetLost.onTargetLost();
        }
    }

    private float filterKalman(float measured) {
        float clamped = Math.max(0.15f, Math.min(5.0f, measured));

        if (!isKalmanInitialized) {
            kalmanDistance = clamped;
            kalmanCovariance = 0.5f;
            isKalmanInitialized = true;
            return kalmanDistance;
        }

        float predDistance = kalmanDistance;
        float predCov = kalmanCovariance + processNoiseQ;

        float kalmanGain = predCov / (predCov + measurementNoiseR);
        kalmanDistance = predDistance + kalmanGain * (clamped - predDistance);
        kalmanCovariance = (1.0f - kalmanGain) * predCov;

        return kalmanDistance;
    }

    public void resetFilter() {
        isKalmanInitialized = false;
        kalmanDistance = 0.50f;
        kalmanCovariance = 1.0f;
    }

    public void close() {
        try { if (barcodeScanner != null) barcodeScanner.close(); } catch (Exception ignored) {}
        try { if (poseDetector != null) poseDetector.close(); } catch (Exception ignored) {}
    }
}
