package com.robotcamera.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.min

/**
 * Quản lý giao tiếp mạng UDP với Robot Mecanum
 * Giữ nguyên 100% cấu trúc gói tin, thông số và logic backend từ Form1.cs (C# PC Monitor)
 */
class RobotUdpManager {

    companion object {
        private const val TAG = "RobotUdpManager"
        const val DEFAULT_IP = "192.168.2.93"
        const val DEFAULT_PORT = 5001
        const val SEND_INTERVAL_MS = 25L // Chu kỳ gửi 25ms (40Hz) chuẩn backend
    }

    data class RobotTelemetry(
        val seq: Int,
        val timeSec: Double,
        val xd: Float, val x: Float,
        val yd: Float, val y: Float,
        val thetad: Float, val theta: Float, // đơn vị radian
        val vxd: Float, val vx: Float,
        val vyd: Float, val vy: Float,
        val wd: Float, val w: Float,
        val w1d: Float, val w1: Float, // RL (Rear Left)
        val w2d: Float, val w2: Float, // FL (Front Left)
        val w3d: Float, val w3: Float, // RR (Rear Right)
        val w4d: Float, val w4: Float  // FR (Front Right)
    ) {
        val thetaDeg: Float get() = (theta * 180.0 / PI).toFloat()
        val thetadDeg: Float get() = (thetad * 180.0 / PI).toFloat()
    }

    /**
     * Chế độ phân quyền điều khiển khi nhiều thiết bị cùng kết nối
     */
    enum class ControlPermission {
        AUTO,   // Tự động: Ai nhấn nút người đó lái, nhả nút tự ngắt gửi 0 để nhường máy khác
        MASTER, // Độc quyền: Giữ quyền lái chính, khóa các thiết bị khác sang Chỉ xem
        VIEWER  // Chỉ xem: Chỉ nhận Telemetry hiển thị, khóa chiều gửi lệnh điều khiển
    }

    // Callbacks
    var onTelemetryReceived: ((RobotTelemetry) -> Unit)? = null
    var onConnectionStateChanged: ((isConnected: Boolean, status: String) -> Unit)? = null
    var onPermissionChanged: ((ControlPermission, String) -> Unit)? = null

    @Volatile
    var permissionMode: ControlPermission = ControlPermission.AUTO
        private set

    // Cờ đánh dấu thiết bị này đang chủ động phát lệnh điều khiển (nhấn giữ nút hoặc chạy tự hành)
    @Volatile
    var isActivelyDriving: Boolean = false
        private set

    // Số gói tin gửi nốt khi nhả nút (Stop = 0) hoặc lệnh 1 lần (One-shot) trước khi về trạng thái nghỉ (Idle)
    @Volatile
    private var burstPacketsRemaining: Int = 0

    // Network components
    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var targetPort: Int = DEFAULT_PORT

    @Volatile
    var isConnected: Boolean = false
        private set

    private var sendExecutor: ScheduledExecutorService? = null
    private var receiveThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Bộ đếm Sequence gửi đi & nhận về
    private var txSeqCount: Int = 0
    private var expectedSeq: Int = 0
    private var isFirstPacket: Boolean = true
    private var startTimeMillis: Long = 0L

    // Trạng thái vận hành Robot (state_run)
    @Volatile
    var stateRun: Byte = 0x01 // Mặc định 0x01: Stop

    // 10 biến Float gửi xuống Robot
    @Volatile var setX: Float = 0.0f
    @Volatile var setY: Float = 0.0f
    @Volatile var setTheta: Float = 0.0f
    @Volatile var setVx: Float = 0.0f
    @Volatile var setVy: Float = 0.0f
    @Volatile var setW: Float = 0.0f
    @Volatile var setW1: Float = 0.0f
    @Volatile var setW2: Float = 0.0f
    @Volatile var setW3: Float = 0.0f
    @Volatile var setW4: Float = 0.0f

    var manualSpeed: Float = 10.0f // Tốc độ điều khiển thủ công 10 rad/s chuẩn backend

    // Trạng thái kết hợp Joystick 360° + Xoay Trái/Phải (hỗ trợ vừa chạy vừa xoay để Drift)
    @Volatile private var activeJoyX: Float = 0.0f
    @Volatile private var activeJoyY: Float = 0.0f
    @Volatile private var isTurnLeftActive: Boolean = false
    @Volatile private var isTurnRightActive: Boolean = false

    /**
     * Bắt đầu kết nối UDP tới Robot
     */
    fun connect(ip: String, port: Int) {
        if (isConnected) disconnect()

        Executors.newSingleThreadExecutor().execute {
            try {
                targetAddress = InetAddress.getByName(ip.trim())
                targetPort = port

                socket = DatagramSocket() // Hệ điều hành tự cấp port ngẫu nhiên
                isConnected = true
                txSeqCount = 0
                expectedSeq = 0
                isFirstPacket = true
                startTimeMillis = System.currentTimeMillis()

                notifyState(true, "Đang gửi Wake-up...")

                // Gửi gói tin Wake-up [0xAA, 0xFF, 0x00, 0x00, 0x55]
                val wakeUp = byteArrayOf(0xAA.toByte(), 0xFF.toByte(), 0x00, 0x00, 0x55)
                sendRawPacket(wakeUp)

                notifyState(true, "Đã kết nối UDP tới $ip:$port")

                // Khởi động luồng nhận dữ liệu
                startReceiveLoop()

                // Khởi động luồng gửi định kỳ 25ms
                startSendLoop()

            } catch (e: Exception) {
                Log.e(TAG, "Lỗi kết nối UDP: ${e.message}")
                disconnect()
                notifyState(false, "Lỗi kết nối: ${e.message}")
            }
        }
    }

    /**
     * Ngắt kết nối UDP
     */
    fun disconnect() {
        isConnected = false
        sendExecutor?.shutdownNow()
        sendExecutor = null

        receiveThread?.interrupt()
        receiveThread = null

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null

        notifyState(false, "Đã ngắt kết nối")
    }

    // =========================================================================
    // LUỒNG GỬI DỮ LIỆU ĐỊNH KỲ 25MS (40HZ) - CƠ CHẾ ACTIVE-ONLY TX CHỐNG TRANH QUYỀN
    // =========================================================================

    private fun startSendLoop() {
        sendExecutor?.shutdownNow()
        sendExecutor = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleAtFixedRate({
                if (!isConnected || socket == null || targetAddress == null) return@scheduleAtFixedRate

                // Nếu đang ở chế độ CHỈ XEM (VIEWER) -> Không gửi gói điều khiển 47 bytes
                if (permissionMode == ControlPermission.VIEWER && burstPacketsRemaining <= 0) {
                    return@scheduleAtFixedRate
                }

                // CHỈ gửi gói 47 bytes khi đang nhấn nút điều khiển (isActivelyDriving)
                // hoặc đang gửi nốt cụm gói Stop/One-shot (burstPacketsRemaining > 0).
                // Khi thiết bị ở trạng thái nghỉ (Idle), tuyệt đối KHÔNG spam tốc độ = 0 để không chặn thiết bị khác!
                val shouldSend = isActivelyDriving || burstPacketsRemaining > 0
                if (!shouldSend) {
                    return@scheduleAtFixedRate
                }

                if (burstPacketsRemaining > 0) {
                    burstPacketsRemaining--
                }

                try {
                    val frame = ByteArray(47)

                    // 1. Header & SEQ & ID
                    frame[0] = 0xAA.toByte()
                    frame[1] = (txSeqCount and 0xFF).toByte()
                    frame[2] = ((txSeqCount shr 8) and 0xFF).toByte()
                    frame[3] = stateRun

                    // 2. Data: 10 biến float Little Endian (40 bytes)
                    putFloatLE(frame, 4, setX)
                    putFloatLE(frame, 8, setY)
                    putFloatLE(frame, 12, setTheta)
                    putFloatLE(frame, 16, setVx)
                    putFloatLE(frame, 20, setVy)
                    putFloatLE(frame, 24, setW)
                    putFloatLE(frame, 28, setW1)
                    putFloatLE(frame, 32, setW2)
                    putFloatLE(frame, 36, setW3)
                    putFloatLE(frame, 40, setW4)

                    // 3. Tính CRC16 trên 44 byte đầu (Từ index 0 đến 43)
                    val crc = calculateCRC16(frame, 44)
                    frame[44] = (crc and 0xFF).toByte()
                    frame[45] = ((crc shr 8) and 0xFF).toByte()

                    // 4. Footer
                    frame[46] = 0x55.toByte()

                    val packet = DatagramPacket(frame, frame.size, targetAddress, targetPort)
                    socket?.send(packet)
                    txSeqCount = (txSeqCount + 1) and 0xFFFF

                    // Nếu là lệnh camera 1 lần (0x07..0x0C) đã gửi xong burst -> trả stateRun về 0x01
                    if (!isActivelyDriving && burstPacketsRemaining == 0 && stateRun >= 0x07) {
                        stateRun = 0x01
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi gửi frame UDP: ${e.message}")
                }
            }, 0, SEND_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
    }

    // =========================================================================
    // LUỒNG NHẬN DỮ LIỆU ĐỊNH KỲ (RX 86 BYTES) - GIỮ NGUYÊN FORM1.CS
    // =========================================================================

    private fun startReceiveLoop() {
        receiveThread = Thread {
            val buffer = ByteArray(256)
            while (isConnected && socket != null && !Thread.currentThread().isInterrupted) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val len = packet.length
                    val data = packet.data

                    // KIỂM TRA FRAME GỬI TỪ RASPBERRY PI (86 bytes)
                    if (len == 86 && (data[0].toInt() and 0xFF) == 0xAA && (data[85].toInt() and 0xFF) == 0x55) {
                        // 1. Kiểm tra CRC16 (Tính trên 83 byte đầu)
                        val receivedCrc = getUShortLE(data, 83)
                        val calculatedCrc = calculateCRC16(data, 83)
                        if (receivedCrc != calculatedCrc) continue

                        // 2. Lấy số Sequence
                        val seq = getUShortLE(data, 1)
                        if (isFirstPacket) {
                            expectedSeq = seq
                            isFirstPacket = false
                        }

                        // 3. Xử lý Sequence chống mất gói
                        var diff = seq - expectedSeq
                        if (diff < -32768) diff += 65536 else if (diff > 32768) diff -= 65536

                        if (diff > 0) {
                            val missingCount = min(diff, 256)
                            for (i in 0 until missingCount) {
                                val missingSeq = (expectedSeq + i) % 65536
                                requestMissingPacket(missingSeq)
                            }
                            expectedSeq = (seq + 1) % 65536
                        } else if (diff == 0) {
                            expectedSeq = (seq + 1) % 65536
                        }

                        // 4. Bóc tách 20 số Float cho Mecanum
                        val floats = FloatArray(20)
                        for (i in 0 until 20) {
                            val offset = 3 + (i * 4)
                            floats[i] = getFloatLE(data, offset)
                        }

                        val elapsedSec = (System.currentTimeMillis() - startTimeMillis) / 1000.0

                        val telemetry = RobotTelemetry(
                            seq = seq,
                            timeSec = elapsedSec,
                            xd = floats[0],
                            x = floats[1],
                            yd = floats[2],
                            y = floats[3],
                            thetad = floats[4],
                            theta = floats[5],
                            vxd = floats[6],
                            vx = floats[7],
                            vyd = floats[8],
                            vy = floats[9],
                            wd = floats[10],
                            w = floats[11],
                            w1d = floats[12],
                            w1 = floats[13],
                            w2d = floats[14],
                            w2 = floats[15],
                            w3d = floats[16],
                            w3 = floats[17],
                            w4d = floats[18],
                            w4 = floats[19]
                        )

                        mainHandler.post {
                            onTelemetryReceived?.invoke(telemetry)
                        }
                    }
                } catch (_: Exception) {
                    if (!isConnected) break
                }
            }
        }.apply {
            name = "RobotUdpReceiver"
            start()
        }
    }

    private fun requestMissingPacket(missingSeq: Int) {
        try {
            val reqPacket = ByteArray(5)
            reqPacket[0] = 0xAA.toByte()
            reqPacket[1] = 0xFF.toByte()
            reqPacket[2] = (missingSeq and 0xFF).toByte()
            reqPacket[3] = ((missingSeq shr 8) and 0xFF).toByte()
            reqPacket[4] = 0x55.toByte()

            sendRawPacket(reqPacket)
        } catch (_: Exception) {}
    }

    private fun sendRawPacket(bytes: ByteArray) {
        try {
            if (socket != null && targetAddress != null) {
                val packet = DatagramPacket(bytes, bytes.size, targetAddress, targetPort)
                socket?.send(packet)
            }
        } catch (_: Exception) {}
    }

    private fun notifyState(connected: Boolean, message: String) {
        mainHandler.post {
            onConnectionStateChanged?.invoke(connected, message)
        }
    }

    // =========================================================================
    // QUẢN LÝ PHÂN QUYỀN THIẾT BỊ & NHƯỜNG QUYỀN TỰ ĐỘNG (MULTI-DEVICE ARBITRATION)
    // =========================================================================

    fun setPermission(mode: ControlPermission, reason: String = "") {
        permissionMode = mode
        if (mode == ControlPermission.VIEWER) {
            isActivelyDriving = false
            burstPacketsRemaining = 0
            stateRun = 0x01
            setW1 = 0.0f; setW2 = 0.0f; setW3 = 0.0f; setW4 = 0.0f
        }
        mainHandler.post {
            onPermissionChanged?.invoke(mode, reason)
        }
    }

    fun cyclePermissionMode(): ControlPermission {
        val prev = permissionMode
        val next = when (prev) {
            ControlPermission.AUTO -> ControlPermission.MASTER
            ControlPermission.MASTER -> ControlPermission.VIEWER
            ControlPermission.VIEWER -> ControlPermission.AUTO
        }
        setPermission(next)
        when (next) {
            ControlPermission.MASTER -> ButtonSyncManager.sendButtonEvent("PERMISSION", "MASTER", "TABLET")
            else -> if (prev == ControlPermission.MASTER) {
                ButtonSyncManager.sendButtonEvent("PERMISSION", "RELEASE", "TABLET")
            }
        }
        return next
    }

    /**
     * Khi thiết bị khác trên mạng LAN đang điều khiển, thiết bị này lập tức nhường quyền
     * (ngắt gửi gói 47 bytes tốc độ = 0 để không chặn thiết bị kia).
     */
    fun yieldControlToRemote() {
        isActivelyDriving = false
        burstPacketsRemaining = 0
        stateRun = 0x01
        setW1 = 0.0f
        setW2 = 0.0f
        setW3 = 0.0f
        setW4 = 0.0f
    }

    // =========================================================================
    // HÀM ĐIỀU KHIỂN ROBOT THỦ CÔNG & TỰ ĐỘNG - KHỚP 100% FORM1.CS
    // =========================================================================

    // --- ĐIỀU KHIỂN BẰNG NÚT BẤM (MANUAL D-PAD) ---

    fun moveForward(isPressed: Boolean) {
        if (permissionMode == ControlPermission.VIEWER) return
        if (isPressed) {
            stateRun = 0x03
            setW1 = manualSpeed
            setW2 = manualSpeed
            setW3 = manualSpeed
            setW4 = manualSpeed
            isActivelyDriving = true
            burstPacketsRemaining = 0
        } else {
            stopMotors()
        }
    }

    fun moveBackward(isPressed: Boolean) {
        if (permissionMode == ControlPermission.VIEWER) return
        if (isPressed) {
            stateRun = 0x03
            setW1 = -manualSpeed
            setW2 = -manualSpeed
            setW3 = -manualSpeed
            setW4 = -manualSpeed
            isActivelyDriving = true
            burstPacketsRemaining = 0
        } else {
            stopMotors()
        }
    }

    fun strafeLeft(isPressed: Boolean) {
        if (permissionMode == ControlPermission.VIEWER) return
        if (isPressed) {
            stateRun = 0x03
            setW1 = -manualSpeed
            setW2 = manualSpeed
            setW3 = manualSpeed
            setW4 = -manualSpeed
            isActivelyDriving = true
            burstPacketsRemaining = 0
        } else {
            stopMotors()
        }
    }

    fun strafeRight(isPressed: Boolean) {
        if (permissionMode == ControlPermission.VIEWER) return
        if (isPressed) {
            stateRun = 0x03
            setW1 = manualSpeed
            setW2 = -manualSpeed
            setW3 = -manualSpeed
            setW4 = manualSpeed
            isActivelyDriving = true
            burstPacketsRemaining = 0
        } else {
            stopMotors()
        }
    }

    fun turnLeft(isPressed: Boolean) {
        if (permissionMode == ControlPermission.VIEWER) return
        isTurnLeftActive = isPressed
        updateCombinedManualDrive()
    }

    fun turnRight(isPressed: Boolean) {
        if (permissionMode == ControlPermission.VIEWER) return
        isTurnRightActive = isPressed
        updateCombinedManualDrive()
    }

    /**
     * Điều khiển Mecanum đa hướng mượt mà bằng Joystick 360° (kết hợp đồng thời với nút Xoay để Drift):
     * @param nx Trục ngang (-1.0 = Dạt Trái, +1.0 = Dạt Phải)
     * @param ny Trục dọc   (-1.0 = Lùi,      +1.0 = Tiến)
     */
    fun driveJoystick(nx: Float, ny: Float) {
        if (permissionMode == ControlPermission.VIEWER) return
        val mag = kotlin.math.hypot(nx.toDouble(), ny.toDouble()).toFloat()
        if (mag < 0.12f) {
            activeJoyX = 0.0f
            activeJoyY = 0.0f
        } else {
            activeJoyX = nx.coerceIn(-1f, 1f)
            activeJoyY = ny.coerceIn(-1f, 1f)
        }
        updateCombinedManualDrive()
    }

    /**
     * Trộn động học 4 bánh Mecanum từ đồng thời Joystick (activeJoyX, activeJoyY) + Xoay (isTurnLeftActive / isTurnRightActive)
     * giúp xe vừa tiến/lùi/dạt ngang vừa xoay thân xe để Drift mượt mà.
     */
    private fun updateCombinedManualDrive() {
        if (permissionMode == ControlPermission.VIEWER) return

        val tx = activeJoyX
        val ty = activeJoyY
        val joyMag = kotlin.math.hypot(tx.toDouble(), ty.toDouble()).toFloat()
        val hasJoy = joyMag >= 0.12f

        val turnDir = when {
            isTurnLeftActive && !isTurnRightActive -> -1.0f
            isTurnRightActive && !isTurnLeftActive -> 1.0f
            else -> 0.0f
        }
        val hasTurn = turnDir != 0.0f

        if (!hasJoy && !hasTurn) {
            stopMotors()
            return
        }

        // Thành phần tịnh tiến từ Joystick:
        // W1 (RL) & W4 (FR) = ty + tx
        // W2 (FL) & W3 (RR) = ty - tx
        val t1 = if (hasJoy) (ty + tx) else 0.0f
        val t2 = if (hasJoy) (ty - tx) else 0.0f
        val t3 = if (hasJoy) (ty - tx) else 0.0f
        val t4 = if (hasJoy) (ty + tx) else 0.0f

        // Thành phần xoay từ 2 nút Xoay Trái (-1) / Xoay Phải (+1):
        // Xoay Trái (-1): W1=-, W2=-, W3=+, W4=+
        // Xoay Phải (+1): W1=+, W2=+, W3=-, W4=-
        // Khi vừa đẩy Joystick vừa nhấn Xoay (Drift), trộn hệ số xoay 0.85f để xe vừa lướt vừa quăng đuôi mượt mà
        val rotWeight = if (hasJoy) 0.85f else 1.0f
        val r1 = turnDir * rotWeight
        val r2 = turnDir * rotWeight
        val r3 = -turnDir * rotWeight
        val r4 = -turnDir * rotWeight

        val raw1 = t1 + r1
        val raw2 = t2 + r2
        val raw3 = t3 + r3
        val raw4 = t4 + r4

        val peak = maxOf(
            1.0f,
            kotlin.math.abs(raw1),
            kotlin.math.abs(raw2),
            kotlin.math.abs(raw3),
            kotlin.math.abs(raw4)
        )

        stateRun = 0x03
        setW1 = (raw1 / peak) * manualSpeed
        setW2 = (raw2 / peak) * manualSpeed
        setW3 = (raw3 / peak) * manualSpeed
        setW4 = (raw4 / peak) * manualSpeed
        isActivelyDriving = true
        burstPacketsRemaining = 0
    }

    fun stopMotors() {
        activeJoyX = 0.0f
        activeJoyY = 0.0f
        isTurnLeftActive = false
        isTurnRightActive = false
        stateRun = 0x01
        setW1 = 0.0f
        setW2 = 0.0f
        setW3 = 0.0f
        setW4 = 0.0f
        isActivelyDriving = false
        // Chỉ gửi 8 gói Stop (200ms) để xe dừng hẳn, sau đó tự động ngắt gửi để nhường quyền cho máy khác
        burstPacketsRemaining = 8
    }

    // --- CÀI ĐẶT TỐC ĐỘ BÁNH XE (SET WHEEL VELOCITY) ---
    fun setWheelVelocities(w1: Float, w2: Float, w3: Float, w4: Float) {
        if (permissionMode == ControlPermission.VIEWER) return
        if (w1 == 0f && w2 == 0f && w3 == 0f && w4 == 0f) {
            stopMotors()
            return
        }
        stateRun = 0x02
        setW1 = w1
        setW2 = w2
        setW3 = w3
        setW4 = w4
        isActivelyDriving = true
        burstPacketsRemaining = 0
    }

    fun resetWheelVelocities() {
        stopMotors()
    }

    // --- CÁC CHẾ ĐỘ TỰ HÀNH & ĐIỀU HÀNH ---

    fun circleLine() {
        if (permissionMode == ControlPermission.VIEWER) return
        stateRun = 0x04
        isActivelyDriving = true
        burstPacketsRemaining = 0
    }

    fun returnHome() {
        if (permissionMode == ControlPermission.VIEWER) return
        stateRun = 0x05
        setX = 0.0f
        setY = 0.0f
        setTheta = 0.0f
        isActivelyDriving = true
        burstPacketsRemaining = 0
    }

    fun emergencyStop() {
        // Nút Dừng khẩn cấp luôn được ưu tiên thực thi dù ở chế độ nào
        stateRun = 0x01
        setX = 0.0f
        setY = 0.0f
        setTheta = 0.0f
        setVx = 0.0f
        setVy = 0.0f
        setW = 0.0f
        setW1 = 0.0f
        setW2 = 0.0f
        setW3 = 0.0f
        setW4 = 0.0f
        isActivelyDriving = false
        burstPacketsRemaining = 10
    }

    fun setRobotValues(vx: Float, vy: Float, w: Float, x: Float, y: Float, thetaDeg: Float) {
        if (permissionMode == ControlPermission.VIEWER) return
        setVx = vx
        setVy = vy
        setW = w
        setX = x
        setY = y
        setTheta = (thetaDeg * PI / 180.0).toFloat()
        stateRun = 0x06
        isActivelyDriving = true
        burstPacketsRemaining = 0
    }

    fun resetRobotValues() {
        setVx = 0.0f
        setVy = 0.0f
        setW = 0.0f
        setX = 0.0f
        setY = 0.0f
        setTheta = 0.0f
        isActivelyDriving = false
        burstPacketsRemaining = 8
    }

    // --- LỆNH CAMERA GỬI XUỐNG PI ---
    fun sendTakePhoto() {
        if (permissionMode == ControlPermission.VIEWER) return
        stateRun = 0x07
        burstPacketsRemaining = 5
    }
    fun sendStartRecord() {
        if (permissionMode == ControlPermission.VIEWER) return
        stateRun = 0x08
        burstPacketsRemaining = 5
    }
    fun sendStopRecord() {
        if (permissionMode == ControlPermission.VIEWER) return
        stateRun = 0x09
        burstPacketsRemaining = 5
    }
    fun sendSwitchCamera() {
        if (permissionMode == ControlPermission.VIEWER) return
        stateRun = 0x0A
        burstPacketsRemaining = 5
    }
    fun sendStopStream() {
        if (permissionMode == ControlPermission.VIEWER) return
        stateRun = 0x0C
        burstPacketsRemaining = 5
    }

    // =========================================================================
    // CÁC HÀM TIỆN ÍCH CRC16 & BYTE MANIPULATION (CHUẨN MODBUS NHƯ C#)
    // =========================================================================

    private fun calculateCRC16(data: ByteArray, length: Int): Int {
        var crc = 0xFFFF
        for (i in 0 until length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            for (j in 0 until 8) {
                crc = if ((crc and 1) != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }

    private fun putFloatLE(buffer: ByteArray, offset: Int, value: Float) {
        val bits = java.lang.Float.floatToIntBits(value)
        buffer[offset] = (bits and 0xFF).toByte()
        buffer[offset + 1] = ((bits ushr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((bits ushr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((bits ushr 24) and 0xFF).toByte()
    }

    private fun getFloatLE(buffer: ByteArray, offset: Int): Float {
        val bits = (buffer[offset].toInt() and 0xFF) or
                ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
                ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
                ((buffer[offset + 3].toInt() and 0xFF) shl 24)
        val v = java.lang.Float.intBitsToFloat(bits)
        return if (v.isNaN() || v.isInfinite()) 0.0f else v
    }

    private fun getUShortLE(buffer: ByteArray, offset: Int): Int {
        return (buffer[offset].toInt() and 0xFF) or ((buffer[offset + 1].toInt() and 0xFF) shl 8)
    }
}
