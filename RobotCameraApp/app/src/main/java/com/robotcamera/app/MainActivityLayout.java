package com.robotcamera.app;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.camera.view.PreviewView;

/**
 * Dựng giao diện gốc (setupUI) và các cụm nút của màn hình Streaming:
 * - Góc trên trái: Thanh Pill trắng hợp nhất [Cài đặt] [Chọn vai trò] IP: ... [Gimbal: BLE]
 * - Góc trên phải: Pill trắng [• LIVE]
 * - Cạnh phải: Nút vuông trắng bo góc [T]
 * - Chính giữa dưới: Thanh [ẢNH | VIDEO] + Dock trắng nổi chứa Zoom Pill (.5 | 1x | 2) và 3 nút tròn (Thư viện, Chụp xanh, Đổi camera).
 */
public final class MainActivityLayout {

    private MainActivityLayout() {}

    public static void setupUI(MainActivity act) {
        act.rootLayout = new RelativeLayout(act);
        act.rootLayout.setBackgroundColor(Color.parseColor("#D6E6FC"));

        // 1. Khung xem trước Camera toàn màn hình
        act.previewView = new PreviewView(act);
        act.previewView.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        act.previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        act.rootLayout.addView(act.previewView);

        // 2. Viewfinder Overlay: Khung ngắm bo góc trung tâm tinh tế
        act.viewfinderOverlay = new ViewfinderOverlayView(act);
        act.viewfinderOverlay.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        act.rootLayout.addView(act.viewfinderOverlay);

        // 3. Lớp chớp sáng màn trập (Shutter Flash)
        act.shutterFlashOverlay = new View(act);
        act.shutterFlashOverlay.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        act.shutterFlashOverlay.setBackgroundColor(Color.WHITE);
        act.shutterFlashOverlay.setAlpha(0f);
        act.shutterFlashOverlay.setVisibility(View.GONE);
        act.rootLayout.addView(act.shutterFlashOverlay);

        // 4. Vòng tròn lấy nét chạm màn hình (Focus Reticle)
        act.focusReticle = new View(act);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.RECTANGLE);
        ring.setCornerRadius((float) MainActivityUiHelpers.dp(act, 16f));
        ring.setStroke(MainActivityUiHelpers.dp(act, 2f), Color.parseColor("#1D5CE5"));
        ring.setColor(Color.parseColor("#181D5CE5"));
        act.focusReticle.setBackground(ring);
        act.focusReticle.setLayoutParams(new RelativeLayout.LayoutParams(
                MainActivityUiHelpers.dp(act, 64f),
                MainActivityUiHelpers.dp(act, 64f)
        ));
        act.focusReticle.setVisibility(View.GONE);
        act.rootLayout.addView(act.focusReticle);

        // 4b. Lớp phủ FPV HUD
        act.fpvHudOverlay = new FpvHudOverlayView(act);
        act.fpvHudOverlay.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        act.fpvHudOverlay.setVisibility(View.GONE);
        act.rootLayout.addView(act.fpvHudOverlay);

        // 5. Container chứa toàn bộ các nút điều khiển của màn hình Streaming
        act.overlayContainer = new RelativeLayout(act);
        act.overlayContainer.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));

        // 6b. Khung xem trước FPV từ xa (Remote MJPEG View) cho chế độ Điều khiển
        act.remoteMjpegView = new RemoteMjpegView(act);
        act.remoteMjpegView.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        act.remoteMjpegView.setVisibility(View.GONE);
        act.remoteMjpegView.setOnFrameReceived(jpegBytes -> {
            if (act.isStreaming && act.mjpegServer != null) {
                act.mjpegServer.pushJpegBytes(jpegBytes);
            }
        });
        act.remoteMjpegView.setOnSwitchToLocalCamRequested(() -> {
            act.runOnUiThread(() -> MainActivityController.switchToLocalCamMode(act));
        });
        act.rootLayout.addView(act.remoteMjpegView);

        // 6. Quản lý kết nối Robot Mecanum qua UDP & Chế độ Tự hành (Auto Follow)
        act.udpManager = new RobotUdpManager();
        act.udpManager.setOnConnectionStateChanged((connected, status) -> {
            MainActivityController.updateUdpUiState(act, connected, status);
        });

        act.autoFollowManager = new AutoFollowManager(act.udpManager);
        act.visualDistanceDetector = new VisualDistanceDetector();
        act.visualDistanceDetector.setOnTargetDetected(target -> {
            act.autoFollowManager.onTargetDetected(target);
            act.remoteMjpegView.updateTarget(target);
            act.fpvHudOverlay.setCurrentTarget(target);
        });
        act.visualDistanceDetector.setOnTargetLost(() -> {
            act.autoFollowManager.onTargetLost();
            act.remoteMjpegView.updateTarget(null);
            act.fpvHudOverlay.setCurrentTarget(null);
        });

        // Kết nối giải mã Bitmap FPV từ xa vào động cơ thị giác máy tính
        act.remoteMjpegView.setOnBitmapDecoded(bmp -> {
            act.visualDistanceDetector.processBitmap(bmp);
        });

        // 6b. Bảng điều khiển Robot Mecanum (Full-Screen Floating Cockpit)
        act.controlDashboardView = new RobotControlDashboardView(act, act.udpManager, act.autoFollowManager);
        act.controlDashboardView.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        act.controlDashboardView.setVisibility(View.GONE);
        act.controlDashboardView.setOnSwitchRoleRequested(() -> {
            act.runOnUiThread(() -> act.showRoleSelection());
        });
        act.controlDashboardView.setOnTakePhotoRequested(() -> {
            act.runOnUiThread(() -> {
                if (act.currentRole == MainActivity.AppRole.STREAMER || "LOCAL".equals(act.controllerCamSource)) {
                    MainActivityUiHelpers.triggerShutterFlash(act);
                    MainActivityCamera.takePhoto(act);
                } else {
                    MainActivityController.sendHttpCommandToStreamer(act, "/snap");
                }
            });
        });
        act.controlDashboardView.setOnStartRecordRequested(() -> {
            act.runOnUiThread(() -> {
                if (act.currentRole == MainActivity.AppRole.STREAMER || "LOCAL".equals(act.controllerCamSource)) {
                    if (!act.isRecordingVideo) {
                        MainActivityCamera.startVideoRecording(act);
                    }
                } else {
                    MainActivityController.sendHttpCommandToStreamer(act, "/record/start");
                }
            });
        });
        act.controlDashboardView.setOnStopRecordRequested(() -> {
            act.runOnUiThread(() -> {
                if (act.currentRole == MainActivity.AppRole.STREAMER || "LOCAL".equals(act.controllerCamSource)) {
                    if (act.isRecordingVideo) {
                        MainActivityCamera.stopVideoRecording(act);
                    }
                } else {
                    MainActivityController.sendHttpCommandToStreamer(act, "/record/stop");
                }
            });
        });
        act.controlDashboardView.setOnSwitchCameraRequested(() -> {
            act.runOnUiThread(() -> {
                if (act.currentRole == MainActivity.AppRole.STREAMER || "LOCAL".equals(act.controllerCamSource)) {
                    MainActivityCamera.switchCamera(act);
                } else {
                    MainActivityController.sendHttpCommandToStreamer(act, "/switch");
                }
            });
        });
        act.rootLayout.addView(act.controlDashboardView);

        // 6c. Thanh công cụ nổi IP cho Camera máy này (khi ở chế độ Điều khiển)
        act.controllerLocalCamBar = MainActivityController.createControllerLocalCamBar(act);
        act.rootLayout.addView(act.controllerLocalCamBar);

        // 6d. Thanh công cụ nổi góc trái trên (Cài đặt, Chọn vai trò, IP UDP)
        act.controllerTopLeftBar = MainActivityController.createControllerTopLeftBar(act);
        act.rootLayout.addView(act.controllerTopLeftBar);

        setupTopBar(act, act.overlayContainer);
        setupSmartScanButton(act, act.overlayContainer);
        setupZoomBar(act, act.overlayContainer);
        setupBottomControls(act, act.overlayContainer);

        act.rootLayout.addView(act.overlayContainer);

        // 7. Màn hình Khởi động Chọn Vai trò (Role Selection View)
        act.roleSelectionView = new RoleSelectionView(act);
        act.roleSelectionView.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        act.roleSelectionView.setVisibility(View.GONE);
        act.roleSelectionView.setOnRoleSelected((role, remember) -> {
            act.runOnUiThread(() -> {
                MainActivity.AppRole appRole = (role == RoleSelectionView.Role.CONTROLLER)
                        ? MainActivity.AppRole.CONTROLLER
                        : MainActivity.AppRole.STREAMER;
                act.applyRole(appRole, remember);
            });
        });
        act.rootLayout.addView(act.roleSelectionView);

        act.setContentView(act.rootLayout);
        MainActivityStreamer.updateIpDisplay(act);
    }

    /**
     * Thanh công cụ đỉnh (Top Bar) của màn hình Streaming:
     * - Góc trái trên: Pill trắng hợp nhất [Cài đặt] [Chọn vai trò] IP: 192.168.x.x [Gimbal: BLE]
     * - Góc phải trên: Pill trắng [• LIVE]
     */
    public static void setupTopBar(MainActivity act, RelativeLayout container) {
        act.topBarLayout = new LinearLayout(act);
        act.topBarLayout.setId(View.generateViewId());
        act.topBarLayout.setOrientation(LinearLayout.HORIZONTAL);
        act.topBarLayout.setGravity(Gravity.CENTER_VERTICAL);
        act.topBarLayout.setPadding(
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 10f),
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 4f)
        );
        RelativeLayout.LayoutParams topLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        topLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        act.topBarLayout.setLayoutParams(topLp);

        // 1. PILL TRẮNG HỢP NHẤT GÓC TRÊN TRÁI
        LinearLayout leftUnifiedPill = new LinearLayout(act);
        leftUnifiedPill.setOrientation(LinearLayout.HORIZONTAL);
        leftUnifiedPill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 26f));
        pillBg.setColor(Color.parseColor("#F5FFFFFF"));
        pillBg.setStroke(MainActivityUiHelpers.dp(act, 1f), Color.parseColor("#E2E8F0"));
        leftUnifiedPill.setBackground(pillBg);
        leftUnifiedPill.setElevation((float) MainActivityUiHelpers.dp(act, 6f));
        leftUnifiedPill.setPadding(
                MainActivityUiHelpers.dp(act, 10f),
                MainActivityUiHelpers.dp(act, 6f),
                MainActivityUiHelpers.dp(act, 10f),
                MainActivityUiHelpers.dp(act, 6f)
        );

        // [Cài đặt]
        TextView btnSettingsPill = new TextView(act);
        btnSettingsPill.setText("Cài đặt");
        btnSettingsPill.setTextSize(10.5f);
        btnSettingsPill.setTypeface(Typeface.DEFAULT_BOLD);
        btnSettingsPill.setTextColor(Color.parseColor("#0F172A"));
        btnSettingsPill.setGravity(Gravity.CENTER);
        btnSettingsPill.setSingleLine(true);
        GradientDrawable settingsBg = new GradientDrawable();
        settingsBg.setShape(GradientDrawable.RECTANGLE);
        settingsBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 16f));
        settingsBg.setColor(Color.WHITE);
        settingsBg.setStroke(MainActivityUiHelpers.dp(act, 1.2f), Color.parseColor("#CBD5E1"));
        btnSettingsPill.setBackground(settingsBg);
        btnSettingsPill.setPadding(
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f),
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f)
        );
        MainActivityUiHelpers.applySpringTouchEffect(btnSettingsPill);
        btnSettingsPill.setOnClickListener(v -> MainActivitySettings.showSettingsDialog(act));
        leftUnifiedPill.addView(btnSettingsPill);

        leftUnifiedPill.addView(MainActivityUiHelpers.createHorizontalSpacer(act, 8));

        // [Chọn vai trò]
        TextView btnSwitchRolePill = new TextView(act);
        btnSwitchRolePill.setText("Chọn vai trò");
        btnSwitchRolePill.setTextSize(10.5f);
        btnSwitchRolePill.setTypeface(Typeface.DEFAULT_BOLD);
        btnSwitchRolePill.setTextColor(Color.parseColor("#0F172A"));
        btnSwitchRolePill.setGravity(Gravity.CENTER);
        btnSwitchRolePill.setSingleLine(true);
        GradientDrawable switchRoleBg = new GradientDrawable();
        switchRoleBg.setShape(GradientDrawable.RECTANGLE);
        switchRoleBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 16f));
        switchRoleBg.setColor(Color.WHITE);
        switchRoleBg.setStroke(MainActivityUiHelpers.dp(act, 1.2f), Color.parseColor("#CBD5E1"));
        btnSwitchRolePill.setBackground(switchRoleBg);
        btnSwitchRolePill.setPadding(
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f),
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f)
        );
        MainActivityUiHelpers.applySpringTouchEffect(btnSwitchRolePill);
        btnSwitchRolePill.setOnClickListener(v -> act.showRoleSelection());
        leftUnifiedPill.addView(btnSwitchRolePill);

        leftUnifiedPill.addView(MainActivityUiHelpers.createHorizontalSpacer(act, 12));

        // IP: 192.168.x.x nằm gọn bên trong thanh Pill trắng
        setupIpBadge(act, leftUnifiedPill);

        leftUnifiedPill.addView(MainActivityUiHelpers.createHorizontalSpacer(act, 12));

        // [Gimbal: BLE]
        LinearLayout btnGimbalPill = new LinearLayout(act);
        btnGimbalPill.setOrientation(LinearLayout.HORIZONTAL);
        btnGimbalPill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable gimbalBg = new GradientDrawable();
        gimbalBg.setShape(GradientDrawable.RECTANGLE);
        gimbalBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 16f));
        gimbalBg.setColor(Color.parseColor("#DCE8FD"));
        btnGimbalPill.setBackground(gimbalBg);
        btnGimbalPill.setPadding(
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f),
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f)
        );

        act.tvGimbalStatusBadge = new TextView(act);
        act.tvGimbalStatusBadge.setText("Gimbal: BLE");
        act.tvGimbalStatusBadge.setTextSize(10.5f);
        act.tvGimbalStatusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        act.tvGimbalStatusBadge.setTextColor(Color.parseColor("#1D5CE5"));
        act.tvGimbalStatusBadge.setGravity(Gravity.CENTER);
        act.tvGimbalStatusBadge.setSingleLine(true);
        btnGimbalPill.addView(act.tvGimbalStatusBadge);

        MainActivityUiHelpers.applySpringTouchEffect(btnGimbalPill);
        btnGimbalPill.setOnClickListener(v -> MainActivityStreamer.showGimbalBleDialog(act));
        leftUnifiedPill.addView(btnGimbalPill);

        GimbalBleManager.addStateListener((connected, msg) -> {
            if (act.tvGimbalStatusBadge != null) {
                if (connected) {
                    int rx = GimbalBleManager.rxPacketCount;
                    act.tvGimbalStatusBadge.setText(rx > 0 ? "● Gimbal: Đã nối" : "● Gimbal: RS3 Mini");
                    act.tvGimbalStatusBadge.setTextColor(Color.parseColor("#15803D"));
                    if (btnGimbalPill.getBackground() instanceof GradientDrawable) {
                        ((GradientDrawable) btnGimbalPill.getBackground()).setColor(Color.parseColor("#DCFCE7"));
                    }
                } else {
                    act.tvGimbalStatusBadge.setText("Gimbal: BLE");
                    act.tvGimbalStatusBadge.setTextColor(Color.parseColor("#1D5CE5"));
                    if (btnGimbalPill.getBackground() instanceof GradientDrawable) {
                        ((GradientDrawable) btnGimbalPill.getBackground()).setColor(Color.parseColor("#DCE8FD"));
                    }
                }
            }
        });

        act.topBarLayout.addView(leftUnifiedPill);

        // Giữ tham chiếu tvFlashIcon và tvResolutionBadge (ẩn để giao diện sạch đúng mockup)
        act.tvFlashIcon = new TextView(act);
        act.tvFlashIcon.setText("Flash");
        act.tvFlashIcon.setVisibility(View.GONE);
        act.topBarLayout.addView(act.tvFlashIcon);

        act.tvResolutionBadge = new TextView(act);
        act.tvResolutionBadge.setText("720p");
        act.tvResolutionBadge.setVisibility(View.GONE);
        act.topBarLayout.addView(act.tvResolutionBadge);

        act.topBarLayout.addView(MainActivityUiHelpers.createFlexSpacer(act));

        // Timer đếm giờ quay phim (chỉ hiện khi đang quay video)
        act.tvRecTimerBadge = new TextView(act);
        act.tvRecTimerBadge.setText("● REC 00:00");
        act.tvRecTimerBadge.setTextSize(11.5f);
        act.tvRecTimerBadge.setTypeface(Typeface.DEFAULT_BOLD);
        act.tvRecTimerBadge.setTextColor(Color.parseColor("#DC2626"));
        GradientDrawable recBg = new GradientDrawable();
        recBg.setShape(GradientDrawable.RECTANGLE);
        recBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 16f));
        recBg.setColor(Color.parseColor("#F5FFFFFF"));
        recBg.setStroke(MainActivityUiHelpers.dp(act, 1f), Color.parseColor("#FCA5A5"));
        act.tvRecTimerBadge.setBackground(recBg);
        act.tvRecTimerBadge.setElevation((float) MainActivityUiHelpers.dp(act, 4f));
        act.tvRecTimerBadge.setPadding(
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 6f),
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 6f)
        );
        act.tvRecTimerBadge.setVisibility(View.GONE);
        act.topBarLayout.addView(act.tvRecTimerBadge);

        act.topBarLayout.addView(MainActivityUiHelpers.createHorizontalSpacer(act, 8));

        // 2. PILL TRẮNG [• LIVE] Ở GÓC TRÊN PHẢI
        FrameLayout livePill = new FrameLayout(act);
        livePill.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        GradientDrawable liveBg = new GradientDrawable();
        liveBg.setShape(GradientDrawable.RECTANGLE);
        liveBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 18f));
        liveBg.setColor(Color.parseColor("#F5FFFFFF"));
        liveBg.setStroke(MainActivityUiHelpers.dp(act, 1f), Color.parseColor("#E2E8F0"));
        livePill.setBackground(liveBg);
        livePill.setElevation((float) MainActivityUiHelpers.dp(act, 6f));
        livePill.setPadding(
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f),
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f)
        );

        act.tvMotionLiveText = new TextView(act);
        act.tvMotionLiveText.setText("• LIVE");
        act.tvMotionLiveText.setTextSize(10.5f);
        act.tvMotionLiveText.setTypeface(Typeface.DEFAULT_BOLD);
        act.tvMotionLiveText.setTextColor(Color.parseColor("#15803D"));
        act.tvMotionLiveText.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        livePill.addView(act.tvMotionLiveText);
        livePill.setOnClickListener(v -> MainActivityStreamer.showStreamInfoDialog(act));
        act.btnMotionLiveIcon = livePill;
        act.topBarLayout.addView(act.btnMotionLiveIcon);

        container.addView(act.topBarLayout);
    }

    public static void setupIpBadge(MainActivity act, LinearLayout container) {
        LinearLayout ipContainer = new LinearLayout(act);
        ipContainer.setId(View.generateViewId());
        ipContainer.setOrientation(LinearLayout.HORIZONTAL);
        ipContainer.setGravity(Gravity.CENTER_VERTICAL);
        ipContainer.setPadding(
                MainActivityUiHelpers.dp(act, 2f),
                MainActivityUiHelpers.dp(act, 4f),
                MainActivityUiHelpers.dp(act, 2f),
                MainActivityUiHelpers.dp(act, 4f)
        );

        act.tvIpDisplayBadge = new TextView(act);
        act.tvIpDisplayBadge.setText("IP: Đang kết nối...");
        act.tvIpDisplayBadge.setTextSize(10.5f);
        act.tvIpDisplayBadge.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        act.tvIpDisplayBadge.setTextColor(Color.parseColor("#0F172A"));
        act.tvIpDisplayBadge.setGravity(Gravity.CENTER);
        act.tvIpDisplayBadge.setMaxLines(1);
        ipContainer.addView(act.tvIpDisplayBadge);

        MainActivityUiHelpers.applySpringTouchEffect(ipContainer);
        ipContainer.setOnClickListener(v -> MainActivityStreamer.copyStreamUrlToClipboard(act));
        act.ipBadgeContainer = ipContainer;
        container.addView(ipContainer);
    }

    /**
     * Nút Scan thông minh [T] nền trắng bo góc ở cạnh phải màn hình
     */
    public static void setupSmartScanButton(MainActivity act, RelativeLayout container) {
        FrameLayout smartScan = new FrameLayout(act);
        int size = MainActivityUiHelpers.dp(act, 44f);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(size, size);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        params.addRule(RelativeLayout.CENTER_VERTICAL);
        params.rightMargin = MainActivityUiHelpers.dp(act, 18f);
        smartScan.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 14f));
        bg.setColor(Color.parseColor("#F5FFFFFF"));
        bg.setStroke(MainActivityUiHelpers.dp(act, 1f), Color.parseColor("#E2E8F0"));
        smartScan.setBackground(bg);
        smartScan.setElevation((float) MainActivityUiHelpers.dp(act, 6f));

        TextView tvIcon = new TextView(act);
        tvIcon.setText("[T]");
        tvIcon.setTextSize(12.5f);
        tvIcon.setTypeface(Typeface.DEFAULT_BOLD);
        tvIcon.setTextColor(Color.parseColor("#0F172A"));
        tvIcon.setGravity(Gravity.CENTER);
        tvIcon.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        smartScan.addView(tvIcon);

        MainActivityUiHelpers.applySpringTouchEffect(smartScan);
        smartScan.setOnClickListener(v -> MainActivityStreamer.copyStreamUrlToClipboard(act));

        act.btnSmartScan = smartScan;
        container.addView(act.btnSmartScan);
    }

    /**
     * Khởi tạo cụm Zoom Segmented Pill (.5 | 1x | 2) để đặt vào trong Dock trắng ở đáy
     */
    public static void setupZoomBar(MainActivity act, RelativeLayout container) {
        act.zoomContainer = new LinearLayout(act);
        act.zoomContainer.setId(View.generateViewId());
        act.zoomContainer.setOrientation(LinearLayout.HORIZONTAL);
        act.zoomContainer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams zoomParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        zoomParams.bottomMargin = MainActivityUiHelpers.dp(act, 10f);
        act.zoomContainer.setLayoutParams(zoomParams);

        act.zoomPillContainer = new LinearLayout(act);
        act.zoomPillContainer.setOrientation(LinearLayout.HORIZONTAL);
        act.zoomPillContainer.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 18f));
        pillBg.setColor(Color.parseColor("#EEF4FF"));
        act.zoomPillContainer.setBackground(pillBg);
        act.zoomPillContainer.setPadding(
                MainActivityUiHelpers.dp(act, 4f),
                MainActivityUiHelpers.dp(act, 3f),
                MainActivityUiHelpers.dp(act, 4f),
                MainActivityUiHelpers.dp(act, 3f)
        );

        float[] zoomLevels = {0.6f, 1.0f, 2.0f};
        for (float zoom : zoomLevels) {
            String label = (zoom == 0.6f) ? ".5" : (zoom == 1.0f) ? "1x" : "2";
            TextView chip = new TextView(act);
            chip.setText(label);
            chip.setTextSize(11f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    MainActivityUiHelpers.dp(act, 34f),
                    MainActivityUiHelpers.dp(act, 28f)
            );
            chipLp.setMargins(MainActivityUiHelpers.dp(act, 2f), 0, MainActivityUiHelpers.dp(act, 2f), 0);
            chip.setLayoutParams(chipLp);
            chip.setOnClickListener(v -> MainActivityCamera.selectZoomRatio(act, zoom));
            act.zoomChips.put(zoom, chip);
            act.zoomPillContainer.addView(chip);
        }
        act.zoomContainer.addView(act.zoomPillContainer);
        MainActivityCamera.updateZoomChipsUi(act, 1.0f);
    }

    /**
     * Cụm điều khiển đáy với DOCK TRẮNG NỔI (Floating White Control Dock):
     * - Phía trên Dock: Dải chọn chế độ [ẢNH]  [VIDEO]
     * - Bên trong Dock trắng:
     *   + Hàng 1: Viên thuốc Zoom (.5 | 1x | 2)
     *   + Hàng 2: Nút Thư viện tròn xanh nhạt - Nút Chụp tròn xanh đậm - Nút Đổi Camera tròn xanh nhạt
     */
    public static void setupBottomControls(MainActivity act, RelativeLayout container) {
        LinearLayout bottomWrapper = new LinearLayout(act);
        bottomWrapper.setOrientation(LinearLayout.VERTICAL);
        bottomWrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        RelativeLayout.LayoutParams bwLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        bwLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        bwLp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        bwLp.bottomMargin = MainActivityUiHelpers.dp(act, 14f);
        bottomWrapper.setLayoutParams(bwLp);

        // 1. DẢI CHỌN CHẾ ĐỘ [ẢNH] & [VIDEO] NẰM TRÊN DOCK TRẮNG
        LinearLayout modeBar = new LinearLayout(act);
        modeBar.setOrientation(LinearLayout.HORIZONTAL);
        modeBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        mbLp.bottomMargin = MainActivityUiHelpers.dp(act, 6f);
        modeBar.setLayoutParams(mbLp);

        act.tvModePhoto = new TextView(act);
        act.tvModePhoto.setText("ẢNH");
        act.tvModePhoto.setTextSize(11.5f);
        act.tvModePhoto.setTypeface(Typeface.DEFAULT_BOLD);
        act.tvModePhoto.setTextColor(Color.parseColor("#1D5CE5"));
        act.tvModePhoto.setPadding(
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 4f),
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 4f)
        );
        act.tvModePhoto.setOnClickListener(v -> switchMode(act, MainActivity.CameraMode.PHOTO));
        modeBar.addView(act.tvModePhoto);

        modeBar.addView(MainActivityUiHelpers.createHorizontalSpacer(act, 8));

        act.tvModeVideo = new TextView(act);
        act.tvModeVideo.setText("VIDEO");
        act.tvModeVideo.setTextSize(11.5f);
        act.tvModeVideo.setTypeface(Typeface.DEFAULT_BOLD);
        act.tvModeVideo.setTextColor(Color.parseColor("#64748B"));
        act.tvModeVideo.setPadding(
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 4f),
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 4f)
        );
        act.tvModeVideo.setOnClickListener(v -> switchMode(act, MainActivity.CameraMode.VIDEO));
        modeBar.addView(act.tvModeVideo);

        act.tvModeControl = new TextView(act);
        act.tvModeControl.setText("ĐIỀU KHIỂN");
        act.tvModeControl.setTextSize(11.5f);
        act.tvModeControl.setTypeface(Typeface.DEFAULT_BOLD);
        act.tvModeControl.setTextColor(Color.parseColor("#64748B"));
        act.tvModeControl.setPadding(
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 4f),
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 4f)
        );
        act.tvModeControl.setVisibility(View.GONE);
        act.tvModeControl.setOnClickListener(v -> switchMode(act, MainActivity.CameraMode.CONTROL));
        modeBar.addView(act.tvModeControl);

        bottomWrapper.addView(modeBar);

        // 2. DOCK TRẮNG BO TRÒN NỔI (FLOATING WHITE DOCK)
        LinearLayout whiteDock = new LinearLayout(act);
        whiteDock.setOrientation(LinearLayout.VERTICAL);
        whiteDock.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setShape(GradientDrawable.RECTANGLE);
        dockBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 26f));
        dockBg.setColor(Color.parseColor("#F5FFFFFF"));
        dockBg.setStroke(MainActivityUiHelpers.dp(act, 1f), Color.parseColor("#E2E8F0"));
        whiteDock.setBackground(dockBg);
        whiteDock.setElevation((float) MainActivityUiHelpers.dp(act, 8f));
        whiteDock.setPadding(
                MainActivityUiHelpers.dp(act, 28f),
                MainActivityUiHelpers.dp(act, 10f),
                MainActivityUiHelpers.dp(act, 28f),
                MainActivityUiHelpers.dp(act, 12f)
        );
        whiteDock.setMinimumWidth(MainActivityUiHelpers.dp(act, 430f));
        whiteDock.setLayoutParams(new LinearLayout.LayoutParams(
                MainActivityUiHelpers.dp(act, 430f),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Đưa thanh Zoom (.5 | 1x | 2) vào hàng trên của Dock trắng
        if (act.zoomContainer != null && act.zoomContainer.getParent() instanceof ViewGroup) {
            ((ViewGroup) act.zoomContainer.getParent()).removeView(act.zoomContainer);
        }
        if (act.zoomContainer != null) {
            whiteDock.addView(act.zoomContainer);
        }

        // Hàng 3 nút chính: Gallery (trái) - Shutter (giữa) - Switch Camera (phải)
        act.mainActionRow = new RelativeLayout(act);
        act.mainActionRow.setLayoutParams(new LinearLayout.LayoutParams(
                MainActivityUiHelpers.dp(act, 240f),
                MainActivityUiHelpers.dp(act, 60f)
        ));

        // --- NÚT BỘ SƯU TẬP (GALLERY THUMBNAIL) ---
        act.btnGalleryThumbnail = new ImageView(act);
        int galSize = MainActivityUiHelpers.dp(act, 42f);
        RelativeLayout.LayoutParams galLp = new RelativeLayout.LayoutParams(galSize, galSize);
        galLp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        galLp.addRule(RelativeLayout.CENTER_VERTICAL);
        act.btnGalleryThumbnail.setLayoutParams(galLp);
        act.btnGalleryThumbnail.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        GradientDrawable galBg = new GradientDrawable();
        galBg.setShape(GradientDrawable.OVAL);
        galBg.setColor(Color.parseColor("#EEF4FF"));
        act.btnGalleryThumbnail.setBackground(galBg);
        act.btnGalleryThumbnail.setImageDrawable(createBlueGalleryIconDrawable(act));
        MainActivityUiHelpers.applySpringTouchEffect(act.btnGalleryThumbnail);
        act.btnGalleryThumbnail.setOnClickListener(v -> MainActivityCamera.openGallery(act));
        act.mainActionRow.addView(act.btnGalleryThumbnail);

        // --- NÚT SHUTTER TRÒN XANH HOÀNG GIA ---
        act.shutterContainer = new RelativeLayout(act);
        int shutterSize = MainActivityUiHelpers.dp(act, 58f);
        RelativeLayout.LayoutParams shutterLp = new RelativeLayout.LayoutParams(shutterSize, shutterSize);
        shutterLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        act.shutterContainer.setLayoutParams(shutterLp);
        GradientDrawable shutterRingBg = new GradientDrawable();
        shutterRingBg.setShape(GradientDrawable.OVAL);
        shutterRingBg.setColor(Color.TRANSPARENT);
        shutterRingBg.setStroke(MainActivityUiHelpers.dp(act, 2.8f), Color.parseColor("#1D5CE5"));
        act.shutterContainer.setBackground(shutterRingBg);

        act.shutterCore = new View(act);
        int coreSize = MainActivityUiHelpers.dp(act, 44f);
        RelativeLayout.LayoutParams coreLp = new RelativeLayout.LayoutParams(coreSize, coreSize);
        coreLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        act.shutterCore.setLayoutParams(coreLp);
        GradientDrawable shutterCoreBg = new GradientDrawable();
        shutterCoreBg.setShape(GradientDrawable.OVAL);
        shutterCoreBg.setColor(Color.parseColor("#1D5CE5"));
        act.shutterCore.setBackground(shutterCoreBg);
        act.shutterContainer.addView(act.shutterCore);

        MainActivityUiHelpers.applyShutterTouchAnimation(act, act.shutterContainer, act.shutterCore, () -> {
            MainActivityCamera.handleShutterClick(act);
        });
        act.mainActionRow.addView(act.shutterContainer);

        // --- NÚT ĐỔI CAMERA TRƯỚC / SAU ---
        act.btnSwitchCamera = new FrameLayout(act);
        int switchSize = MainActivityUiHelpers.dp(act, 42f);
        RelativeLayout.LayoutParams switchLp = new RelativeLayout.LayoutParams(switchSize, switchSize);
        switchLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        switchLp.addRule(RelativeLayout.CENTER_VERTICAL);
        act.btnSwitchCamera.setLayoutParams(switchLp);
        GradientDrawable switchBg = new GradientDrawable();
        switchBg.setShape(GradientDrawable.OVAL);
        switchBg.setColor(Color.parseColor("#EEF4FF"));
        act.btnSwitchCamera.setBackground(switchBg);

        act.ivSwitchCameraIcon = new ImageView(act);
        act.ivSwitchCameraIcon.setImageDrawable(createBlueFlipCameraDrawable(act));
        act.ivSwitchCameraIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        act.ivSwitchCameraIcon.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ));
        ((FrameLayout) act.btnSwitchCamera).addView(act.ivSwitchCameraIcon);

        MainActivityUiHelpers.applySpringTouchEffect(act.btnSwitchCamera);
        act.btnSwitchCamera.setOnClickListener(v -> {
            act.ivSwitchCameraIcon.animate().rotationBy(180f).setDuration(280).start();
            MainActivityCamera.switchCamera(act);
            ButtonSyncManager.sendButtonEvent("SWITCH_CAMERA", "CLICK");
        });
        act.mainActionRow.addView(act.btnSwitchCamera);

        whiteDock.addView(act.mainActionRow);
        bottomWrapper.addView(whiteDock);
        container.addView(bottomWrapper);
    }

    public static BitmapDrawable createBlueGalleryIconDrawable(MainActivity act) {
        int sizePx = Math.max(MainActivityUiHelpers.dp(act, 22f), 24);
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor("#1D5CE5"));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth((float) MainActivityUiHelpers.dp(act, 1.8f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        float pad = (float) MainActivityUiHelpers.dp(act, 3f);
        RectF rect = new RectF(pad, pad + MainActivityUiHelpers.dp(act, 1f), sizePx - pad, sizePx - pad - MainActivityUiHelpers.dp(act, 1f));
        c.drawRoundRect(rect, (float) MainActivityUiHelpers.dp(act, 3f), (float) MainActivityUiHelpers.dp(act, 3f), p);

        // Mặt trời nhỏ
        Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sunPaint.setColor(Color.parseColor("#1D5CE5"));
        sunPaint.setStyle(Paint.Style.FILL);
        c.drawCircle(rect.left + rect.width() * 0.32f, rect.top + rect.height() * 0.34f, (float) MainActivityUiHelpers.dp(act, 1.5f), sunPaint);

        // Đường núi
        Path mountain = new Path();
        mountain.moveTo(rect.left + MainActivityUiHelpers.dp(act, 2f), rect.bottom - MainActivityUiHelpers.dp(act, 2.5f));
        mountain.lineTo(rect.left + rect.width() * 0.45f, rect.top + rect.height() * 0.52f);
        mountain.lineTo(rect.left + rect.width() * 0.68f, rect.top + rect.height() * 0.72f);
        mountain.lineTo(rect.right - MainActivityUiHelpers.dp(act, 2f), rect.top + rect.height() * 0.56f);
        c.drawPath(mountain, p);

        return new BitmapDrawable(act.getResources(), bmp);
    }

    public static BitmapDrawable createBlueFlipCameraDrawable(MainActivity act) {
        int sizePx = Math.max(MainActivityUiHelpers.dp(act, 22f), 24);
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor("#1D5CE5"));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth((float) MainActivityUiHelpers.dp(act, 1.8f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        float cx = sizePx / 2f;
        float cy = sizePx / 2f;
        float r = (float) MainActivityUiHelpers.dp(act, 6.5f);
        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(oval, -150f, 120f, false, p);
        c.drawArc(oval, 30f, 120f, false, p);

        // Mũi tên 1 (góc trên phải)
        Path a1 = new Path();
        float x1 = cx + r * 0.86f;
        float y1 = cy - r * 0.5f;
        a1.moveTo(x1 - MainActivityUiHelpers.dp(act, 3.2f), y1 - MainActivityUiHelpers.dp(act, 1f));
        a1.lineTo(x1, y1);
        a1.lineTo(x1 + MainActivityUiHelpers.dp(act, 0.8f), y1 - MainActivityUiHelpers.dp(act, 3.2f));
        c.drawPath(a1, p);

        // Mũi tên 2 (góc dưới trái)
        Path a2 = new Path();
        float x2 = cx - r * 0.86f;
        float y2 = cy + r * 0.5f;
        a2.moveTo(x2 + MainActivityUiHelpers.dp(act, 3.2f), y2 + MainActivityUiHelpers.dp(act, 1f));
        a2.lineTo(x2, y2);
        a2.lineTo(x2 - MainActivityUiHelpers.dp(act, 0.8f), y2 + MainActivityUiHelpers.dp(act, 3.2f));
        c.drawPath(a2, p);

        return new BitmapDrawable(act.getResources(), bmp);
    }

    public static void switchMode(MainActivity act, MainActivity.CameraMode mode) {
        act.currentMode = mode;

        int normalColor = Color.parseColor("#64748B");
        int activeColor = Color.parseColor("#1D5CE5");

        if (act.tvModePhoto != null) act.tvModePhoto.setTextColor(mode == MainActivity.CameraMode.PHOTO ? activeColor : normalColor);
        if (act.tvModeVideo != null) act.tvModeVideo.setTextColor(mode == MainActivity.CameraMode.VIDEO ? activeColor : normalColor);
        if (act.tvModeControl != null) act.tvModeControl.setTextColor(mode == MainActivity.CameraMode.CONTROL ? activeColor : normalColor);

        TextView activeTv = (mode == MainActivity.CameraMode.PHOTO) ? act.tvModePhoto :
                (mode == MainActivity.CameraMode.VIDEO) ? act.tvModeVideo : act.tvModeControl;

        if (activeTv != null) {
            activeTv.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).withEndAction(() -> {
                activeTv.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
            }).start();
        }

        if (mode == MainActivity.CameraMode.CONTROL) {
            act.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            if (act.topBarLayout != null) act.topBarLayout.setVisibility(View.GONE);
            if (act.viewfinderOverlay != null) act.viewfinderOverlay.setVisibility(View.GONE);
            if (act.mainActionRow != null) act.mainActionRow.setVisibility(View.GONE);
            if (act.zoomContainer != null) act.zoomContainer.setVisibility(View.GONE);
            if (act.btnSmartScan != null) act.btnSmartScan.setVisibility(View.GONE);
            if (act.ipBadgeContainer != null) act.ipBadgeContainer.setVisibility(View.GONE);
            if (act.tvRecTimerBadge != null) act.tvRecTimerBadge.setVisibility(View.GONE);
            if (act.isRecordingVideo) MainActivityCamera.stopVideoRecording(act);

            if (act.rootLayout != null) {
                act.rootLayout.post(() -> updateControlLayoutDimensions(act));
            }
        } else {
            act.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

            if (act.previewView != null) {
                act.previewView.setLayoutParams(new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT
                ));
                act.previewView.setVisibility(View.VISIBLE);
            }
            if (act.fpvHudOverlay != null) act.fpvHudOverlay.setVisibility(View.GONE);
            if (act.controlDashboardView != null) act.controlDashboardView.setVisibility(View.GONE);

            if (act.topBarLayout != null) act.topBarLayout.setVisibility(View.VISIBLE);
            if (act.viewfinderOverlay != null) act.viewfinderOverlay.setVisibility(View.VISIBLE);
            if (act.mainActionRow != null) act.mainActionRow.setVisibility(View.VISIBLE);
            if (act.zoomContainer != null) act.zoomContainer.setVisibility(View.VISIBLE);
            if (act.btnSmartScan != null) act.btnSmartScan.setVisibility(View.VISIBLE);
            if (act.ipBadgeContainer != null) act.ipBadgeContainer.setVisibility(View.VISIBLE);

            if (mode == MainActivity.CameraMode.PHOTO) {
                if (act.shutterContainer != null) {
                    GradientDrawable sBg = new GradientDrawable();
                    sBg.setShape(GradientDrawable.OVAL);
                    sBg.setColor(Color.TRANSPARENT);
                    sBg.setStroke(MainActivityUiHelpers.dp(act, 2.8f), Color.parseColor("#1D5CE5"));
                    act.shutterContainer.setBackground(sBg);
                }
                if (act.shutterCore != null) {
                    GradientDrawable cBg = new GradientDrawable();
                    cBg.setShape(GradientDrawable.OVAL);
                    cBg.setColor(Color.parseColor("#1D5CE5"));
                    act.shutterCore.setBackground(cBg);
                    act.shutterCore.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
                if (act.tvRecTimerBadge != null) act.tvRecTimerBadge.setVisibility(View.GONE);
                if (act.isRecordingVideo) MainActivityCamera.stopVideoRecording(act);

                if (act.imageCapture == null || act.cameraProvider == null || !act.cameraProvider.isBound(act.imageCapture)) {
                    MainActivityCamera.startCamera(act);
                }
            } else {
                if (act.shutterContainer != null) {
                    GradientDrawable sBg = new GradientDrawable();
                    sBg.setShape(GradientDrawable.OVAL);
                    sBg.setColor(Color.TRANSPARENT);
                    sBg.setStroke(MainActivityUiHelpers.dp(act, 2.8f), Color.parseColor("#DC2626"));
                    act.shutterContainer.setBackground(sBg);
                }
                if (act.shutterCore != null) {
                    GradientDrawable cBg = new GradientDrawable();
                    cBg.setShape(GradientDrawable.OVAL);
                    cBg.setColor(Color.parseColor("#DC2626"));
                    act.shutterCore.setBackground(cBg);
                    act.shutterCore.animate().scaleX(0.88f).scaleY(0.88f).setDuration(150).start();
                }

                if (act.videoCapture == null || act.cameraProvider == null || !act.cameraProvider.isBound(act.videoCapture)) {
                    MainActivityCamera.startCamera(act);
                }
            }
        }
    }

    public static void updateControlLayoutDimensions(MainActivity act) {
        if (act.currentMode != MainActivity.CameraMode.CONTROL) return;
        RelativeLayout.LayoutParams fullLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );
        if (act.previewView != null) {
            act.previewView.setLayoutParams(fullLp);
            act.previewView.setVisibility(View.VISIBLE);
        }
        if (act.controlDashboardView != null) {
            act.controlDashboardView.setLayoutParams(fullLp);
            act.controlDashboardView.setVisibility(View.VISIBLE);
            act.controlDashboardView.bringToFront();
        }
        if (act.overlayContainer != null) {
            act.overlayContainer.bringToFront();
        }
    }
}
