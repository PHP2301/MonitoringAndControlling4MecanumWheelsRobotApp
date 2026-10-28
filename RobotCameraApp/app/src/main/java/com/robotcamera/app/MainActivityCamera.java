package com.robotcamera.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.TorchState;
import androidx.camera.core.UseCase;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CameraX: khởi động camera, chụp ảnh, quay video, zoom, lấy nét, flash, xoay màn hình, quyền truy cập.
 */
public class MainActivityCamera {

    private static final String TAG = "MainActivityCamera";

    public static void handleShutterClick(MainActivity act) {
        if (act.currentMode == MainActivity.CameraMode.PHOTO) {
            MainActivityUiHelpers.triggerShutterFlash(act);
            takePhoto(act);
            ButtonSyncManager.sendButtonEvent("TAKE_PHOTO", "CLICK");
        } else {
            toggleVideoRecording(act);
            if (act.isRecordingVideo) {
                ButtonSyncManager.sendButtonEvent("START_RECORD", "CLICK");
            } else {
                ButtonSyncManager.sendButtonEvent("STOP_RECORD", "CLICK");
            }
        }
    }

    public static void toggleVideoRecording(MainActivity act) {
        if (act.recording == null) {
            startVideoRecording(act);
        } else {
            stopVideoRecording(act);
        }
    }

    public static void startVideoRecording(MainActivity act) {
        VideoCapture<Recorder> capture = act.videoCapture;
        if (capture == null || act.cameraProvider == null || !act.cameraProvider.isBound(capture)) {
            try {
                if (act.imageCapture != null && act.cameraProvider != null && act.cameraProvider.isBound(act.imageCapture)) {
                    act.cameraProvider.unbind(act.imageCapture);
                }
                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build();
                capture = VideoCapture.withOutput(recorder);
                act.videoCapture = capture;
                if (act.cameraProvider != null && act.preview != null && act.imageAnalysis != null) {
                    act.camera = act.cameraProvider.bindToLifecycle(
                            act,
                            new CameraSelector.Builder().requireLensFacing(act.lensFacing).build(),
                            act.preview,
                            act.imageAnalysis,
                            capture
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "Lỗi bind videoCapture: " + e.getMessage());
            }
        }

        VideoCapture<Recorder> activeCapture = act.videoCapture;
        if (activeCapture == null) {
            Toast.makeText(act, "Camera Video đang chuẩn bị...", Toast.LENGTH_SHORT).show();
            startCamera(act);
            return;
        }

        if (act.recording != null) {
            Log.w(TAG, "Đang có phiên quay video hoạt động");
            return;
        }

        String name = "ROBOT_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name + ".mp4");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RobotCamera");
        }

        MediaStoreOutputOptions mediaStoreOutput = new MediaStoreOutputOptions.Builder(
                act.getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build();

        PendingRecording recordingBuilder = activeCapture.getOutput().prepareRecording(act, mediaStoreOutput);
        if (ContextCompat.checkSelfPermission(act, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                recordingBuilder.withAudioEnabled();
            } catch (Exception e) {
                Log.w(TAG, "Không thể bật âm thanh: " + e.getMessage());
            }
        }

        try {
            act.recording = recordingBuilder.start(ContextCompat.getMainExecutor(act), recordEvent -> {
                if (recordEvent instanceof VideoRecordEvent.Start) {
                    act.isRecordingVideo = true;
                    act.recordingStartTime = SystemClock.elapsedRealtime();
                    if (act.tvRecTimerBadge != null) {
                        act.tvRecTimerBadge.setVisibility(View.VISIBLE);
                        act.tvRecTimerBadge.setText("● REC 00:00");
                    }
                    act.uiHandler.post(act.videoTimerRunnable);

                    // Nút Shutter đổi sang hình vuông đỏ (Nút Dừng)
                    if (act.shutterCore != null) {
                        GradientDrawable rectDrawable = new GradientDrawable();
                        rectDrawable.setShape(GradientDrawable.RECTANGLE);
                        rectDrawable.setCornerRadius((float) MainActivityUiHelpers.dp(act, 10f));
                        rectDrawable.setColor(Color.parseColor("#E53935"));
                        act.shutterCore.setBackground(rectDrawable);
                        act.shutterCore.animate().scaleX(0.55f).scaleY(0.55f).setDuration(180).start();
                    }

                    Toast.makeText(act, "Đang quay video: " + name + ".mp4", Toast.LENGTH_SHORT).show();
                } else if (recordEvent instanceof VideoRecordEvent.Finalize) {
                    VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) recordEvent;
                    act.isRecordingVideo = false;
                    act.uiHandler.removeCallbacks(act.videoTimerRunnable);
                    if (act.tvRecTimerBadge != null) {
                        act.tvRecTimerBadge.setVisibility(View.GONE);
                    }

                    // Trả nút Shutter về hình tròn đỏ
                    if (act.shutterCore != null) {
                        GradientDrawable circleDrawable = new GradientDrawable();
                        circleDrawable.setShape(GradientDrawable.OVAL);
                        circleDrawable.setColor(Color.parseColor("#E53935"));
                        act.shutterCore.setBackground(circleDrawable);
                        act.shutterCore.animate().scaleX(0.85f).scaleY(0.85f).setDuration(180).start();
                    }

                    if (!finalizeEvent.hasError()) {
                        Uri savedUri = finalizeEvent.getOutputResults().getOutputUri();
                        Toast.makeText(act, "Đã lưu video thành công vào Bộ sưu tập!", Toast.LENGTH_LONG).show();
                        updateGalleryThumbnail(act, savedUri);
                    } else {
                        Log.e(TAG, "Lỗi quay video: " + finalizeEvent.getError());
                        Toast.makeText(act, "Lỗi quay video: " + finalizeEvent.getError(), Toast.LENGTH_SHORT).show();
                    }
                    act.recording = null;
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Không thể bắt đầu quay video: " + e.getMessage());
            Toast.makeText(act, "Không thể quay video: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void stopVideoRecording(MainActivity act) {
        if (act.recording != null) {
            try {
                act.recording.stop();
            } catch (Exception e) {
                Log.e(TAG, "Lỗi dừng quay video: " + e.getMessage());
            }
            act.recording = null;
        } else {
            act.isRecordingVideo = false;
            act.uiHandler.removeCallbacks(act.videoTimerRunnable);
            if (act.tvRecTimerBadge != null) {
                act.tvRecTimerBadge.setVisibility(View.GONE);
            }
        }
    }

    public static void openGallery(MainActivity act) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setType("image/*");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(intent);
        } catch (Exception ignored) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_GALLERY);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                act.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(act, "Không thể mở Bộ sưu tập", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void setupGestures(MainActivity act) {
        act.scaleGestureDetector = new ScaleGestureDetector(act, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float currentZoom = 1.0f;
                if (act.camera != null && act.camera.getCameraInfo().getZoomState().getValue() != null) {
                    currentZoom = act.camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
                }
                float delta = detector.getScaleFactor();
                float targetZoom = Math.max(1.0f, Math.min(5.0f, currentZoom * delta));
                if (act.camera != null) {
                    act.camera.getCameraControl().setZoomRatio(targetZoom);
                }
                updateZoomChipsUi(act, targetZoom);
                return true;
            }
        });

        act.gestureDetector = new GestureDetector(act, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                triggerFocusAndMetering(act, e.getX(), e.getY());
                return true;
            }
        });

        if (act.previewView != null) {
            act.previewView.setOnTouchListener((v, event) -> {
                if (act.scaleGestureDetector != null) act.scaleGestureDetector.onTouchEvent(event);
                if (act.gestureDetector != null) act.gestureDetector.onTouchEvent(event);
                return true;
            });
        }
    }

    public static void triggerFocusAndMetering(MainActivity act, float x, float y) {
        if (act.camera == null || act.previewView == null) return;
        CameraControl control = act.camera.getCameraControl();
        MeteringPointFactory factory = act.previewView.getMeteringPointFactory();
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(
                point, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE
        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build();
        control.startFocusAndMetering(action);

        if (act.focusReticle != null) {
            act.focusReticle.setX(x - (act.focusReticle.getWidth() / 2f));
            act.focusReticle.setY(y - (act.focusReticle.getHeight() / 2f));
            act.focusReticle.setVisibility(View.VISIBLE);
            act.focusReticle.setAlpha(1.0f);
            act.focusReticle.setScaleX(1.4f);
            act.focusReticle.setScaleY(1.4f);

            act.focusReticle.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .setDuration(350)
                    .withEndAction(() -> {
                        act.focusReticle.animate()
                                .alpha(0.0f)
                                .setStartDelay(600)
                                .setDuration(400)
                                .withEndAction(() -> act.focusReticle.setVisibility(View.GONE))
                                .start();
                    })
                    .start();
        }
    }

    public static void selectZoomRatio(MainActivity act, float targetZoom) {
        float min = 1.0f;
        float max = 5.0f;
        if (act.camera != null && act.camera.getCameraInfo().getZoomState().getValue() != null) {
            min = act.camera.getCameraInfo().getZoomState().getValue().getMinZoomRatio();
            max = act.camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
        }
        float clamped = Math.max(min, Math.min(max, targetZoom));
        if (act.camera != null) {
            act.camera.getCameraControl().setZoomRatio(clamped);
        }
        updateZoomChipsUi(act, clamped);
    }

    public static void updateZoomChipsUi(MainActivity act, float currentZoom) {
        act.currentZoomRatio = currentZoom;
        float closestZoom = 1.0f;
        float minDiff = Float.MAX_VALUE;
        for (Float zoom : act.zoomChips.keySet()) {
            float diff = Math.abs(zoom - currentZoom);
            if (diff < minDiff) {
                minDiff = diff;
                closestZoom = zoom;
            }
        }

        for (Map.Entry<Float, TextView> entry : act.zoomChips.entrySet()) {
            float zoom = entry.getKey();
            TextView chip = entry.getValue();
            if (Math.abs(zoom - closestZoom) < 0.001f) {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 14f));
                bg.setColor(Color.parseColor("#1D5CE5"));
                chip.setBackground(bg);
                chip.setTextColor(Color.WHITE);
            } else {
                chip.setBackground(null);
                chip.setTextColor(Color.parseColor("#475569"));
            }
        }
    }

    public static void startCamera(MainActivity act) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(act);
        cameraProviderFuture.addListener(() -> {
            try {
                act.cameraProvider = cameraProviderFuture.get();

                int displayRotation = Surface.ROTATION_0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (act.getDisplay() != null) {
                        displayRotation = act.getDisplay().getRotation();
                    }
                } else {
                    displayRotation = act.getWindowManager().getDefaultDisplay().getRotation();
                }

                int targetRot;
                switch (act.orientationOption) {
                    case LANDSCAPE:
                        targetRot = (displayRotation == Surface.ROTATION_270) ? Surface.ROTATION_270 : Surface.ROTATION_90;
                        break;
                    case PORTRAIT:
                        targetRot = Surface.ROTATION_0;
                        break;
                    case AUTO:
                    default:
                        targetRot = displayRotation;
                        break;
                }
                act.currentTargetRotation = targetRot;

                Preview.Builder previewBuilder = new Preview.Builder()
                        .setTargetResolution(act.targetResolution)
                        .setTargetRotation(targetRot);
                act.preview = previewBuilder.build();
                if (act.previewView != null) {
                    act.preview.setSurfaceProvider(act.previewView.getSurfaceProvider());
                }

                ImageAnalysis.Builder analysisBuilder = new ImageAnalysis.Builder()
                        .setTargetResolution(act.targetResolution)
                        .setTargetRotation(targetRot)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888);
                act.imageAnalysis = analysisBuilder.build();
                act.imageAnalysis.setAnalyzer(act.cameraExecutor, imageProxy -> {
                    if (act.isStreaming && act.mjpegServer != null) {
                        act.mjpegServer.pushFrame(imageProxy);
                    } else {
                        imageProxy.close();
                    }
                });

                act.imageCapture = new ImageCapture.Builder()
                        .setTargetRotation(targetRot)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build();
                act.videoCapture = VideoCapture.withOutput(recorder);

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(act.lensFacing)
                        .build();

                try {
                    act.cameraProvider.unbindAll();
                    try {
                        act.camera = act.cameraProvider.bindToLifecycle(
                                act,
                                cameraSelector,
                                act.preview,
                                act.imageAnalysis,
                                act.imageCapture,
                                act.videoCapture
                        );
                    } catch (Exception e) {
                        Log.w(TAG, "Không thể bind cả 4 use case cùng lúc, bind theo mode hiện tại (" + act.currentMode + "): " + e.getMessage());
                        UseCase activeUseCase = (act.currentMode == MainActivity.CameraMode.VIDEO) ? act.videoCapture : act.imageCapture;
                        act.camera = act.cameraProvider.bindToLifecycle(
                                act,
                                cameraSelector,
                                act.preview,
                                act.imageAnalysis,
                                activeUseCase
                        );
                    }

                    observeCameraState(act);
                    setupOrientationListener(act);

                    if (!act.isStreaming || act.mjpegServer == null || !act.mjpegServer.isAlive()) {
                        act.startStreaming();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi khởi chạy camera: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "Lỗi ProcessCameraProvider: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(act));
    }

    public static void observeCameraState(MainActivity act) {
        if (act.camera == null) return;
        CameraInfo info = act.camera.getCameraInfo();
        info.getZoomState().observe(act, zoomState -> {
            if (zoomState != null) {
                updateZoomChipsUi(act, zoomState.getZoomRatio());
            }
        });

        info.getTorchState().observe(act, torchState -> {
            act.isTorchOn = (torchState != null && torchState == TorchState.ON);
            if (act.tvFlashIcon != null) {
                act.tvFlashIcon.setTextColor(act.isTorchOn ? Color.parseColor("#F5C518") : Color.WHITE);
            }
        });
    }

    public static void switchCamera(MainActivity act) {
        act.lensFacing = (act.lensFacing == CameraSelector.LENS_FACING_BACK) ?
                CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        try {
            if (act.imageAnalysis != null) {
                act.imageAnalysis.clearAnalyzer();
            }
        } catch (Exception ignored) {}
        startCamera(act);
        String camName = (act.lensFacing == CameraSelector.LENS_FACING_BACK) ?
                "Camera Sau (Rear)" : "Camera Trước (Front)";
        Toast.makeText(act, "Đã đổi sang " + camName, Toast.LENGTH_SHORT).show();
    }

    public static boolean toggleTorch(MainActivity act) {
        if (act.camera == null || !act.camera.getCameraInfo().hasFlashUnit()) return false;
        boolean target = !act.isTorchOn;
        act.camera.getCameraControl().enableTorch(target);
        return target;
    }

    public static void cycleResolution(MainActivity act) {
        int w = act.targetResolution.getWidth();
        if (w == 1280) {
            act.targetResolution = new Size(1920, 1080);
            if (act.mjpegServer != null) act.mjpegServer.targetOutputResolution = act.targetResolution;
            if (act.tvResolutionBadge != null) act.tvResolutionBadge.setText("1080p");
            Toast.makeText(act, "Độ phân giải: 1080p FHD (16:9)", Toast.LENGTH_SHORT).show();
        } else if (w == 1920) {
            act.targetResolution = new Size(854, 480);
            if (act.mjpegServer != null) act.mjpegServer.targetOutputResolution = act.targetResolution;
            if (act.tvResolutionBadge != null) act.tvResolutionBadge.setText("480p");
            Toast.makeText(act, "Độ phân giải: 480p SD (16:9)", Toast.LENGTH_SHORT).show();
        } else {
            act.targetResolution = new Size(1280, 720);
            if (act.mjpegServer != null) act.mjpegServer.targetOutputResolution = act.targetResolution;
            if (act.tvResolutionBadge != null) act.tvResolutionBadge.setText("720p");
            Toast.makeText(act, "Độ phân giải: 720p HD (16:9)", Toast.LENGTH_SHORT).show();
        }
        startCamera(act);
    }

    public static void cycleQuality(MainActivity act) {
        int currentQ = (act.mjpegServer != null) ? act.mjpegServer.jpegQuality : 80;
        int newQ;
        if (currentQ == 80) newQ = 95;
        else if (currentQ == 95) newQ = 60;
        else newQ = 80;
        if (act.mjpegServer != null) {
            act.mjpegServer.jpegQuality = newQ;
        }
        Toast.makeText(act, "Chất lượng JPEG: " + newQ + "%", Toast.LENGTH_SHORT).show();
    }

    public static void takePhoto(MainActivity act) {
        ImageCapture capture = act.imageCapture;
        if (capture == null || act.cameraProvider == null || !act.cameraProvider.isBound(capture)) {
            try {
                if (act.videoCapture != null && act.cameraProvider != null && act.cameraProvider.isBound(act.videoCapture)) {
                    act.cameraProvider.unbind(act.videoCapture);
                }
                capture = new ImageCapture.Builder()
                        .setTargetRotation(act.currentTargetRotation)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();
                act.imageCapture = capture;
                if (act.cameraProvider != null && act.preview != null && act.imageAnalysis != null) {
                    act.camera = act.cameraProvider.bindToLifecycle(
                            act,
                            new CameraSelector.Builder().requireLensFacing(act.lensFacing).build(),
                            act.preview,
                            act.imageAnalysis,
                            capture
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "Lỗi bind imageCapture trong takePhoto: " + e.getMessage());
            }
        }

        ImageCapture activeCapture = act.imageCapture;
        if (activeCapture == null) return;
        String filename = "ROBOT_" + System.currentTimeMillis() + ".jpg";

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RobotCamera");
        }

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                act.getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
        ).build();

        activeCapture.takePicture(
                outputOptions,
                act.cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        act.runOnUiThread(() -> {
                            Toast.makeText(act, "Đã lưu ảnh vào Bộ sưu tập!", Toast.LENGTH_SHORT).show();
                            Uri uri = outputFileResults.getSavedUri();
                            if (uri != null) {
                                updateGalleryThumbnail(act, uri);
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        act.runOnUiThread(() -> {
                            Toast.makeText(act, "Lỗi chụp ảnh: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }

    public static void updateGalleryThumbnail(MainActivity act, Uri uri) {
        try {
            Bitmap bitmap = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    bitmap = act.getContentResolver().loadThumbnail(uri, new Size(120, 120), null);
                } catch (Exception ignored) {}
            }
            if (bitmap == null) {
                try (InputStream stream = act.getContentResolver().openInputStream(uri)) {
                    if (stream != null) {
                        bitmap = BitmapFactory.decodeStream(stream);
                    }
                }
            }

            if (bitmap != null && act.btnGalleryThumbnail != null) {
                Bitmap circularBitmap = getCircularBitmap(bitmap);
                act.btnGalleryThumbnail.setImageBitmap(circularBitmap);
                act.btnGalleryThumbnail.setPadding(0, 0, 0, 0);
            }
        } catch (Exception ignored) {}
    }

    public static Bitmap getCircularBitmap(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF rect = new RectF(0f, 0f, (float) size, (float) size);
        canvas.drawOval(rect, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        float left = (size - bitmap.getWidth()) / 2f;
        float top = (size - bitmap.getHeight()) / 2f;
        canvas.drawBitmap(bitmap, left, top, paint);
        return output;
    }

    public static void requestPermissions(MainActivity act) {
        if (allPermissionsGranted(act)) {
            if (act.currentRole == MainActivity.AppRole.STREAMER ||
                    (act.currentRole == MainActivity.AppRole.CONTROLLER && "LOCAL".equals(act.controllerCamSource))) {
                startCamera(act);
            }
        } else {
            ActivityCompat.requestPermissions(act, MainActivity.REQUIRED_PERMISSIONS, MainActivity.REQUEST_CODE_PERMISSIONS);
        }
    }

    public static boolean allPermissionsGranted(Context ctx) {
        for (String perm : MainActivity.REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static void setupOrientationListener(MainActivity act) {
        if (act.orientationEventListener != null) return;

        act.orientationEventListener = new OrientationEventListener(act) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN) return;
                if (act.orientationOption != MainActivity.ScreenOrientationOption.AUTO) return;

                int newRotation;
                if (orientation >= 45 && orientation <= 134) {
                    newRotation = Surface.ROTATION_270;
                } else if (orientation >= 135 && orientation <= 224) {
                    newRotation = Surface.ROTATION_180;
                } else if (orientation >= 225 && orientation <= 314) {
                    newRotation = Surface.ROTATION_90;
                } else {
                    newRotation = Surface.ROTATION_0;
                }

                if (newRotation != act.currentTargetRotation) {
                    boolean wasLandscape = (act.currentTargetRotation == Surface.ROTATION_90 || act.currentTargetRotation == Surface.ROTATION_270);
                    boolean isLandscape = (newRotation == Surface.ROTATION_90 || newRotation == Surface.ROTATION_270);

                    act.currentTargetRotation = newRotation;

                    if (wasLandscape != isLandscape) {
                        startCamera(act);
                    } else {
                        try {
                            if (act.preview != null) act.preview.setTargetRotation(newRotation);
                            if (act.imageAnalysis != null) act.imageAnalysis.setTargetRotation(newRotation);
                            if (act.imageCapture != null) act.imageCapture.setTargetRotation(newRotation);
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi cập nhật targetRotation: " + e.getMessage());
                        }
                    }
                }
            }
        };

        if (act.orientationEventListener.canDetectOrientation()) {
            act.orientationEventListener.enable();
        }
    }
}
