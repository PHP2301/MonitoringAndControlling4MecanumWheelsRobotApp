package com.robotcamera.app;

import android.os.Handler;
import android.os.Looper;

/**
 * Trình điều phối Chế độ Tự hành (Auto Follow & Orbit Manager)
 * - Quản lý 2 chế độ chính:
 *   1. MANUAL (Thủ công): Joystick + Nút Xoay Drift
 *   2. AUTO (Tự động):
 *      + Trường hợp 1: Following (Bám theo người giữ cự ly 50 cm ±10 cm)
 *      + Trường hợp 2: Orbit 360° (Vừa xoay 360 độ quanh người vừa giữ cự ly 50 cm ±10 cm)
 */
public class AutoFollowManager {

    private static final String TAG = "AutoFollowManager";

    public static final float TARGET_DISTANCE_M = 0.50f;
    public static final float DEADBAND_MIN_M = 0.42f;
    public static final float DEADBAND_MAX_M = 0.58f;
    public static final float HARD_SAFETY_STOP_M = 0.38f;

    public static final float MAX_AUTO_SPEED = 7.0f;
    public static final float GENTLE_FORWARD_SPEED = 5.0f;
    public static final float GENTLE_REVERSE_SPEED = 4.0f;
    public static final float ORBIT_TANGENTIAL_SPEED = 5.5f;

    public static final float KP_DISTANCE = 8.0f;
    public static final float KP_HEADING = 4.5f;
    public static final float MAX_HEADING_SPEED = 5.0f;

    public static final long TARGET_TIMEOUT_MS = 600L;

    public enum OperatingMode {
        MANUAL,
        AUTO
    }

    public enum AutoSubMode {
        FOLLOW_50CM,
        ORBIT_360
    }

    public enum AutoState {
        IDLE,
        SEARCHING,
        LOCKED,
        TRACKING,
        ORBITING,
        SAFETY_STOP,
        LOST
    }

    public interface OnOperatingModeChangedListener {
        void onOperatingModeChanged(OperatingMode mode);
    }

    public interface OnAutoSubModeChangedListener {
        void onAutoSubModeChanged(AutoSubMode subMode);
    }

    public interface OnAutoStateChangedListener {
        void onAutoStateChanged(AutoState state, String message);
    }

    public interface OnTelemetryUpdatedListener {
        void onTelemetryUpdated(float distanceCm, float azimuthDeg, boolean isSafe);
    }

    public interface OnOrbitProgressListener {
        void onOrbitProgress(float progressDeg);
    }

    private OnOperatingModeChangedListener mOnOperatingModeChanged;
    private OnAutoSubModeChangedListener mOnAutoSubModeChanged;
    private OnAutoStateChangedListener mOnAutoStateChanged;
    private OnTelemetryUpdatedListener mOnTelemetryUpdated;
    private OnOrbitProgressListener mOnOrbitProgress;

    public OnOperatingModeChangedListener getOnOperatingModeChanged() {
        return mOnOperatingModeChanged;
    }

    public void setOnOperatingModeChanged(OnOperatingModeChangedListener l) {
        this.mOnOperatingModeChanged = l;
    }

    public OnAutoSubModeChangedListener getOnAutoSubModeChanged() {
        return mOnAutoSubModeChanged;
    }

    public void setOnAutoSubModeChanged(OnAutoSubModeChangedListener l) {
        this.mOnAutoSubModeChanged = l;
    }

    public OnAutoStateChangedListener getOnAutoStateChanged() {
        return mOnAutoStateChanged;
    }

    public void setOnAutoStateChanged(OnAutoStateChangedListener l) {
        this.mOnAutoStateChanged = l;
    }

    public OnTelemetryUpdatedListener getOnTelemetryUpdated() {
        return mOnTelemetryUpdated;
    }

    public void setOnTelemetryUpdated(OnTelemetryUpdatedListener l) {
        this.mOnTelemetryUpdated = l;
    }

    public OnOrbitProgressListener getOnOrbitProgress() {
        return mOnOrbitProgress;
    }

    public void setOnOrbitProgress(OnOrbitProgressListener l) {
        this.mOnOrbitProgress = l;
    }

    private final RobotUdpManager udpManager;

    private volatile OperatingMode currentOperatingMode = OperatingMode.MANUAL;
    private volatile AutoSubMode currentSubMode = AutoSubMode.FOLLOW_50CM;
    private volatile AutoState currentAutoState = AutoState.IDLE;
    private volatile boolean isHoldToRunActive = false;

    public OperatingMode getCurrentOperatingMode() {
        return currentOperatingMode;
    }

    public AutoSubMode getCurrentSubMode() {
        return currentSubMode;
    }

    public AutoState getCurrentAutoState() {
        return currentAutoState;
    }

    public boolean isHoldToRunActive() {
        return isHoldToRunActive;
    }

    private volatile VisualDistanceDetector.TargetResult lastTarget = null;
    private volatile long lastTargetTimeMs = 0L;

    private volatile float accumulatedOrbitRad = 0.0f;
    private long lastLoopTimeMs = 0L;

    public int orbitDirection = 1;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AutoFollowManager(RobotUdpManager udpManager) {
        this.udpManager = udpManager;
    }

    public void setOperatingMode(OperatingMode mode) {
        if (currentOperatingMode == mode) return;
        currentOperatingMode = mode;
        if (mode == OperatingMode.MANUAL) {
            isHoldToRunActive = false;
            accumulatedOrbitRad = 0f;
            updateState(AutoState.IDLE, "Đã chuyển sang Thủ công");
            udpManager.stopMotors();
        } else {
            updateState(AutoState.SEARCHING, "Sẵn sàng tự hành - Giữ nút để chạy");
        }
        mainHandler.post(() -> {
            if (mOnOperatingModeChanged != null) {
                mOnOperatingModeChanged.onOperatingModeChanged(mode);
            }
        });
    }

    public void setAutoSubMode(AutoSubMode subMode) {
        currentSubMode = subMode;
        accumulatedOrbitRad = 0f;
        mainHandler.post(() -> {
            if (mOnAutoSubModeChanged != null) {
                mOnAutoSubModeChanged.onAutoSubModeChanged(subMode);
            }
        });
        String msg = (subMode == AutoSubMode.FOLLOW_50CM) ? "Chế độ: Bám theo 50cm" : "Chế độ: Xoay 360° quanh người";
        updateState(currentAutoState, msg);
    }

    public void setHoldToRun(boolean pressed) {
        isHoldToRunActive = pressed;
        if (!pressed) {
            udpManager.stopMotors();
            accumulatedOrbitRad = 0f;
            updateState(AutoState.IDLE, "Đã nhả nút an toàn (Dừng)");
        } else {
            if (currentOperatingMode == OperatingMode.AUTO) {
                lastLoopTimeMs = System.currentTimeMillis();
                updateState(AutoState.SEARCHING, "Đang giữ nút - Đang tìm mục tiêu...");
            }
        }
    }

    public void onTargetDetected(VisualDistanceDetector.TargetResult result) {
        lastTarget = result;
        lastTargetTimeMs = System.currentTimeMillis();

        float distanceCm = result.getDistanceCm();
        float azimuthDeg = result.azimuthNorm * 35.0f;
        boolean isSafe = result.isSafeZone();

        mainHandler.post(() -> {
            if (mOnTelemetryUpdated != null) {
                mOnTelemetryUpdated.onTelemetryUpdated(distanceCm, azimuthDeg, isSafe);
            }
        });

        if (currentOperatingMode == OperatingMode.AUTO) {
            executeAutoControlLoop(result);
        }
    }

    public void onTargetLost() {
        long now = System.currentTimeMillis();
        if (now - lastTargetTimeMs > TARGET_TIMEOUT_MS) {
            lastTarget = null;
            if (currentOperatingMode == OperatingMode.AUTO && isHoldToRunActive) {
                udpManager.stopMotors();
                updateState(AutoState.LOST, "Mất dấu mục tiêu (Đã dừng an toàn)");
            }
        }
    }

    private void executeAutoControlLoop(VisualDistanceDetector.TargetResult target) {
        long now = System.currentTimeMillis();
        float dt = (lastLoopTimeMs > 0) ? Math.max(0.01f, Math.min(0.1f, (now - lastLoopTimeMs) / 1000.0f)) : 0.033f;
        lastLoopTimeMs = now;

        if (!isHoldToRunActive) {
            updateState(AutoState.LOCKED, "Đã thấy mục tiêu (" + String.format("%.1f", target.getDistanceCm()) + "cm) - Nhấn giữ để chạy");
            return;
        }

        float distance = target.filteredDistanceM;

        if (distance < HARD_SAFETY_STOP_M) {
            udpManager.stopMotors();
            updateState(AutoState.SAFETY_STOP, "CẢNH BÁO: Quá gần (<38cm)! Cắt an toàn!");
            return;
        }

        float azimuth = target.azimuthNorm;
        float headingDeadband = 0.08f;
        float omegaTurn = (Math.abs(azimuth) > headingDeadband)
            ? Math.max(-MAX_HEADING_SPEED, Math.min(MAX_HEADING_SPEED, -azimuth * KP_HEADING))
            : 0.0f;

        switch (currentSubMode) {
            case FOLLOW_50CM: {
                float distError = distance - TARGET_DISTANCE_M;
                float vxForward;
                if (distance >= DEADBAND_MIN_M && distance <= DEADBAND_MAX_M) {
                    vxForward = 0.0f;
                } else if (distError > 0) {
                    vxForward = Math.max(1.5f, Math.min(GENTLE_FORWARD_SPEED, distError * KP_DISTANCE));
                } else {
                    vxForward = Math.max(-GENTLE_REVERSE_SPEED, Math.min(-1.5f, distError * KP_DISTANCE));
                }

                applyMecanumSpeeds(vxForward, 0.0f, omegaTurn);

                String stateMsg;
                if (distance >= DEADBAND_MIN_M && distance <= DEADBAND_MAX_M) {
                    stateMsg = "Đang giữ đúng cự ly 50cm (" + String.format("%.1f", target.getDistanceCm()) + "cm)";
                } else if (distError > 0) {
                    stateMsg = "Đang tiến bám theo (" + String.format("%.1f", target.getDistanceCm()) + "cm)";
                } else {
                    stateMsg = "Đang lùi giữ khoảng cách (" + String.format("%.1f", target.getDistanceCm()) + "cm)";
                }
                updateState(AutoState.TRACKING, stateMsg);
                break;
            }
            case ORBIT_360: {
                float distError = distance - TARGET_DISTANCE_M;
                float vxRadial;
                if (distance >= DEADBAND_MIN_M && distance <= DEADBAND_MAX_M) {
                    vxRadial = 0.0f;
                } else if (distError > 0) {
                    vxRadial = Math.max(1.0f, Math.min(3.5f, distError * KP_DISTANCE));
                } else {
                    vxRadial = Math.max(-3.5f, Math.min(-1.0f, distError * KP_DISTANCE));
                }

                float vyTangential = ORBIT_TANGENTIAL_SPEED * orbitDirection;
                float baseOrbitTurn = (ORBIT_TANGENTIAL_SPEED / TARGET_DISTANCE_M) * 0.15f * orbitDirection;
                float totalOmega = Math.max(-MAX_AUTO_SPEED, Math.min(MAX_AUTO_SPEED, baseOrbitTurn + omegaTurn));

                applyMecanumSpeeds(vxRadial, vyTangential, totalOmega);

                float deltaRad = Math.abs(totalOmega * dt * 0.1f);
                accumulatedOrbitRad += deltaRad;
                float progressDeg = (float) Math.min(360.0, accumulatedOrbitRad * 180.0 / Math.PI);

                mainHandler.post(() -> {
                    if (mOnOrbitProgress != null) {
                        mOnOrbitProgress.onOrbitProgress(progressDeg);
                    }
                });

                String orbitMsg = "Đang xoay quanh: " + ((int) progressDeg) + "°/360° · Cự ly " + String.format("%.1f", target.getDistanceCm()) + "cm";
                updateState(AutoState.ORBITING, orbitMsg);
                break;
            }
        }
    }

    private void applyMecanumSpeeds(float vx, float vy, float omega) {
        float w1 = vx - vy + omega;
        float w2 = vx + vy + omega;
        float w3 = vx + vy - omega;
        float w4 = vx - vy - omega;

        float peak = Math.max(MAX_AUTO_SPEED, Math.max(Math.abs(w1), Math.max(Math.abs(w2), Math.max(Math.abs(w3), Math.abs(w4)))));
        float scale = (peak > MAX_AUTO_SPEED) ? (MAX_AUTO_SPEED / peak) : 1.0f;

        float finalW1 = w1 * scale;
        float finalW2 = w2 * scale;
        float finalW3 = w3 * scale;
        float finalW4 = w4 * scale;

        udpManager.stateRun = 0x03;
        udpManager.setW1 = finalW1;
        udpManager.setW2 = finalW2;
        udpManager.setW3 = finalW3;
        udpManager.setW4 = finalW4;
    }

    public void emergencyStop() {
        isHoldToRunActive = false;
        accumulatedOrbitRad = 0f;
        udpManager.emergencyStop();
        updateState(AutoState.SAFETY_STOP, "DỪNG KHẨN CẤP (EMERGENCY STOP)");
    }

    private void updateState(AutoState state, String message) {
        currentAutoState = state;
        mainHandler.post(() -> {
            if (mOnAutoStateChanged != null) {
                mOnAutoStateChanged.onAutoStateChanged(state, message);
            }
        });
    }
}
