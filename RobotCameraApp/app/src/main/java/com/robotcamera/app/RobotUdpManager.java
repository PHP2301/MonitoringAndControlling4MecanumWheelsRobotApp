package com.robotcamera.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Quản lý giao tiếp mạng UDP với Robot Mecanum
 * Giữ nguyên 100% cấu trúc gói tin, thông số và logic backend từ Form1.cs (C# PC Monitor)
 */
public class RobotUdpManager {

    private static final String TAG = "RobotUdpManager";
    public static final String DEFAULT_IP = "192.168.2.93";
    public static final int DEFAULT_PORT = 5001;
    public static final long SEND_INTERVAL_MS = 25L; // Chu kỳ gửi 25ms (40Hz) chuẩn backend

    public static class RobotTelemetry {
        public final int seq;
        public final double timeSec;
        public final float xd, x;
        public final float yd, y;
        public final float thetad, theta; // đơn vị radian
        public final float vxd, vx;
        public final float vyd, vy;
        public final float wd, w;
        public final float w1d, w1; // RL (Rear Left)
        public final float w2d, w2; // FL (Front Left)
        public final float w3d, w3; // RR (Rear Right)
        public final float w4d, w4; // FR (Front Right)
        public final float thetaDeg;
        public final float thetadDeg;

        public RobotTelemetry(int seq, double timeSec,
                              float xd, float x,
                              float yd, float y,
                              float thetad, float theta,
                              float vxd, float vx,
                              float vyd, float vy,
                              float wd, float w,
                              float w1d, float w1,
                              float w2d, float w2,
                              float w3d, float w3,
                              float w4d, float w4) {
            this.seq = seq;
            this.timeSec = timeSec;
            this.xd = xd; this.x = x;
            this.yd = yd; this.y = y;
            this.thetad = thetad; this.theta = theta;
            this.vxd = vxd; this.vx = vx;
            this.vyd = vyd; this.vy = vy;
            this.wd = wd; this.w = w;
            this.w1d = w1d; this.w1 = w1;
            this.w2d = w2d; this.w2 = w2;
            this.w3d = w3d; this.w3 = w3;
            this.w4d = w4d; this.w4 = w4;
            this.thetaDeg = (float) (theta * 180.0 / Math.PI);
            this.thetadDeg = (float) (thetad * 180.0 / Math.PI);
        }

        public float getThetaDeg() {
            return (float) (theta * 180.0 / Math.PI);
        }

        public float getThetadDeg() {
            return (float) (thetad * 180.0 / Math.PI);
        }
    }

    public enum ControlPermission {
        AUTO,   // Tự động: Ai nhấn nút người đó lái, nhả nút tự ngắt gửi 0 để nhường máy khác
        MASTER, // Độc quyền: Giữ quyền lái chính, khóa các thiết bị khác sang Chỉ xem
        VIEWER  // Chỉ xem: Chỉ nhận Telemetry hiển thị, khóa chiều gửi lệnh điều khiển
    }

    public interface OnTelemetryReceivedListener {
        void onTelemetryReceived(RobotTelemetry telemetry);
    }

    public interface OnConnectionStateChangedListener {
        void onConnectionStateChanged(boolean isConnected, String status);
    }

    public interface OnPermissionChangedListener {
        void onPermissionChanged(ControlPermission permission, String reason);
    }

    private OnTelemetryReceivedListener onTelemetryReceived;
    private OnConnectionStateChangedListener onConnectionStateChanged;
    private OnPermissionChangedListener onPermissionChanged;

    public void setOnTelemetryReceived(OnTelemetryReceivedListener listener) {
        this.onTelemetryReceived = listener;
    }

    public OnTelemetryReceivedListener getOnTelemetryReceived() {
        return onTelemetryReceived;
    }

    public void setOnConnectionStateChanged(OnConnectionStateChangedListener listener) {
        this.onConnectionStateChanged = listener;
    }

    public OnConnectionStateChangedListener getOnConnectionStateChanged() {
        return onConnectionStateChanged;
    }

    public void setOnPermissionChanged(OnPermissionChangedListener listener) {
        this.onPermissionChanged = listener;
    }

    public OnPermissionChangedListener getOnPermissionChanged() {
        return onPermissionChanged;
    }

    public volatile ControlPermission permissionMode = ControlPermission.AUTO;

    public ControlPermission getPermissionMode() {
        return permissionMode;
    }

    private volatile boolean isActivelyDriving = false;

    public boolean isActivelyDriving() {
        return isActivelyDriving;
    }

    private volatile int burstPacketsRemaining = 0;

    private DatagramSocket socket;
    private InetAddress targetAddress;
    private int targetPort = DEFAULT_PORT;

    public volatile boolean isConnected = false;

    public boolean isConnected() {
        return isConnected;
    }

    private ScheduledExecutorService sendExecutor;
    private Thread receiveThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int txSeqCount = 0;
    private int expectedSeq = 0;
    private boolean isFirstPacket = true;
    private long startTimeMillis = 0L;

    public volatile byte stateRun = 0x01; // Mặc định 0x01: Stop

    public volatile float setX = 0.0f;
    public volatile float setY = 0.0f;
    public volatile float setTheta = 0.0f;
    public volatile float setVx = 0.0f;
    public volatile float setVy = 0.0f;
    public volatile float setW = 0.0f;
    public volatile float setW1 = 0.0f;
    public volatile float setW2 = 0.0f;
    public volatile float setW3 = 0.0f;
    public volatile float setW4 = 0.0f;

    public float manualSpeed = 10.0f;

    private volatile float activeJoyX = 0.0f;
    private volatile float activeJoyY = 0.0f;
    private volatile boolean isTurnLeftActive = false;
    private volatile boolean isTurnRightActive = false;

    public void connect(String ip, int port) {
        if (isConnected) disconnect();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                targetAddress = InetAddress.getByName(ip.trim());
                targetPort = port;

                socket = new DatagramSocket();
                isConnected = true;
                txSeqCount = 0;
                expectedSeq = 0;
                isFirstPacket = true;
                startTimeMillis = System.currentTimeMillis();

                notifyState(true, "Đang gửi Wake-up...");

                byte[] wakeUp = new byte[]{(byte) 0xAA, (byte) 0xFF, 0x00, 0x00, 0x55};
                sendRawPacket(wakeUp);

                notifyState(true, "Đã kết nối UDP tới " + ip + ":" + port);

                startReceiveLoop();
                startSendLoop();

            } catch (Exception e) {
                Log.e(TAG, "Lỗi kết nối UDP: " + e.getMessage());
                disconnect();
                notifyState(false, "Lỗi kết nối: " + e.getMessage());
            }
        });
    }

    public void disconnect() {
        isConnected = false;
        if (sendExecutor != null) {
            sendExecutor.shutdownNow();
            sendExecutor = null;
        }

        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }

        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
        socket = null;

        notifyState(false, "Đã ngắt kết nối");
    }

    private void startSendLoop() {
        if (sendExecutor != null) {
            sendExecutor.shutdownNow();
        }
        sendExecutor = Executors.newSingleThreadScheduledExecutor();
        sendExecutor.scheduleAtFixedRate(() -> {
            if (!isConnected || socket == null || targetAddress == null) return;

            if (permissionMode == ControlPermission.VIEWER && burstPacketsRemaining <= 0) {
                return;
            }

            boolean shouldSend = isActivelyDriving || burstPacketsRemaining > 0;
            if (!shouldSend) {
                return;
            }

            if (burstPacketsRemaining > 0) {
                burstPacketsRemaining--;
            }

            try {
                byte[] frame = new byte[47];

                // 1. Header & SEQ & ID
                frame[0] = (byte) 0xAA;
                frame[1] = (byte) (txSeqCount & 0xFF);
                frame[2] = (byte) ((txSeqCount >> 8) & 0xFF);
                frame[3] = stateRun;

                // 2. Data
                putFloatLE(frame, 4, setX);
                putFloatLE(frame, 8, setY);
                putFloatLE(frame, 12, setTheta);
                putFloatLE(frame, 16, setVx);
                putFloatLE(frame, 20, setVy);
                putFloatLE(frame, 24, setW);
                putFloatLE(frame, 28, setW1);
                putFloatLE(frame, 32, setW2);
                putFloatLE(frame, 36, setW3);
                putFloatLE(frame, 40, setW4);

                // 3. CRC16
                int crc = calculateCRC16(frame, 44);
                frame[44] = (byte) (crc & 0xFF);
                frame[45] = (byte) ((crc >> 8) & 0xFF);

                // 4. Footer
                frame[46] = 0x55;

                DatagramPacket packet = new DatagramPacket(frame, frame.length, targetAddress, targetPort);
                if (socket != null) {
                    socket.send(packet);
                }
                txSeqCount = (txSeqCount + 1) & 0xFFFF;

                if (!isActivelyDriving && burstPacketsRemaining == 0 && (stateRun & 0xFF) >= 0x07) {
                    stateRun = 0x01;
                }
            } catch (Exception e) {
                Log.e(TAG, "Lỗi gửi frame UDP: " + e.getMessage());
            }
        }, 0, SEND_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void startReceiveLoop() {
        receiveThread = new Thread(() -> {
            byte[] buffer = new byte[256];
            while (isConnected && socket != null && !Thread.currentThread().isInterrupted()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    int len = packet.getLength();
                    byte[] data = packet.getData();

                    if (len == 86 && (data[0] & 0xFF) == 0xAA && (data[85] & 0xFF) == 0x55) {
                        int receivedCrc = getUShortLE(data, 83);
                        int calculatedCrc = calculateCRC16(data, 83);
                        if (receivedCrc != calculatedCrc) continue;

                        int seq = getUShortLE(data, 1);
                        if (isFirstPacket) {
                            expectedSeq = seq;
                            isFirstPacket = false;
                        }

                        int diff = seq - expectedSeq;
                        if (diff < -32768) diff += 65536;
                        else if (diff > 32768) diff -= 65536;

                        if (diff > 0) {
                            int missingCount = Math.min(diff, 256);
                            for (int i = 0; i < missingCount; i++) {
                                int missingSeq = (expectedSeq + i) % 65536;
                                requestMissingPacket(missingSeq);
                            }
                            expectedSeq = (seq + 1) % 65536;
                        } else if (diff == 0) {
                            expectedSeq = (seq + 1) % 65536;
                        }

                        float[] floats = new float[20];
                        for (int i = 0; i < 20; i++) {
                            int offset = 3 + (i * 4);
                            floats[i] = getFloatLE(data, offset);
                        }

                        double elapsedSec = (System.currentTimeMillis() - startTimeMillis) / 1000.0;

                        RobotTelemetry telemetry = new RobotTelemetry(
                            seq, elapsedSec,
                            floats[0], floats[1],
                            floats[2], floats[3],
                            floats[4], floats[5],
                            floats[6], floats[7],
                            floats[8], floats[9],
                            floats[10], floats[11],
                            floats[12], floats[13],
                            floats[14], floats[15],
                            floats[16], floats[17],
                            floats[18], floats[19]
                        );

                        mainHandler.post(() -> {
                            if (onTelemetryReceived != null) {
                                onTelemetryReceived.onTelemetryReceived(telemetry);
                            }
                        });
                    }
                } catch (Exception ignored) {
                    if (!isConnected) break;
                }
            }
        }, "RobotUdpReceiver");
        receiveThread.start();
    }

    private void requestMissingPacket(int missingSeq) {
        try {
            byte[] reqPacket = new byte[5];
            reqPacket[0] = (byte) 0xAA;
            reqPacket[1] = (byte) 0xFF;
            reqPacket[2] = (byte) (missingSeq & 0xFF);
            reqPacket[3] = (byte) ((missingSeq >> 8) & 0xFF);
            reqPacket[4] = 0x55;
            sendRawPacket(reqPacket);
        } catch (Exception ignored) {}
    }

    private void sendRawPacket(byte[] bytes) {
        try {
            if (socket != null && targetAddress != null) {
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, targetAddress, targetPort);
                socket.send(packet);
            }
        } catch (Exception ignored) {}
    }

    private void notifyState(boolean connected, String message) {
        mainHandler.post(() -> {
            if (onConnectionStateChanged != null) {
                onConnectionStateChanged.onConnectionStateChanged(connected, message);
            }
        });
    }

    public void setPermission(ControlPermission mode) {
        setPermission(mode, "");
    }

    public void setPermission(ControlPermission mode, String reason) {
        permissionMode = mode;
        if (mode == ControlPermission.VIEWER) {
            isActivelyDriving = false;
            burstPacketsRemaining = 0;
            stateRun = 0x01;
            setW1 = 0.0f; setW2 = 0.0f; setW3 = 0.0f; setW4 = 0.0f;
        }
        mainHandler.post(() -> {
            if (onPermissionChanged != null) {
                onPermissionChanged.onPermissionChanged(mode, reason);
            }
        });
    }

    public ControlPermission cyclePermissionMode() {
        ControlPermission prev = permissionMode;
        ControlPermission next;
        if (prev == ControlPermission.AUTO) next = ControlPermission.MASTER;
        else if (prev == ControlPermission.MASTER) next = ControlPermission.VIEWER;
        else next = ControlPermission.AUTO;

        setPermission(next);
        if (next == ControlPermission.MASTER) {
            ButtonSyncManager.sendButtonEvent("PERMISSION", "MASTER", "TABLET");
        } else if (prev == ControlPermission.MASTER) {
            ButtonSyncManager.sendButtonEvent("PERMISSION", "RELEASE", "TABLET");
        }
        return next;
    }

    public void yieldControlToRemote() {
        isActivelyDriving = false;
        burstPacketsRemaining = 0;
        stateRun = 0x01;
        setW1 = 0.0f; setW2 = 0.0f; setW3 = 0.0f; setW4 = 0.0f;
    }

    public void moveForward(boolean isPressed) {
        if (permissionMode == ControlPermission.VIEWER) return;
        if (isPressed) {
            stateRun = 0x03;
            setW1 = manualSpeed; setW2 = manualSpeed; setW3 = manualSpeed; setW4 = manualSpeed;
            isActivelyDriving = true;
            burstPacketsRemaining = 0;
        } else {
            stopMotors();
        }
    }

    public void moveBackward(boolean isPressed) {
        if (permissionMode == ControlPermission.VIEWER) return;
        if (isPressed) {
            stateRun = 0x03;
            setW1 = -manualSpeed; setW2 = -manualSpeed; setW3 = -manualSpeed; setW4 = -manualSpeed;
            isActivelyDriving = true;
            burstPacketsRemaining = 0;
        } else {
            stopMotors();
        }
    }

    public void strafeLeft(boolean isPressed) {
        if (permissionMode == ControlPermission.VIEWER) return;
        if (isPressed) {
            stateRun = 0x03;
            setW1 = -manualSpeed; setW2 = manualSpeed; setW3 = manualSpeed; setW4 = -manualSpeed;
            isActivelyDriving = true;
            burstPacketsRemaining = 0;
        } else {
            stopMotors();
        }
    }

    public void strafeRight(boolean isPressed) {
        if (permissionMode == ControlPermission.VIEWER) return;
        if (isPressed) {
            stateRun = 0x03;
            setW1 = manualSpeed; setW2 = -manualSpeed; setW3 = -manualSpeed; setW4 = manualSpeed;
            isActivelyDriving = true;
            burstPacketsRemaining = 0;
        } else {
            stopMotors();
        }
    }

    public void turnLeft(boolean isPressed) {
        if (permissionMode == ControlPermission.VIEWER) return;
        isTurnLeftActive = isPressed;
        updateCombinedManualDrive();
    }

    public void turnRight(boolean isPressed) {
        if (permissionMode == ControlPermission.VIEWER) return;
        isTurnRightActive = isPressed;
        updateCombinedManualDrive();
    }

    public void driveJoystick(float nx, float ny) {
        if (permissionMode == ControlPermission.VIEWER) return;
        float mag = (float) Math.hypot(nx, ny);
        if (mag < 0.12f) {
            activeJoyX = 0.0f;
            activeJoyY = 0.0f;
        } else {
            activeJoyX = Math.max(-1f, Math.min(1f, nx));
            activeJoyY = Math.max(-1f, Math.min(1f, ny));
        }
        updateCombinedManualDrive();
    }

    public void driveGamepad(float lx, float ly, float rx) {
        if (permissionMode == ControlPermission.VIEWER) return;
        float mag = (float) Math.hypot(lx, ly);
        if (mag < 0.12f) {
            activeJoyX = 0.0f;
            activeJoyY = 0.0f;
        } else {
            activeJoyX = Math.max(-1f, Math.min(1f, lx));
            activeJoyY = Math.max(-1f, Math.min(1f, ly));
        }

        if (Math.abs(rx) < 0.12f) {
            isTurnLeftActive = false;
            isTurnRightActive = false;
        } else if (rx < 0) {
            isTurnLeftActive = true;
            isTurnRightActive = false;
        } else {
            isTurnLeftActive = false;
            isTurnRightActive = true;
        }
        updateCombinedManualDrive();
    }

    private void updateCombinedManualDrive() {
        if (permissionMode == ControlPermission.VIEWER) return;

        float tx = activeJoyX;
        float ty = activeJoyY;
        float joyMag = (float) Math.hypot(tx, ty);
        boolean hasJoy = joyMag >= 0.12f;

        float turnDir = 0.0f;
        if (isTurnLeftActive && !isTurnRightActive) turnDir = -1.0f;
        else if (isTurnRightActive && !isTurnLeftActive) turnDir = 1.0f;
        boolean hasTurn = turnDir != 0.0f;

        if (!hasJoy && !hasTurn) {
            stopMotors();
            return;
        }

        float t1 = hasJoy ? (ty + tx) : 0.0f;
        float t2 = hasJoy ? (ty - tx) : 0.0f;
        float t3 = hasJoy ? (ty - tx) : 0.0f;
        float t4 = hasJoy ? (ty + tx) : 0.0f;

        float rotWeight = hasJoy ? 0.85f : 1.0f;
        float r1 = turnDir * rotWeight;
        float r2 = turnDir * rotWeight;
        float r3 = -turnDir * rotWeight;
        float r4 = -turnDir * rotWeight;

        float raw1 = t1 + r1;
        float raw2 = t2 + r2;
        float raw3 = t3 + r3;
        float raw4 = t4 + r4;

        float peak = Math.max(1.0f, Math.max(Math.abs(raw1), Math.max(Math.abs(raw2), Math.max(Math.abs(raw3), Math.abs(raw4)))));

        stateRun = 0x03;
        setW1 = (raw1 / peak) * manualSpeed;
        setW2 = (raw2 / peak) * manualSpeed;
        setW3 = (raw3 / peak) * manualSpeed;
        setW4 = (raw4 / peak) * manualSpeed;
        isActivelyDriving = true;
        burstPacketsRemaining = 0;
    }

    public void stopMotors() {
        activeJoyX = 0.0f;
        activeJoyY = 0.0f;
        isTurnLeftActive = false;
        isTurnRightActive = false;
        stateRun = 0x01;
        setW1 = 0.0f;
        setW2 = 0.0f;
        setW3 = 0.0f;
        setW4 = 0.0f;
        isActivelyDriving = false;
        burstPacketsRemaining = 8;
    }

    public void setWheelVelocities(float w1, float w2, float w3, float w4) {
        if (permissionMode == ControlPermission.VIEWER) return;
        if (w1 == 0f && w2 == 0f && w3 == 0f && w4 == 0f) {
            stopMotors();
            return;
        }
        stateRun = 0x02;
        setW1 = w1;
        setW2 = w2;
        setW3 = w3;
        setW4 = w4;
        isActivelyDriving = true;
        burstPacketsRemaining = 0;
    }

    public void resetWheelVelocities() {
        stopMotors();
    }

    public void circleLine() {
        if (permissionMode == ControlPermission.VIEWER) return;
        stateRun = 0x04;
        isActivelyDriving = true;
        burstPacketsRemaining = 0;
    }

    public void returnHome() {
        if (permissionMode == ControlPermission.VIEWER) return;
        stateRun = 0x05;
        setX = 0.0f;
        setY = 0.0f;
        setTheta = 0.0f;
        isActivelyDriving = true;
        burstPacketsRemaining = 0;
    }

    public void emergencyStop() {
        stateRun = 0x01;
        setX = 0.0f;
        setY = 0.0f;
        setTheta = 0.0f;
        setVx = 0.0f;
        setVy = 0.0f;
        setW = 0.0f;
        setW1 = 0.0f;
        setW2 = 0.0f;
        setW3 = 0.0f;
        setW4 = 0.0f;
        isActivelyDriving = false;
        burstPacketsRemaining = 10;
    }

    public void setRobotValues(float vx, float vy, float w, float x, float y, float thetaDeg) {
        if (permissionMode == ControlPermission.VIEWER) return;
        setVx = vx;
        setVy = vy;
        setW = w;
        setX = x;
        setY = y;
        setTheta = (float) (thetaDeg * Math.PI / 180.0);
        stateRun = 0x06;
        isActivelyDriving = true;
        burstPacketsRemaining = 0;
    }

    public void resetRobotValues() {
        setVx = 0.0f;
        setVy = 0.0f;
        setW = 0.0f;
        setX = 0.0f;
        setY = 0.0f;
        setTheta = 0.0f;
        isActivelyDriving = false;
        burstPacketsRemaining = 8;
    }

    public void sendTakePhoto() {
        if (permissionMode == ControlPermission.VIEWER) return;
        stateRun = 0x07;
        burstPacketsRemaining = 5;
    }

    public void sendStartRecord() {
        if (permissionMode == ControlPermission.VIEWER) return;
        stateRun = 0x08;
        burstPacketsRemaining = 5;
    }

    public void sendStopRecord() {
        if (permissionMode == ControlPermission.VIEWER) return;
        stateRun = 0x09;
        burstPacketsRemaining = 5;
    }

    public void sendSwitchCamera() {
        if (permissionMode == ControlPermission.VIEWER) return;
        stateRun = 0x0A;
        burstPacketsRemaining = 5;
    }

    public void sendStopStream() {
        if (permissionMode == ControlPermission.VIEWER) return;
        stateRun = 0x0C;
        burstPacketsRemaining = 5;
    }

    private static int calculateCRC16(byte[] data, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    private static void putFloatLE(byte[] buffer, int offset, float value) {
        int bits = Float.floatToIntBits(value);
        buffer[offset] = (byte) (bits & 0xFF);
        buffer[offset + 1] = (byte) ((bits >>> 8) & 0xFF);
        buffer[offset + 2] = (byte) ((bits >>> 16) & 0xFF);
        buffer[offset + 3] = (byte) ((bits >>> 24) & 0xFF);
    }

    private static float getFloatLE(byte[] buffer, int offset) {
        int bits = (buffer[offset] & 0xFF) |
                ((buffer[offset + 1] & 0xFF) << 8) |
                ((buffer[offset + 2] & 0xFF) << 16) |
                ((buffer[offset + 3] & 0xFF) << 24);
        float v = Float.intBitsToFloat(bits);
        return (Float.isNaN(v) || Float.isInfinite(v)) ? 0.0f : v;
    }

    private static int getUShortLE(byte[] buffer, int offset) {
        return (buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8);
    }
}
