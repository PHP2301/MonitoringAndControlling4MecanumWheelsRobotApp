package com.robotcamera.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.view.PreviewView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Robot Camera App - Giao diện Samsung Galaxy Camera (One UI) chuẩn 100%
 *
 * Tính năng thiết kế:
 * 1. Viewfinder chuẩn 4:3 với Lưới 3x3 và Thước cân bằng chân trời (Level Indicator) màu vàng ở tâm.
 * 2. Nút Scan thông minh [T] màu vàng ở góc phải màn hình xem trước (Bấm là xem & copy IP Stream).
 * 3. Cụm chọn Zoom Segmented Pill: [.5], [1x], [2] với chip tròn trắng nổi bật + nút hiệu ứng 4 chấm.
 * 4. Dải chọn chế độ rút gọn ĐÚNG YÊU CẦU: Chỉ có 2 chế độ [ẢNH] và [VIDEO].
 * 5. Cụm điều khiển chính:
 *    - Trái: Vòng tròn xem ảnh thư viện (Gallery Thumbnail).
 *    - Giữa: Nút Shutter cơ học (Trắng tinh tế khi ở [ẢNH] / Đỏ quay phim khi ở [VIDEO]).
 *    - Phải: Nút đảo Camera trước/sau xoay 180°.
 * 6. Vẫn giữ nguyên máy chủ phát MJPEG Server 8080 kết nối siêu mượt với app điều khiển xe Robot trên PC!
 *
 * Ghi chú cấu trúc: MainActivity được tách thành các file helper module:
 *  - MainActivity.java: Trạng thái (fields), vòng đời Activity, chọn vai trò, đồng bộ nút PC, CommandListener.
 *  - MainActivityController.java: Vai trò THIẾT BỊ ĐIỀU KHIỂN: bố cục chia đôi Camera/Bảng lái, thanh UDP góc trái, thanh Camera IP góc phải.
 *  - MainActivityStreamer.java: Vai trò THIẾT BỊ STREAMING: máy chủ MJPEG 8080, IP WiFi, chia sẻ link stream.
 *  - MainActivityLayout.java: Dựng giao diện gốc (setupUI) và các cụm nút của màn hình Camera (Top bar, Zoom, Shutter...).
 *  - MainActivityCamera.java: CameraX: khởi động camera, chụp ảnh, quay video, zoom, lấy nét, flash, xoay màn hình, quyền truy cập.
 *  - MainActivitySettings.java: Hộp thoại Cài đặt (chế độ độ trễ, chất lượng, khóa xoay màn hình).
 *  - MainActivityUiHelpers.java: Tiện ích giao diện dùng chung: dp(), spacer, hiệu ứng chạm lò xo, chớp màn trập.
 */
public class MainActivity extends AppCompatActivity implements MjpegServer.CommandListener {

    public static final String TAG = "RobotCamera";
    public static final int REQUEST_CODE_PERMISSIONS = 10;
    public static final int STREAM_PORT = 8080;

    public static final String[] REQUIRED_PERMISSIONS = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ? new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}
            : new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};

    public enum AppRole {
        NONE, CONTROLLER, STREAMER
    }
    public AppRole currentRole = AppRole.NONE;

    public RoleSelectionView roleSelectionView;
    public RemoteMjpegView remoteMjpegView;

    public enum CameraMode {
        PHOTO, VIDEO, CONTROL
    }

    public enum ScreenOrientationOption {
        AUTO, LANDSCAPE, PORTRAIT
    }
    public ScreenOrientationOption orientationOption = ScreenOrientationOption.AUTO;

    // CameraX components
    public Camera camera;
    public Preview preview;
    public ImageCapture imageCapture;
    public ImageAnalysis imageAnalysis;
    public VideoCapture<Recorder> videoCapture;
    public Recording recording;
    public ProcessCameraProvider cameraProvider;
    public int lensFacing = CameraSelector.LENS_FACING_BACK;
    public Size targetResolution = new Size(1280, 720);

    // Orientation sensor
    public OrientationEventListener orientationEventListener;
    public int currentTargetRotation = Surface.ROTATION_0;

    // MJPEG Stream Server
    public MjpegServer mjpegServer;
    public boolean isStreaming = false;
    public final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    // Mode hiện tại: Mặc định là ẢNH
    public CameraMode currentMode = CameraMode.PHOTO;
    public boolean isRecordingVideo = false;
    public long recordingStartTime = 0L;

    // UI Root
    public RelativeLayout rootLayout;
    public PreviewView previewView;
    public ViewfinderOverlayView viewfinderOverlay;
    public View focusReticle;
    public View shutterFlashOverlay;
    public FpvHudOverlayView fpvHudOverlay;
    public RelativeLayout overlayContainer;

    // Top Bar Controls
    public LinearLayout topBarLayout;
    public TextView tvFlashIcon;
    public TextView tvResolutionBadge;
    public View btnMotionLiveIcon;
    public TextView tvMotionLiveText;
    public TextView tvRecTimerBadge;
    public TextView tvIpDisplayBadge;
    public View ipBadgeContainer;
    public TextView tvGimbalStatusBadge;
    public boolean isTorchOn = false;

    // Zoom Controls
    public LinearLayout zoomContainer;
    public LinearLayout zoomPillContainer;
    public final Map<Float, TextView> zoomChips = new HashMap<>();
    public float currentZoomRatio = 1.0f;

    // Smart Scan Button [T]
    public View btnSmartScan;

    // Bottom Action Controls
    public RelativeLayout mainActionRow;
    public ImageView btnGalleryThumbnail;
    public RelativeLayout shutterContainer;
    public View shutterCore;
    public View btnSwitchCamera;
    public ImageView ivSwitchCameraIcon;

    // Mode Selector Texts
    public TextView tvModePhoto;
    public TextView tvModeVideo;
    public TextView tvModeControl;

    // Robot UDP Control Dashboard
    public RobotUdpManager udpManager;
    public AutoFollowManager autoFollowManager;
    public VisualDistanceDetector visualDistanceDetector;
    public RobotControlDashboardView controlDashboardView;

    // Controller Mode Camera Components
    public LinearLayout controllerLocalCamBar;
    public TextView tvControllerLocalIp;
    public String controllerCamSource = "LOCAL"; // "LOCAL" or "REMOTE"

    // Controller Mode Top-Left Bar & UDP Controls
    public LinearLayout controllerTopLeftBar;
    public EditText etUdpAddress;
    public TextView btnConnectUdp;
    public TextView tvUdpStatusBadge;

    // Gestures
    public ScaleGestureDetector scaleGestureDetector;
    public GestureDetector gestureDetector;

    // Handlers
    public final Handler uiHandler = new Handler(Looper.getMainLooper());
    public final Runnable videoTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecordingVideo) {
                long elapsed = (SystemClock.elapsedRealtime() - recordingStartTime) / 1000;
                long mins = elapsed / 60;
                long secs = elapsed % 60;
                if (tvRecTimerBadge != null) {
                    tvRecTimerBadge.setText(String.format(Locale.getDefault(), "● REC %02d:%02d", mins, secs));
                }
                uiHandler.postDelayed(this, 1000);
            }
        }
    };

    public ButtonSyncManager.OnButtonEventListener buttonSyncListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        setupUI();
        setupGestures();
        setupButtonSyncListener();
        checkInitialRole();
    }

    public void checkInitialRole() {
        SharedPreferences prefs = getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE);
        boolean remember = prefs.getBoolean("pref_remember_role", false);
        String savedRoleStr = prefs.getString("pref_saved_role", null);
        if (remember && savedRoleStr != null) {
            AppRole role = "CONTROLLER".equals(savedRoleStr) ? AppRole.CONTROLLER : AppRole.STREAMER;
            applyRole(role, true);
        } else {
            showRoleSelection();
        }
    }

    public void showRoleSelection() {
        currentRole = AppRole.NONE;

        // 1. Dừng stream từ xa nếu đang chạy
        if (remoteMjpegView != null) {
            remoteMjpegView.stopStream();
            remoteMjpegView.setVisibility(View.GONE);
        }

        // 2. Dừng CameraX và Server MJPEG nếu đang chạy
        stopStreaming();
        try {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }
            camera = null;
        } catch (Exception ignored) {}

        // 3. Khôi phục xoay tự do
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        // 4. Ẩn toàn bộ các giao diện con
        if (previewView != null) previewView.setVisibility(View.GONE);
        if (viewfinderOverlay != null) viewfinderOverlay.setVisibility(View.GONE);
        if (overlayContainer != null) overlayContainer.setVisibility(View.GONE);
        if (controlDashboardView != null) controlDashboardView.setVisibility(View.GONE);
        if (fpvHudOverlay != null) fpvHudOverlay.setVisibility(View.GONE);
        if (controllerLocalCamBar != null) controllerLocalCamBar.setVisibility(View.GONE);
        if (controllerTopLeftBar != null) controllerTopLeftBar.setVisibility(View.GONE);
        if (udpManager != null) udpManager.disconnect();

        // 5. Hiển thị màn hình chọn vai trò
        if (roleSelectionView != null) {
            SharedPreferences prefs = getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE);
            boolean wasRemember = prefs.getBoolean("pref_remember_role", false);
            roleSelectionView.setRememberChecked(wasRemember);
            roleSelectionView.setVisibility(View.VISIBLE);
            roleSelectionView.bringToFront();
        }
    }

    public void applyRole(AppRole role, boolean remember) {
        currentRole = role;
        SharedPreferences prefs = getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean("pref_remember_role", remember)
                .putString("pref_saved_role", (role == AppRole.CONTROLLER) ? "CONTROLLER" : "STREAMER")
                .apply();

        if (roleSelectionView != null) {
            roleSelectionView.setVisibility(View.GONE);
        }

        if (role == AppRole.CONTROLLER) {
            startControllerRole();
        } else {
            startStreamerRole();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentRole != AppRole.NONE) {
            showRoleSelection();
        } else {
            super.onBackPressed();
        }
    }

    public void setupButtonSyncListener() {
        if (buttonSyncListener != null) {
            ButtonSyncManager.removeListener(buttonSyncListener);
        }
        buttonSyncListener = (btnId, action, extra) -> {
            if (btnId != null && btnId.startsWith("GIMBAL_")) {
                GimbalBleManager.handleIncomingNetworkCommand(btnId, action, extra);
            }
            runOnUiThread(() -> {
                if ("TAKE_PHOTO".equals(btnId)) {
                    if (currentMode != CameraMode.PHOTO && currentMode != CameraMode.CONTROL) {
                        switchMode(CameraMode.PHOTO);
                    }
                    triggerShutterFlash();
                    takePhoto();
                    Toast.makeText(MainActivity.this, "PC vừa kích hoạt Chụp ảnh!", Toast.LENGTH_SHORT).show();
                } else if ("START_RECORD".equals(btnId)) {
                    if (currentMode != CameraMode.VIDEO && currentMode != CameraMode.CONTROL) {
                        switchMode(CameraMode.VIDEO);
                    }
                    if (!isRecordingVideo) {
                        startVideoRecording();
                        Toast.makeText(MainActivity.this, "PC vừa kích hoạt Bắt đầu quay video!", Toast.LENGTH_SHORT).show();
                    }
                } else if ("STOP_RECORD".equals(btnId)) {
                    if (isRecordingVideo) {
                        stopVideoRecording();
                        Toast.makeText(MainActivity.this, "PC vừa dừng quay video!", Toast.LENGTH_SHORT).show();
                    }
                } else if ("SWITCH_CAMERA".equals(btnId)) {
                    if (ivSwitchCameraIcon != null) {
                        ivSwitchCameraIcon.animate().rotationBy(180f).setDuration(280).start();
                    }
                    switchCamera();
                } else if ("START_STREAM".equals(btnId)) {
                    if (!isStreaming || mjpegServer == null || !mjpegServer.isAlive()) {
                        startStreaming();
                    }
                    Toast.makeText(MainActivity.this, "PC vừa bật Live Stream!", Toast.LENGTH_SHORT).show();
                } else if ("STOP_STREAM".equals(btnId)) {
                    if (isStreaming) {
                        stopStreaming();
                    }
                    Toast.makeText(MainActivity.this, "PC vừa dừng Live Stream!", Toast.LENGTH_SHORT).show();
                }
            });
        };
        ButtonSyncManager.addListener(buttonSyncListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentRole == AppRole.STREAMER) {
            if (viewfinderOverlay != null) viewfinderOverlay.startSensors();
            if (orientationEventListener != null) orientationEventListener.enable();
            updateIpDisplay();

            if (allPermissionsGranted()) {
                if (cameraProvider == null || camera == null) {
                    Log.i(TAG, "onResume: Tự động phục hồi CameraX...");
                    startCamera();
                } else if (!isStreaming || mjpegServer == null || !mjpegServer.isAlive()) {
                    Log.i(TAG, "onResume: Tự động phục hồi MJPEG Stream Server...");
                    startStreaming();
                }
            }
        } else if (currentRole == AppRole.CONTROLLER) {
            if ("REMOTE".equals(controllerCamSource)) {
                if (remoteMjpegView != null) {
                    remoteMjpegView.startStream();
                }
                if (!isStreaming || mjpegServer == null || !mjpegServer.isAlive()) {
                    startStreaming();
                }
            } else {
                updateControllerLocalIpDisplay();
                if (allPermissionsGranted()) {
                    if (cameraProvider == null || camera == null) {
                        startCamera();
                    }
                    if (!isStreaming || mjpegServer == null || !mjpegServer.isAlive()) {
                        startStreaming();
                    }
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentRole == AppRole.STREAMER) {
            if (viewfinderOverlay != null) viewfinderOverlay.stopSensors();
            if (orientationEventListener != null) orientationEventListener.disable();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (currentRole == AppRole.NONE) {
            if (roleSelectionView != null) {
                roleSelectionView.refreshLayout();
            }
            return;
        }
        if (currentRole == AppRole.CONTROLLER) {
            if (rootLayout != null) {
                rootLayout.post(this::updateControllerLayoutDimensions);
            }
            return;
        }
        if (currentRole != AppRole.STREAMER) return;
        if (orientationOption != ScreenOrientationOption.AUTO) return;

        int displayRotation;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayRotation = (getDisplay() != null) ? getDisplay().getRotation() : Surface.ROTATION_0;
        } else {
            //noinspection deprecation
            displayRotation = getWindowManager().getDefaultDisplay().getRotation();
        }

        if (displayRotation != currentTargetRotation) {
            boolean wasLandscape = (currentTargetRotation == Surface.ROTATION_90 || currentTargetRotation == Surface.ROTATION_270);
            boolean isLandscape = (displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270);

            currentTargetRotation = displayRotation;

            if (wasLandscape != isLandscape) {
                startCamera();
            } else {
                try {
                    if (preview != null) preview.setTargetRotation(displayRotation);
                    if (imageAnalysis != null) imageAnalysis.setTargetRotation(displayRotation);
                    if (imageCapture != null) imageCapture.setTargetRotation(displayRotation);
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi onConfigurationChanged: " + e.getMessage());
                }
            }
        }
    }

    // =========================================================================
    // COMMAND LISTENER (MjpegServer.CommandListener)
    // =========================================================================

    @Override
    public boolean onToggleTorch() {
        final boolean[] state = new boolean[]{false};
        runOnUiThread(() -> state[0] = toggleTorch());
        return state[0];
    }

    @Override
    public void onSwitchCamera() {
        runOnUiThread(() -> {
            if (ivSwitchCameraIcon != null) {
                ivSwitchCameraIcon.animate().rotationBy(180f).setDuration(280).start();
            }
            switchCamera();
        });
    }

    @Override
    public void onSetZoom(float ratio) {
        runOnUiThread(() -> selectZoomRatio(ratio));
    }

    @Override
    public void onTakePhoto() {
        runOnUiThread(() -> {
            if (currentMode != CameraMode.PHOTO && currentMode != CameraMode.CONTROL) {
                switchMode(CameraMode.PHOTO);
            }
            triggerShutterFlash();
            takePhoto();
        });
    }

    @Override
    public void onStartRecord() {
        runOnUiThread(() -> {
            if (currentMode != CameraMode.VIDEO && currentMode != CameraMode.CONTROL) {
                switchMode(CameraMode.VIDEO);
            }
            if (!isRecordingVideo) {
                startVideoRecording();
            }
        });
    }

    @Override
    public void onStopRecord() {
        runOnUiThread(() -> {
            if (isRecordingVideo) {
                stopVideoRecording();
            }
        });
    }

    // =========================================================================
    // PERMISSIONS & LIFECYCLE
    // =========================================================================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                if (currentRole == AppRole.STREAMER || (currentRole == AppRole.CONTROLLER && "LOCAL".equals(controllerCamSource))) {
                    startCamera();
                }
            } else {
                Toast.makeText(this, "Ứng dụng cần cấp quyền Camera & Micro để hoạt động!", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRecordingVideo || recording != null) {
            stopVideoRecording();
        }
        if (buttonSyncListener != null) {
            ButtonSyncManager.removeListener(buttonSyncListener);
            buttonSyncListener = null;
        }
        if (udpManager != null) {
            udpManager.disconnect();
        }
        if (visualDistanceDetector != null) {
            visualDistanceDetector.close();
        }
        if (orientationEventListener != null) {
            orientationEventListener.disable();
        }
        uiHandler.removeCallbacks(videoTimerRunnable);
        stopStreaming();
        cameraExecutor.shutdown();
    }

    // =========================================================================
    // DELEGATES TO MODULAR HELPER CLASSES
    // =========================================================================

    public void setupUI() {
        MainActivityLayout.setupUI(this);
    }

    public void setupGestures() {
        MainActivityCamera.setupGestures(this);
    }

    public void startCamera() {
        MainActivityCamera.startCamera(this);
    }

    public void takePhoto() {
        MainActivityCamera.takePhoto(this);
    }

    public void switchCamera() {
        MainActivityCamera.switchCamera(this);
    }

    public boolean toggleTorch() {
        return MainActivityCamera.toggleTorch(this);
    }

    public void startVideoRecording() {
        MainActivityCamera.startVideoRecording(this);
    }

    public void stopVideoRecording() {
        MainActivityCamera.stopVideoRecording(this);
    }

    public void selectZoomRatio(float ratio) {
        MainActivityCamera.selectZoomRatio(this, ratio);
    }

    public void startStreaming() {
        MainActivityStreamer.startStreaming(this);
    }

    public void stopStreaming() {
        MainActivityStreamer.stopStreaming(this);
    }

    public void updateIpDisplay() {
        MainActivityStreamer.updateIpDisplay(this);
    }

    public void updateControllerLocalIpDisplay() {
        MainActivityController.updateControllerLocalIpDisplay(this);
    }

    public void switchMode(CameraMode mode) {
        MainActivityLayout.switchMode(this, mode);
    }

    public void triggerShutterFlash() {
        MainActivityUiHelpers.triggerShutterFlash(this);
    }

    public boolean allPermissionsGranted() {
        return MainActivityCamera.allPermissionsGranted(this);
    }

    public void requestPermissions() {
        MainActivityCamera.requestPermissions(this);
    }

    public void startControllerRole() {
        MainActivityController.startControllerRole(this);
    }

    public void startStreamerRole() {
        MainActivityStreamer.startStreamerRole(this);
    }

    public void switchToLocalCamMode() {
        MainActivityController.switchToLocalCamMode(this);
    }

    public void switchToRemoteFpvMode(String targetIp) {
        MainActivityController.switchToRemoteFpvMode(this, targetIp);
    }

    public void updateUdpUiState(boolean connected, String status) {
        MainActivityController.updateUdpUiState(this, connected, status);
    }

    public void sendHttpCommandToStreamer(String endpoint) {
        MainActivityController.sendHttpCommandToStreamer(this, endpoint);
    }

    public void updateControlLayoutDimensions() {
        MainActivityLayout.updateControlLayoutDimensions(this);
    }

    public void updateControllerLayoutDimensions() {
        MainActivityController.updateControllerLayoutDimensions(this);
    }

    public void showSettingsDialog() {
        MainActivitySettings.showSettingsDialog(this);
    }

    public void showStreamInfoDialog() {
        MainActivityStreamer.showStreamInfoDialog(this);
    }

    public void copyStreamUrlToClipboard() {
        MainActivityStreamer.copyStreamUrlToClipboard(this);
    }

    public void showGimbalBleDialog() {
        MainActivityStreamer.showGimbalBleDialog(this);
    }

    public void openGallery() {
        MainActivityCamera.openGallery(this);
    }

    public void handleShutterClick() {
        MainActivityCamera.handleShutterClick(this);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (MainActivityGamepad.handleGenericMotionEvent(this, event)) {
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (MainActivityGamepad.handleKeyEvent(this, event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
