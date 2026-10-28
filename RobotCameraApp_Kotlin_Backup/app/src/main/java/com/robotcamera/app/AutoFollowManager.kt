package com.robotcamera.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Trình điều phối Chế độ Tự hành (Auto Follow & Orbit Manager)
 * - Quản lý 2 chế độ chính:
 *   1. MANUAL (Thủ công): Joystick + Nút Xoay Drift
 *   2. AUTO (Tự động):
 *      + Trường hợp 1: Following (Bám theo người giữ cự ly 50 cm ±10 cm)
 *      + Trường hợp 2: Orbit 360° (Vừa xoay 360 độ quanh người vừa giữ cự ly 50 cm ±10 cm)
 *
 * - Tiêu chuẩn an toàn:
 *   + Nút an toàn Hold-to-Run (Dead-man's switch): Chỉ di chuyển khi người điều khiển giữ tay vào nút
 *   + Cắt cứng khẩn cấp (Hard Safety Cutoff): Nếu khoảng cách < 38 cm -> phanh gấp và dừng ngay
 *   + Vùng dừng êm (Deadband): 42 cm đến 58 cm (giữ nguyên cự ly, không bị giật tiến lùi liên tục)
 *   + Mất mục tiêu Timeout: Tự dừng sau 500ms nếu khuất tầm nhìn
 */
class AutoFollowManager(
    private val udpManager: RobotUdpManager
) {

    companion object {
        private const val TAG = "AutoFollowManager"

        // Khoảng cách mục tiêu chuẩn: 50 cm = 0.50 m
        const val TARGET_DISTANCE_M = 0.50f

        // Ngưỡng chết êm (Deadband) xung quanh 50cm (42cm .. 58cm) để tránh robot rung lắc giật cục
        const val DEADBAND_MIN_M = 0.42f
        const val DEADBAND_MAX_M = 0.58f

        // Ngưỡng an toàn tuyệt đối: Dưới 38cm lập tức ngắt động cơ
        const val HARD_SAFETY_STOP_M = 0.38f

        // Vận tốc bánh xe tối đa trong chế độ tự hành (rad/s, an toàn và êm ái)
        const val MAX_AUTO_SPEED = 7.0f
        const val GENTLE_FORWARD_SPEED = 5.0f
        const val GENTLE_REVERSE_SPEED = 4.0f
        const val ORBIT_TANGENTIAL_SPEED = 5.5f

        // Hệ số điều khiển PID
        const val KP_DISTANCE = 8.0f     // Tốc độ phản hồi theo sai số khoảng cách
        const val KP_HEADING = 4.5f      // Tốc độ xoay bám theo góc lệch mục tiêu
        const val MAX_HEADING_SPEED = 5.0f

        const val TARGET_TIMEOUT_MS = 600L
    }

    enum class OperatingMode {
        MANUAL, // Chế độ điều khiển thủ công
        AUTO    // Chế độ tự động
    }

    enum class AutoSubMode {
        FOLLOW_50CM, // Bám theo giữ khoảng cách 50cm
        ORBIT_360    // Xoay 360 độ quanh người giữ khoảng cách 50cm
    }

    enum class AutoState {
        IDLE,           // Chờ lệnh (chưa giữ nút Hold)
        SEARCHING,      // Đang tìm mục tiêu
        LOCKED,         // Đã khóa mục tiêu trong tầm nhìn
        TRACKING,       // Đang bám theo người (50cm)
        ORBITING,       // Đang quay 360° quanh người (50cm)
        SAFETY_STOP,    // Cắt an toàn (quá gần <38cm hoặc khẩn cấp)
        LOST            // Mất dấu mục tiêu
    }

    // Callbacks cập nhật giao diện
    var onOperatingModeChanged: ((OperatingMode) -> Unit)? = null
    var onAutoSubModeChanged: ((AutoSubMode) -> Unit)? = null
    var onAutoStateChanged: ((AutoState, String) -> Unit)? = null
    var onTelemetryUpdated: ((distanceCm: Float, azimuthDeg: Float, isSafe: Boolean) -> Unit)? = null
    var onOrbitProgress: ((progressDeg: Float) -> Unit)? = null

    // Trạng thái vận hành
    @Volatile
    var currentOperatingMode: OperatingMode = OperatingMode.MANUAL
        private set

    @Volatile
    var currentSubMode: AutoSubMode = AutoSubMode.FOLLOW_50CM
        private set

    @Volatile
    var currentAutoState: AutoState = AutoState.IDLE
        private set

    // Cờ Dead-man's switch (người dùng giữ tay trên nút "HOLD TO RUN")
    @Volatile
    var isHoldToRunActive: Boolean = false
        private set

    // Thông tin mục tiêu mới nhất từ VisualDistanceDetector
    @Volatile
    private var lastTarget: VisualDistanceDetector.TargetResult? = null
    @Volatile
    private var lastTargetTimeMs: Long = 0L

    // Tích lũy góc xoay quanh mục tiêu trong chế độ Orbit 360°
    @Volatile
    private var accumulatedOrbitRad: Float = 0.0f
    private var lastLoopTimeMs: Long = 0L

    // Hướng xoay quỹ đạo: +1 (theo chiều kim đồng hồ) hoặc -1 (ngược chiều kim đồng hồ)
    var orbitDirection: Int = 1

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Chuyển đổi giữa chế độ THỦ CÔNG (MANUAL) và TỰ ĐỘNG (AUTO)
     */
    fun setOperatingMode(mode: OperatingMode) {
        if (currentOperatingMode == mode) return
        currentOperatingMode = mode
        if (mode == OperatingMode.MANUAL) {
            isHoldToRunActive = false
            accumulatedOrbitRad = 0f
            updateState(AutoState.IDLE, "Đã chuyển sang Thủ công")
            udpManager.stopMotors()
        } else {
            updateState(AutoState.SEARCHING, "Sẵn sàng tự hành - Giữ nút để chạy")
        }
        mainHandler.post {
            onOperatingModeChanged?.invoke(mode)
        }
    }

    /**
     * Chuyển đổi trường hợp tự động (Bám 50cm hoặc Xoay 360°)
     */
    fun setAutoSubMode(subMode: AutoSubMode) {
        currentSubMode = subMode
        accumulatedOrbitRad = 0f
        mainHandler.post {
            onAutoSubModeChanged?.invoke(subMode)
        }
        val msg = if (subMode == AutoSubMode.FOLLOW_50CM) "Chế độ: Bám theo 50cm" else "Chế độ: Xoay 360° quanh người"
        updateState(currentAutoState, msg)
    }

    /**
     * Kích hoạt hoặc nhả nút an toàn Hold-to-Run (Dead-man's switch)
     */
    fun setHoldToRun(pressed: Boolean) {
        isHoldToRunActive = pressed
        if (!pressed) {
            // Nhả nút an toàn -> Lập tức dừng mọi chuyển động bánh xe
            udpManager.stopMotors()
            accumulatedOrbitRad = 0f
            updateState(AutoState.IDLE, "Đã nhả nút an toàn (Dừng)")
        } else {
            if (currentOperatingMode == OperatingMode.AUTO) {
                lastLoopTimeMs = System.currentTimeMillis()
                updateState(AutoState.SEARCHING, "Đang giữ nút - Đang tìm mục tiêu...")
            }
        }
    }

    /**
     * Nhận kết quả phát hiện từ VisualDistanceDetector (chạy từ luồng phân tích camera)
     */
    fun onTargetDetected(result: VisualDistanceDetector.TargetResult) {
        lastTarget = result
        lastTargetTimeMs = System.currentTimeMillis()

        val distanceCm = result.distanceCm
        val azimuthDeg = result.azimuthNorm * 35.0f // Ước tính góc độ lệch khoảng ±35° theo HFOV
        val isSafe = result.isSafeZone

        mainHandler.post {
            onTelemetryUpdated?.invoke(distanceCm, azimuthDeg, isSafe)
        }

        if (currentOperatingMode == OperatingMode.AUTO) {
            executeAutoControlLoop(result)
        }
    }

    /**
     * Thông báo mất mục tiêu
     */
    fun onTargetLost() {
        val now = System.currentTimeMillis()
        if (now - lastTargetTimeMs > TARGET_TIMEOUT_MS) {
            lastTarget = null
            if (currentOperatingMode == OperatingMode.AUTO && isHoldToRunActive) {
                udpManager.stopMotors()
                updateState(AutoState.LOST, "Mất dấu mục tiêu (Đã dừng an toàn)")
            }
        }
    }

    /**
     * Vòng lặp điều khiển tự hành thời gian thực (Real-time Autonomous Control Loop)
     */
    private fun executeAutoControlLoop(target: VisualDistanceDetector.TargetResult) {
        val now = System.currentTimeMillis()
        val dt = if (lastLoopTimeMs > 0) ((now - lastLoopTimeMs) / 1000.0f).coerceIn(0.01f, 0.1f) else 0.033f
        lastLoopTimeMs = now

        // Nếu người điều khiển không giữ nút Hold-to-run -> Không cho phép robot di chuyển!
        if (!isHoldToRunActive) {
            updateState(AutoState.LOCKED, "Đã thấy mục tiêu (${String.format("%.1f", target.distanceCm)}cm) - Nhấn giữ để chạy")
            return
        }

        val distance = target.filteredDistanceM

        // =========================================================================
        // 1. KIỂM TRA AN TOÀN TUYỆT ĐỐI (CRITICAL SAFETY STOP)
        // =========================================================================
        if (distance < HARD_SAFETY_STOP_M) {
            udpManager.stopMotors()
            updateState(AutoState.SAFETY_STOP, "CẢNH BÁO: Quá gần (<38cm)! Cắt an toàn!")
            return
        }

        // =========================================================================
        // 2. TÍNH TOÁN BÙ GÓC XOAY ROBOT ĐỂ MỤC TIÊU LUÔN Ở GIỮA KHUNG HÌNH (HEADING)
        // =========================================================================
        val azimuth = target.azimuthNorm // [-1.0 .. +1.0]
        val headingDeadband = 0.08f // Ngưỡng dung sai góc nhỏ để không lắc qua lắc lại
        val omegaTurn: Float = if (abs(azimuth) > headingDeadband) {
            (-azimuth * KP_HEADING).coerceIn(-MAX_HEADING_SPEED, MAX_HEADING_SPEED)
        } else {
            0.0f
        }

        // =========================================================================
        // 3. TÍNH TOÁN THEO TỪNG CHẾ ĐỘ
        // =========================================================================
        when (currentSubMode) {
            AutoSubMode.FOLLOW_50CM -> {
                // TRƯỜNG HỢP 1: BÁM THEO CỰ LY 50CM (SAI SỐ CHO PHÉP ±10CM TỨC 40CM - 60CM)
                val distError = distance - TARGET_DISTANCE_M // Dương = Xa hơn 50cm, Âm = Gần hơn 50cm

                val vxForward: Float = when {
                    // Trong vùng dừng êm (42cm .. 58cm): Không tiến lùi, chỉ xoay chỉnh góc
                    distance in DEADBAND_MIN_M..DEADBAND_MAX_M -> {
                        0.0f
                    }
                    // Quá xa (> 58cm): Tiến nhẹ nhàng lại gần
                    distError > 0 -> {
                        val forwardCmd = (distError * KP_DISTANCE).coerceIn(1.5f, GENTLE_FORWARD_SPEED)
                        forwardCmd
                    }
                    // Hơi gần (38cm .. 42cm): Lùi từ từ để tạo khoảng cách an toàn
                    else -> {
                        val reverseCmd = (distError * KP_DISTANCE).coerceIn(-GENTLE_REVERSE_SPEED, -1.5f)
                        reverseCmd
                    }
                }

                // Chế độ Follow chỉ tịnh tiến tiến/lùi (vx) và xoay thân xe (omegaTurn)
                applyMecanumSpeeds(vx = vxForward, vy = 0.0f, omega = omegaTurn)

                val stateMsg = when {
                    distance in DEADBAND_MIN_M..DEADBAND_MAX_M -> "Đang giữ đúng cự ly 50cm (${String.format("%.1f", target.distanceCm)}cm)"
                    distError > 0 -> "Đang tiến bám theo (${String.format("%.1f", target.distanceCm)}cm)"
                    else -> "Đang lùi giữ khoảng cách (${String.format("%.1f", target.distanceCm)}cm)"
                }
                updateState(AutoState.TRACKING, stateMsg)
            }

            AutoSubMode.ORBIT_360 -> {
                // TRƯỜNG HỢP 2: XOAY 360° QUANH NGƯỜI NHƯNG VẪN GIỮ CỰ LY 50CM (±10CM)
                // - Vận tốc xuyên tâm (Radial): Điều chỉnh để giữ bán kính r = 50cm
                // - Vận tốc tiếp tuyến (Tangential): Di chuyển ngang theo chu vi vòng tròn
                // - Vận tốc quay (Yaw): Giúp camera liên tục chĩa thẳng vào tâm người

                val distError = distance - TARGET_DISTANCE_M
                val vxRadial: Float = when {
                    distance in DEADBAND_MIN_M..DEADBAND_MAX_M -> 0.0f
                    distError > 0 -> (distError * KP_DISTANCE).coerceIn(1.0f, 3.5f)
                    else -> (distError * KP_DISTANCE).coerceIn(-3.5f, -1.0f)
                }

                // Vận tốc dạt ngang tiếp tuyến vòng tròn:
                // orbitDirection: +1 = Quay cùng chiều kim đồng hồ, -1 = Ngược chiều
                val vyTangential = ORBIT_TANGENTIAL_SPEED * orbitDirection

                // Xoay thân xe để vừa bay quỹ đạo tròn vừa ngắm vào người
                // Kết hợp tốc độ xoay tự nhiên của quỹ đạo + bù sai số azimuth từ camera
                val baseOrbitTurn = (ORBIT_TANGENTIAL_SPEED / TARGET_DISTANCE_M) * 0.15f * orbitDirection
                val totalOmega = (baseOrbitTurn + omegaTurn).coerceIn(-MAX_AUTO_SPEED, MAX_AUTO_SPEED)

                // Bơm tốc độ bánh xe Mecanum đa hướng
                applyMecanumSpeeds(vx = vxRadial, vy = vyTangential, omega = totalOmega)

                // Tích lũy góc xoay
                val deltaRad = abs(totalOmega * dt * 0.1f)
                accumulatedOrbitRad += deltaRad
                val progressDeg = min(360.0f, (accumulatedOrbitRad * 180.0f / PI).toFloat())

                mainHandler.post {
                    onOrbitProgress?.invoke(progressDeg)
                }

                val orbitMsg = "Đang xoay quanh: ${progressDeg.toInt()}°/360° · Cự ly ${String.format("%.1f", target.distanceCm)}cm"
                updateState(AutoState.ORBITING, orbitMsg)
            }
        }
    }

    /**
     * Động học nghịch bánh Mecanum 4 bánh (Inverse Mecanum Kinematics):
     * W1 (RL Rear Left)   = vx - vy + omega
     * W2 (FL Front Left)  = vx + vy + omega
     * W3 (RR Rear Right)  = vx + vy - omega
     * W4 (FR Front Right) = vx - vy - omega
     */
    private fun applyMecanumSpeeds(vx: Float, vy: Float, omega: Float) {
        val w1 = vx - vy + omega
        val w2 = vx + vy + omega
        val w3 = vx + vy - omega
        val w4 = vx - vy - omega

        val peak = maxOf(
            MAX_AUTO_SPEED,
            abs(w1),
            abs(w2),
            abs(w3),
            abs(w4)
        )

        val scale = if (peak > MAX_AUTO_SPEED) MAX_AUTO_SPEED / peak else 1.0f

        val finalW1 = (w1 * scale)
        val finalW2 = (w2 * scale)
        val finalW3 = (w3 * scale)
        val finalW4 = (w4 * scale)

        // Bơm lệnh điều khiển động cơ xuống Robot qua UDP (stateRun = 0x03)
        udpManager.stateRun = 0x03
        udpManager.setW1 = finalW1
        udpManager.setW2 = finalW2
        udpManager.setW3 = finalW3
        udpManager.setW4 = finalW4
    }

    /**
     * Dừng khẩn cấp toàn bộ hệ thống
     */
    fun emergencyStop() {
        isHoldToRunActive = false
        accumulatedOrbitRad = 0f
        udpManager.emergencyStop()
        updateState(AutoState.SAFETY_STOP, "DỪNG KHẨN CẤP (EMERGENCY STOP)")
    }

    private fun updateState(state: AutoState, message: String) {
        currentAutoState = state
        mainHandler.post {
            onAutoStateChanged?.invoke(state, message)
        }
    }
}
