package com.robotcamera.app;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Toast;

/**
 * Xử lý sự kiện tay cầm (Gamepad / Game Controller / Xbox 360) trên Android.
 * - Hỗ trợ cả kết nối có dây USB-OTG và không dây Bluetooth.
 * - Điều khiển Robot Mecanum qua cần Analog Trái & Phải.
 * - Điều khiển Gimbal RS 3 Mini qua D-Pad và Right Thumb click.
 * - Chụp ảnh, quay video, đổi cam qua các nút vai và cò trigger.
 */
public class MainActivityGamepad {

    private static final float DEADZONE = 0.18f;
    private static boolean wasDriving = false;

    public static boolean handleGenericMotionEvent(MainActivity activity, MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
            (event.getSource() & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) {
            return false;
        }

        if (event.getAction() != MotionEvent.ACTION_MOVE) return false;

        // Trục cần Analog Trái: X (trượt ngang), Y (tiến/lùi)
        float lx = event.getAxisValue(MotionEvent.AXIS_X);
        float ly = -event.getAxisValue(MotionEvent.AXIS_Y); // Đảo trục Y (hướng lên là tiến dương)

        // Trục cần Analog Phải: Z hoặc RX (xoay trái/phải)
        float rx = event.getAxisValue(MotionEvent.AXIS_Z);
        if (Math.abs(rx) < DEADZONE) {
            rx = event.getAxisValue(MotionEvent.AXIS_RX);
        }

        // Khử deadzone
        if (Math.abs(lx) < DEADZONE) lx = 0f;
        if (Math.abs(ly) < DEADZONE) ly = 0f;
        if (Math.abs(rx) < DEADZONE) rx = 0f;

        boolean isMoving = (lx != 0f || ly != 0f || rx != 0f);

        // Hiển thị Realtime chuyển động cần gạt lên núm Joystick ảo và các nút xoay trên màn hình
        if (activity != null && activity.controlDashboardView != null) {
            if (activity.controlDashboardView.getJoystickView() != null) {
                activity.controlDashboardView.getJoystickView().setExternalKnob(lx, ly);
            }
            if (activity.controlDashboardView.getBtnTurnL() != null) {
                activity.controlDashboardView.getBtnTurnL().setPressed(rx < -DEADZONE);
            }
            if (activity.controlDashboardView.getBtnTurnR() != null) {
                activity.controlDashboardView.getBtnTurnR().setPressed(rx > DEADZONE);
            }
        }

        if (activity != null && activity.udpManager != null) {
            if (isMoving) {
                wasDriving = true;
                activity.udpManager.driveGamepad(lx, ly, rx);
            } else if (wasDriving) {
                wasDriving = false;
                activity.udpManager.stopMotors();
                if (activity.controlDashboardView != null) {
                    if (activity.controlDashboardView.getJoystickView() != null) {
                        activity.controlDashboardView.getJoystickView().setExternalKnob(0f, 0f);
                    }
                    if (activity.controlDashboardView.getBtnTurnL() != null) {
                        activity.controlDashboardView.getBtnTurnL().setPressed(false);
                    }
                    if (activity.controlDashboardView.getBtnTurnR() != null) {
                        activity.controlDashboardView.getBtnTurnR().setPressed(false);
                    }
                }
            }
        }

        // Đọc cò Trigger LT / RT
        float lt = event.getAxisValue(MotionEvent.AXIS_BRAKE);
        if (lt <= 0.1f) lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
        float rt = event.getAxisValue(MotionEvent.AXIS_GAS);
        if (rt <= 0.1f) rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);

        if (lt > 0.7f && activity != null) {
            activity.onTakePhoto();
        }
        if (rt > 0.7f && activity != null) {
            activity.switchCamera();
        }

        // D-Pad dạng HAT Axis
        float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (activity != null) {
            if (hatY < -0.5f) {
                GimbalBleManager.sendRemoteGimbalCommand(activity, "GIMBAL_UP", "DOWN", null, "gimbal/move?pan=0&tilt=1");
            } else if (hatY > 0.5f) {
                GimbalBleManager.sendRemoteGimbalCommand(activity, "GIMBAL_DOWN", "DOWN", null, "gimbal/move?pan=0&tilt=-1");
            } else if (hatX < -0.5f) {
                GimbalBleManager.sendRemoteGimbalCommand(activity, "GIMBAL_LEFT", "DOWN", null, "gimbal/move?pan=-1&tilt=0");
            } else if (hatX > 0.5f) {
                GimbalBleManager.sendRemoteGimbalCommand(activity, "GIMBAL_RIGHT", "DOWN", null, "gimbal/move?pan=1&tilt=0");
            }
        }

        return true;
    }

    public static boolean handleKeyEvent(MainActivity activity, KeyEvent event) {
        if (activity == null) return false;

        int action = event.getAction();
        boolean isDown = (action == KeyEvent.ACTION_DOWN);
        int keyCode = event.getKeyCode();

        switch (keyCode) {
            // Nút A -> Về gốc tọa độ (Return Home)
            case KeyEvent.KEYCODE_BUTTON_A:
                if (isDown && activity.udpManager != null) {
                    activity.udpManager.returnHome();
                    Toast.makeText(activity, "🎮 Tay cầm: Return Home", Toast.LENGTH_SHORT).show();
                }
                return true;

            // Nút B -> Dừng mềm & Reset xe
            case KeyEvent.KEYCODE_BUTTON_B:
                if (isDown && activity.udpManager != null) {
                    activity.udpManager.resetRobotValues();
                    activity.udpManager.stopMotors();
                    Toast.makeText(activity, "🎮 Tay cầm: Stop / Reset", Toast.LENGTH_SHORT).show();
                }
                return true;

            // Nút X -> Dừng khẩn cấp (Emergency Stop)
            case KeyEvent.KEYCODE_BUTTON_X:
                if (isDown && activity.udpManager != null) {
                    activity.udpManager.emergencyStop();
                    Toast.makeText(activity, "🎮 Tay cầm: DỪNG KHẨN CẤP!", Toast.LENGTH_SHORT).show();
                }
                return true;

            // Nút Y -> Quỹ đạo tròn (Circle Trajectory)
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (isDown && activity.udpManager != null) {
                    activity.udpManager.circleLine();
                    Toast.makeText(activity, "🎮 Tay cầm: Chạy quỹ đạo tròn", Toast.LENGTH_SHORT).show();
                }
                return true;

            // LB (Left Bumper) -> Bắt đầu quay video
            case KeyEvent.KEYCODE_BUTTON_L1:
                if (isDown) {
                    activity.onStartRecord();
                }
                return true;

            // RB (Right Bumper) -> Dừng quay video
            case KeyEvent.KEYCODE_BUTTON_R1:
                if (isDown) {
                    activity.onStopRecord();
                }
                return true;

            // LT (Left Trigger dạng nút) -> Chụp ảnh
            case KeyEvent.KEYCODE_BUTTON_L2:
                if (isDown) {
                    activity.onTakePhoto();
                }
                return true;

            // RT (Right Trigger dạng nút) -> Đổi Camera
            case KeyEvent.KEYCODE_BUTTON_R2:
                if (isDown) {
                    activity.switchCamera();
                }
                return true;

            // D-Pad -> Gimbal RS 3 Mini Pan / Tilt
            case KeyEvent.KEYCODE_DPAD_UP:
                GimbalBleManager.sendRemoteGimbalCommand(activity, "GIMBAL_UP", isDown ? "DOWN" : "UP", null, "gimbal/move?pan=0&tilt=" + (isDown ? 1 : 0));
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                GimbalBleManager.sendRemoteGimbalCommand(activity, "GIMBAL_DOWN", isDown ? "DOWN" : "UP", null, "gimbal/move?pan=0&tilt=" + (isDown ? -1 : 0));
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                GimbalBleManager.sendRemoteGimbalCommand(activity, "GIMBAL_LEFT", isDown ? "DOWN" : "UP", null, "gimbal/move?pan=" + (isDown ? -1 : 0) + "&tilt=0");
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                GimbalBleManager.sendRemoteGimbalCommand(activity, "GIMBAL_RIGHT", isDown ? "DOWN" : "UP", null, "gimbal/move?pan=" + (isDown ? 1 : 0) + "&tilt=0");
                return true;

            // Right Stick Click -> Gimbal Recenter
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                if (isDown) {
                    GimbalBleManager.sendRemoteGimbalCommand(activity, "GIMBAL_RECENTER", "CLICK", null, "gimbal/recenter");
                    Toast.makeText(activity, "🎮 Gimbal: Căn giữa (Recenter)", Toast.LENGTH_SHORT).show();
                }
                return true;

            // Left Stick Click -> Đổi tốc độ (10 <-> 20 rad/s)
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                if (isDown && activity.udpManager != null) {
                    float spd = (activity.udpManager.manualSpeed >= 15f) ? 10f : 20f;
                    activity.udpManager.manualSpeed = spd;
                    Toast.makeText(activity, "🎮 Tốc độ: " + spd + " rad/s", Toast.LENGTH_SHORT).show();
                }
                return true;
        }

        return false;
    }
}
