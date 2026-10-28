package com.robotcamera.app

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Quản lý kết nối Bluetooth Low Energy (BLE) tới Gimbal DJI RS 3 Mini / Ronin
 * trên thiết bị Streaming gắn trên xe, đồng thời nhận lệnh điều khiển qua WiFi (UDP 5005 / HTTP 8080)
 * từ thiết bị Điều khiển (Tablet / PC) để chuyển tiếp xuống Gimbal.
 */
@SuppressLint("MissingPermission")
object GimbalBleManager {
    private const val TAG = "GimbalBleManager"
    private const val PREFS_NAME = "robot_app_prefs"
    private const val PREF_LAST_GIMBAL_MAC = "pref_last_gimbal_mac"
    private const val PREF_LAST_GIMBAL_NAME = "pref_last_gimbal_name"

    // UUID chuẩn của dòng DJI Ronin / RS 3 Mini / Osmo (Service FFF0, Notify/Pair FFF4, Write FFF5)
    private val UUID_SERVICE_FFF0 = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val UUID_CHAR_FFF4 = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")
    private val UUID_CHAR_FFF5 = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb")
    private val UUID_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    enum class FollowMode(val code: Int, val label: String, val desc: String) {
        PF(0, "PF", "Pan Follow (Khóa trục dọc, bám ngang)"),
        PTF(1, "PTF", "Pan & Tilt Follow (Bám ngang & dọc)"),
        FPV(2, "FPV", "FPV 3 trục tự do");

        fun next(): FollowMode = when (this) {
            PF -> PTF
            PTF -> FPV
            FPV -> PF
        }

        companion object {
            fun fromLabel(text: String): FollowMode {
                return values().firstOrNull { it.label.equals(text.trim(), ignoreCase = true) } ?: PF
            }
        }
    }

    data class ScannedBleDevice(
        val name: String,
        val address: String,
        val rssi: Int,
        val isLikelyDjiGimbal: Boolean,
        val device: BluetoothDevice
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharFff5: BluetoothGattCharacteristic? = null
    private var notifyCharFff4: BluetoothGattCharacteristic? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    @Volatile
    var connectedDeviceName: String = ""
        private set

    @Volatile
    var connectedDeviceAddress: String = ""
        private set

    @Volatile
    var statusMessage: String = "Chưa kết nối"
        private set

    @Volatile
    var currentFollowMode: FollowMode = FollowMode.PF
        private set

    var onFollowModeChanged: ((FollowMode) -> Unit)? = null

    // Đảo chiều điều khiển (nếu mô-tơ gimbal quay ngược hướng mong muốn)
    @Volatile
    var invertPan: Boolean = false

    @Volatile
    var invertTilt: Boolean = false

    @Volatile
    var rxPacketCount: Int = 0
        private set

    @Volatile
    var txPacketCount: Int = 0
        private set

    @Volatile
    var lastRxTimestampMs: Long = 0L
        private set

    // Địa chỉ DUML học tự động từ gói tin Gimbal gửi lên (mặc định: App=0x02, Gimbal=0x04)
    @Volatile
    private var dumlAppId: Int = 0x02

    @Volatile
    private var dumlGimbalId: Int = 0x04

    @Volatile
    private var detectedCrc8Init: Int = 0x77

    @Volatile
    private var detectedCrc16Init: Int = 0x3692

    // Hàng đợi ghi BLE tuần tự để tránh lỗi "prior command is not finished" trên Android BluetoothGatt
    private val bleWriteQueue = ConcurrentLinkedQueue<ByteArray>()

    @Volatile
    private var isBleWriteBusy: Boolean = false

    @Volatile
    private var lastBleWriteStartMs: Long = 0L

    @Volatile
    private var isDescriptorReady: Boolean = false

    // Tốc độ xoay mặc định (đơn vị 0.1 deg/s -> 450 = 45.0 deg/s)
    @Volatile
    var maxDegPerSecTenths: Int = 450

    // Trạng thái tốc độ hiện tại (-1.0 .. +1.0)
    @Volatile
    private var currentPanNorm: Float = 0f

    @Volatile
    private var currentTiltNorm: Float = 0f

    @Volatile
    private var stopBurstRemaining: Int = 0

    @Volatile
    private var seqCounter: Int = 1

    private var txExecutor: ScheduledExecutorService? = null
    private val httpExecutor = Executors.newCachedThreadPool()

    private val discoveredDevices = LinkedHashMap<String, ScannedBleDevice>()
    private var isScanning = false
    private var currentScanCallback: ScanCallback? = null

    private val stateListeners = CopyOnWriteArrayList<(Boolean, String) -> Unit>()
    private val scanListeners = CopyOnWriteArrayList<(List<ScannedBleDevice>) -> Unit>()

    fun addStateListener(listener: (Boolean, String) -> Unit) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener)
        }
        listener(isConnected, statusMessage)
    }

    fun removeStateListener(listener: (Boolean, String) -> Unit) {
        stateListeners.remove(listener)
    }

    fun addScanListener(listener: (List<ScannedBleDevice>) -> Unit) {
        if (!scanListeners.contains(listener)) {
            scanListeners.add(listener)
        }
        listener(getSortedDeviceList())
    }

    fun removeScanListener(listener: (List<ScannedBleDevice>) -> Unit) {
        scanListeners.remove(listener)
    }

    private fun notifyState(connected: Boolean, msg: String) {
        isConnected = connected
        statusMessage = msg
        mainHandler.post {
            for (l in stateListeners) {
                try {
                    l(connected, msg)
                } catch (_: Exception) {}
            }
        }
    }

    private fun notifyScanResults() {
        val list = getSortedDeviceList()
        mainHandler.post {
            for (l in scanListeners) {
                try {
                    l(list)
                } catch (_: Exception) {}
            }
        }
    }

    private fun getSortedDeviceList(): List<ScannedBleDevice> {
        synchronized(discoveredDevices) {
            return discoveredDevices.values.sortedWith(
                compareByDescending<ScannedBleDevice> { it.isLikelyDjiGimbal }
                    .thenByDescending { it.rssi }
            )
        }
    }

    /**
     * Tự động kết nối lại Gimbal đã lưu từ lần trước (khi mở chế độ Streaming)
     */
    fun autoReconnectIfSaved(context: Context) {
        if (isConnected) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMac = prefs.getString(PREF_LAST_GIMBAL_MAC, null)
        if (!savedMac.isNullOrBlank()) {
            try {
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = manager?.adapter
                if (adapter != null && adapter.isEnabled) {
                    val device = adapter.getRemoteDevice(savedMac)
                    if (device != null) {
                        connectToDevice(context, device)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Auto reconnect failed: ${e.message}")
            }
        }
    }

    /**
     * Bắt đầu quét thiết bị BLE xung quanh (ưu tiên DJI RS 3 Mini / Ronin)
     */
    fun startScan(context: Context, autoConnectFirstDji: Boolean = false) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            notifyState(false, "Hãy bật Bluetooth")
            return
        }

        stopScan(context)
        synchronized(discoveredDevices) {
            discoveredDevices.clear()
        }

        // 1. Đưa các thiết bị đã ghép đôi (Bonded) trong hệ thống vào danh sách trước
        try {
            adapter.bondedDevices?.forEach { dev ->
                val devName = dev.name ?: ""
                if (devName.isNotBlank()) {
                    val isDji = isDjiGimbalName(devName)
                    val item = ScannedBleDevice(
                        name = "$devName (Đã ghép đôi)",
                        address = dev.address,
                        rssi = -45,
                        isLikelyDjiGimbal = isDji,
                        device = dev
                    )
                    synchronized(discoveredDevices) {
                        discoveredDevices[dev.address] = item
                    }
                }
            }
            notifyScanResults()
        } catch (_: Exception) {}

        // 2. Quét BLE trực tiếp
        val scanner = adapter.bluetoothLeScanner ?: return
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val rawName = result.scanRecord?.deviceName ?: dev.name ?: ""
                val hasFff0 = result.scanRecord?.serviceUuids?.any {
                    it.uuid.toString().lowercase().contains("fff0")
                } == true
                val isDji = isDjiGimbalName(rawName) || hasFff0

                // Bỏ qua các thiết bị vô danh không phải Gimbal để danh sách gọn gàng
                if (rawName.isBlank() && !hasFff0) return

                val displayName = if (rawName.isNotBlank()) rawName else "Gimbal BLE (${dev.address.takeLast(5)})"
                val item = ScannedBleDevice(
                    name = displayName,
                    address = dev.address,
                    rssi = result.rssi,
                    isLikelyDjiGimbal = isDji,
                    device = dev
                )
                synchronized(discoveredDevices) {
                    discoveredDevices[dev.address] = item
                }
                notifyScanResults()

                if (autoConnectFirstDji && isDji && !isConnected) {
                    stopScan(context)
                    connectToDevice(context, dev)
                }
            }
        }

        currentScanCallback = callback
        isScanning = true
        notifyState(isConnected, if (isConnected) "Đã kết nối: $connectedDeviceName" else "Đang quét BLE...")

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, callback)

            // Tự dừng quét sau 10 giây
            mainHandler.postDelayed({
                if (isScanning) {
                    stopScan(context)
                }
            }, 10000)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi quét BLE: ${e.message}")
            isScanning = false
            notifyState(false, "Thiếu quyền Bluetooth")
        }
    }

    fun stopScan(context: Context) {
        if (!isScanning) return
        isScanning = false
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val scanner = manager?.adapter?.bluetoothLeScanner
            currentScanCallback?.let { scanner?.stopScan(it) }
        } catch (_: Exception) {}
        currentScanCallback = null
        if (!isConnected) {
            notifyState(false, "Chưa kết nối")
        }
    }

    private fun isDjiGimbalName(name: String): Boolean {
        val u = name.uppercase()
        return u.contains("RS 3") || u.contains("RS3") || u.contains("RONIN") ||
                u.contains("DJI") || u.contains("RSC") || u.contains("RS 2") ||
                u.contains("RS2") || u.contains("OSMO") || u.contains("GIMBAL")
    }

    /**
     * Kết nối GATT tới Gimbal RS 3 Mini
     */
    fun connectToDevice(context: Context, device: BluetoothDevice) {
        stopScan(context)
        disconnect()

        rxPacketCount = 0
        txPacketCount = 0
        isDescriptorReady = false
        isBleWriteBusy = false
        bleWriteQueue.clear()

        val devName = device.name ?: device.address
        notifyState(false, "Đang nối $devName...")

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedDeviceName = gatt.device.name ?: "RS3 Mini"
                    connectedDeviceAddress = gatt.device.address
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putString(PREF_LAST_GIMBAL_MAC, connectedDeviceAddress)
                        .putString(PREF_LAST_GIMBAL_NAME, connectedDeviceName)
                        .apply()

                    notifyState(true, "Đã nối: $connectedDeviceName")
                    try {
                        gatt.requestMtu(517)
                    } catch (_: Exception) {
                        gatt.discoverServices()
                    }
                    // Dự phòng trường hợp onMtuChanged không gọi trên một số dòng máy Android
                    mainHandler.postDelayed({
                        try {
                            if (writeCharFff5 == null && isConnected) {
                                gatt.discoverServices()
                            }
                        } catch (_: Exception) {}
                    }, 600)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    stopTxLoop()
                    writeCharFff5 = null
                    notifyCharFff4 = null
                    isDescriptorReady = false
                    isBleWriteBusy = false
                    bleWriteQueue.clear()
                    notifyState(false, "Đã ngắt BLE")
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                try {
                    gatt.discoverServices()
                } catch (_: Exception) {}
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) return

                // Tìm Service FFF0 và Characteristic FFF5 (Write) + FFF4 (Notify/Handshake)
                val fff0Service = gatt.getService(UUID_SERVICE_FFF0)
                if (fff0Service != null) {
                    writeCharFff5 = fff0Service.getCharacteristic(UUID_CHAR_FFF5)
                    notifyCharFff4 = fff0Service.getCharacteristic(UUID_CHAR_FFF4)
                }

                // Nếu firmware có UUID dịch vụ khác, tự động dò Characteristic có đuôi fff5/fff4 hoặc có quyền Write
                if (writeCharFff5 == null) {
                    for (service in gatt.services) {
                        for (ch in service.characteristics) {
                            val uuidStr = ch.uuid.toString().lowercase()
                            if (uuidStr.contains("fff5")) {
                                writeCharFff5 = ch
                            } else if (uuidStr.contains("fff4")) {
                                notifyCharFff4 = ch
                            }
                        }
                    }
                }
                if (writeCharFff5 == null) {
                    for (service in gatt.services) {
                        for (ch in service.characteristics) {
                            val props = ch.properties
                            if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ||
                                (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                            ) {
                                writeCharFff5 = ch
                                break
                            }
                        }
                        if (writeCharFff5 != null) break
                    }
                }

                // Bật Notification trên FFF4 (không ghi trực tiếp vào FFF4 vì FFF4 là Notify-Only trên RS 3 Mini)
                armDjiBleCharacteristics(gatt)
                startTxLoop()
                notifyState(true, "Gimbal: $connectedDeviceName")
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                Log.i(TAG, "onDescriptorWrite status=$status uuid=${descriptor.uuid}")
                isDescriptorReady = true
                isBleWriteBusy = false
                // Gửi gói kích hoạt / bắt tay BLE chuẩn DJI qua FFF5 sau khi CCCD hoàn tất
                sendDjiBleHandshake()
                drainBleWriteQueue()
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                isBleWriteBusy = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "onCharacteristicWrite status=$status uuid=${characteristic.uuid}")
                }
                drainBleWriteQueue()
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                @Suppress("DEPRECATION")
                val data = characteristic.value ?: return
                handleIncomingBleNotification(data)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleIncomingBleNotification(value)
            }
        }

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    private fun armDjiBleCharacteristics(gatt: BluetoothGatt) {
        try {
            notifyCharFff4?.let { ch4 ->
                gatt.setCharacteristicNotification(ch4, true)
                val desc = ch4.getDescriptor(UUID_CCCD)
                if (desc != null) {
                    isBleWriteBusy = true
                    lastBleWriteStartMs = System.currentTimeMillis()
                    @Suppress("DEPRECATION")
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(desc)
                } else {
                    isDescriptorReady = true
                    sendDjiBleHandshake()
                }
            } ?: run {
                isDescriptorReady = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "armDjiBleCharacteristics error: ${e.message}")
            isDescriptorReady = true
            isBleWriteBusy = false
        }
    }

    /**
     * Xử lý gói tin Notification nhận từ FFF4 của Gimbal RS 3 Mini
     */
    private fun handleIncomingBleNotification(data: ByteArray) {
        if (data.isEmpty()) return
        rxPacketCount++
        lastRxTimestampMs = System.currentTimeMillis()

        // Log 15 gói đầu tiên và mỗi 40 gói tiếp theo để chẩn đoán khung truyền thực tế của RS 3 Mini
        if (rxPacketCount <= 15 || rxPacketCount % 40 == 0) {
            val hex = data.joinToString(" ") { String.format("%02X", it) }
            Log.i(TAG, "RX FFF4 (#$rxPacketCount, ${data.size}B): $hex")
        }

        // Nếu nhận được khung DUML 0x55 từ Gimbal: tự động học CRC seed & địa chỉ Sender/Receiver thực tế
        if (data.size >= 13 && data[0] == 0x55.toByte()) {
            val hdrCrc = data[3].toInt() and 0xFF
            val c77 = calcDjiCrc8WithSeed(data, 3, 0x77).toInt() and 0xFF
            val cEE = calcDjiCrc8WithSeed(data, 3, 0xEE).toInt() and 0xFF
            if (hdrCrc == c77 && detectedCrc8Init != 0x77) {
                detectedCrc8Init = 0x77
                Log.i(TAG, "Auto-detected CRC8 seed = 0x77")
            } else if (hdrCrc == cEE && detectedCrc8Init != 0xEE) {
                detectedCrc8Init = 0xEE
                Log.i(TAG, "Auto-detected CRC8 seed = 0xEE")
            }

            val sender = data[4].toInt() and 0xFF
            val receiver = data[5].toInt() and 0xFF
            if (rxPacketCount <= 5) {
                Log.i(TAG, "DUML RX sender=0x${String.format("%02X", sender)} receiver=0x${String.format("%02X", receiver)} cmdSet=0x${String.format("%02X", data[9])} cmdId=0x${String.format("%02X", data[10])}")
            }
        }

        // Cập nhật trạng thái UI khi nhận gói đầu tiên
        if (rxPacketCount == 1) {
            notifyState(true, "Đã kết nối ($connectedDeviceName)")
        }
    }

    /**
     * Gửi gói bắt tay / xác thực BLE chuẩn DJI qua FFF5
     */
    private fun sendDjiBleHandshake() {
        // Gửi yêu cầu lấy phiên bản / xác thực PIN mặc định DJI Ronin BLE (CmdSet=0x00 CmdId=0x01)
        enqueueBlePacket(buildDuml55Frame(cmdSet = 0x00, cmdId = 0x01, payload = byteArrayOf()))
    }

    fun disconnect() {
        stopTxLoop()
        bleWriteQueue.clear()
        isBleWriteBusy = false
        isDescriptorReady = false
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
        writeCharFff5 = null
        notifyCharFff4 = null
        notifyState(false, "Chưa kết nối")
    }

    // =========================================================================
    // ĐIỀU KHIỂN GÓC QUAY PAN / TILT, RECENTER & CHẾ ĐỘ FOLLOW (PF / PTF / FPV)
    // =========================================================================

    /**
     * Cập nhật vận tốc xoay trục Pan (-1..+1: Trái/Phải) và Tilt (-1..+1: Xuống/Lên)
     */
    fun setPanTilt(panNorm: Float, tiltNorm: Float) {
        val clampedPan = panNorm.coerceIn(-1f, 1f)
        val clampedTilt = tiltNorm.coerceIn(-1f, 1f)
        val wasMoving = (currentPanNorm != 0f || currentTiltNorm != 0f)
        currentPanNorm = clampedPan
        currentTiltNorm = clampedTilt

        if (clampedPan == 0f && clampedTilt == 0f && wasMoving) {
            // Gửi 5 gói vận tốc = 0 để dừng mượt ngay lập tức
            stopBurstRemaining = 5
        } else if (clampedPan != 0f || clampedTilt != 0f) {
            stopBurstRemaining = 0
        }
    }

    fun setPanDirection(dir: Int) {
        setPanTilt(dir.toFloat().coerceIn(-1f, 1f), currentTiltNorm)
    }

    fun setTiltDirection(dir: Int) {
        setPanTilt(currentPanNorm, dir.toFloat().coerceIn(-1f, 1f))
    }

    /**
     * Đưa Gimbal về vị trí chính giữa (Recenter)
     * Gửi đồng thời các khung lệnh DUML 0x55 tương thích RS 3 Mini:
     * 1. CmdSet=0x0E, CmdId=0x0E (Recenter / Selfie trigger)
     * 2. CmdSet=0x0E, CmdId=0x00 (Position move_to: yaw=0, roll=0, pitch=0)
     * 3. CmdSet=0x04, CmdId=0x4C (Gimbal Home / Recenter)
     */
    fun recenter() {
        currentPanNorm = 0f
        currentTiltNorm = 0f
        stopBurstRemaining = 2
        if (!isConnected) return

        // 1. DJI R-SDK DUML Selfie / Recenter (CmdSet = 0x0E, CmdId = 0x0E, payload = [0x01, 0x01])
        enqueueBlePacket(buildDuml55Frame(cmdSet = 0x0E, cmdId = 0x0E, payload = byteArrayOf(0x01, 0x01)))

        // 2. Handheld Gimbal Position Control (CmdSet = 0x0E, CmdId = 0x00, target 0,0,0, time 1.0s)
        val posPayload = ByteArray(8)
        putInt16LE(posPayload, 0, 0) // yaw = 0
        putInt16LE(posPayload, 2, 0) // roll = 0
        putInt16LE(posPayload, 4, 0) // pitch = 0
        posPayload[6] = 10           // 10 * 0.1s = 1.0s
        posPayload[7] = 0x00         // Absolute control mode
        enqueueBlePacket(buildDuml55Frame(cmdSet = 0x0E, cmdId = 0x00, payload = posPayload))

        // 3. DUML Gimbal Return to Home (CmdSet = 0x04, CmdId = 0x4C, payload = [0x01, 0x00])
        enqueueBlePacket(buildDuml55Frame(cmdSet = 0x04, cmdId = 0x4C, payload = byteArrayOf(0x01, 0x00)))

        // 4. DUML Gimbal Angle Reset (CmdSet = 0x04, CmdId = 0x01, payload = [0,0, 0,0, 0,0, 10,0])
        val anglePayload = ByteArray(8)
        putInt16LE(anglePayload, 0, 0)
        putInt16LE(anglePayload, 2, 0)
        putInt16LE(anglePayload, 4, 0)
        anglePayload[6] = 10
        anglePayload[7] = 0
        enqueueBlePacket(buildDuml55Frame(cmdSet = 0x04, cmdId = 0x01, payload = anglePayload))
    }

    /**
     * Đổi chế độ Follow của Gimbal: PF (Pan Follow), PTF (Pan-Tilt Follow), FPV
     */
    fun setFollowMode(mode: FollowMode) {
        currentFollowMode = mode
        mainHandler.post {
            onFollowModeChanged?.invoke(mode)
        }
        if (!isConnected) return

        val mByte = mode.code.toByte()
        // 1. Gói DUML R-SDK Set Work Mode (CmdSet = 0x0E, CmdId = 0x03)
        enqueueBlePacket(buildDuml55Frame(cmdSet = 0x0E, cmdId = 0x03, payload = byteArrayOf(mByte)))
        // 2. Gói DUML Gimbal Set Mode (CmdSet = 0x04, CmdId = 0x4C)
        enqueueBlePacket(buildDuml55Frame(cmdSet = 0x04, cmdId = 0x4C, payload = byteArrayOf(mByte, 0x00)))
        // 3. Gói DUML Gimbal Set Profile M1/M2/M3 (CmdSet = 0x04, CmdId = 0x10)
        enqueueBlePacket(buildDuml55Frame(cmdSet = 0x04, cmdId = 0x10, payload = byteArrayOf(mByte)))
    }

    private fun startTxLoop() {
        stopTxLoop()
        txExecutor = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleAtFixedRate({
                if (!isConnected || writeCharFff5 == null || !isDescriptorReady) return@scheduleAtFixedRate

                val active = (currentPanNorm != 0f || currentTiltNorm != 0f)
                if (!active && stopBurstRemaining <= 0) return@scheduleAtFixedRate

                if (stopBurstRemaining > 0) {
                    stopBurstRemaining--
                }

                val effPan = if (invertPan) -currentPanNorm else currentPanNorm
                val effTilt = if (invertTilt) -currentTiltNorm else currentTiltNorm

                // Thứ tự trục chuẩn của dòng DJI Ronin RS 3 Mini:
                // Trục 1 (byte 0..1): Yaw (Pan - Xoay Ngang)
                // Trục 2 (byte 2..3): Roll (0)
                // Trục 3 (byte 4..5): Pitch (Tilt - Ngẩng / Cúi)
                val yawSpeed = (effPan * maxDegPerSecTenths).roundToInt().coerceIn(-1800, 1800)
                val pitchSpeed = (effTilt * maxDegPerSecTenths).roundToInt().coerceIn(-1800, 1800)
                val rollSpeed = 0

                // Giới hạn hàng đợi tối đa 4 gói để không bao giờ bị trễ lệnh
                if (bleWriteQueue.size > 4) {
                    bleWriteQueue.clear()
                }

                // 1. Khung DUML R-SDK Speed Control (CmdSet = 0x0E, CmdId = 0x01:
                //    yaw(int16 LE), roll(int16 LE), pitch(int16 LE), ctrl_byte(0x80=speed ctrl))
                val rSdkPayload = ByteArray(7)
                putInt16LE(rSdkPayload, 0, yawSpeed)
                putInt16LE(rSdkPayload, 2, rollSpeed)
                putInt16LE(rSdkPayload, 4, pitchSpeed)
                rSdkPayload[6] = if (active) 0x80.toByte() else 0x00.toByte()
                enqueueBlePacket(buildDuml55Frame(cmdSet = 0x0E, cmdId = 0x01, payload = rSdkPayload))

                // 2. Khung DUML Gimbal Ext Speed Control (CmdSet = 0x04, CmdId = 0x0C:
                //    yaw(int16 LE), roll(int16 LE), pitch(int16 LE), flags(0x81=active))
                val dumlPayload = ByteArray(7)
                putInt16LE(dumlPayload, 0, yawSpeed)
                putInt16LE(dumlPayload, 2, rollSpeed)
                putInt16LE(dumlPayload, 4, pitchSpeed)
                dumlPayload[6] = if (active) 0x81.toByte() else 0x00.toByte()
                enqueueBlePacket(buildDuml55Frame(cmdSet = 0x04, cmdId = 0x0C, payload = dumlPayload))
            }, 0, 50, TimeUnit.MILLISECONDS)
        }
    }

    private fun stopTxLoop() {
        txExecutor?.shutdownNow()
        txExecutor = null
    }

    private fun enqueueBlePacket(packet: ByteArray) {
        if (!isConnected) return
        bleWriteQueue.offer(packet)
        drainBleWriteQueue()
    }

    @Synchronized
    private fun drainBleWriteQueue() {
        val gatt = bluetoothGatt ?: return
        val ch = writeCharFff5 ?: return
        if (!isDescriptorReady) return

        // Giải phóng khóa nếu một lệnh ghi trước đó bị treo quá 120ms
        val now = System.currentTimeMillis()
        if (isBleWriteBusy && (now - lastBleWriteStartMs) > 120L) {
            isBleWriteBusy = false
        }
        if (isBleWriteBusy) return

        val nextPacket = bleWriteQueue.poll() ?: return
        try {
            isBleWriteBusy = true
            lastBleWriteStartMs = now
            val props = ch.properties
            ch.writeType = if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            @Suppress("DEPRECATION")
            ch.value = nextPacket
            @Suppress("DEPRECATION")
            val ok = gatt.writeCharacteristic(ch)
            if (ok) {
                txPacketCount++
            } else {
                isBleWriteBusy = false
            }
        } catch (e: Exception) {
            isBleWriteBusy = false
            Log.e(TAG, "drainBleWriteQueue error: ${e.message}")
        }
    }

    // =========================================================================
    // ĐÓNG GÓI KHUNG TRUYỀN DJI DUML (0x55) & DJI R SDK (0xAA) + CRC8 / CRC16
    // =========================================================================

    /**
     * Khung DUML 0x55:
     * [0x55] [len_lo] [ver<<2 | len_hi] [crc8] [sender] [receiver] [seq: 2B LE] [flags] [cmdSet] [cmdId] [payload] [crc16: 2B LE]
     */
    private fun buildDuml55Frame(cmdSet: Int, cmdId: Int, payload: ByteArray): ByteArray {
        val totalLen = 13 + payload.size
        val buf = ByteArray(totalLen)
        buf[0] = 0x55.toByte()
        buf[1] = (totalLen and 0xFF).toByte()
        buf[2] = (((1 shl 2) or ((totalLen shr 8) and 0x03)) and 0xFF).toByte() // ver = 1 -> 0x04
        buf[3] = calcDjiCrc8(buf, 3)

        // Sender = App (0x02), Receiver = Gimbal (0x04)
        buf[4] = (dumlAppId and 0xFF).toByte()
        buf[5] = (dumlGimbalId and 0xFF).toByte()

        val seq = nextSeq()
        buf[6] = (seq and 0xFF).toByte()
        buf[7] = ((seq shr 8) and 0xFF).toByte()

        buf[8] = 0x00.toByte() // No ACK required for low-latency streaming
        buf[9] = (cmdSet and 0xFF).toByte()
        buf[10] = (cmdId and 0xFF).toByte()

        System.arraycopy(payload, 0, buf, 11, payload.size)

        val crc16 = calcDjiCrc16(buf, totalLen - 2)
        buf[totalLen - 2] = (crc16 and 0xFF).toByte()
        buf[totalLen - 1] = ((crc16 shr 8) and 0xFF).toByte()
        return buf
    }

    /**
     * Khung DJI R SDK 0xAA:
     * [0xAA] [len_lo] [ver<<2 | len_hi] [crc8] [seq: 2B LE] [0x00] [0x02] [cmdSet] [cmdId] [payload] [crc16]
     */
    private fun buildRoninAAFrame(cmdSet: Int, cmdId: Int, payload: ByteArray): ByteArray {
        val totalLen = 12 + payload.size
        val buf = ByteArray(totalLen)
        buf[0] = 0xAA.toByte()
        buf[1] = (totalLen and 0xFF).toByte()
        buf[2] = 0x04.toByte()
        buf[3] = calcDjiCrc8(buf, 3)

        val seq = nextSeq()
        buf[4] = (seq and 0xFF).toByte()
        buf[5] = ((seq shr 8) and 0xFF).toByte()
        buf[6] = 0x00.toByte()
        buf[7] = 0x02.toByte()
        buf[8] = (cmdSet and 0xFF).toByte()
        buf[9] = (cmdId and 0xFF).toByte()

        System.arraycopy(payload, 0, buf, 10, payload.size)

        val crc16 = calcDjiCrc16(buf, totalLen - 2)
        buf[totalLen - 2] = (crc16 and 0xFF).toByte()
        buf[totalLen - 1] = ((crc16 shr 8) and 0xFF).toByte()
        return buf
    }

    private fun nextSeq(): Int {
        seqCounter = (seqCounter + 1) and 0xFFFF
        return seqCounter
    }

    private fun putInt16LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun calcDjiCrc8(data: ByteArray, len: Int): Byte {
        return calcDjiCrc8WithSeed(data, len, detectedCrc8Init)
    }

    private fun calcDjiCrc8WithSeed(data: ByteArray, len: Int, seed: Int): Byte {
        var crc = seed and 0xFF
        for (i in 0 until len) {
            crc = crc xor (data[i].toInt() and 0xFF)
            for (bit in 0 until 8) {
                crc = if ((crc and 0x01) != 0) {
                    (crc ushr 1) xor 0x8C
                } else {
                    crc ushr 1
                }
            }
        }
        return (crc and 0xFF).toByte()
    }

    private fun calcDjiCrc16(data: ByteArray, len: Int): Int {
        var crc = detectedCrc16Init and 0xFFFF
        for (i in 0 until len) {
            crc = crc xor (data[i].toInt() and 0xFF)
            for (bit in 0 until 8) {
                crc = if ((crc and 0x0001) != 0) {
                    (crc ushr 1) xor 0x8408
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }

    // =========================================================================
    // GỬI LỆNH ĐIỀU KHIỂN GIMBAL QUA WIFI TỪ THIẾT BỊ ĐIỀU KHIỂN -> THIẾT BỊ STREAMING
    // =========================================================================

    /**
     * Gửi lệnh từ thiết bị Điều khiển (Controller) sang thiết bị Streaming qua cả 2 kênh:
     * 1. UDP Broadcast cổng 5005 (ButtonSyncManager) -> Độ trễ cực thấp (< 2ms)
     * 2. HTTP GET cổng 8080 (/gimbal/...) tới IP máy Streaming -> Đảm bảo tin cậy
     */
    fun sendRemoteGimbalCommand(
        context: Context,
        btnId: String,
        action: String,
        extra: String? = null,
        httpEndpoint: String? = null
    ) {
        // 1. Phát qua UDP 5005
        ButtonSyncManager.sendButtonEvent(btnId, action, extra)

        // 2. Nếu chính máy này đang kết nối BLE với Gimbal thì thực thi trực tiếp luôn
        if (isConnected) {
            handleIncomingNetworkCommand(btnId, action, extra)
            return
        }

        // 3. Gửi thêm qua HTTP cổng 8080 tới IP Camera Streaming đã lưu
        if (!httpEndpoint.isNullOrBlank()) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val streamIp = prefs.getString("pref_remote_fpv_ip", "192.168.2.80")?.trim() ?: "192.168.2.80"
            if (streamIp.isNotEmpty()) {
                httpExecutor.execute {
                    try {
                        val host = streamIp.substringBefore(":")
                        val url = URL("http://$host:8080/$httpEndpoint")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 800
                            readTimeout = 800
                        }
                        conn.responseCode
                        conn.disconnect()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Xử lý lệnh Gimbal nhận được từ mạng LAN (UDP 5005 hoặc HTTP 8080) trên máy Streaming
     */
    fun handleIncomingNetworkCommand(btnId: String, action: String, extra: String? = null) {
        val isDown = action.equals("DOWN", ignoreCase = true)
        when (btnId.uppercase()) {
            "GIMBAL_UP" -> setTiltDirection(if (isDown) 1 else 0)
            "GIMBAL_DOWN" -> setTiltDirection(if (isDown) -1 else 0)
            "GIMBAL_LEFT" -> setPanDirection(if (isDown) -1 else 0)
            "GIMBAL_RIGHT" -> setPanDirection(if (isDown) 1 else 0)
            "GIMBAL_RECENTER" -> recenter()
            "GIMBAL_MODE" -> {
                val mode = if (!extra.isNullOrBlank()) FollowMode.fromLabel(extra) else currentFollowMode.next()
                setFollowMode(mode)
            }
        }
    }
}
