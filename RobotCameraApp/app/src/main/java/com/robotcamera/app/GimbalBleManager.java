package com.robotcamera.app;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Quản lý kết nối Bluetooth Low Energy (BLE) tới Gimbal DJI RS 3 Mini / Ronin
 * trên thiết bị Streaming gắn trên xe, đồng thời nhận lệnh điều khiển qua WiFi (UDP 5005 / HTTP 8080)
 * từ thiết bị Điều khiển (Tablet / PC) để chuyển tiếp xuống Gimbal.
 */
@SuppressLint("MissingPermission")
public class GimbalBleManager {
    public static final GimbalBleManager INSTANCE = new GimbalBleManager();

    public static GimbalBleManager getInstance() {
        return INSTANCE;
    }

    private static final String TAG = "GimbalBleManager";
    private static final String PREFS_NAME = "robot_app_prefs";
    private static final String PREF_LAST_GIMBAL_MAC = "pref_last_gimbal_mac";
    private static final String PREF_LAST_GIMBAL_NAME = "pref_last_gimbal_name";

    // UUID chuẩn của dòng DJI Ronin / RS 3 Mini / Osmo (Service FFF0, Notify/Pair FFF4, Write FFF5)
    private static final UUID UUID_SERVICE_FFF0 = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_CHAR_FFF4 = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_CHAR_FFF5 = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public enum FollowMode {
        PF(0, "PF", "Pan Follow (Khóa trục dọc, bám ngang)"),
        PTF(1, "PTF", "Pan & Tilt Follow (Bám ngang & dọc)"),
        FPV(2, "FPV", "FPV 3 trục tự do");

        private final int code;
        private final String label;
        private final String desc;

        FollowMode(int code, String label, String desc) {
            this.code = code;
            this.label = label;
            this.desc = desc;
        }

        public int getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        public String getDesc() {
            return desc;
        }

        public FollowMode next() {
            switch (this) {
                case PF:
                    return PTF;
                case PTF:
                    return FPV;
                case FPV:
                default:
                    return PF;
            }
        }

        public static FollowMode fromLabel(String text) {
            if (text == null) return PF;
            String trimmed = text.trim();
            for (FollowMode m : values()) {
                if (m.label.equalsIgnoreCase(trimmed)) {
                    return m;
                }
            }
            return PF;
        }

        public static class Companion {
            public FollowMode fromLabel(String text) {
                return FollowMode.fromLabel(text);
            }
        }

        public static final Companion Companion = new Companion();
    }

    public static class ScannedBleDevice {
        private final String name;
        private final String address;
        private final int rssi;
        private final boolean isLikelyDjiGimbal;
        private final BluetoothDevice device;

        public ScannedBleDevice(String name, String address, int rssi, boolean isLikelyDjiGimbal, BluetoothDevice device) {
            this.name = name;
            this.address = address;
            this.rssi = rssi;
            this.isLikelyDjiGimbal = isLikelyDjiGimbal;
            this.device = device;
        }

        public String getName() {
            return name;
        }

        public String getAddress() {
            return address;
        }

        public int getRssi() {
            return rssi;
        }

        public boolean isLikelyDjiGimbal() {
            return isLikelyDjiGimbal;
        }

        public BluetoothDevice getDevice() {
            return device;
        }
    }

    public interface StateListener {
        void onStateChanged(boolean connected, String statusMessage);
    }

    public interface ScanListener {
        void onScanResults(List<ScannedBleDevice> devices);
    }

    public interface FollowModeListener {
        void onFollowModeChanged(FollowMode mode);
    }

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static BluetoothGatt bluetoothGatt = null;
    private static BluetoothGattCharacteristic writeCharFff5 = null;
    private static BluetoothGattCharacteristic notifyCharFff4 = null;

    public static volatile boolean isConnected = false;
    public static volatile String connectedDeviceName = "";
    public static volatile String connectedDeviceAddress = "";
    public static volatile String statusMessage = "Chưa kết nối";
    public static volatile FollowMode currentFollowMode = FollowMode.PF;

    public static volatile FollowModeListener followModeListener = null;

    // Đảo chiều điều khiển (nếu mô-tơ gimbal quay ngược hướng mong muốn)
    public static volatile boolean invertPan = false;
    public static volatile boolean invertTilt = false;

    public static volatile int rxPacketCount = 0;
    public static volatile int txPacketCount = 0;
    public static volatile long lastRxTimestampMs = 0L;

    // Địa chỉ DUML học tự động từ gói tin Gimbal gửi lên (mặc định: App=0x02, Gimbal=0x04)
    private static volatile int dumlAppId = 0x02;
    private static volatile int dumlGimbalId = 0x04;
    private static volatile int detectedCrc8Init = 0x77;
    private static volatile int detectedCrc16Init = 0x3692;

    // Hàng đợi ghi BLE tuần tự để tránh lỗi "prior command is not finished" trên Android BluetoothGatt
    private static final ConcurrentLinkedQueue<byte[]> bleWriteQueue = new ConcurrentLinkedQueue<>();
    private static volatile boolean isBleWriteBusy = false;
    private static volatile long lastBleWriteStartMs = 0L;
    private static volatile boolean isDescriptorReady = false;

    // Tốc độ xoay mặc định (đơn vị 0.1 deg/s -> 450 = 45.0 deg/s)
    public static volatile int maxDegPerSecTenths = 450;

    // Trạng thái tốc độ hiện tại (-1.0 .. +1.0)
    private static volatile float currentPanNorm = 0f;
    private static volatile float currentTiltNorm = 0f;
    private static volatile int stopBurstRemaining = 0;
    private static volatile int seqCounter = 1;

    private static ScheduledExecutorService txExecutor = null;
    private static final ExecutorService httpExecutor = Executors.newCachedThreadPool();

    private static final Map<String, ScannedBleDevice> discoveredDevices = new LinkedHashMap<>();
    private static boolean isScanning = false;
    private static ScanCallback currentScanCallback = null;

    private static final CopyOnWriteArrayList<StateListener> stateListeners = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<ScanListener> scanListeners = new CopyOnWriteArrayList<>();

    // --- Getters & Setters matching Kotlin property syntax ---
    public static boolean isConnected() {
        return isConnected;
    }

    public boolean getIsConnected() {
        return isConnected;
    }

    public static String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    public static String getConnectedDeviceAddress() {
        return connectedDeviceAddress;
    }

    public static String getStatusMessage() {
        return statusMessage;
    }

    public static FollowMode getCurrentFollowMode() {
        return currentFollowMode;
    }

    public static boolean getInvertPan() {
        return invertPan;
    }

    public static void setInvertPan(boolean v) {
        invertPan = v;
    }

    public static boolean getInvertTilt() {
        return invertTilt;
    }

    public static void setInvertTilt(boolean v) {
        invertTilt = v;
    }

    public static int getRxPacketCount() {
        return rxPacketCount;
    }

    public static int getTxPacketCount() {
        return txPacketCount;
    }

    public static long getLastRxTimestampMs() {
        return lastRxTimestampMs;
    }

    public static int getMaxDegPerSecTenths() {
        return maxDegPerSecTenths;
    }

    public static void setMaxDegPerSecTenths(int v) {
        maxDegPerSecTenths = v;
    }

    public static void setFollowModeListener(FollowModeListener listener) {
        followModeListener = listener;
    }

    // --- State Listener Management ---
    public static void addStateListener(StateListener listener) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener);
        }
        listener.onStateChanged(isConnected, statusMessage);
    }

    public static void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }

    // --- Scan Listener Management ---
    public static void addScanListener(ScanListener listener) {
        if (!scanListeners.contains(listener)) {
            scanListeners.add(listener);
        }
        listener.onScanResults(getSortedDeviceList());
    }

    public static void removeScanListener(ScanListener listener) {
        scanListeners.remove(listener);
    }

    private static void notifyState(boolean connected, String msg) {
        isConnected = connected;
        statusMessage = msg;
        mainHandler.post(() -> {
            for (StateListener l : stateListeners) {
                try {
                    l.onStateChanged(connected, msg);
                } catch (Exception ignored) {}
            }
        });
    }

    private static void notifyScanResults() {
        final List<ScannedBleDevice> list = getSortedDeviceList();
        mainHandler.post(() -> {
            for (ScanListener l : scanListeners) {
                try {
                    l.onScanResults(list);
                } catch (Exception ignored) {}
            }
        });
    }

    public static List<ScannedBleDevice> getSortedDeviceList() {
        synchronized (discoveredDevices) {
            List<ScannedBleDevice> list = new ArrayList<>(discoveredDevices.values());
            Collections.sort(list, (a, b) -> {
                if (a.isLikelyDjiGimbal() != b.isLikelyDjiGimbal()) {
                    return b.isLikelyDjiGimbal() ? 1 : -1;
                }
                return Integer.compare(b.getRssi(), a.getRssi());
            });
            return list;
        }
    }

    /**
     * Tự động kết nối lại Gimbal đã lưu từ lần trước (khi mở chế độ Streaming)
     */
    public static void autoReconnectIfSaved(Context context) {
        if (isConnected) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedMac = prefs.getString(PREF_LAST_GIMBAL_MAC, null);
        if (savedMac != null && !savedMac.trim().isEmpty()) {
            try {
                BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter adapter = (manager != null) ? manager.getAdapter() : null;
                if (adapter != null && adapter.isEnabled()) {
                    BluetoothDevice device = adapter.getRemoteDevice(savedMac);
                    if (device != null) {
                        connectToDevice(context, device);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Auto reconnect failed: " + e.getMessage());
            }
        }
    }

    /**
     * Bắt đầu quét thiết bị BLE xung quanh (ưu tiên DJI RS 3 Mini / Ronin)
     */
    public static void startScan(Context context) {
        startScan(context, false);
    }

    public static void startScan(Context context, boolean autoConnectFirstDji) {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = (manager != null) ? manager.getAdapter() : null;
        if (adapter == null || !adapter.isEnabled()) {
            notifyState(false, "Hãy bật Bluetooth");
            return;
        }

        stopScan(context);
        synchronized (discoveredDevices) {
            discoveredDevices.clear();
        }

        // 1. Đưa các thiết bị đã ghép đôi (Bonded) trong hệ thống vào danh sách trước
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice dev : bonded) {
                    String devName = dev.getName();
                    if (devName != null && !devName.trim().isEmpty()) {
                        boolean isDji = isDjiGimbalName(devName);
                        ScannedBleDevice item = new ScannedBleDevice(
                                devName + " (Đã ghép đôi)",
                                dev.getAddress(),
                                -45,
                                isDji,
                                dev
                        );
                        synchronized (discoveredDevices) {
                            discoveredDevices.put(dev.getAddress(), item);
                        }
                    }
                }
                notifyScanResults();
            }
        } catch (Exception ignored) {}

        // 2. Quét BLE trực tiếp
        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) return;

        ScanCallback callback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice dev = result.getDevice();
                if (dev == null) return;

                ScanRecord record = result.getScanRecord();
                String rawName = (record != null && record.getDeviceName() != null) ? record.getDeviceName() : dev.getName();
                if (rawName == null) rawName = "";

                boolean hasFff0 = false;
                if (record != null && record.getServiceUuids() != null) {
                    for (ParcelUuid puuid : record.getServiceUuids()) {
                        if (puuid.getUuid().toString().toLowerCase().contains("fff0")) {
                            hasFff0 = true;
                            break;
                        }
                    }
                }
                boolean isDji = isDjiGimbalName(rawName) || hasFff0;

                // Bỏ qua các thiết bị vô danh không phải Gimbal để danh sách gọn gàng
                if (rawName.trim().isEmpty() && !hasFff0) return;

                String displayName = !rawName.trim().isEmpty() ? rawName : "Gimbal BLE (" + dev.getAddress().substring(Math.max(0, dev.getAddress().length() - 5)) + ")";
                ScannedBleDevice item = new ScannedBleDevice(
                        displayName,
                        dev.getAddress(),
                        result.getRssi(),
                        isDji,
                        dev
                );
                synchronized (discoveredDevices) {
                    discoveredDevices.put(dev.getAddress(), item);
                }
                notifyScanResults();

                if (autoConnectFirstDji && isDji && !isConnected) {
                    stopScan(context);
                    connectToDevice(context, dev);
                }
            }
        };

        currentScanCallback = callback;
        isScanning = true;
        notifyState(isConnected, isConnected ? "Đã kết nối: " + connectedDeviceName : "Đang quét BLE...");

        try {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            scanner.startScan(null, settings, callback);

            // Tự dừng quét sau 10 giây
            mainHandler.postDelayed(() -> {
                if (isScanning) {
                    stopScan(context);
                }
            }, 10000);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi quét BLE: " + e.getMessage());
            isScanning = false;
            notifyState(false, "Thiếu quyền Bluetooth");
        }
    }

    public static void stopScan(Context context) {
        if (!isScanning) return;
        isScanning = false;
        try {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = (manager != null) ? manager.getAdapter() : null;
            BluetoothLeScanner scanner = (adapter != null) ? adapter.getBluetoothLeScanner() : null;
            if (scanner != null && currentScanCallback != null) {
                scanner.stopScan(currentScanCallback);
            }
        } catch (Exception ignored) {}
        currentScanCallback = null;
        if (!isConnected) {
            notifyState(false, "Chưa kết nối");
        }
    }

    private static boolean isDjiGimbalName(String name) {
        if (name == null) return false;
        String u = name.toUpperCase();
        return u.contains("RS 3") || u.contains("RS3") || u.contains("RONIN") ||
                u.contains("DJI") || u.contains("RSC") || u.contains("RS 2") ||
                u.contains("RS2") || u.contains("OSMO") || u.contains("GIMBAL");
    }

    /**
     * Kết nối GATT tới Gimbal RS 3 Mini
     */
    public static void connectToDevice(Context context, BluetoothDevice device) {
        stopScan(context);
        disconnect();

        rxPacketCount = 0;
        txPacketCount = 0;
        isDescriptorReady = false;
        isBleWriteBusy = false;
        bleWriteQueue.clear();

        String devName = device.getName();
        if (devName == null || devName.trim().isEmpty()) {
            devName = device.getAddress();
        }
        notifyState(false, "Đang nối " + devName + "...");

        BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    String name = gatt.getDevice().getName();
                    connectedDeviceName = (name != null && !name.trim().isEmpty()) ? name : "RS3 Mini";
                    connectedDeviceAddress = gatt.getDevice().getAddress();
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                            .putString(PREF_LAST_GIMBAL_MAC, connectedDeviceAddress)
                            .putString(PREF_LAST_GIMBAL_NAME, connectedDeviceName)
                            .apply();

                    notifyState(true, "Đã nối: " + connectedDeviceName);
                    try {
                        gatt.requestMtu(517);
                    } catch (Exception ignored) {
                        gatt.discoverServices();
                    }
                    // Dự phòng trường hợp onMtuChanged không gọi trên một số dòng máy Android
                    mainHandler.postDelayed(() -> {
                        try {
                            if (writeCharFff5 == null && isConnected) {
                                gatt.discoverServices();
                            }
                        } catch (Exception ignored) {}
                    }, 600);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    stopTxLoop();
                    writeCharFff5 = null;
                    notifyCharFff4 = null;
                    isDescriptorReady = false;
                    isBleWriteBusy = false;
                    bleWriteQueue.clear();
                    notifyState(false, "Đã ngắt BLE");
                }
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                try {
                    gatt.discoverServices();
                } catch (Exception ignored) {}
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) return;

                // Tìm Service FFF0 và Characteristic FFF5 (Write) + FFF4 (Notify/Handshake)
                android.bluetooth.BluetoothGattService fff0Service = gatt.getService(UUID_SERVICE_FFF0);
                if (fff0Service != null) {
                    writeCharFff5 = fff0Service.getCharacteristic(UUID_CHAR_FFF5);
                    notifyCharFff4 = fff0Service.getCharacteristic(UUID_CHAR_FFF4);
                }

                // Nếu firmware có UUID dịch vụ khác, tự động dò Characteristic có đuôi fff5/fff4 hoặc có quyền Write
                if (writeCharFff5 == null) {
                    for (android.bluetooth.BluetoothGattService service : gatt.getServices()) {
                        for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                            String uuidStr = ch.getUuid().toString().toLowerCase();
                            if (uuidStr.contains("fff5")) {
                                writeCharFff5 = ch;
                            } else if (uuidStr.contains("fff4")) {
                                notifyCharFff4 = ch;
                            }
                        }
                    }
                }
                if (writeCharFff5 == null) {
                    for (android.bluetooth.BluetoothGattService service : gatt.getServices()) {
                        for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                            int props = ch.getProperties();
                            if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ||
                                    (props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                                writeCharFff5 = ch;
                                break;
                            }
                        }
                        if (writeCharFff5 != null) break;
                    }
                }

                // Bật Notification trên FFF4 (không ghi trực tiếp vào FFF4 vì FFF4 là Notify-Only trên RS 3 Mini)
                armDjiBleCharacteristics(gatt);
                startTxLoop();
                notifyState(true, "Gimbal: " + connectedDeviceName);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                Log.i(TAG, "onDescriptorWrite status=" + status + " uuid=" + descriptor.getUuid());
                isDescriptorReady = true;
                isBleWriteBusy = false;
                // Gửi gói kích hoạt / bắt tay BLE chuẩn DJI qua FFF5 sau khi CCCD hoàn tất
                sendDjiBleHandshake();
                drainBleWriteQueue();
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                isBleWriteBusy = false;
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "onCharacteristicWrite status=" + status + " uuid=" + characteristic.getUuid());
                }
                drainBleWriteQueue();
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                byte[] data = characteristic.getValue();
                if (data != null) {
                    handleIncomingBleNotification(data);
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
                handleIncomingBleNotification(value);
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        }
    }

    private static void armDjiBleCharacteristics(BluetoothGatt gatt) {
        try {
            if (notifyCharFff4 != null) {
                gatt.setCharacteristicNotification(notifyCharFff4, true);
                BluetoothGattDescriptor desc = notifyCharFff4.getDescriptor(UUID_CCCD);
                if (desc != null) {
                    isBleWriteBusy = true;
                    lastBleWriteStartMs = System.currentTimeMillis();
                    desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(desc);
                } else {
                    isDescriptorReady = true;
                    sendDjiBleHandshake();
                }
            } else {
                isDescriptorReady = true;
            }
        } catch (Exception e) {
            Log.w(TAG, "armDjiBleCharacteristics error: " + e.getMessage());
            isDescriptorReady = true;
            isBleWriteBusy = false;
        }
    }

    /**
     * Xử lý gói tin Notification nhận từ FFF4 của Gimbal RS 3 Mini
     */
    private static void handleIncomingBleNotification(byte[] data) {
        if (data == null || data.length == 0) return;
        rxPacketCount++;
        lastRxTimestampMs = System.currentTimeMillis();

        // Log 15 gói đầu tiên và mỗi 40 gói tiếp theo để chẩn đoán khung truyền thực tế của RS 3 Mini
        if (rxPacketCount <= 15 || rxPacketCount % 40 == 0) {
            StringBuilder sb = new StringBuilder();
            for (byte b : data) {
                sb.append(String.format("%02X ", b));
            }
            Log.i(TAG, "RX FFF4 (#" + rxPacketCount + ", " + data.length + "B): " + sb.toString().trim());
        }

        // Nếu nhận được khung DUML 0x55 từ Gimbal: tự động học CRC seed & địa chỉ Sender/Receiver thực tế
        if (data.length >= 13 && data[0] == 0x55) {
            int hdrCrc = data[3] & 0xFF;
            int c77 = calcDjiCrc8WithSeed(data, 3, 0x77) & 0xFF;
            int cEE = calcDjiCrc8WithSeed(data, 3, 0xEE) & 0xFF;
            if (hdrCrc == c77 && detectedCrc8Init != 0x77) {
                detectedCrc8Init = 0x77;
                Log.i(TAG, "Auto-detected CRC8 seed = 0x77");
            } else if (hdrCrc == cEE && detectedCrc8Init != 0xEE) {
                detectedCrc8Init = 0xEE;
                Log.i(TAG, "Auto-detected CRC8 seed = 0xEE");
            }

            int sender = data[4] & 0xFF;
            int receiver = data[5] & 0xFF;
            if (rxPacketCount <= 5) {
                Log.i(TAG, "DUML RX sender=0x" + String.format("%02X", sender) +
                        " receiver=0x" + String.format("%02X", receiver) +
                        " cmdSet=0x" + String.format("%02X", data[9]) +
                        " cmdId=0x" + String.format("%02X", data[10]));
            }
        }

        // Cập nhật trạng thái UI khi nhận gói đầu tiên
        if (rxPacketCount == 1) {
            notifyState(true, "Đã kết nối (" + connectedDeviceName + ")");
        }
    }

    /**
     * Gửi gói bắt tay / xác thực BLE chuẩn DJI qua FFF5
     */
    private static void sendDjiBleHandshake() {
        // Gửi yêu cầu lấy phiên bản / xác thực PIN mặc định DJI Ronin BLE (CmdSet=0x00 CmdId=0x01)
        enqueueBlePacket(buildDuml55Frame(0x00, 0x01, new byte[0]));
    }

    public static void disconnect() {
        stopTxLoop();
        bleWriteQueue.clear();
        isBleWriteBusy = false;
        isDescriptorReady = false;
        try {
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
        } catch (Exception ignored) {}
        bluetoothGatt = null;
        writeCharFff5 = null;
        notifyCharFff4 = null;
        notifyState(false, "Chưa kết nối");
    }

    // =========================================================================
    // ĐIỀU KHIỂN GÓC QUAY PAN / TILT, RECENTER & CHẾ ĐỘ FOLLOW (PF / PTF / FPV)
    // =========================================================================

    /**
     * Cập nhật vận tốc xoay trục Pan (-1..+1: Trái/Phải) và Tilt (-1..+1: Xuống/Lên)
     */
    public static void setPanTilt(float panNorm, float tiltNorm) {
        float clampedPan = Math.max(-1f, Math.min(1f, panNorm));
        float clampedTilt = Math.max(-1f, Math.min(1f, tiltNorm));
        boolean wasMoving = (currentPanNorm != 0f || currentTiltNorm != 0f);
        currentPanNorm = clampedPan;
        currentTiltNorm = clampedTilt;

        if (clampedPan == 0f && clampedTilt == 0f && wasMoving) {
            // Gửi 5 gói vận tốc = 0 để dừng mượt ngay lập tức
            stopBurstRemaining = 5;
        } else if (clampedPan != 0f || clampedTilt != 0f) {
            stopBurstRemaining = 0;
        }
    }

    public static void setPanDirection(int dir) {
        float p = Math.max(-1f, Math.min(1f, (float) dir));
        setPanTilt(p, currentTiltNorm);
    }

    public static void setTiltDirection(int dir) {
        float t = Math.max(-1f, Math.min(1f, (float) dir));
        setPanTilt(currentPanNorm, t);
    }

    /**
     * Đưa Gimbal về vị trí chính giữa (Recenter)
     */
    public static void recenter() {
        currentPanNorm = 0f;
        currentTiltNorm = 0f;
        stopBurstRemaining = 2;
        if (!isConnected) return;

        // 1. DJI R-SDK DUML Selfie / Recenter (CmdSet = 0x0E, CmdId = 0x0E, payload = [0x01, 0x01])
        enqueueBlePacket(buildDuml55Frame(0x0E, 0x0E, new byte[]{0x01, 0x01}));

        // 2. Handheld Gimbal Position Control (CmdSet = 0x0E, CmdId = 0x00, target 0,0,0, time 1.0s)
        byte[] posPayload = new byte[8];
        putInt16LE(posPayload, 0, 0); // yaw = 0
        putInt16LE(posPayload, 2, 0); // roll = 0
        putInt16LE(posPayload, 4, 0); // pitch = 0
        posPayload[6] = 10;           // 10 * 0.1s = 1.0s
        posPayload[7] = 0x00;         // Absolute control mode
        enqueueBlePacket(buildDuml55Frame(0x0E, 0x0E, posPayload));

        // 3. DUML Gimbal Return to Home (CmdSet = 0x04, CmdId = 0x4C, payload = [0x01, 0x00])
        enqueueBlePacket(buildDuml55Frame(0x04, 0x4C, new byte[]{0x01, 0x00}));

        // 4. DUML Gimbal Angle Reset (CmdSet = 0x04, CmdId = 0x01, payload = [0,0, 0,0, 0,0, 10,0])
        byte[] anglePayload = new byte[8];
        putInt16LE(anglePayload, 0, 0);
        putInt16LE(anglePayload, 2, 0);
        putInt16LE(anglePayload, 4, 0);
        anglePayload[6] = 10;
        anglePayload[7] = 0;
        enqueueBlePacket(buildDuml55Frame(0x04, 0x01, anglePayload));
    }

    /**
     * Đổi chế độ Follow của Gimbal: PF (Pan Follow), PTF (Pan-Tilt Follow), FPV
     */
    public static void setFollowMode(FollowMode mode) {
        currentFollowMode = mode;
        mainHandler.post(() -> {
            if (followModeListener != null) {
                try {
                    followModeListener.onFollowModeChanged(mode);
                } catch (Exception ignored) {}
            }
        });
        if (!isConnected) return;

        byte mByte = (byte) mode.getCode();
        // 1. Gói DUML R-SDK Set Work Mode (CmdSet = 0x0E, CmdId = 0x03)
        enqueueBlePacket(buildDuml55Frame(0x0E, 0x03, new byte[]{mByte}));
        // 2. Gói DUML Gimbal Set Mode (CmdSet = 0x04, CmdId = 0x4C)
        enqueueBlePacket(buildDuml55Frame(0x04, 0x4C, new byte[]{mByte, 0x00}));
        // 3. Gói DUML Gimbal Set Profile M1/M2/M3 (CmdSet = 0x04, CmdId = 0x10)
        enqueueBlePacket(buildDuml55Frame(0x04, 0x10, new byte[]{mByte}));
    }

    private static void startTxLoop() {
        stopTxLoop();
        txExecutor = Executors.newSingleThreadScheduledExecutor();
        txExecutor.scheduleAtFixedRate(() -> {
            if (!isConnected || writeCharFff5 == null || !isDescriptorReady) return;

            boolean active = (currentPanNorm != 0f || currentTiltNorm != 0f);
            if (!active && stopBurstRemaining <= 0) return;

            if (stopBurstRemaining > 0) {
                stopBurstRemaining--;
            }

            float effPan = invertPan ? -currentPanNorm : currentPanNorm;
            float effTilt = invertTilt ? -currentTiltNorm : currentTiltNorm;

            // Thứ tự trục chuẩn của dòng DJI Ronin RS 3 Mini:
            // Trục 1 (byte 0..1): Yaw (Pan - Xoay Ngang)
            // Trục 2 (byte 2..3): Roll (0)
            // Trục 3 (byte 4..5): Pitch (Tilt - Ngẩng / Cúi)
            int yawSpeed = Math.max(-1800, Math.min(1800, Math.round(effPan * maxDegPerSecTenths)));
            int pitchSpeed = Math.max(-1800, Math.min(1800, Math.round(effTilt * maxDegPerSecTenths)));
            int rollSpeed = 0;

            // Giới hạn hàng đợi tối đa 4 gói để không bao giờ bị trễ lệnh
            if (bleWriteQueue.size() > 4) {
                bleWriteQueue.clear();
            }

            // 1. Khung DUML R-SDK Speed Control (CmdSet = 0x0E, CmdId = 0x01)
            byte[] rSdkPayload = new byte[7];
            putInt16LE(rSdkPayload, 0, yawSpeed);
            putInt16LE(rSdkPayload, 2, rollSpeed);
            putInt16LE(rSdkPayload, 4, pitchSpeed);
            rSdkPayload[6] = active ? (byte) 0x80 : (byte) 0x00;
            enqueueBlePacket(buildDuml55Frame(0x0E, 0x01, rSdkPayload));

            // 2. Khung DUML Gimbal Ext Speed Control (CmdSet = 0x04, CmdId = 0x0C)
            byte[] dumlPayload = new byte[7];
            putInt16LE(dumlPayload, 0, yawSpeed);
            putInt16LE(dumlPayload, 2, rollSpeed);
            putInt16LE(dumlPayload, 4, pitchSpeed);
            dumlPayload[6] = active ? (byte) 0x81 : (byte) 0x00;
            enqueueBlePacket(buildDuml55Frame(0x04, 0x0C, dumlPayload));
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    private static void stopTxLoop() {
        if (txExecutor != null) {
            txExecutor.shutdownNow();
            txExecutor = null;
        }
    }

    private static void enqueueBlePacket(byte[] packet) {
        if (!isConnected) return;
        bleWriteQueue.offer(packet);
        drainBleWriteQueue();
    }

    private static synchronized void drainBleWriteQueue() {
        BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null) return;
        BluetoothGattCharacteristic ch = writeCharFff5;
        if (ch == null) return;
        if (!isDescriptorReady) return;

        // Giải phóng khóa nếu một lệnh ghi trước đó bị treo quá 120ms
        long now = System.currentTimeMillis();
        if (isBleWriteBusy && (now - lastBleWriteStartMs) > 120L) {
            isBleWriteBusy = false;
        }
        if (isBleWriteBusy) return;

        byte[] nextPacket = bleWriteQueue.poll();
        if (nextPacket == null) return;

        try {
            isBleWriteBusy = true;
            lastBleWriteStartMs = now;
            int props = ch.getProperties();
            if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                ch.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            } else {
                ch.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }
            ch.setValue(nextPacket);
            boolean ok = gatt.writeCharacteristic(ch);
            if (ok) {
                txPacketCount++;
            } else {
                isBleWriteBusy = false;
            }
        } catch (Exception e) {
            isBleWriteBusy = false;
            Log.e(TAG, "drainBleWriteQueue error: " + e.getMessage());
        }
    }

    // =========================================================================
    // ĐÓNG GÓI KHUNG TRUYỀN DJI DUML (0x55) & DJI R SDK (0xAA) + CRC8 / CRC16
    // =========================================================================

    /**
     * Khung DUML 0x55:
     * [0x55] [len_lo] [ver<<2 | len_hi] [crc8] [sender] [receiver] [seq: 2B LE] [flags] [cmdSet] [cmdId] [payload] [crc16: 2B LE]
     */
    private static byte[] buildDuml55Frame(int cmdSet, int cmdId, byte[] payload) {
        int totalLen = 13 + payload.length;
        byte[] buf = new byte[totalLen];
        buf[0] = 0x55;
        buf[1] = (byte) (totalLen & 0xFF);
        buf[2] = (byte) (((1 << 2) | ((totalLen >> 8) & 0x03)) & 0xFF); // ver = 1 -> 0x04
        buf[3] = calcDjiCrc8(buf, 3);

        // Sender = App (0x02), Receiver = Gimbal (0x04)
        buf[4] = (byte) (dumlAppId & 0xFF);
        buf[5] = (byte) (dumlGimbalId & 0xFF);

        int seq = nextSeq();
        buf[6] = (byte) (seq & 0xFF);
        buf[7] = (byte) ((seq >> 8) & 0xFF);

        buf[8] = 0x00; // No ACK required for low-latency streaming
        buf[9] = (byte) (cmdSet & 0xFF);
        buf[10] = (byte) (cmdId & 0xFF);

        System.arraycopy(payload, 0, buf, 11, payload.length);

        int crc16 = calcDjiCrc16(buf, totalLen - 2);
        buf[totalLen - 2] = (byte) (crc16 & 0xFF);
        buf[totalLen - 1] = (byte) ((crc16 >> 8) & 0xFF);
        return buf;
    }

    /**
     * Khung DJI R SDK 0xAA:
     * [0xAA] [len_lo] [ver<<2 | len_hi] [crc8] [seq: 2B LE] [0x00] [0x02] [cmdSet] [cmdId] [payload] [crc16]
     */
    private static byte[] buildRoninAAFrame(int cmdSet, int cmdId, byte[] payload) {
        int totalLen = 12 + payload.length;
        byte[] buf = new byte[totalLen];
        buf[0] = (byte) 0xAA;
        buf[1] = (byte) (totalLen & 0xFF);
        buf[2] = 0x04;
        buf[3] = calcDjiCrc8(buf, 3);

        int seq = nextSeq();
        buf[4] = (byte) (seq & 0xFF);
        buf[5] = (byte) ((seq >> 8) & 0xFF);
        buf[6] = 0x00;
        buf[7] = 0x02;
        buf[8] = (byte) (cmdSet & 0xFF);
        buf[9] = (byte) (cmdId & 0xFF);

        System.arraycopy(payload, 0, buf, 10, payload.length);

        int crc16 = calcDjiCrc16(buf, totalLen - 2);
        buf[totalLen - 2] = (byte) (crc16 & 0xFF);
        buf[totalLen - 1] = (byte) ((crc16 >> 8) & 0xFF);
        return buf;
    }

    private static int nextSeq() {
        seqCounter = (seqCounter + 1) & 0xFFFF;
        return seqCounter;
    }

    private static void putInt16LE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static byte calcDjiCrc8(byte[] data, int len) {
        return calcDjiCrc8WithSeed(data, len, detectedCrc8Init);
    }

    private static byte calcDjiCrc8WithSeed(byte[] data, int len, int seed) {
        int crc = seed & 0xFF;
        for (int i = 0; i < len; i++) {
            crc = crc ^ (data[i] & 0xFF);
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x01) != 0) {
                    crc = (crc >>> 1) ^ 0x8C;
                } else {
                    crc = crc >>> 1;
                }
            }
        }
        return (byte) (crc & 0xFF);
    }

    private static int calcDjiCrc16(byte[] data, int len) {
        int crc = detectedCrc16Init & 0xFFFF;
        for (int i = 0; i < len; i++) {
            crc = crc ^ (data[i] & 0xFF);
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ 0x8408;
                } else {
                    crc = crc >>> 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    // =========================================================================
    // GỬI LỆNH ĐIỀU KHIỂN GIMBAL QUA WIFI TỪ THIẾT BỊ ĐIỀU KHIỂN -> THIẾT BỊ STREAMING
    // =========================================================================

    public static void sendRemoteGimbalCommand(Context context, String btnId, String action) {
        sendRemoteGimbalCommand(context, btnId, action, null, null);
    }

    public static void sendRemoteGimbalCommand(Context context, String btnId, String action, String extra) {
        sendRemoteGimbalCommand(context, btnId, action, extra, null);
    }

    /**
     * Gửi lệnh từ thiết bị Điều khiển (Controller) sang thiết bị Streaming qua cả 2 kênh:
     * 1. UDP Broadcast cổng 5005 (ButtonSyncManager) -> Độ trễ cực thấp (< 2ms)
     * 2. HTTP GET cổng 8080 (/gimbal/...) tới IP máy Streaming -> Đảm bảo tin cậy
     */
    public static void sendRemoteGimbalCommand(
            Context context,
            String btnId,
            String action,
            String extra,
            String httpEndpoint
    ) {
        // 1. Phát qua UDP 5005
        ButtonSyncManager.sendButtonEvent(btnId, action, extra);

        // 2. Nếu chính máy này đang kết nối BLE với Gimbal thì thực thi trực tiếp luôn
        if (isConnected) {
            handleIncomingNetworkCommand(btnId, action, extra);
            return;
        }

        // 3. Gửi thêm qua HTTP cổng 8080 tới IP Camera Streaming đã lưu
        if (httpEndpoint != null && !httpEndpoint.trim().isEmpty()) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String rawIp = prefs.getString("pref_remote_fpv_ip", "192.168.2.80");
            final String streamIp = (rawIp != null) ? rawIp.trim() : "192.168.2.80";
            if (!streamIp.isEmpty()) {
                httpExecutor.execute(() -> {
                    try {
                        String host = streamIp.contains(":") ? streamIp.split(":")[0] : streamIp;
                        URL url = new URL("http://" + host + ":8080/" + httpEndpoint);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(800);
                        conn.setReadTimeout(800);
                        conn.getResponseCode();
                        conn.disconnect();
                    } catch (Exception ignored) {}
                });
            }
        }
    }

    public static void handleIncomingNetworkCommand(String btnId, String action) {
        handleIncomingNetworkCommand(btnId, action, null);
    }

    /**
     * Xử lý lệnh Gimbal nhận được từ mạng LAN (UDP 5005 hoặc HTTP 8080) trên máy Streaming
     */
    public static void handleIncomingNetworkCommand(String btnId, String action, String extra) {
        if (btnId == null) return;
        boolean isDown = "DOWN".equalsIgnoreCase(action);
        switch (btnId.toUpperCase()) {
            case "GIMBAL_UP":
                setTiltDirection(isDown ? 1 : 0);
                break;
            case "GIMBAL_DOWN":
                setTiltDirection(isDown ? -1 : 0);
                break;
            case "GIMBAL_LEFT":
                setPanDirection(isDown ? -1 : 0);
                break;
            case "GIMBAL_RIGHT":
                setPanDirection(isDown ? 1 : 0);
                break;
            case "GIMBAL_RECENTER":
                recenter();
                break;
            case "GIMBAL_MODE":
                FollowMode mode = (extra != null && !extra.trim().isEmpty()) ?
                        FollowMode.fromLabel(extra) : currentFollowMode.next();
                setFollowMode(mode);
                break;
        }
    }
}
