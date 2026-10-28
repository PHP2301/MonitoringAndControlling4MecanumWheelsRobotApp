package com.robotcamera.app;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Giao diện Bảng Điều Khiển Robot Mecanum (Full-Screen Floating FPV Cockpit)
 * - Nền trong suốt phủ trên luồng video FPV toàn màn hình:
 *   + Góc dưới trái: Cụm Joystick Ảo Đa Hướng 360° (MecanumJoystickView) điều khiển mượt mà.
 *   + Chính giữa dưới: Thẻ nổi hiển thị trạng thái UDP, Telemetry (Vx, Vy, θ) và nút Chụp ảnh / Quay video.
 *   + Góc dưới phải: 2 nút vuông bo góc màu xanh dương xoay trái (↺) và xoay phải (↻).
 *   + Góc trên phải (dưới thanh Camera): Thẻ nổi điều khiển Gimbal RS 3 Mini (< ^ v > + Mode).
 */
@SuppressLint("ViewConstructor")
public class RobotControlDashboardView extends FrameLayout {

    public interface ActionCallback {
        void onAction();
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()
        );
    }

    private float dpF(float v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()
        );
    }

    private final RobotUdpManager udpManager;
    private final AutoFollowManager autoFollowManager;

    // Connection views & Status
    private TextView tvStatusBadge;
    private TextView tvMiniTelemetry;

    // Chế độ Tự hành (Auto Follow & Orbit)
    private TextView tabManual;
    private TextView tabAuto;
    private LinearLayout layoutManualContent;
    private LinearLayout layoutAutoContent;
    private TextView tvAutoDistance;
    private TextView tvAutoStatusBadge;
    private TextView btnHoldToRun;
    private TextView btnAutoEmergencyStop;

    // Joystick & Turn buttons
    private MecanumJoystickView joystickView;
    private FrameLayout btnTurnL;
    private FrameLayout btnTurnR;

    public MecanumJoystickView getJoystickView() {
        return joystickView;
    }

    public FrameLayout getBtnTurnL() {
        return btnTurnL;
    }

    public FrameLayout getBtnTurnR() {
        return btnTurnR;
    }

    public AutoFollowManager getAutoFollowManager() {
        return autoFollowManager;
    }

    // Gimbal RS3 Mini buttons
    private LinearLayout gimbalCard;
    private View btnGimbalLeft;
    private View btnGimbalUp;
    private View btnGimbalDown;
    private View btnGimbalRight;
    private View btnGimbalCenter;
    private TextView btnGimbalMode;
    private GimbalBleManager.FollowMode controllerGimbalMode = GimbalBleManager.FollowMode.PF;

    // Camera action callbacks
    public ActionCallback onTakePhotoRequested = null;
    public ActionCallback onStartRecordRequested = null;
    public ActionCallback onStopRecordRequested = null;
    public ActionCallback onSwitchCameraRequested = null;
    public ActionCallback onSwitchRoleRequested = null;

    public void setOnTakePhotoRequested(ActionCallback cb) { this.onTakePhotoRequested = cb; }
    public void setOnStartRecordRequested(ActionCallback cb) { this.onStartRecordRequested = cb; }
    public void setOnStopRecordRequested(ActionCallback cb) { this.onStopRecordRequested = cb; }
    public void setOnSwitchCameraRequested(ActionCallback cb) { this.onSwitchCameraRequested = cb; }
    public void setOnSwitchRoleRequested(ActionCallback cb) { this.onSwitchRoleRequested = cb; }

    // Cache Telemetry mới nhất
    private RobotUdpManager.RobotTelemetry latestTelemetry = null;

    // Tham chiếu views trong Dialog thông số
    private Dialog paramsDialog = null;
    private TextView dialogTvX = null;
    private TextView dialogTvY = null;
    private TextView dialogTvTheta = null;
    private TextView dialogTvVx = null;
    private TextView dialogTvVy = null;
    private TextView dialogTvW = null;
    private TextView dialogTvW1 = null;
    private TextView dialogTvW2 = null;
    private TextView dialogTvW3 = null;
    private TextView dialogTvW4 = null;

    // Lưu trữ tạm giá trị cài đặt
    private String savedW1 = "0";
    private String savedW2 = "0";
    private String savedW3 = "0";
    private String savedW4 = "0";

    private String savedVx = "0";
    private String savedVy = "0";
    private String savedWSet = "0";
    private String savedXSet = "0";
    private String savedYSet = "0";
    private String savedThetaSet = "0";

    private EditText dialogEtW1 = null;
    private EditText dialogEtW2 = null;
    private EditText dialogEtW3 = null;
    private EditText dialogEtW4 = null;

    private EditText dialogEtVx = null;
    private EditText dialogEtVy = null;
    private EditText dialogEtWSet = null;
    private EditText dialogEtXSet = null;
    private EditText dialogEtYSet = null;
    private EditText dialogEtThetaSet = null;

    // Đồng bộ nút hướng cho PC / thiết bị khác khi kéo Joystick
    private String syncedDirection = null;

    public RobotControlDashboardView(@NonNull Context context, @NonNull RobotUdpManager udpManager) {
        this(context, udpManager, null);
    }

    public RobotControlDashboardView(
            @NonNull Context context,
            @NonNull RobotUdpManager udpManager,
            @Nullable AutoFollowManager autoFollowManager
    ) {
        super(context);
        this.udpManager = udpManager;
        this.autoFollowManager = autoFollowManager;

        setBackgroundColor(Color.TRANSPARENT);
        setClipChildren(false);
        setClipToPadding(false);
        setMotionEventSplittingEnabled(true);
        setupView();
        setupListeners();
    }

    private void setupView() {
        // =========================================================================
        // 1. GÓC TRÊN PHẢI (HÀNG 2): THẺ NỔI ĐIỀU KHIỂN GIMBAL RS 3 MINI
        // =========================================================================
        gimbalCard = new LinearLayout(getContext());
        gimbalCard.setOrientation(LinearLayout.HORIZONTAL);
        gimbalCard.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable gimbalBg = new GradientDrawable();
        gimbalBg.setShape(GradientDrawable.RECTANGLE);
        gimbalBg.setCornerRadius(dpF(26f));
        gimbalBg.setColor(Color.parseColor("#F5FFFFFF"));
        gimbalBg.setStroke(dp(1f), Color.parseColor("#E2E8F0"));
        gimbalCard.setBackground(gimbalBg);
        gimbalCard.setElevation(dpF(6f));
        gimbalCard.setPadding(dp(14f), dp(6f), dp(10f), dp(6f));

        LayoutParams gimbalLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        gimbalLp.gravity = Gravity.TOP | Gravity.END;
        gimbalLp.topMargin = dp(98f);
        gimbalLp.rightMargin = dp(14f);
        gimbalCard.setLayoutParams(gimbalLp);

        TextView tvGimbalLabel = new TextView(getContext());
        tvGimbalLabel.setText("Gimbal");
        tvGimbalLabel.setTextSize(11.5f);
        tvGimbalLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvGimbalLabel.setTextColor(Color.parseColor("#1D5CE5"));
        tvGimbalLabel.setPadding(0, 0, dp(10f), 0);
        tvGimbalLabel.setOnClickListener(v -> cycleGimbalMode());
        gimbalCard.addView(tvGimbalLabel);

        btnGimbalLeft = createGimbalCircleChevronButton(ChevronDir.LEFT, pressed -> {
            GimbalBleManager.sendRemoteGimbalCommand(
                    getContext(), "GIMBAL_LEFT", pressed ? "DOWN" : "UP", null,
                    "gimbal/move?pan=" + (pressed ? -1 : 0) + "&tilt=0"
            );
        });
        gimbalCard.addView(btnGimbalLeft);

        btnGimbalUp = createGimbalCircleChevronButton(ChevronDir.UP, pressed -> {
            GimbalBleManager.sendRemoteGimbalCommand(
                    getContext(), "GIMBAL_UP", pressed ? "DOWN" : "UP", null,
                    "gimbal/move?pan=0&tilt=" + (pressed ? 1 : 0)
            );
        });
        gimbalCard.addView(btnGimbalUp);

        btnGimbalDown = createGimbalCircleChevronButton(ChevronDir.DOWN, pressed -> {
            GimbalBleManager.sendRemoteGimbalCommand(
                    getContext(), "GIMBAL_DOWN", pressed ? "DOWN" : "UP", null,
                    "gimbal/move?pan=0&tilt=" + (pressed ? -1 : 0)
            );
        });
        gimbalCard.addView(btnGimbalDown);

        btnGimbalRight = createGimbalCircleChevronButton(ChevronDir.RIGHT, pressed -> {
            GimbalBleManager.sendRemoteGimbalCommand(
                    getContext(), "GIMBAL_RIGHT", pressed ? "DOWN" : "UP", null,
                    "gimbal/move?pan=" + (pressed ? 1 : 0) + "&tilt=0"
            );
        });
        gimbalCard.addView(btnGimbalRight);

        btnGimbalCenter = createGimbalCircleChevronButton(ChevronDir.CENTER, pressed -> {
            if (pressed) {
                GimbalBleManager.sendRemoteGimbalCommand(
                        getContext(), "GIMBAL_RECENTER", "CLICK", null,
                        "gimbal/recenter"
                );
                Toast.makeText(getContext(), "Đã căn giữa Gimbal (Center)", Toast.LENGTH_SHORT).show();
            }
        });
        gimbalCard.addView(btnGimbalCenter);

        btnGimbalMode = new TextView(getContext());
        btnGimbalMode.setText(controllerGimbalMode.getLabel());
        btnGimbalMode.setTextSize(10f);
        btnGimbalMode.setTypeface(Typeface.DEFAULT_BOLD);
        btnGimbalMode.setTextColor(Color.parseColor("#1D5CE5"));
        btnGimbalMode.setGravity(Gravity.CENTER);

        GradientDrawable modeBg = new GradientDrawable();
        modeBg.setShape(GradientDrawable.RECTANGLE);
        modeBg.setCornerRadius(dpF(14f));
        modeBg.setColor(Color.parseColor("#EEF4FF"));
        modeBg.setStroke(dp(1f), Color.parseColor("#BFDBFE"));
        btnGimbalMode.setBackground(modeBg);
        btnGimbalMode.setPadding(dp(8f), dp(4f), dp(8f), dp(4f));

        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(30f)
        );
        modeLp.leftMargin = dp(4f);
        btnGimbalMode.setLayoutParams(modeLp);
        btnGimbalMode.setOnClickListener(v -> cycleGimbalMode());
        gimbalCard.addView(btnGimbalMode);
        updateGimbalModeUi(controllerGimbalMode);

        addView(gimbalCard);

        // =========================================================================
        // 2. GÓC DƯỚI TRÁI: CỤM JOYSTICK ẢO ĐA HƯỚNG MECANUM 360°
        // =========================================================================
        int joySize = dp(180f);
        joystickView = new MecanumJoystickView(getContext());
        LayoutParams joyLp = new LayoutParams(joySize, joySize);
        joyLp.gravity = Gravity.BOTTOM | Gravity.START;
        joyLp.leftMargin = dp(22f);
        joyLp.bottomMargin = dp(18f);
        joystickView.setLayoutParams(joyLp);
        joystickView.setElevation(dpF(6f));
        joystickView.setOnJoystickMoved((nx, ny, active) -> handleJoystickInput(nx, ny, active));
        addView(joystickView);

        // =========================================================================
        // 3. CHÍNH GIỮA DƯỚI: THẺ NỔI TELEMETRY & CHỤP ẢNH / QUAY VIDEO
        // =========================================================================
        LinearLayout bottomCenterCard = new LinearLayout(getContext());
        bottomCenterCard.setOrientation(LinearLayout.VERTICAL);
        bottomCenterCard.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(dpF(24f));
        cardBg.setColor(Color.parseColor("#F5FFFFFF"));
        cardBg.setStroke(dp(1f), Color.parseColor("#E2E8F0"));
        bottomCenterCard.setBackground(cardBg);
        bottomCenterCard.setElevation(dpF(6f));
        bottomCenterCard.setPadding(dp(28f), dp(12f), dp(28f), dp(12f));
        bottomCenterCard.setMinimumWidth(dp(340f));

        LayoutParams bottomCenterLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        bottomCenterLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        bottomCenterLp.bottomMargin = dp(18f);
        bottomCenterCard.setLayoutParams(bottomCenterLp);

        tvStatusBadge = new TextView(getContext());
        tvStatusBadge.setText(udpManager.isConnected ? "Đã kết nối UDP tới 192.168.2.93:5001" : "Chưa kết nối UDP");
        tvStatusBadge.setTextSize(11.5f);
        tvStatusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatusBadge.setTextColor(udpManager.isConnected ? Color.parseColor("#15803D") : Color.parseColor("#0284C7"));
        tvStatusBadge.setGravity(Gravity.CENTER);
        tvStatusBadge.setPadding(0, 0, 0, dp(6f));
        bottomCenterCard.addView(tvStatusBadge);

        // --- CỤM CHỌN CHẾ ĐỘ: MANUAL (THỦ CÔNG) | AUTO (TỰ ĐỘNG) ---
        LinearLayout modeSwitchRow = new LinearLayout(getContext());
        modeSwitchRow.setOrientation(LinearLayout.HORIZONTAL);
        modeSwitchRow.setGravity(Gravity.CENTER);

        GradientDrawable modeSwitchBg = new GradientDrawable();
        modeSwitchBg.setShape(GradientDrawable.RECTANGLE);
        modeSwitchBg.setCornerRadius(dpF(16f));
        modeSwitchBg.setColor(Color.parseColor("#F1F5F9"));
        modeSwitchBg.setStroke(dp(1f), Color.parseColor("#CBD5E1"));
        modeSwitchRow.setBackground(modeSwitchBg);
        modeSwitchRow.setPadding(dp(3f), dp(3f), dp(3f), dp(3f));

        LinearLayout.LayoutParams modeSwitchLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        modeSwitchLp.bottomMargin = dp(8f);
        modeSwitchRow.setLayoutParams(modeSwitchLp);

        tabManual = new TextView(getContext());
        tabManual.setText("🎮 THỦ CÔNG");
        tabManual.setTextSize(10.5f);
        tabManual.setTypeface(Typeface.DEFAULT_BOLD);
        tabManual.setGravity(Gravity.CENTER);
        tabManual.setPadding(dp(14f), dp(5f), dp(14f), dp(5f));
        tabManual.setOnClickListener(v -> {
            if (autoFollowManager != null) {
                autoFollowManager.setOperatingMode(AutoFollowManager.OperatingMode.MANUAL);
            }
            updateOperatingModeUi(AutoFollowManager.OperatingMode.MANUAL);
            ButtonSyncManager.sendButtonEvent("AUTO_MODE", "MANUAL");
        });
        modeSwitchRow.addView(tabManual);

        tabAuto = new TextView(getContext());
        tabAuto.setText("🤖 TỰ ĐỘNG");
        tabAuto.setTextSize(10.5f);
        tabAuto.setTypeface(Typeface.DEFAULT_BOLD);
        tabAuto.setGravity(Gravity.CENTER);
        tabAuto.setPadding(dp(14f), dp(5f), dp(14f), dp(5f));
        tabAuto.setOnClickListener(v -> {
            if (autoFollowManager != null) {
                autoFollowManager.setOperatingMode(AutoFollowManager.OperatingMode.AUTO);
            }
            updateOperatingModeUi(AutoFollowManager.OperatingMode.AUTO);
            ButtonSyncManager.sendButtonEvent("AUTO_MODE", "AUTO");
        });
        modeSwitchRow.addView(tabAuto);
        bottomCenterCard.addView(modeSwitchRow);

        // --- NỘI DUNG CHẾ ĐỘ THỦ CÔNG (MANUAL CONTENT) ---
        layoutManualContent = new LinearLayout(getContext());
        layoutManualContent.setOrientation(LinearLayout.VERTICAL);
        layoutManualContent.setGravity(Gravity.CENTER_HORIZONTAL);
        layoutManualContent.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        tvMiniTelemetry = new TextView(getContext());
        tvMiniTelemetry.setText("Vx: 0.000 m/s  ·  Vy: 0.000 m/s  ·  θ: 0.00°");
        tvMiniTelemetry.setTextSize(11f);
        tvMiniTelemetry.setTypeface(Typeface.MONOSPACE);
        tvMiniTelemetry.setTextColor(Color.parseColor("#334155"));
        tvMiniTelemetry.setGravity(Gravity.CENTER);
        tvMiniTelemetry.setPadding(0, dp(1f), 0, dp(8f));
        layoutManualContent.addView(tvMiniTelemetry);

        LinearLayout quickCamRow = new LinearLayout(getContext());
        quickCamRow.setOrientation(LinearLayout.HORIZONTAL);
        quickCamRow.setGravity(Gravity.CENTER);
        quickCamRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView btnCamSnap = createSmallCamPillButton("Chụp ảnh", () -> {
            udpManager.sendTakePhoto();
            if (onTakePhotoRequested != null) onTakePhotoRequested.onAction();
            ButtonSyncManager.sendButtonEvent("TAKE_PHOTO", "CLICK");
            Toast.makeText(getContext(), "Lệnh chụp ảnh (0x07)", Toast.LENGTH_SHORT).show();
        });
        quickCamRow.addView(btnCamSnap);

        quickCamRow.addView(createHorizontalSpacer(dp(10f)));

        TextView btnCamRec = createSmallCamPillButton("Quay video", () -> {
            udpManager.sendStartRecord();
            if (onStartRecordRequested != null) onStartRecordRequested.onAction();
            ButtonSyncManager.sendButtonEvent("START_RECORD", "CLICK");
            Toast.makeText(getContext(), "Lệnh bắt đầu quay (0x08)", Toast.LENGTH_SHORT).show();
        });
        quickCamRow.addView(btnCamRec);
        layoutManualContent.addView(quickCamRow);
        bottomCenterCard.addView(layoutManualContent);

        // --- NỘI DUNG CHẾ ĐỘ TỰ ĐỘNG (AUTO CONTENT) ---
        layoutAutoContent = new LinearLayout(getContext());
        layoutAutoContent.setOrientation(LinearLayout.VERTICAL);
        layoutAutoContent.setGravity(Gravity.CENTER_HORIZONTAL);
        layoutAutoContent.setVisibility(View.GONE);
        layoutAutoContent.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Đo khoảng cách & Trạng thái bám
        LinearLayout distanceRow = new LinearLayout(getContext());
        distanceRow.setOrientation(LinearLayout.HORIZONTAL);
        distanceRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams distLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        distLp.bottomMargin = dp(8f);
        distanceRow.setLayoutParams(distLp);

        tvAutoDistance = new TextView(getContext());
        tvAutoDistance.setText("Cự ly: -- cm");
        tvAutoDistance.setTextSize(12.5f);
        tvAutoDistance.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        tvAutoDistance.setTextColor(Color.parseColor("#0F172A"));
        tvAutoDistance.setPadding(0, 0, dp(8f), 0);
        distanceRow.addView(tvAutoDistance);

        tvAutoStatusBadge = new TextView(getContext());
        tvAutoStatusBadge.setText("Chờ lệnh (Giữ nút để chạy)");
        tvAutoStatusBadge.setTextSize(10f);
        tvAutoStatusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvAutoStatusBadge.setTextColor(Color.parseColor("#475569"));

        GradientDrawable statusBadgeBg = new GradientDrawable();
        statusBadgeBg.setShape(GradientDrawable.RECTANGLE);
        statusBadgeBg.setCornerRadius(dpF(12f));
        statusBadgeBg.setColor(Color.parseColor("#F1F5F9"));
        statusBadgeBg.setStroke(dp(1f), Color.parseColor("#CBD5E1"));
        tvAutoStatusBadge.setBackground(statusBadgeBg);
        tvAutoStatusBadge.setPadding(dp(8f), dp(3f), dp(8f), dp(3f));
        distanceRow.addView(tvAutoStatusBadge);
        layoutAutoContent.addView(distanceRow);

        // Hàng nút hành động: NÚT AN TOÀN HOLD-TO-RUN & DỪNG KHẨN CẤP
        LinearLayout autoActionRow = new LinearLayout(getContext());
        autoActionRow.setOrientation(LinearLayout.HORIZONTAL);
        autoActionRow.setGravity(Gravity.CENTER);
        autoActionRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        GradientDrawable normalHoldBg = new GradientDrawable();
        normalHoldBg.setShape(GradientDrawable.RECTANGLE);
        normalHoldBg.setCornerRadius(dpF(20f));
        normalHoldBg.setColor(Color.parseColor("#059669")); // Emerald 600

        GradientDrawable pressedHoldBg = new GradientDrawable();
        pressedHoldBg.setShape(GradientDrawable.RECTANGLE);
        pressedHoldBg.setCornerRadius(dpF(20f));
        pressedHoldBg.setColor(Color.parseColor("#10B981")); // Emerald 500

        btnHoldToRun = new TextView(getContext());
        btnHoldToRun.setText("🛡️ GIỮ ĐỂ CHẠY (HOLD)");
        btnHoldToRun.setTextSize(12f);
        btnHoldToRun.setTypeface(Typeface.DEFAULT_BOLD);
        btnHoldToRun.setTextColor(Color.WHITE);
        btnHoldToRun.setGravity(Gravity.CENTER);
        btnHoldToRun.setBackground(normalHoldBg);
        btnHoldToRun.setElevation(dpF(4f));
        btnHoldToRun.setPadding(dp(22f), dp(10f), dp(22f), dp(10f));
        btnHoldToRun.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setBackground(pressedHoldBg);
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start();
                    if (autoFollowManager != null) {
                        autoFollowManager.setHoldToRun(true);
                    }
                    ButtonSyncManager.sendButtonEvent("AUTO_HOLD", "DOWN");
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setBackground(normalHoldBg);
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start();
                    if (autoFollowManager != null) {
                        autoFollowManager.setHoldToRun(false);
                    }
                    ButtonSyncManager.sendButtonEvent("AUTO_HOLD", "UP");
                    return true;
                default:
                    return false;
            }
        });
        autoActionRow.addView(btnHoldToRun);

        autoActionRow.addView(createHorizontalSpacer(dp(10f)));

        btnAutoEmergencyStop = new TextView(getContext());
        btnAutoEmergencyStop.setText("🛑 DỪNG");
        btnAutoEmergencyStop.setTextSize(11.5f);
        btnAutoEmergencyStop.setTypeface(Typeface.DEFAULT_BOLD);
        btnAutoEmergencyStop.setTextColor(Color.WHITE);
        btnAutoEmergencyStop.setGravity(Gravity.CENTER);

        GradientDrawable emergBg = new GradientDrawable();
        emergBg.setShape(GradientDrawable.RECTANGLE);
        emergBg.setCornerRadius(dpF(20f));
        emergBg.setColor(Color.parseColor("#DC2626")); // Red 600
        btnAutoEmergencyStop.setBackground(emergBg);
        btnAutoEmergencyStop.setElevation(dpF(4f));
        btnAutoEmergencyStop.setPadding(dp(16f), dp(10f), dp(16f), dp(10f));
        btnAutoEmergencyStop.setOnClickListener(v -> {
            if (autoFollowManager != null) {
                autoFollowManager.emergencyStop();
            }
            ButtonSyncManager.sendButtonEvent("EMERGENCY", "CLICK");
            Toast.makeText(getContext(), "DỪNG KHẨN CẤP!", Toast.LENGTH_SHORT).show();
        });
        autoActionRow.addView(btnAutoEmergencyStop);
        layoutAutoContent.addView(autoActionRow);

        bottomCenterCard.addView(layoutAutoContent);
        addView(bottomCenterCard);

        updateOperatingModeUi(AutoFollowManager.OperatingMode.MANUAL);
        updateAutoSubModeUi(AutoFollowManager.AutoSubMode.FOLLOW_50CM);

        // =========================================================================
        // 4. GÓC DƯỚI PHẢI: 2 NÚT VUÔNG BO GÓC MÀU XANH XOAY TRÁI (↺) & XOAY PHẢI (↻)
        // =========================================================================
        LinearLayout turnRow = new LinearLayout(getContext());
        turnRow.setOrientation(LinearLayout.HORIZONTAL);
        turnRow.setGravity(Gravity.CENTER_VERTICAL);
        turnRow.setMotionEventSplittingEnabled(true);

        LayoutParams turnLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        turnLp.gravity = Gravity.BOTTOM | Gravity.END;
        turnLp.rightMargin = dp(22f);
        turnLp.bottomMargin = dp(18f);
        turnRow.setLayoutParams(turnLp);

        btnTurnL = createBlueRoundedSquareTurnButton(true, pressed -> {
            if (checkDrivingPermission(pressed)) {
                udpManager.turnLeft(pressed);
                ButtonSyncManager.sendButtonEvent("TURN_LEFT", pressed ? "DOWN" : "UP");
            }
        });
        turnRow.addView(btnTurnL);

        turnRow.addView(createHorizontalSpacer(dp(12f)));

        btnTurnR = createBlueRoundedSquareTurnButton(false, pressed -> {
            if (checkDrivingPermission(pressed)) {
                udpManager.turnRight(pressed);
                ButtonSyncManager.sendButtonEvent("TURN_RIGHT", pressed ? "DOWN" : "UP");
            }
        });
        turnRow.addView(btnTurnR);

        addView(turnRow);
    }

    private void cycleGimbalMode() {
        GimbalBleManager.FollowMode next = controllerGimbalMode.next();
        updateGimbalModeUi(next);
        GimbalBleManager.sendRemoteGimbalCommand(
                getContext(), "GIMBAL_MODE", "CLICK", next.getLabel(),
                "gimbal/mode?m=" + next.getLabel()
        );
        Toast.makeText(getContext(), "Gimbal Mode: " + next.getDesc(), Toast.LENGTH_SHORT).show();
    }

    private void handleJoystickInput(float nx, float ny, boolean active) {
        if (!active) {
            udpManager.driveJoystick(0f, 0f);
            if (syncedDirection != null) {
                ButtonSyncManager.sendButtonEvent(syncedDirection, "UP");
                syncedDirection = null;
            }
            return;
        }

        if (!checkDrivingPermission(true)) {
            udpManager.stopMotors();
            return;
        }

        udpManager.driveJoystick(nx, ny);

        float mag = (float) Math.hypot(nx, ny);
        String dominantDir;
        if (mag < 0.22f) {
            dominantDir = null;
        } else if (Math.abs(ny) >= Math.abs(nx)) {
            dominantDir = ny > 0 ? "UP" : "DOWN";
        } else {
            dominantDir = nx > 0 ? "RIGHT" : "LEFT";
        }

        if (dominantDir != null && !dominantDir.equals(syncedDirection)) {
            if (syncedDirection != null) {
                ButtonSyncManager.sendButtonEvent(syncedDirection, "UP");
            }
            ButtonSyncManager.sendButtonEvent(dominantDir, "DOWN");
            syncedDirection = dominantDir;
        } else if (dominantDir == null && syncedDirection != null) {
            ButtonSyncManager.sendButtonEvent(syncedDirection, "UP");
            syncedDirection = null;
        }
    }

    public void applyControlSizes(int totalW, int totalH) {
        if (totalW <= 0 || totalH <= 0) return;
        boolean isCompact = totalW < dp(700f);
        int joyDim = isCompact ? dp(148f) : dp(180f);
        if (joystickView != null && joystickView.getLayoutParams() instanceof LayoutParams) {
            LayoutParams lp = (LayoutParams) joystickView.getLayoutParams();
            lp.width = joyDim;
            lp.height = joyDim;
            joystickView.setLayoutParams(lp);
        }
    }

    private enum ChevronDir { LEFT, UP, DOWN, RIGHT, CENTER }

    public interface HoldListener {
        void onHold(boolean isHeld);
    }

    @SuppressLint("ClickableViewAccessibility")
    private View createGimbalCircleChevronButton(ChevronDir dir, HoldListener onHold) {
        int size = dp(34f);
        View view = new View(getContext()) {
            public boolean isHeldState = false;

            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                bgPaint.setStyle(Paint.Style.FILL);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(dpF(1.2f));
                iconPaint.setStyle(Paint.Style.STROKE);
                iconPaint.setStrokeWidth(dpF(2f));
                iconPaint.setStrokeCap(Paint.Cap.ROUND);
                iconPaint.setStrokeJoin(Paint.Join.ROUND);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float r = (Math.min(getWidth(), getHeight()) / 2f) - dpF(1.5f);

                if (isHeldState) {
                    bgPaint.setColor(Color.parseColor("#1D5CE5"));
                    strokePaint.setColor(Color.parseColor("#1D5CE5"));
                    iconPaint.setColor(Color.WHITE);
                } else {
                    bgPaint.setColor(Color.WHITE);
                    strokePaint.setColor(Color.parseColor("#CBD5E1"));
                    iconPaint.setColor(Color.parseColor("#1D5CE5"));
                }

                canvas.drawCircle(cx, cy, r, bgPaint);
                canvas.drawCircle(cx, cy, r, strokePaint);

                float s = dpF(3.8f);
                Path path = new Path();
                switch (dir) {
                    case LEFT:
                        path.moveTo(cx + s * 0.5f, cy - s);
                        path.lineTo(cx - s * 0.6f, cy);
                        path.lineTo(cx + s * 0.5f, cy + s);
                        break;
                    case RIGHT:
                        path.moveTo(cx - s * 0.5f, cy - s);
                        path.lineTo(cx + s * 0.6f, cy);
                        path.lineTo(cx - s * 0.5f, cy + s);
                        break;
                    case UP:
                        path.moveTo(cx - s, cy + s * 0.5f);
                        path.lineTo(cx, cy - s * 0.6f);
                        path.lineTo(cx + s, cy + s * 0.5f);
                        break;
                    case DOWN:
                        path.moveTo(cx - s, cy - s * 0.5f);
                        path.lineTo(cx, cy + s * 0.6f);
                        path.lineTo(cx + s, cy - s * 0.5f);
                        break;
                    case CENTER:
                        canvas.drawCircle(cx, cy, dpF(3.2f), iconPaint);
                        float cr1 = dpF(4.8f);
                        float cr2 = dpF(7.5f);
                        canvas.drawLine(cx - cr2, cy, cx - cr1, cy, iconPaint);
                        canvas.drawLine(cx + cr1, cy, cx + cr2, cy, iconPaint);
                        canvas.drawLine(cx, cy - cr2, cx, cy - cr1, iconPaint);
                        canvas.drawLine(cx, cy + cr1, cx, cy + cr2, iconPaint);
                        break;
                }
                if (dir != ChevronDir.CENTER) {
                    canvas.drawPath(path, iconPaint);
                }
            }
        };

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.leftMargin = dp(3f);
        lp.rightMargin = dp(3f);
        view.setLayoutParams(lp);

        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    setGimbalHoldVisual(v, true);
                    onHold.onHold(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    setGimbalHoldVisual(v, false);
                    onHold.onHold(false);
                    return true;
                default:
                    return false;
            }
        });

        return view;
    }

    private void setGimbalHoldVisual(View btn, boolean held) {
        try {
            Field field = btn.getClass().getDeclaredField("isHeldState");
            field.setAccessible(true);
            field.setBoolean(btn, held);
            btn.invalidate();
        } catch (Exception ignored) {
            btn.setAlpha(held ? 0.7f : 1.0f);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private FrameLayout createBlueRoundedSquareTurnButton(boolean isLeft, HoldListener onHoldStateChanged) {
        int w = dp(82f);
        int h = dp(72f);
        FrameLayout container = new FrameLayout(getContext());
        container.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        container.setElevation(dpF(5f));

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.RECTANGLE);
        normalBg.setCornerRadius(dpF(20f));
        normalBg.setColor(Color.parseColor("#1D5CE5"));

        GradientDrawable pressedBg = new GradientDrawable();
        pressedBg.setShape(GradientDrawable.RECTANGLE);
        pressedBg.setCornerRadius(dpF(20f));
        pressedBg.setColor(Color.parseColor("#1342A8"));

        container.setBackground(normalBg);

        View iconView = new View(getContext()) {
            private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                arcPaint.setColor(Color.WHITE);
                arcPaint.setStyle(Paint.Style.STROKE);
                arcPaint.setStrokeWidth(dpF(2.6f));
                arcPaint.setStrokeCap(Paint.Cap.ROUND);
                arcPaint.setStrokeJoin(Paint.Join.ROUND);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float r = dpF(12f);
                RectF rect = new RectF(cx - r, cy - r, cx + r, cy + r);

                if (isLeft) {
                    canvas.drawArc(rect, -40f, 285f, false, arcPaint);
                    double endAngleRad = Math.toRadians(-40.0);
                    float ax = (float) (cx + r * Math.cos(endAngleRad));
                    float ay = (float) (cy + r * Math.sin(endAngleRad));
                    Path arrowPath = new Path();
                    arrowPath.moveTo(ax - dpF(5.5f), ay - dpF(1f));
                    arrowPath.lineTo(ax, ay);
                    arrowPath.lineTo(ax - dpF(1f), ay + dpF(5.5f));
                    canvas.drawPath(arrowPath, arcPaint);
                } else {
                    canvas.drawArc(rect, 220f, -285f, false, arcPaint);
                    double endAngleRad = Math.toRadians(220.0);
                    float ax = (float) (cx + r * Math.cos(endAngleRad));
                    float ay = (float) (cy + r * Math.sin(endAngleRad));
                    Path arrowPath = new Path();
                    arrowPath.moveTo(ax + dpF(5.5f), ay - dpF(1f));
                    arrowPath.lineTo(ax, ay);
                    arrowPath.lineTo(ax + dpF(1f), ay + dpF(5.5f));
                    canvas.drawPath(arrowPath, arcPaint);
                }
            }
        };
        iconView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        container.addView(iconView);

        container.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    v.setBackground(pressedBg);
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).start();
                    onHoldStateChanged.onHold(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setBackground(normalBg);
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(70).start();
                    onHoldStateChanged.onHold(false);
                    return true;
                default:
                    return false;
            }
        });

        return container;
    }

    private TextView createSmallCamPillButton(String text, Runnable onClick) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(10.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.parseColor("#0F172A"));
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpF(16f));
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(1.2f), Color.parseColor("#CBD5E1"));
        tv.setBackground(bg);
        tv.setPadding(dp(18f), dp(6f), dp(18f), dp(6f));
        tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    // =========================================================================
    // 5. DIALOG BẢNG THÔNG SỐ & CÀI ĐẶT ROBOT TẬP TRUNG (2 CỘT CHUẨN MOCKUP)
    // =========================================================================
    public void showParametersBottomSheet() {
        Dialog dialog = new Dialog(getContext());
        paramsDialog = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0.55f);
        }

        LinearLayout rootCard = new LinearLayout(getContext());
        rootCard.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setShape(GradientDrawable.RECTANGLE);
        rootBg.setCornerRadius(dpF(26f));
        rootBg.setColor(Color.WHITE);
        rootCard.setBackground(rootBg);
        rootCard.setElevation(dpF(10f));
        rootCard.setPadding(dp(24f), dp(20f), dp(24f), dp(20f));

        // Tiêu đề & Nút Phân quyền + Đóng
        LinearLayout titleRow = new LinearLayout(getContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleLp.bottomMargin = dp(14f);
        titleRow.setLayoutParams(titleLp);

        TextView tvTitle = new TextView(getContext());
        tvTitle.setText("BẢNG THÔNG SỐ & CÀI ĐẶT XE");
        tvTitle.setTextSize(15.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(Color.parseColor("#1D5CE5"));
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(tvTitle);

        TextView btnPerm = new TextView(getContext());
        btnPerm.setText(getPermissionLabel(udpManager.permissionMode));
        btnPerm.setTextSize(11f);
        btnPerm.setTypeface(Typeface.DEFAULT_BOLD);
        btnPerm.setTextColor(Color.WHITE);
        btnPerm.setGravity(Gravity.CENTER);

        GradientDrawable permBg = new GradientDrawable();
        permBg.setShape(GradientDrawable.RECTANGLE);
        permBg.setCornerRadius(dpF(18f));
        permBg.setColor(Color.parseColor("#1D5CE5"));
        btnPerm.setBackground(permBg);
        btnPerm.setPadding(dp(16f), dp(7f), dp(16f), dp(7f));
        btnPerm.setOnClickListener(v -> {
            RobotUdpManager.ControlPermission next = udpManager.cyclePermissionMode();
            btnPerm.setText(getPermissionLabel(next));
        });
        titleRow.addView(btnPerm);
        titleRow.addView(createHorizontalSpacer(dp(8f)));

        TextView btnClose = new TextView(getContext());
        btnClose.setText("Đóng");
        btnClose.setTextSize(11f);
        btnClose.setTypeface(Typeface.DEFAULT_BOLD);
        btnClose.setTextColor(Color.parseColor("#0F172A"));
        btnClose.setGravity(Gravity.CENTER);

        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.RECTANGLE);
        closeBg.setCornerRadius(dpF(18f));
        closeBg.setColor(Color.WHITE);
        closeBg.setStroke(dp(1.2f), Color.parseColor("#CBD5E1"));
        btnClose.setBackground(closeBg);
        btnClose.setPadding(dp(16f), dp(7f), dp(16f), dp(7f));
        btnClose.setOnClickListener(v -> dialog.dismiss());
        titleRow.addView(btnClose);
        rootCard.addView(titleRow);

        NestedScrollView bodyScroll = new NestedScrollView(getContext());
        bodyScroll.setFillViewport(true);
        bodyScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Bố cục 2 cột ngang: Cột trái (Monitor) | Cột phải (Tốc độ bánh + Robot Setpoint)
        LinearLayout twoColLayout = new LinearLayout(getContext());
        twoColLayout.setOrientation(LinearLayout.HORIZONTAL);
        twoColLayout.setWeightSum(1f);
        twoColLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Cột Trái: Thông số xe thời gian thực
        LinearLayout leftCol = new LinearLayout(getContext());
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.45f));
        leftCol.addView(createDialogTelemetryCard());
        twoColLayout.addView(leftCol);

        twoColLayout.addView(createHorizontalSpacer(dp(14f)));

        // Cột Phải: Cài đặt tốc độ bánh + Cài đặt vị trí & vận tốc mục tiêu
        LinearLayout rightCol = new LinearLayout(getContext());
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.55f));
        rightCol.addView(createDialogWheelVelocityCard());
        rightCol.addView(createVerticalSpacer(dp(12f)));
        rightCol.addView(createDialogRobotSetpointCard());
        twoColLayout.addView(rightCol);

        bodyScroll.addView(twoColLayout);
        rootCard.addView(bodyScroll);

        dialog.setContentView(rootCard);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout((int) (dm.widthPixels * 0.92f), LinearLayout.LayoutParams.WRAP_CONTENT);
        }

        dialog.setOnDismissListener(d -> {
            if (dialogEtW1 != null) savedW1 = dialogEtW1.getText().toString();
            if (dialogEtW2 != null) savedW2 = dialogEtW2.getText().toString();
            if (dialogEtW3 != null) savedW3 = dialogEtW3.getText().toString();
            if (dialogEtW4 != null) savedW4 = dialogEtW4.getText().toString();

            if (dialogEtVx != null) savedVx = dialogEtVx.getText().toString();
            if (dialogEtVy != null) savedVy = dialogEtVy.getText().toString();
            if (dialogEtWSet != null) savedWSet = dialogEtWSet.getText().toString();
            if (dialogEtXSet != null) savedXSet = dialogEtXSet.getText().toString();
            if (dialogEtYSet != null) savedYSet = dialogEtYSet.getText().toString();
            if (dialogEtThetaSet != null) savedThetaSet = dialogEtThetaSet.getText().toString();

            dialogTvX = null;
            dialogTvY = null;
            dialogTvTheta = null;
            dialogTvVx = null;
            dialogTvVy = null;
            dialogTvW = null;
            dialogTvW1 = null;
            dialogTvW2 = null;
            dialogTvW3 = null;
            dialogTvW4 = null;

            dialogEtW1 = null;
            dialogEtW2 = null;
            dialogEtW3 = null;
            dialogEtW4 = null;

            dialogEtVx = null;
            dialogEtVy = null;
            dialogEtWSet = null;
            dialogEtXSet = null;
            dialogEtYSet = null;
            dialogEtThetaSet = null;

            paramsDialog = null;
        });

        if (latestTelemetry != null) {
            updateDialogTelemetry(latestTelemetry);
        }

        dialog.show();
    }

    private String getPermissionLabel(RobotUdpManager.ControlPermission m) {
        if (m == null) return "Quyền: Tự động";
        switch (m) {
            case MASTER:
                return "Quyền: Độc quyền";
            case VIEWER:
                return "Quyền: Chỉ xem";
            case AUTO:
            default:
                return "Quyền: Tự động";
        }
    }

    private View createDialogTelemetryCard() {
        LinearLayout card = createCardContainer();
        card.addView(createSectionHeader("THÔNG SỐ XE THỜI GIAN THỰC (MONITOR)"));

        LinearLayout row1 = new LinearLayout(getContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setWeightSum(3f);
        row1.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dialogTvX = createTelemetryPill(row1, "X (m)", "0.000");
        dialogTvY = createTelemetryPill(row1, "Y (m)", "0.000");
        dialogTvTheta = createTelemetryPill(row1, "θ (độ)", "0.00°");
        card.addView(row1);

        card.addView(createVerticalSpacer(dp(8f)));

        LinearLayout row2 = new LinearLayout(getContext());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setWeightSum(3f);
        row2.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dialogTvVx = createTelemetryPill(row2, "Vx (m/s)", "0.000");
        dialogTvVy = createTelemetryPill(row2, "Vy (m/s)", "0.000");
        dialogTvW = createTelemetryPill(row2, "W (rad/s)", "0.000");
        card.addView(row2);

        card.addView(createVerticalSpacer(dp(8f)));

        LinearLayout row3 = new LinearLayout(getContext());
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setWeightSum(2f);
        row3.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dialogTvW1 = createTelemetryPill(row3, "W_RL", "0.000");
        dialogTvW2 = createTelemetryPill(row3, "W_FL", "0.000");
        card.addView(row3);

        card.addView(createVerticalSpacer(dp(8f)));

        LinearLayout row4 = new LinearLayout(getContext());
        row4.setOrientation(LinearLayout.HORIZONTAL);
        row4.setWeightSum(2f);
        row4.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dialogTvW3 = createTelemetryPill(row4, "W_RR", "0.000");
        dialogTvW4 = createTelemetryPill(row4, "W_FR", "0.000");
        card.addView(row4);

        return card;
    }

    private void updateDialogTelemetry(RobotUdpManager.RobotTelemetry t) {
        if (dialogTvX != null) dialogTvX.setText(String.format(Locale.US, "%.3f", t.x));
        if (dialogTvY != null) dialogTvY.setText(String.format(Locale.US, "%.3f", t.y));
        if (dialogTvTheta != null) dialogTvTheta.setText(String.format(Locale.US, "%.2f°", t.thetaDeg));

        if (dialogTvVx != null) dialogTvVx.setText(String.format(Locale.US, "%.3f", t.vx));
        if (dialogTvVy != null) dialogTvVy.setText(String.format(Locale.US, "%.3f", t.vy));
        if (dialogTvW != null) dialogTvW.setText(String.format(Locale.US, "%.3f", t.w));

        if (dialogTvW1 != null) dialogTvW1.setText(String.format(Locale.US, "%.3f", t.w1));
        if (dialogTvW2 != null) dialogTvW2.setText(String.format(Locale.US, "%.3f", t.w2));
        if (dialogTvW3 != null) dialogTvW3.setText(String.format(Locale.US, "%.3f", t.w3));
        if (dialogTvW4 != null) dialogTvW4.setText(String.format(Locale.US, "%.3f", t.w4));
    }

    private View createDialogWheelVelocityCard() {
        LinearLayout card = createCardContainer();
        card.addView(createSectionHeader("CÀI ĐẶT TỐC ĐỘ BÁNH XE (RAD/S)"));

        LinearLayout grid = new LinearLayout(getContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout r1 = new LinearLayout(getContext());
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setWeightSum(2f);
        r1.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dialogEtW1 = createLabeledInput(r1, "W Rear Left:", savedW1);
        dialogEtW2 = createLabeledInput(r1, "W Front Left:", savedW2);
        grid.addView(r1);

        grid.addView(createVerticalSpacer(dp(6f)));

        LinearLayout r2 = new LinearLayout(getContext());
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setWeightSum(2f);
        r2.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dialogEtW3 = createLabeledInput(r2, "W Rear Right:", savedW3);
        dialogEtW4 = createLabeledInput(r2, "W Front Right:", savedW4);
        grid.addView(r2);

        card.addView(grid);
        card.addView(createVerticalSpacer(dp(10f)));

        LinearLayout btnRow = new LinearLayout(getContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView btnReset = createOutlineActionButton("Reset", () -> {
            if (!checkDrivingPermission(true)) return;
            savedW1 = "0"; savedW2 = "0"; savedW3 = "0"; savedW4 = "0";
            if (dialogEtW1 != null) dialogEtW1.setText("0");
            if (dialogEtW2 != null) dialogEtW2.setText("0");
            if (dialogEtW3 != null) dialogEtW3.setText("0");
            if (dialogEtW4 != null) dialogEtW4.setText("0");
            udpManager.resetWheelVelocities();
            ButtonSyncManager.sendButtonEvent("RESET_WHEELS", "CLICK");
            Toast.makeText(getContext(), "Đã reset tốc độ bánh về 0", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(btnReset);

        btnRow.addView(createHorizontalSpacer(dp(8f)));

        TextView btnSet = createSmallActionButton("Set Vận Tốc", Color.parseColor("#1D5CE5"), () -> {
            if (!checkDrivingPermission(true)) return;
            float w1 = 0f, w2 = 0f, w3 = 0f, w4 = 0f;
            try {
                if (dialogEtW1 != null) w1 = Float.parseFloat(dialogEtW1.getText().toString());
                if (dialogEtW2 != null) w2 = Float.parseFloat(dialogEtW2.getText().toString());
                if (dialogEtW3 != null) w3 = Float.parseFloat(dialogEtW3.getText().toString());
                if (dialogEtW4 != null) w4 = Float.parseFloat(dialogEtW4.getText().toString());
            } catch (Exception ignored) {}
            savedW1 = String.valueOf(w1);
            savedW2 = String.valueOf(w2);
            savedW3 = String.valueOf(w3);
            savedW4 = String.valueOf(w4);
            udpManager.setWheelVelocities(w1, w2, w3, w4);
            ButtonSyncManager.sendButtonEvent("SET_WHEELS", "CLICK", w1 + "," + w2 + "," + w3 + "," + w4);
            Toast.makeText(getContext(), "Đã set tốc độ: RL=" + w1 + ", FL=" + w2 + ", RR=" + w3 + ", FR=" + w4, Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(btnSet);

        card.addView(btnRow);
        return card;
    }

    private View createDialogRobotSetpointCard() {
        LinearLayout card = createCardContainer();
        card.addView(createSectionHeader("CÀI ĐẶT VỊ TRÍ & VẬN TỐC MỤC TIÊU (ROBOT SETPOINT)"));

        LinearLayout setGrid = new LinearLayout(getContext());
        setGrid.setOrientation(LinearLayout.VERTICAL);
        setGrid.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout r1 = new LinearLayout(getContext());
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setWeightSum(3f);
        r1.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dialogEtVx = createLabeledInput(r1, "Vx (m/s):", savedVx);
        dialogEtVy = createLabeledInput(r1, "Vy (m/s):", savedVy);
        dialogEtWSet = createLabeledInput(r1, "W (rad/s):", savedWSet);
        setGrid.addView(r1);

        setGrid.addView(createVerticalSpacer(dp(6f)));

        LinearLayout r2 = new LinearLayout(getContext());
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setWeightSum(3f);
        r2.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dialogEtXSet = createLabeledInput(r2, "X (m):", savedXSet);
        dialogEtYSet = createLabeledInput(r2, "Y (m):", savedYSet);
        dialogEtThetaSet = createLabeledInput(r2, "θ (độ):", savedThetaSet);
        setGrid.addView(r2);

        card.addView(setGrid);
        card.addView(createVerticalSpacer(dp(10f)));

        LinearLayout btnRow = new LinearLayout(getContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView btnResetSetpoints = createOutlineActionButton("Reset", () -> {
            if (!checkDrivingPermission(true)) return;
            savedVx = "0"; savedVy = "0"; savedWSet = "0";
            savedXSet = "0"; savedYSet = "0"; savedThetaSet = "0";
            if (dialogEtVx != null) dialogEtVx.setText("0");
            if (dialogEtVy != null) dialogEtVy.setText("0");
            if (dialogEtWSet != null) dialogEtWSet.setText("0");
            if (dialogEtXSet != null) dialogEtXSet.setText("0");
            if (dialogEtYSet != null) dialogEtYSet.setText("0");
            if (dialogEtThetaSet != null) dialogEtThetaSet.setText("0");
            udpManager.resetRobotValues();
            ButtonSyncManager.sendButtonEvent("RESET_ROBOT", "CLICK");
            Toast.makeText(getContext(), "Đã reset thông số về 0", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(btnResetSetpoints);

        btnRow.addView(createHorizontalSpacer(dp(8f)));

        TextView btnSetRobot = createSmallActionButton("Set Mục Tiêu", Color.parseColor("#1D5CE5"), () -> {
            if (!checkDrivingPermission(true)) return;
            float vx = 0f, vy = 0f, w = 0f, x = 0f, y = 0f, th = 0f;
            try {
                if (dialogEtVx != null) vx = Float.parseFloat(dialogEtVx.getText().toString());
                if (dialogEtVy != null) vy = Float.parseFloat(dialogEtVy.getText().toString());
                if (dialogEtWSet != null) w = Float.parseFloat(dialogEtWSet.getText().toString());
                if (dialogEtXSet != null) x = Float.parseFloat(dialogEtXSet.getText().toString());
                if (dialogEtYSet != null) y = Float.parseFloat(dialogEtYSet.getText().toString());
                if (dialogEtThetaSet != null) th = Float.parseFloat(dialogEtThetaSet.getText().toString());
            } catch (Exception ignored) {}
            savedVx = String.valueOf(vx);
            savedVy = String.valueOf(vy);
            savedWSet = String.valueOf(w);
            savedXSet = String.valueOf(x);
            savedYSet = String.valueOf(y);
            savedThetaSet = String.valueOf(th);
            udpManager.setRobotValues(vx, vy, w, x, y, th);
            ButtonSyncManager.sendButtonEvent("SET_ROBOT", "CLICK", vx + "," + vy + "," + w + "," + x + "," + y + "," + th);
            Toast.makeText(getContext(), "Đã gửi thông số Setpoint:\nVx=" + vx + ", Vy=" + vy + ", W=" + w + ", X=" + x + ", Y=" + y + ", θ=" + th + "°", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(btnSetRobot);

        card.addView(btnRow);
        return card;
    }

    private boolean checkDrivingPermission(boolean pressed) {
        if (udpManager.permissionMode == RobotUdpManager.ControlPermission.VIEWER) {
            if (pressed) {
                Toast.makeText(
                        getContext(),
                        "Đang ở chế độ CHỈ XEM! Mở [Cài đặt] để đổi quyền điều khiển.",
                        Toast.LENGTH_SHORT
                ).show();
            }
            return false;
        }
        return true;
    }

    public void updatePermissionStatusText(String statusMsg) {
        post(() -> {
            if (tvStatusBadge != null) {
                tvStatusBadge.setText(statusMsg);
                tvStatusBadge.setTextColor(Color.parseColor("#0284C7"));
            }
        });
    }

    public void updateUdpBannerState(boolean connected, String statusText) {
        post(() -> {
            if (tvStatusBadge != null) {
                tvStatusBadge.setText(statusText);
                tvStatusBadge.setTextColor(
                        connected ? Color.parseColor("#15803D") : Color.parseColor("#64748B")
                );
            }
        });
    }

    private void setupListeners() {
        udpManager.setOnTelemetryReceived(t -> {
            latestTelemetry = t;
            post(() -> {
                if (tvMiniTelemetry != null) {
                    tvMiniTelemetry.setText(String.format(
                            Locale.US,
                            "Vx: %.3f m/s  ·  Vy: %.3f m/s  ·  θ: %.2f°",
                            t.vx, t.vy, t.thetaDeg
                    ));
                }
                if (dialogTvX != null) {
                    updateDialogTelemetry(t);
                }
            });
        });

        ButtonSyncManager.addListener((btnId, action, extra) -> {
            post(() -> {
                if (btnId == null) return;
                switch (btnId) {
                    case "PERMISSION":
                        if ("MASTER".equals(action)) {
                            udpManager.setPermission(
                                    RobotUdpManager.ControlPermission.VIEWER,
                                    "Chỉ xem (Máy khác đang giữ Độc quyền)"
                            );
                            Toast.makeText(getContext(), "Thiết bị khác đã bật Độc quyền lái -> Chuyển sang Chỉ xem", Toast.LENGTH_SHORT).show();
                        } else if ("RELEASE".equals(action)) {
                            if (udpManager.permissionMode == RobotUdpManager.ControlPermission.VIEWER) {
                                udpManager.setPermission(
                                        RobotUdpManager.ControlPermission.AUTO,
                                        "Tự động (Đã mở khóa quyền lái)"
                                    );
                                Toast.makeText(getContext(), "Đã mở khóa quyền điều khiển -> Chế độ Tự động", Toast.LENGTH_SHORT).show();
                            }
                        }
                        break;
                    case "CLAIM_CONTROL":
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote();
                            if (tvStatusBadge != null) {
                                if ("DOWN".equals(action)) {
                                    tvStatusBadge.setText("Thiết bị khác đang điều khiển...");
                                    tvStatusBadge.setTextColor(Color.parseColor("#F59E0B"));
                                } else {
                                    tvStatusBadge.setText("Đã kết nối UDP");
                                    tvStatusBadge.setTextColor(Color.parseColor("#15803D"));
                                }
                            }
                        }
                        break;
                    case "UP":
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote();
                        }
                        if (joystickView != null) {
                            joystickView.setExternalKnob(0f, "DOWN".equals(action) ? 1f : 0f);
                        }
                        break;
                    case "DOWN":
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote();
                        }
                        if (joystickView != null) {
                            joystickView.setExternalKnob(0f, "DOWN".equals(action) ? -1f : 0f);
                        }
                        break;
                    case "LEFT":
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote();
                        }
                        if (joystickView != null) {
                            joystickView.setExternalKnob("DOWN".equals(action) ? -1f : 0f, 0f);
                        }
                        break;
                    case "RIGHT":
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote();
                        }
                        if (joystickView != null) {
                            joystickView.setExternalKnob("DOWN".equals(action) ? 1f : 0f, 0f);
                        }
                        break;
                    case "TURN_LEFT":
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote();
                        }
                        if (btnTurnL != null) setTurnButtonHeld(btnTurnL, "DOWN".equals(action));
                        break;
                    case "TURN_RIGHT":
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote();
                        }
                        if (btnTurnR != null) setTurnButtonHeld(btnTurnR, "DOWN".equals(action));
                        break;
                    case "CIRCLE_LINE":
                    case "RETURN_HOME":
                    case "EMERGENCY":
                        udpManager.yieldControlToRemote();
                        break;
                    case "RESET_WHEELS":
                        savedW1 = "0"; savedW2 = "0"; savedW3 = "0"; savedW4 = "0";
                        if (dialogEtW1 != null) dialogEtW1.setText("0");
                        if (dialogEtW2 != null) dialogEtW2.setText("0");
                        if (dialogEtW3 != null) dialogEtW3.setText("0");
                        if (dialogEtW4 != null) dialogEtW4.setText("0");
                        udpManager.yieldControlToRemote();
                        break;
                    case "SET_WHEELS":
                        if (extra != null && !extra.isEmpty()) {
                            String[] nums = extra.split(",");
                            if (nums.length >= 4) {
                                savedW1 = nums[0]; savedW2 = nums[1]; savedW3 = nums[2]; savedW4 = nums[3];
                                if (dialogEtW1 != null) dialogEtW1.setText(nums[0]);
                                if (dialogEtW2 != null) dialogEtW2.setText(nums[1]);
                                if (dialogEtW3 != null) dialogEtW3.setText(nums[2]);
                                if (dialogEtW4 != null) dialogEtW4.setText(nums[3]);
                                udpManager.yieldControlToRemote();
                            }
                        }
                        break;
                    case "RESET_ROBOT":
                        savedVx = "0"; savedVy = "0"; savedWSet = "0";
                        savedXSet = "0"; savedYSet = "0"; savedThetaSet = "0";
                        if (dialogEtVx != null) dialogEtVx.setText("0");
                        if (dialogEtVy != null) dialogEtVy.setText("0");
                        if (dialogEtWSet != null) dialogEtWSet.setText("0");
                        if (dialogEtXSet != null) dialogEtXSet.setText("0");
                        if (dialogEtYSet != null) dialogEtYSet.setText("0");
                        if (dialogEtThetaSet != null) dialogEtThetaSet.setText("0");
                        udpManager.yieldControlToRemote();
                        break;
                    case "SET_ROBOT":
                        if (extra != null && !extra.isEmpty()) {
                            String[] nums = extra.split(",");
                            if (nums.length >= 6) {
                                savedVx = nums[0]; savedVy = nums[1]; savedWSet = nums[2];
                                savedXSet = nums[3]; savedYSet = nums[4]; savedThetaSet = nums[5];
                                if (dialogEtVx != null) dialogEtVx.setText(nums[0]);
                                if (dialogEtVy != null) dialogEtVy.setText(nums[1]);
                                if (dialogEtWSet != null) dialogEtWSet.setText(nums[2]);
                                if (dialogEtXSet != null) dialogEtXSet.setText(nums[3]);
                                if (dialogEtYSet != null) dialogEtYSet.setText(nums[4]);
                                if (dialogEtThetaSet != null) dialogEtThetaSet.setText(nums[5]);
                                udpManager.yieldControlToRemote();
                            }
                        }
                        break;
                    case "GIMBAL_LEFT":
                        if (btnGimbalLeft != null) setGimbalHoldVisual(btnGimbalLeft, "DOWN".equals(action));
                        break;
                    case "GIMBAL_UP":
                        if (btnGimbalUp != null) setGimbalHoldVisual(btnGimbalUp, "DOWN".equals(action));
                        break;
                    case "GIMBAL_DOWN":
                        if (btnGimbalDown != null) setGimbalHoldVisual(btnGimbalDown, "DOWN".equals(action));
                        break;
                    case "GIMBAL_RIGHT":
                        if (btnGimbalRight != null) setGimbalHoldVisual(btnGimbalRight, "DOWN".equals(action));
                        break;
                    case "GIMBAL_RECENTER":
                        if (btnGimbalCenter != null) setGimbalHoldVisual(btnGimbalCenter, "DOWN".equals(action) || "CLICK".equals(action));
                        break;
                    case "GIMBAL_MODE":
                        if (extra != null && !extra.trim().isEmpty()) {
                            GimbalBleManager.FollowMode m = GimbalBleManager.FollowMode.fromLabel(extra);
                            updateGimbalModeUi(m);
                        }
                        break;
                    case "AUTO_MODE":
                        if ("MANUAL".equals(action)) {
                            if (autoFollowManager != null) {
                                autoFollowManager.setOperatingMode(AutoFollowManager.OperatingMode.MANUAL);
                            }
                            updateOperatingModeUi(AutoFollowManager.OperatingMode.MANUAL);
                        } else {
                            if (autoFollowManager != null) {
                                autoFollowManager.setOperatingMode(AutoFollowManager.OperatingMode.AUTO);
                            }
                            updateOperatingModeUi(AutoFollowManager.OperatingMode.AUTO);
                        }
                        break;
                    case "AUTO_HOLD":
                        if (autoFollowManager != null) {
                            autoFollowManager.setHoldToRun("DOWN".equals(action));
                        }
                        break;
                }
            });
        });

        if (autoFollowManager != null) {
            autoFollowManager.setOnOperatingModeChanged(mode -> post(() -> updateOperatingModeUi(mode)));
            autoFollowManager.setOnAutoSubModeChanged(subMode -> post(() -> updateAutoSubModeUi(subMode)));
            autoFollowManager.setOnAutoStateChanged((state, msg) -> post(() -> updateAutoStateUi(state, msg)));
            autoFollowManager.setOnTelemetryUpdated((distanceCm, azimuthDeg, isSafe) -> post(() -> {
                if (tvAutoDistance != null) {
                    tvAutoDistance.setText(String.format(Locale.US, "Cự ly: %.1f cm", distanceCm));
                    if (isSafe) {
                        tvAutoDistance.setTextColor(Color.parseColor("#15803D"));
                    } else if (distanceCm < 38f) {
                        tvAutoDistance.setTextColor(Color.parseColor("#DC2626"));
                    } else {
                        tvAutoDistance.setTextColor(Color.parseColor("#D97706"));
                    }
                }
            }));
            autoFollowManager.setOnOrbitProgress(progressDeg -> post(() -> {
                if (tvAutoStatusBadge != null) {
                    tvAutoStatusBadge.setText(String.format(Locale.US, "Quỹ đạo: %.0f° / 360°", progressDeg));
                    tvAutoStatusBadge.setTextColor(Color.parseColor("#1D4ED8"));
                }
            }));
        }
    }

    public void updateOperatingModeUi(AutoFollowManager.OperatingMode mode) {
        if (tabManual == null || tabAuto == null) return;
        boolean isAuto = (mode == AutoFollowManager.OperatingMode.AUTO);

        GradientDrawable manBg = new GradientDrawable();
        manBg.setShape(GradientDrawable.RECTANGLE);
        manBg.setCornerRadius(dpF(14f));
        manBg.setColor(!isAuto ? Color.parseColor("#1D5CE5") : Color.TRANSPARENT);
        tabManual.setBackground(manBg);
        tabManual.setTextColor(!isAuto ? Color.WHITE : Color.parseColor("#64748B"));

        GradientDrawable autoBg = new GradientDrawable();
        autoBg.setShape(GradientDrawable.RECTANGLE);
        autoBg.setCornerRadius(dpF(14f));
        autoBg.setColor(isAuto ? Color.parseColor("#1D5CE5") : Color.TRANSPARENT);
        tabAuto.setBackground(autoBg);
        tabAuto.setTextColor(isAuto ? Color.WHITE : Color.parseColor("#64748B"));

        if (layoutManualContent != null) {
            layoutManualContent.setVisibility(!isAuto ? View.VISIBLE : View.GONE);
        }
        if (layoutAutoContent != null) {
            layoutAutoContent.setVisibility(isAuto ? View.VISIBLE : View.GONE);
        }
    }

    public void updateAutoSubModeUi(AutoFollowManager.AutoSubMode subMode) {
        // Chế độ con đã được loại bỏ: chỉ sử dụng 1 chế độ AUTO duy nhất
    }

    public void updateAutoStateUi(AutoFollowManager.AutoState state, String msg) {
        if (tvAutoStatusBadge == null) return;
        tvAutoStatusBadge.setText(msg);

        int bgCol, textCol;
        if (state == AutoFollowManager.AutoState.TRACKING || state == AutoFollowManager.AutoState.ORBITING) {
            bgCol = Color.parseColor("#DCFCE7");
            textCol = Color.parseColor("#15803D");
        } else if (state == AutoFollowManager.AutoState.LOCKED) {
            bgCol = Color.parseColor("#DBEAFE");
            textCol = Color.parseColor("#1D4ED8");
        } else if (state == AutoFollowManager.AutoState.SAFETY_STOP) {
            bgCol = Color.parseColor("#FEE2E2");
            textCol = Color.parseColor("#DC2626");
        } else if (state == AutoFollowManager.AutoState.LOST) {
            bgCol = Color.parseColor("#FEF3C7");
            textCol = Color.parseColor("#D97706");
        } else {
            bgCol = Color.parseColor("#F1F5F9");
            textCol = Color.parseColor("#475569");
        }

        GradientDrawable stBg = new GradientDrawable();
        stBg.setShape(GradientDrawable.RECTANGLE);
        stBg.setCornerRadius(dpF(12f));
        stBg.setColor(bgCol);
        tvAutoStatusBadge.setBackground(stBg);
        tvAutoStatusBadge.setTextColor(textCol);
    }

    private void updateGimbalModeUi(GimbalBleManager.FollowMode mode) {
        controllerGimbalMode = mode;
        if (btnGimbalMode != null) {
            btnGimbalMode.setText(mode.getLabel());
        }
        boolean isFpv = (mode == GimbalBleManager.FollowMode.FPV);
        if (btnGimbalLeft != null) {
            btnGimbalLeft.setVisibility(isFpv ? View.GONE : View.VISIBLE);
        }
        if (btnGimbalRight != null) {
            btnGimbalRight.setVisibility(isFpv ? View.GONE : View.VISIBLE);
        }
    }

    private void setTurnButtonHeld(View button, boolean isHeld) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpF(20f));
        bg.setColor(isHeld ? Color.parseColor("#1342A8") : Color.parseColor("#1D5CE5"));
        button.setBackground(bg);
        button.setScaleX(isHeld ? 0.94f : 1.0f);
        button.setScaleY(isHeld ? 0.94f : 1.0f);
    }

    private LinearLayout createCardContainer() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpF(16f));
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(1f), Color.parseColor("#E2E8F0"));
        card.setBackground(bg);
        card.setPadding(dp(14f), dp(12f), dp(14f), dp(12f));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return card;
    }

    private TextView createSectionHeader(String title) {
        TextView tv = new TextView(getContext());
        tv.setText(title);
        tv.setTextSize(10.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.parseColor("#1D5CE5"));
        tv.setLetterSpacing(0.03f);
        tv.setPadding(0, 0, 0, dp(8f));
        return tv;
    }

    private EditText createStyledEditText(String defaultText) {
        EditText et = new EditText(getContext());
        et.setText(defaultText);
        et.setTextSize(11.5f);
        et.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        et.setTextColor(Color.parseColor("#0F172A"));
        et.setHintTextColor(Color.parseColor("#94A3B8"));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpF(10f));
        bg.setColor(Color.parseColor("#EEF3FB"));
        bg.setStroke(dp(1f), Color.parseColor("#CBD5E1"));
        et.setBackground(bg);
        et.setPadding(dp(10f), dp(4f), dp(10f), dp(4f));
        et.setGravity(Gravity.CENTER_VERTICAL);
        return et;
    }

    private TextView createTelemetryPill(LinearLayout parent, String label, String value) {
        LinearLayout pill = new LinearLayout(getContext());
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpF(12f));
        bg.setColor(Color.parseColor("#EEF3FB"));
        pill.setBackground(bg);
        pill.setPadding(dp(4f), dp(8f), dp(4f), dp(8f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3f), 0, dp(3f), 0);
        pill.setLayoutParams(lp);

        TextView tvLbl = new TextView(getContext());
        tvLbl.setText(label);
        tvLbl.setTextSize(9.5f);
        tvLbl.setTypeface(Typeface.DEFAULT_BOLD);
        tvLbl.setTextColor(Color.parseColor("#475569"));
        tvLbl.setGravity(Gravity.CENTER);
        pill.addView(tvLbl);

        TextView tvVal = new TextView(getContext());
        tvVal.setText(value);
        tvVal.setTextSize(12f);
        tvVal.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        tvVal.setTextColor(Color.parseColor("#1D5CE5"));
        tvVal.setGravity(Gravity.CENTER);
        tvVal.setPadding(0, dp(2f), 0, 0);
        pill.addView(tvVal);

        parent.addView(pill);
        return tvVal;
    }

    private EditText createLabeledInput(LinearLayout parent, String label, String defaultVal) {
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3f), 0, dp(3f), 0);
        container.setLayoutParams(lp);

        TextView lbl = new TextView(getContext());
        lbl.setText(label);
        lbl.setTextSize(9.5f);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setTextColor(Color.parseColor("#475569"));
        lbl.setPadding(0, 0, 0, dp(3f));
        container.addView(lbl);

        EditText et = createStyledEditText(defaultVal);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        et.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34f)));
        container.addView(et);

        parent.addView(container);
        return et;
    }

    private TextView createOutlineActionButton(String text, Runnable onClick) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.parseColor("#0F172A"));
        tv.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpF(16f));
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(1.2f), Color.parseColor("#CBD5E1"));
        tv.setBackground(bg);
        tv.setPadding(dp(16f), dp(6f), dp(16f), dp(6f));
        tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    private TextView createSmallActionButton(String text, int color, Runnable onClick) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpF(16f));
        bg.setColor(color);
        tv.setBackground(bg);
        tv.setPadding(dp(16f), dp(6f), dp(16f), dp(6f));
        tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    private View createHorizontalSpacer(int w) {
        View v = new View(getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(w, 1));
        return v;
    }

    private View createVerticalSpacer(int h) {
        View v = new View(getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(1, h));
        return v;
    }

    // =========================================================================
    // MECANUM ANALOG JOYSTICK VIEW (360° OMNIDIRECTIONAL VIRTUAL JOYSTICK)
    // =========================================================================
    @SuppressLint("ClickableViewAccessibility")
    public static class MecanumJoystickView extends View {

        public interface OnJoystickMovedListener {
            void onJoystickMoved(float nx, float ny, boolean active);
        }

        private float dpF(float v) {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()
            );
        }

        private OnJoystickMovedListener onJoystickMoved = null;

        public void setOnJoystickMoved(OnJoystickMovedListener listener) {
            this.onJoystickMoved = listener;
        }

        private float knobOffsetX = 0f;
        private float knobOffsetY = 0f;
        private boolean isDragging = false;
        private int activePointerId = MotionEvent.INVALID_POINTER_ID;
        private ValueAnimator returnAnimator = null;

        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint baseBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chevronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knobShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public MecanumJoystickView(Context context) {
            super(context);
            basePaint.setColor(Color.parseColor("#F5FFFFFF"));
            basePaint.setStyle(Paint.Style.FILL);

            baseBorderPaint.setColor(Color.parseColor("#D8E6FA"));
            baseBorderPaint.setStyle(Paint.Style.STROKE);
            baseBorderPaint.setStrokeWidth(dpF(1.5f));

            chevronPaint.setColor(Color.parseColor("#1D5CE5"));
            chevronPaint.setStyle(Paint.Style.STROKE);
            chevronPaint.setStrokeWidth(dpF(2.5f));
            chevronPaint.setStrokeCap(Paint.Cap.ROUND);
            chevronPaint.setStrokeJoin(Paint.Join.ROUND);

            knobShadowPaint.setColor(Color.parseColor("#331D5CE5"));
            knobShadowPaint.setStyle(Paint.Style.FILL);

            knobPaint.setColor(Color.parseColor("#1D5CE5"));
            knobPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float outerRadius = (Math.min(getWidth(), getHeight()) / 2f) - dpF(3f);
            if (outerRadius <= 0f) return;

            // 1. Vẽ đĩa tròn nền trắng
            canvas.drawCircle(cx, cy, outerRadius, basePaint);
            canvas.drawCircle(cx, cy, outerRadius, baseBorderPaint);

            // 2. Vẽ 4 mũi tên Chevron (^, v, <, >) ở 4 hướng
            float chevronDist = outerRadius * 0.74f;
            float chevSize = dpF(5.5f);

            // Top (^)
            Path upPath = new Path();
            float uy = cy - chevronDist;
            upPath.moveTo(cx - chevSize, uy + chevSize * 0.45f);
            upPath.lineTo(cx, uy - chevSize * 0.45f);
            upPath.lineTo(cx + chevSize, uy + chevSize * 0.45f);
            chevronPaint.setAlpha(knobOffsetY < -outerRadius * 0.15f ? 255 : 210);
            canvas.drawPath(upPath, chevronPaint);

            // Bottom (v)
            Path downPath = new Path();
            float dy = cy + chevronDist;
            downPath.moveTo(cx - chevSize, dy - chevSize * 0.45f);
            downPath.lineTo(cx, dy + chevSize * 0.45f);
            downPath.lineTo(cx + chevSize, dy - chevSize * 0.45f);
            chevronPaint.setAlpha(knobOffsetY > outerRadius * 0.15f ? 255 : 210);
            canvas.drawPath(downPath, chevronPaint);

            // Left (<)
            Path leftPath = new Path();
            float lx = cx - chevronDist;
            leftPath.moveTo(lx + chevSize * 0.45f, cy - chevSize);
            leftPath.lineTo(lx - chevSize * 0.45f, cy);
            leftPath.lineTo(lx + chevSize * 0.45f, cy + chevSize);
            chevronPaint.setAlpha(knobOffsetX < -outerRadius * 0.15f ? 255 : 210);
            canvas.drawPath(leftPath, chevronPaint);

            // Right (>)
            Path rightPath = new Path();
            float rx = cx + chevronDist;
            rightPath.moveTo(rx - chevSize * 0.45f, cy - chevSize);
            rightPath.lineTo(rx + chevSize * 0.45f, cy);
            rightPath.lineTo(rx - chevSize * 0.45f, cy + chevSize);
            chevronPaint.setAlpha(knobOffsetX > outerRadius * 0.15f ? 255 : 210);
            canvas.drawPath(rightPath, chevronPaint);

            // 3. Vẽ núm điều khiển tròn màu xanh ở tâm (hoặc lệch theo ngón tay)
            float knobRadius = outerRadius * 0.39f;
            float kx = cx + knobOffsetX;
            float ky = cy + knobOffsetY;
            canvas.drawCircle(kx, ky + dpF(2f), knobRadius + dpF(1.5f), knobShadowPaint);
            canvas.drawCircle(kx, ky, knobRadius, knobPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float outerRadius = (Math.min(getWidth(), getHeight()) / 2f) - dpF(3f);
            float maxTravel = Math.max(1f, outerRadius * 0.46f);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    if (returnAnimator != null) returnAnimator.cancel();
                    isDragging = true;
                    activePointerId = event.getPointerId(0);
                    updateKnobFromTouch(event.getX(), event.getY(), cx, cy, maxTravel);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!isDragging) return false;
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    int idx = pointerIndex >= 0 ? pointerIndex : 0;
                    updateKnobFromTouch(event.getX(idx), event.getY(idx), cx, cy, maxTravel);
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    int upPointerIndex = event.getActionIndex();
                    int upPointerId = event.getPointerId(upPointerIndex);
                    if (upPointerId == activePointerId) {
                        int newPointerIndex = (upPointerIndex == 0) ? 1 : 0;
                        if (newPointerIndex < event.getPointerCount()) {
                            activePointerId = event.getPointerId(newPointerIndex);
                            updateKnobFromTouch(
                                    event.getX(newPointerIndex),
                                    event.getY(newPointerIndex),
                                    cx, cy, maxTravel
                            );
                        } else {
                            releaseJoystick();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    releaseJoystick();
                    return true;
            }
            return super.onTouchEvent(event);
        }

        private void updateKnobFromTouch(float tx, float ty, float cx, float cy, float maxTravel) {
            float dx = tx - cx;
            float dy = ty - cy;
            float dist = (float) Math.hypot(dx, dy);

            if (dist > maxTravel) {
                double angle = Math.atan2(dy, dx);
                knobOffsetX = (float) (Math.cos(angle) * maxTravel);
                knobOffsetY = (float) (Math.sin(angle) * maxTravel);
            } else {
                knobOffsetX = dx;
                knobOffsetY = dy;
            }
            invalidate();

            float nx = Math.max(-1f, Math.min(1f, knobOffsetX / maxTravel));
            float ny = Math.max(-1f, Math.min(1f, -knobOffsetY / maxTravel));
            dispatchJoystickMove(nx, ny, true);
        }

        private void releaseJoystick() {
            isDragging = false;
            activePointerId = MotionEvent.INVALID_POINTER_ID;
            dispatchJoystickMove(0f, 0f, false);
            animateReturnToCenter();
        }

        private void dispatchJoystickMove(float nx, float ny, boolean active) {
            if (onJoystickMoved != null) {
                onJoystickMoved.onJoystickMoved(nx, ny, active);
            }
        }

        public void setExternalKnob(float nx, float ny) {
            if (isDragging) return;
            float outerRadius = (Math.min(getWidth(), getHeight()) / 2f) - dpF(3f);
            float maxTravel = Math.max(1f, outerRadius * 0.46f);
            knobOffsetX = Math.max(-1f, Math.min(1f, nx)) * maxTravel;
            knobOffsetY = -Math.max(-1f, Math.min(1f, ny)) * maxTravel;
            invalidate();
        }

        private void animateReturnToCenter() {
            final float startX = knobOffsetX;
            final float startY = knobOffsetY;
            if (returnAnimator != null) returnAnimator.cancel();
            returnAnimator = ValueAnimator.ofFloat(1f, 0f);
            returnAnimator.setDuration(140);
            returnAnimator.setInterpolator(new OvershootInterpolator(1.4f));
            returnAnimator.addUpdateListener(anim -> {
                float f = (Float) anim.getAnimatedValue();
                knobOffsetX = startX * f;
                knobOffsetY = startY * f;
                invalidate();
            });
            returnAnimator.start();
        }
    }
}
