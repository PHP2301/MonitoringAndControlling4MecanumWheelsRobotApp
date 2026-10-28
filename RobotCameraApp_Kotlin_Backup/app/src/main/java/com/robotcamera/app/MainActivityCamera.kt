@file:Suppress("unused")

package com.robotcamera.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.*
import android.view.OrientationEventListener
import android.view.Surface
import android.content.res.Configuration
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.core.widget.NestedScrollView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.robotcamera.app.MainActivity.AppRole
import com.robotcamera.app.MainActivity.CameraMode
import com.robotcamera.app.MainActivity.ScreenOrientationOption
import com.robotcamera.app.MainActivity.Companion.TAG
import com.robotcamera.app.MainActivity.Companion.REQUEST_CODE_PERMISSIONS
import com.robotcamera.app.MainActivity.Companion.REQUIRED_PERMISSIONS

/**
 * CameraX: khởi động camera, chụp ảnh, quay video, zoom, lấy nét, flash, xoay màn hình, quyền truy cập.
 * (Extension functions của MainActivity - tách từ MainActivity.kt)
 */

internal fun MainActivity.handleShutterClick() {
    if (currentMode == CameraMode.PHOTO) {
        triggerShutterFlash()
        takePhoto()
        ButtonSyncManager.sendButtonEvent("TAKE_PHOTO", "CLICK")
    } else {
        toggleVideoRecording()
        if (isRecordingVideo) {
            ButtonSyncManager.sendButtonEvent("START_RECORD", "CLICK")
        } else {
            ButtonSyncManager.sendButtonEvent("STOP_RECORD", "CLICK")
        }
    }
}

internal fun MainActivity.toggleVideoRecording() {
    if (recording == null) {
        startVideoRecording()
    } else {
        stopVideoRecording()
    }
}

internal fun MainActivity.startVideoRecording() {
    var capture = videoCapture
    if (capture == null || cameraProvider?.isBound(capture) != true) {
        try {
            if (imageCapture != null && cameraProvider?.isBound(imageCapture!!) == true) {
                cameraProvider?.unbind(imageCapture!!)
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            capture = VideoCapture.withOutput(recorder)
            this.videoCapture = capture
            val p = preview
            val a = imageAnalysis
            val cp = cameraProvider
            if (cp != null && p != null && a != null) {
                camera = cp.bindToLifecycle(
                    this,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    p,
                    a,
                    capture
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi bind videoCapture: ${e.message}")
        }
    }

    val activeCapture = videoCapture
    if (activeCapture == null) {
        Toast.makeText(this, "Camera Video đang chuẩn bị...", Toast.LENGTH_SHORT).show()
        startCamera()
        return
    }

    if (recording != null) {
        Log.w(TAG, "Đang có phiên quay video hoạt động")
        return
    }

    val name = "ROBOT_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.mp4")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RobotCamera")
        }
    }

    val mediaStoreOutput = MediaStoreOutputOptions.Builder(
        contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(contentValues).build()

    val recordingBuilder = activeCapture.output.prepareRecording(this, mediaStoreOutput)
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
        try {
            recordingBuilder.withAudioEnabled()
        } catch (e: Exception) {
            Log.w(TAG, "Không thể bật âm thanh: ${e.message}")
        }
    }

    try {
        recording = recordingBuilder.start(ContextCompat.getMainExecutor(this)) { recordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Start -> {
                    isRecordingVideo = true
                    recordingStartTime = SystemClock.elapsedRealtime()
                    tvRecTimerBadge.visibility = View.VISIBLE
                    tvRecTimerBadge.text = "● REC 00:00"
                    uiHandler.post(videoTimerRunnable)

                    // Nút Shutter đổi sang hình vuông đỏ (Nút Dừng)
                    val rectDrawable = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(10f).toFloat()
                        setColor(Color.parseColor("#E53935"))
                    }
                    shutterCore.background = rectDrawable
                    shutterCore.animate().scaleX(0.55f).scaleY(0.55f).setDuration(180).start()

                    Toast.makeText(this@startVideoRecording, "Đang quay video: $name.mp4", Toast.LENGTH_SHORT).show()
                }
                is VideoRecordEvent.Finalize -> {
                    isRecordingVideo = false
                    uiHandler.removeCallbacks(videoTimerRunnable)
                    tvRecTimerBadge.visibility = View.GONE

                    // Trả nút Shutter về hình tròn đỏ
                    val circleDrawable = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#E53935"))
                    }
                    shutterCore.background = circleDrawable
                    shutterCore.animate().scaleX(0.85f).scaleY(0.85f).setDuration(180).start()

                    if (!recordEvent.hasError()) {
                        val savedUri = recordEvent.outputResults.outputUri
                        Toast.makeText(this@startVideoRecording, "Đã lưu video thành công vào Bộ sưu tập!", Toast.LENGTH_LONG).show()
                        updateGalleryThumbnail(savedUri)
                    } else {
                        Log.e(TAG, "Lỗi quay video: ${recordEvent.error}")
                        Toast.makeText(this@startVideoRecording, "Lỗi quay video: ${recordEvent.error}", Toast.LENGTH_SHORT).show()
                    }
                    recording = null
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Không thể bắt đầu quay video: ${e.message}")
        Toast.makeText(this, "Không thể quay video: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.stopVideoRecording() {
    if (recording != null) {
        try {
            recording?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi dừng quay video: ${e.message}")
        }
        recording = null
    } else {
        isRecordingVideo = false
        uiHandler.removeCallbacks(videoTimerRunnable)
        tvRecTimerBadge.visibility = View.GONE
    }
}

internal fun MainActivity.openGallery() {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    } catch (_: Exception) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_GALLERY)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở Bộ sưu tập", Toast.LENGTH_SHORT).show()
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.setupGestures() {
    scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentZoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1.0f
            val delta = detector.scaleFactor
            val targetZoom = (currentZoom * delta).coerceIn(1.0f, 5.0f)
            camera?.cameraControl?.setZoomRatio(targetZoom)
            updateZoomChipsUi(targetZoom)
            return true
        }
    })

    gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            triggerFocusAndMetering(e.x, e.y)
            return true
        }
    })

    previewView.setOnTouchListener { _, event ->
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        true
    }
}

internal fun MainActivity.triggerFocusAndMetering(x: Float, y: Float) {
    val control = camera?.cameraControl ?: return
    val factory = previewView.meteringPointFactory
    val point = factory.createPoint(x, y)
    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
        .setAutoCancelDuration(3, TimeUnit.SECONDS)
        .build()
    control.startFocusAndMetering(action)

    focusReticle.x = x - (focusReticle.width / 2)
    focusReticle.y = y - (focusReticle.height / 2)
    focusReticle.visibility = View.VISIBLE
    focusReticle.alpha = 1.0f
    focusReticle.scaleX = 1.4f
    focusReticle.scaleY = 1.4f

    focusReticle.animate()
        .scaleX(1.0f)
        .scaleY(1.0f)
        .setInterpolator(OvershootInterpolator(1.2f))
        .setDuration(350)
        .withEndAction {
            focusReticle.animate()
                .alpha(0.0f)
                .setStartDelay(600)
                .setDuration(400)
                .withEndAction { focusReticle.visibility = View.GONE }
                .start()
        }
        .start()
}

internal fun MainActivity.selectZoomRatio(targetZoom: Float) {
    val min = camera?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1.0f
    val max = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 5.0f
    val clamped = targetZoom.coerceIn(min, max)
    camera?.cameraControl?.setZoomRatio(clamped)
    updateZoomChipsUi(clamped)
}

internal fun MainActivity.updateZoomChipsUi(currentZoom: Float) {
    currentZoomRatio = currentZoom
    var closestZoom = 1.0f
    var minDiff = Float.MAX_VALUE
    for (zoom in zoomChips.keys) {
        val diff = Math.abs(zoom - currentZoom)
        if (diff < minDiff) {
            minDiff = diff
            closestZoom = zoom
        }
    }

    for ((zoom, chip) in zoomChips) {
        if (zoom == closestZoom) {
            chip.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14f).toFloat()
                setColor(Color.parseColor("#1D5CE5"))
            }
            chip.setTextColor(Color.WHITE)
        } else {
            chip.background = null
            chip.setTextColor(Color.parseColor("#475569"))
        }
    }
}

internal fun MainActivity.startCamera() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
        cameraProvider = cameraProviderFuture.get()

        val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

        val targetRot = when (orientationOption) {
            ScreenOrientationOption.LANDSCAPE -> if (displayRotation == Surface.ROTATION_270) Surface.ROTATION_270 else Surface.ROTATION_90
            ScreenOrientationOption.PORTRAIT -> Surface.ROTATION_0
            ScreenOrientationOption.AUTO -> displayRotation
        }
        currentTargetRotation = targetRot

        val previewInstance = Preview.Builder()
            .setTargetResolution(targetResolution)
            .setTargetRotation(targetRot)
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        this.preview = previewInstance

        @Suppress("DEPRECATION")
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(targetResolution)
            .setTargetRotation(targetRot)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            if (isStreaming && mjpegServer != null) {
                mjpegServer?.pushFrame(imageProxy)
            } else {
                imageProxy.close()
            }
        }

        imageCapture = ImageCapture.Builder()
            .setTargetRotation(targetRot)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        val videoCaptureInstance = VideoCapture.withOutput(recorder)
        this.videoCapture = videoCaptureInstance

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider?.unbindAll()
            try {
                // Cố gắng bind cả ImageCapture và VideoCapture đồng thời
                camera = cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    previewInstance,
                    imageAnalysis,
                    imageCapture,
                    videoCaptureInstance
                )
            } catch (e: Exception) {
                Log.w(TAG, "Không thể bind cả 4 use case cùng lúc (${e.message}), bind theo mode hiện tại ($currentMode)")
                val activeUseCase: UseCase = if (currentMode == CameraMode.VIDEO) videoCaptureInstance else imageCapture!!
                camera = cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    previewInstance,
                    imageAnalysis,
                    activeUseCase
                )
            }

            observeCameraState()
            setupOrientationListener()

            if (!isStreaming || mjpegServer == null || mjpegServer?.isAlive != true) {
                startStreaming()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khởi chạy camera: ${e.message}")
        }
    }, ContextCompat.getMainExecutor(this))
}

internal fun MainActivity.observeCameraState() {
    camera?.cameraInfo?.zoomState?.observe(this) { zoomState ->
        updateZoomChipsUi(zoomState.zoomRatio)
    }

    camera?.cameraInfo?.torchState?.observe(this) { torchState ->
        isTorchOn = torchState == TorchState.ON
        tvFlashIcon.setTextColor(if (isTorchOn) Color.parseColor("#F5C518") else Color.WHITE)
    }
}

internal fun MainActivity.switchCamera() {
    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
        CameraSelector.LENS_FACING_FRONT
    } else {
        CameraSelector.LENS_FACING_BACK
    }
    try {
        imageAnalysis?.clearAnalyzer()
    } catch (_: Exception) {}
    startCamera()
    val camName = if (lensFacing == CameraSelector.LENS_FACING_BACK) "Camera Sau (Rear)" else "Camera Trước (Front)"
    Toast.makeText(this, "Đã đổi sang $camName", Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.toggleTorch(): Boolean {
    if (camera?.cameraInfo?.hasFlashUnit() != true) return false
    val target = !isTorchOn
    camera?.cameraControl?.enableTorch(target)
    return target
}

internal fun MainActivity.cycleResolution() {
    when (targetResolution.width) {
        1280 -> {
            targetResolution = Size(1920, 1080)
            mjpegServer?.targetOutputResolution = targetResolution
            tvResolutionBadge.text = "1080p"
            Toast.makeText(this, "Độ phân giải: 1080p FHD (16:9)", Toast.LENGTH_SHORT).show()
        }
        1920 -> {
            targetResolution = Size(854, 480)
            mjpegServer?.targetOutputResolution = targetResolution
            tvResolutionBadge.text = "480p"
            Toast.makeText(this, "Độ phân giải: 480p SD (16:9)", Toast.LENGTH_SHORT).show()
        }
        else -> {
            targetResolution = Size(1280, 720)
            mjpegServer?.targetOutputResolution = targetResolution
            tvResolutionBadge.text = "720p"
            Toast.makeText(this, "Độ phân giải: 720p HD (16:9)", Toast.LENGTH_SHORT).show()
        }
    }
    startCamera()
}

internal fun MainActivity.cycleQuality() {
    val currentQ = mjpegServer?.jpegQuality ?: 80
    val newQ = when (currentQ) {
        80 -> 95
        95 -> 60
        else -> 80
    }
    mjpegServer?.jpegQuality = newQ
    Toast.makeText(this, "Chất lượng JPEG: $newQ%", Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.takePhoto() {
    var capture = imageCapture
    if (capture == null || cameraProvider?.isBound(capture) != true) {
        try {
            if (videoCapture != null && cameraProvider?.isBound(videoCapture!!) == true) {
                cameraProvider?.unbind(videoCapture!!)
            }
            capture = ImageCapture.Builder()
                .setTargetRotation(currentTargetRotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture
            val p = preview
            val a = imageAnalysis
            val cp = cameraProvider
            if (cp != null && p != null && a != null) {
                camera = cp.bindToLifecycle(
                    this,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    p,
                    a,
                    capture
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi bind imageCapture trong takePhoto: ${e.message}")
        }
    }

    val activeCapture = imageCapture ?: return
    val filename = "ROBOT_${System.currentTimeMillis()}.jpg"

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RobotCamera")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    activeCapture.takePicture(
        outputOptions,
        cameraExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                runOnUiThread {
                    Toast.makeText(this@takePhoto, "Đã lưu ảnh vào Bộ sưu tập!", Toast.LENGTH_SHORT).show()
                    outputFileResults.savedUri?.let { uri ->
                        updateGalleryThumbnail(uri)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    Toast.makeText(this@takePhoto, "Lỗi chụp ảnh: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
}

internal fun MainActivity.updateGalleryThumbnail(uri: Uri) {
    try {
        val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                contentResolver.loadThumbnail(uri, Size(120, 120), null)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        } ?: run {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }

        if (bitmap != null) {
            val circularBitmap = getCircularBitmap(bitmap)
            btnGalleryThumbnail.setImageBitmap(circularBitmap)
            btnGalleryThumbnail.setPadding(0, 0, 0, 0)
        }
    } catch (_: Exception) {}
}

internal fun MainActivity.getCircularBitmap(bitmap: Bitmap): Bitmap {
    val size = Math.min(bitmap.width, bitmap.height)
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
    canvas.drawOval(rect, paint)
    paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
    val left = (size - bitmap.width) / 2f
    val top = (size - bitmap.height) / 2f
    canvas.drawBitmap(bitmap, left, top, paint)
    return output
}

internal fun MainActivity.requestPermissions() {
    if (allPermissionsGranted()) {
        if (currentRole == AppRole.STREAMER || (currentRole == AppRole.CONTROLLER && controllerCamSource == "LOCAL")) {
            startCamera()
        }
    } else {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }
}

internal fun MainActivity.allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
}

internal fun MainActivity.setupOrientationListener() {
    if (orientationEventListener != null) return

    orientationEventListener = object : OrientationEventListener(this) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            if (orientationOption != ScreenOrientationOption.AUTO) return

            val newRotation = when (orientation) {
                in 45..134 -> Surface.ROTATION_270
                in 135..224 -> Surface.ROTATION_180
                in 225..314 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }

            if (newRotation != currentTargetRotation) {
                val wasLandscape = (currentTargetRotation == Surface.ROTATION_90 || currentTargetRotation == Surface.ROTATION_270)
                val isLandscape = (newRotation == Surface.ROTATION_90 || newRotation == Surface.ROTATION_270)

                currentTargetRotation = newRotation

                if (wasLandscape != isLandscape) {
                    // Chuyển đổi giữa chế độ Đứng (9:16) và Ngang (16:9)
                    // Cần rebind để CameraX phân bổ buffer độ phân giải ngang chuẩn 1280x720
                    startCamera()
                } else {
                    try {
                        preview?.targetRotation = newRotation
                        imageAnalysis?.targetRotation = newRotation
                        imageCapture?.targetRotation = newRotation
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi cập nhật targetRotation: ${e.message}")
                    }
                }
            }
        }
    }
    if (orientationEventListener?.canDetectOrientation() == true) {
        orientationEventListener?.enable()
    }
}
