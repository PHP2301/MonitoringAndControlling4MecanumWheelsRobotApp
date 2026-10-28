package com.robotcamera.app

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Giao diện Bảng Điều Khiển Robot Mecanum (Full-Screen Floating FPV Cockpit)
 * - Nền trong suốt phủ trên luồng video FPV toàn màn hình:
 *   + Góc dưới trái: Cụm Joystick Ảo Đa Hướng 360° (MecanumJoystickView) điều khiển mượt mà.
 *   + Chính giữa dưới: Thẻ nổi hiển thị trạng thái UDP, Telemetry (Vx, Vy, θ) và nút Chụp ảnh / Quay video.
 *   + Góc dưới phải: 2 nút vuông bo góc màu xanh dương xoay trái (↺) và xoay phải (↻).
 *   + Góc trên phải (dưới thanh Camera): Thẻ nổi điều khiển Gimbal RS 3 Mini (< ^ v > + Mode).
 */
@SuppressLint("ViewConstructor")
class RobotControlDashboardView(
    context: Context,
    private val udpManager: RobotUdpManager,
    val autoFollowManager: AutoFollowManager? = null
) : FrameLayout(context) {

    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    ).toInt()

    private fun dpF(v: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    )

    // Connection views & Status
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvMiniTelemetry: TextView

    // Chế độ Tự hành (Auto Follow & Orbit)
    private lateinit var tabManual: TextView
    private lateinit var tabAuto: TextView
    private lateinit var layoutManualContent: LinearLayout
    private lateinit var layoutAutoContent: LinearLayout
    private lateinit var btnAutoSubFollow: TextView
    private lateinit var btnAutoSubOrbit: TextView
    private lateinit var tvAutoDistance: TextView
    private lateinit var tvAutoStatusBadge: TextView
    private lateinit var btnHoldToRun: TextView
    private lateinit var btnAutoEmergencyStop: TextView

    // Joystick & Turn buttons
    lateinit var joystickView: MecanumJoystickView
        private set
    lateinit var btnTurnL: FrameLayout
        private set
    lateinit var btnTurnR: FrameLayout
        private set

    // Gimbal RS3 Mini buttons
    private lateinit var gimbalCard: LinearLayout
    private lateinit var btnGimbalLeft: View
    private lateinit var btnGimbalUp: View
    private lateinit var btnGimbalDown: View
    private lateinit var btnGimbalRight: View
    private lateinit var btnGimbalCenter: View
    private lateinit var btnGimbalMode: TextView
    private var controllerGimbalMode = GimbalBleManager.FollowMode.PF

    // Camera action callbacks (Kết nối trực tiếp vào MainActivity)
    var onTakePhotoRequested: (() -> Unit)? = null
    var onStartRecordRequested: (() -> Unit)? = null
    var onStopRecordRequested: (() -> Unit)? = null
    var onSwitchCameraRequested: (() -> Unit)? = null
    var onSwitchRoleRequested: (() -> Unit)? = null

    // Cache Telemetry mới nhất
    private var latestTelemetry: RobotUdpManager.RobotTelemetry? = null

    // Tham chiếu views trong Dialog thông số
    private var paramsDialog: android.app.Dialog? = null
    private var dialogTvX: TextView? = null
    private var dialogTvY: TextView? = null
    private var dialogTvTheta: TextView? = null
    private var dialogTvVx: TextView? = null
    private var dialogTvVy: TextView? = null
    private var dialogTvW: TextView? = null
    private var dialogTvW1: TextView? = null
    private var dialogTvW2: TextView? = null
    private var dialogTvW3: TextView? = null
    private var dialogTvW4: TextView? = null

    // Lưu trữ tạm giá trị cài đặt
    private var savedW1 = "0"
    private var savedW2 = "0"
    private var savedW3 = "0"
    private var savedW4 = "0"

    private var savedVx = "0"
    private var savedVy = "0"
    private var savedWSet = "0"
    private var savedXSet = "0"
    private var savedYSet = "0"
    private var savedThetaSet = "0"

    private var dialogEtW1: EditText? = null
    private var dialogEtW2: EditText? = null
    private var dialogEtW3: EditText? = null
    private var dialogEtW4: EditText? = null

    private var dialogEtVx: EditText? = null
    private var dialogEtVy: EditText? = null
    private var dialogEtWSet: EditText? = null
    private var dialogEtXSet: EditText? = null
    private var dialogEtYSet: EditText? = null
    private var dialogEtThetaSet: EditText? = null

    // Đồng bộ nút hướng cho PC / thiết bị khác khi kéo Joystick
    private var syncedDirection: String? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        isMotionEventSplittingEnabled = true
        setupView()
        setupListeners()
    }

    private fun setupView() {
        // =========================================================================
        // 1. GÓC TRÊN PHẢI (HÀNG 2): THẺ NỔI ĐIỀU KHIỂN GIMBAL RS 3 MINI
        // =========================================================================
        gimbalCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(26f)
                setColor(Color.parseColor("#F5FFFFFF"))
                setStroke(dp(1f), Color.parseColor("#E2E8F0"))
            }
            elevation = dpF(6f)
            setPadding(dp(14f), dp(6f), dp(10f), dp(6f))
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(98f)
                rightMargin = dp(14f)
            }
        }

        val tvGimbalLabel = TextView(context).apply {
            text = "Gimbal"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1D5CE5"))
            setPadding(0, 0, dp(10f), 0)
            setOnClickListener {
                cycleGimbalMode()
            }
        }
        gimbalCard.addView(tvGimbalLabel)

        btnGimbalLeft = createGimbalCircleChevronButton(ChevronDir.LEFT) { pressed ->
            GimbalBleManager.sendRemoteGimbalCommand(
                context, "GIMBAL_LEFT", if (pressed) "DOWN" else "UP",
                httpEndpoint = "gimbal/move?pan=${if (pressed) -1 else 0}&tilt=0"
            )
        }
        gimbalCard.addView(btnGimbalLeft)

        btnGimbalUp = createGimbalCircleChevronButton(ChevronDir.UP) { pressed ->
            GimbalBleManager.sendRemoteGimbalCommand(
                context, "GIMBAL_UP", if (pressed) "DOWN" else "UP",
                httpEndpoint = "gimbal/move?pan=0&tilt=${if (pressed) 1 else 0}"
            )
        }
        gimbalCard.addView(btnGimbalUp)

        btnGimbalDown = createGimbalCircleChevronButton(ChevronDir.DOWN) { pressed ->
            GimbalBleManager.sendRemoteGimbalCommand(
                context, "GIMBAL_DOWN", if (pressed) "DOWN" else "UP",
                httpEndpoint = "gimbal/move?pan=0&tilt=${if (pressed) -1 else 0}"
            )
        }
        gimbalCard.addView(btnGimbalDown)

        btnGimbalRight = createGimbalCircleChevronButton(ChevronDir.RIGHT) { pressed ->
            GimbalBleManager.sendRemoteGimbalCommand(
                context, "GIMBAL_RIGHT", if (pressed) "DOWN" else "UP",
                httpEndpoint = "gimbal/move?pan=${if (pressed) 1 else 0}&tilt=0"
            )
        }
        gimbalCard.addView(btnGimbalRight)

        btnGimbalCenter = createGimbalCircleChevronButton(ChevronDir.CENTER) { pressed ->
            if (pressed) {
                GimbalBleManager.sendRemoteGimbalCommand(
                    context, "GIMBAL_RECENTER", "CLICK",
                    httpEndpoint = "gimbal/recenter"
                )
                Toast.makeText(context, "Đã căn giữa Gimbal (Center)", Toast.LENGTH_SHORT).show()
            }
        }
        gimbalCard.addView(btnGimbalCenter)

        btnGimbalMode = TextView(context).apply {
            text = controllerGimbalMode.label
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1D5CE5"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(14f)
                setColor(Color.parseColor("#EEF4FF"))
                setStroke(dp(1f), Color.parseColor("#BFDBFE"))
            }
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(30f)
            ).apply {
                leftMargin = dp(4f)
            }
            setOnClickListener {
                cycleGimbalMode()
            }
        }
        gimbalCard.addView(btnGimbalMode)
        updateGimbalModeUi(controllerGimbalMode)

        addView(gimbalCard)

        // =========================================================================
        // 2. GÓC DƯỚI TRÁI: CỤM JOYSTICK ẢO ĐA HƯỚNG MECANUM 360°
        // =========================================================================
        val joySize = dp(180f)
        joystickView = MecanumJoystickView(context).apply {
            layoutParams = LayoutParams(joySize, joySize).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                leftMargin = dp(22f)
                bottomMargin = dp(18f)
            }
            elevation = dpF(6f)
            onJoystickMoved = { nx, ny, active ->
                handleJoystickInput(nx, ny, active)
            }
        }
        addView(joystickView)

        // =========================================================================
        // 3. CHÍNH GIỮA DƯỚI: THẺ NỔI TELEMETRY & CHỤP ẢNH / QUAY VIDEO
        // =========================================================================
        val bottomCenterCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(24f)
                setColor(Color.parseColor("#F5FFFFFF"))
                setStroke(dp(1f), Color.parseColor("#E2E8F0"))
            }
            elevation = dpF(6f)
            setPadding(dp(28f), dp(12f), dp(28f), dp(12f))
            minimumWidth = dp(340f)
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(18f)
            }
        }

        tvStatusBadge = TextView(context).apply {
            text = if (udpManager.isConnected) "Đã kết nối UDP tới 192.168.2.93:5001" else "Chưa kết nối UDP"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (udpManager.isConnected) Color.parseColor("#15803D") else Color.parseColor("#0284C7"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6f))
        }
        bottomCenterCard.addView(tvStatusBadge)

        // --- CỤM CHỌN CHẾ ĐỘ: MANUAL (THỦ CÔNG) | AUTO (TỰ ĐỘNG) ---
        val modeSwitchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(16f)
                setColor(Color.parseColor("#F1F5F9"))
                setStroke(dp(1f), Color.parseColor("#CBD5E1"))
            }
            setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8f)
            }
        }

        tabManual = TextView(context).apply {
            text = "🎮 THỦ CÔNG"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(5f), dp(14f), dp(5f))
            setOnClickListener {
                autoFollowManager?.setOperatingMode(AutoFollowManager.OperatingMode.MANUAL)
                updateOperatingModeUi(AutoFollowManager.OperatingMode.MANUAL)
                ButtonSyncManager.sendButtonEvent("AUTO_MODE", "MANUAL")
            }
        }
        modeSwitchRow.addView(tabManual)

        tabAuto = TextView(context).apply {
            text = "🤖 TỰ ĐỘNG"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(5f), dp(14f), dp(5f))
            setOnClickListener {
                autoFollowManager?.setOperatingMode(AutoFollowManager.OperatingMode.AUTO)
                updateOperatingModeUi(AutoFollowManager.OperatingMode.AUTO)
                ButtonSyncManager.sendButtonEvent("AUTO_MODE", "FOLLOW_50CM")
            }
        }
        modeSwitchRow.addView(tabAuto)
        bottomCenterCard.addView(modeSwitchRow)

        // --- NỘI DUNG CHẾ ĐỘ THỦ CÔNG (MANUAL CONTENT) ---
        layoutManualContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tvMiniTelemetry = TextView(context).apply {
            text = "Vx: 0.000 m/s  ·  Vy: 0.000 m/s  ·  θ: 0.00°"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#334155"))
            gravity = Gravity.CENTER
            setPadding(0, dp(1f), 0, dp(8f))
        }
        layoutManualContent.addView(tvMiniTelemetry)

        val quickCamRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val btnCamSnap = createSmallCamPillButton("Chụp ảnh") {
            udpManager.sendTakePhoto()
            onTakePhotoRequested?.invoke()
            ButtonSyncManager.sendButtonEvent("TAKE_PHOTO", "CLICK")
            Toast.makeText(context, "Lệnh chụp ảnh (0x07)", Toast.LENGTH_SHORT).show()
        }
        quickCamRow.addView(btnCamSnap)

        quickCamRow.addView(createHorizontalSpacer(dp(10f)))

        val btnCamRec = createSmallCamPillButton("Quay video") {
            udpManager.sendStartRecord()
            onStartRecordRequested?.invoke()
            ButtonSyncManager.sendButtonEvent("START_RECORD", "CLICK")
            Toast.makeText(context, "Lệnh bắt đầu quay (0x08)", Toast.LENGTH_SHORT).show()
        }
        quickCamRow.addView(btnCamRec)
        layoutManualContent.addView(quickCamRow)
        bottomCenterCard.addView(layoutManualContent)

        // --- NỘI DUNG CHẾ ĐỘ TỰ ĐỘNG (AUTO CONTENT) ---
        layoutAutoContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Hàng chọn trường hợp: Bám 50cm hoặc Xoay 360°
        val subModeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6f)
            }
        }

        btnAutoSubFollow = TextView(context).apply {
            text = "🏃 Bám 50cm"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
            setOnClickListener {
                autoFollowManager?.setAutoSubMode(AutoFollowManager.AutoSubMode.FOLLOW_50CM)
                updateAutoSubModeUi(AutoFollowManager.AutoSubMode.FOLLOW_50CM)
                ButtonSyncManager.sendButtonEvent("AUTO_MODE", "FOLLOW_50CM")
            }
        }
        subModeRow.addView(btnAutoSubFollow)

        subModeRow.addView(createHorizontalSpacer(dp(8f)))

        btnAutoSubOrbit = TextView(context).apply {
            text = "🔄 Xoay 360°"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
            setOnClickListener {
                autoFollowManager?.setAutoSubMode(AutoFollowManager.AutoSubMode.ORBIT_360)
                updateAutoSubModeUi(AutoFollowManager.AutoSubMode.ORBIT_360)
                ButtonSyncManager.sendButtonEvent("AUTO_MODE", "ORBIT_360")
            }
        }
        subModeRow.addView(btnAutoSubOrbit)
        layoutAutoContent.addView(subModeRow)

        // Đo khoảng cách & Trạng thái bám
        val distanceRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8f)
            }
        }

        tvAutoDistance = TextView(context).apply {
            text = "Cự ly: -- cm"
            textSize = 12.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, 0, dp(8f), 0)
        }
        distanceRow.addView(tvAutoDistance)

        tvAutoStatusBadge = TextView(context).apply {
            text = "Chờ lệnh (Giữ nút để chạy)"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#475569"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(12f)
                setColor(Color.parseColor("#F1F5F9"))
                setStroke(dp(1f), Color.parseColor("#CBD5E1"))
            }
            setPadding(dp(8f), dp(3f), dp(8f), dp(3f))
        }
        distanceRow.addView(tvAutoStatusBadge)
        layoutAutoContent.addView(distanceRow)

        // Hàng nút hành động: NÚT AN TOÀN HOLD-TO-RUN & DỪNG KHẨN CẤP
        val autoActionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val normalHoldBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(20f)
            setColor(Color.parseColor("#059669")) // Emerald 600
        }
        val pressedHoldBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(20f)
            setColor(Color.parseColor("#10B981")) // Emerald 500 (sáng hơn khi nhấn)
        }

        btnHoldToRun = TextView(context).apply {
            text = "🛡️ GIỮ ĐỂ CHẠY (HOLD)"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = normalHoldBg
            elevation = dpF(4f)
            setPadding(dp(22f), dp(10f), dp(22f), dp(10f))
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.background = pressedHoldBg
                        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
                        autoFollowManager?.setHoldToRun(true)
                        ButtonSyncManager.sendButtonEvent("AUTO_HOLD", "DOWN")
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.background = normalHoldBg
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start()
                        autoFollowManager?.setHoldToRun(false)
                        ButtonSyncManager.sendButtonEvent("AUTO_HOLD", "UP")
                        true
                    }
                    else -> false
                }
            }
        }
        autoActionRow.addView(btnHoldToRun)

        autoActionRow.addView(createHorizontalSpacer(dp(10f)))

        btnAutoEmergencyStop = TextView(context).apply {
            text = "🛑 DỪNG"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(20f)
                setColor(Color.parseColor("#DC2626")) // Red 600
            }
            elevation = dpF(4f)
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            setOnClickListener {
                autoFollowManager?.emergencyStop()
                ButtonSyncManager.sendButtonEvent("EMERGENCY", "CLICK")
                Toast.makeText(context, "DỪNG KHẨN CẤP!", Toast.LENGTH_SHORT).show()
            }
        }
        autoActionRow.addView(btnAutoEmergencyStop)
        layoutAutoContent.addView(autoActionRow)

        bottomCenterCard.addView(layoutAutoContent)
        addView(bottomCenterCard)

        updateOperatingModeUi(AutoFollowManager.OperatingMode.MANUAL)
        updateAutoSubModeUi(AutoFollowManager.AutoSubMode.FOLLOW_50CM)

        // =========================================================================
        // 4. GÓC DƯỚI PHẢI: 2 NÚT VUÔNG BO GÓC MÀU XANH XOAY TRÁI (↺) & XOAY PHẢI (↻)
        // =========================================================================
        val turnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = dp(22f)
                bottomMargin = dp(18f)
            }
        }

        btnTurnL = createBlueRoundedSquareTurnButton(isLeft = true) { pressed ->
            if (checkDrivingPermission(pressed)) {
                udpManager.turnLeft(pressed)
                ButtonSyncManager.sendButtonEvent("TURN_LEFT", if (pressed) "DOWN" else "UP")
            }
        }
        turnRow.addView(btnTurnL)

        turnRow.addView(createHorizontalSpacer(dp(12f)))

        btnTurnR = createBlueRoundedSquareTurnButton(isLeft = false) { pressed ->
            if (checkDrivingPermission(pressed)) {
                udpManager.turnRight(pressed)
                ButtonSyncManager.sendButtonEvent("TURN_RIGHT", if (pressed) "DOWN" else "UP")
            }
        }
        turnRow.addView(btnTurnR)

        addView(turnRow)
    }

    private fun cycleGimbalMode() {
        val next = controllerGimbalMode.next()
        updateGimbalModeUi(next)
        GimbalBleManager.sendRemoteGimbalCommand(
            context, "GIMBAL_MODE", "CLICK", next.label,
            httpEndpoint = "gimbal/mode?m=${next.label}"
        )
        Toast.makeText(context, "Gimbal Mode: ${next.desc}", Toast.LENGTH_SHORT).show()
    }

    private fun handleJoystickInput(nx: Float, ny: Float, active: Boolean) {
        if (!active) {
            // Chỉ đưa vector tịnh tiến của Joystick về (0, 0) thay vì stopMotors()
            // để nếu người dùng vẫn đang giữ nút Xoay Trái/Phải thì xe tiếp tục xoay mượt mà
            udpManager.driveJoystick(0f, 0f)
            syncedDirection?.let { prev ->
                ButtonSyncManager.sendButtonEvent(prev, "UP")
                syncedDirection = null
            }
            return
        }

        if (!checkDrivingPermission(true)) {
            udpManager.stopMotors()
            return
        }

        // Điều khiển trộn tốc độ 4 bánh Mecanum mượt mà theo vector (nx, ny) kết hợp nút xoay (Drift)
        udpManager.driveJoystick(nx, ny)

        // Xác định hướng chính để đồng bộ hiển thị với PC / thiết bị khác
        val mag = hypot(nx.toDouble(), ny.toDouble()).toFloat()
        val dominantDir: String? = if (mag < 0.22f) {
            null
        } else if (abs(ny) >= abs(nx)) {
            if (ny > 0) "UP" else "DOWN"
        } else {
            if (nx > 0) "RIGHT" else "LEFT"
        }

        if (dominantDir != syncedDirection) {
            syncedDirection?.let { prev ->
                ButtonSyncManager.sendButtonEvent(prev, "UP")
            }
            dominantDir?.let { next ->
                ButtonSyncManager.sendButtonEvent(next, "DOWN")
            }
            syncedDirection = dominantDir
        }
    }

    /**
     * Điều chỉnh kích thước linh hoạt theo màn hình
     */
    fun applyControlSizes(totalW: Int, totalH: Int) {
        if (totalW <= 0 || totalH <= 0) return
        val isCompact = totalW < dp(700f)
        val joyDim = if (isCompact) dp(148f) else dp(180f)
        (joystickView.layoutParams as? LayoutParams)?.let { lp ->
            lp.width = joyDim
            lp.height = joyDim
            joystickView.layoutParams = lp
        }
    }

    private enum class ChevronDir { LEFT, UP, DOWN, RIGHT, CENTER }

    @SuppressLint("ClickableViewAccessibility")
    private fun createGimbalCircleChevronButton(dir: ChevronDir, onHold: (Boolean) -> Unit): View {
        val size = dp(34f)
        val view = object : View(context) {
            var isHeldState = false
                set(value) {
                    field = value
                    invalidate()
                }

            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.2f)
            }
            private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val r = (width.coerceAtMost(height) / 2f) - dpF(1.5f)

                if (isHeldState) {
                    bgPaint.color = Color.parseColor("#1D5CE5")
                    strokePaint.color = Color.parseColor("#1D5CE5")
                    iconPaint.color = Color.WHITE
                } else {
                    bgPaint.color = Color.WHITE
                    strokePaint.color = Color.parseColor("#CBD5E1")
                    iconPaint.color = Color.parseColor("#1D5CE5")
                }

                canvas.drawCircle(cx, cy, r, bgPaint)
                canvas.drawCircle(cx, cy, r, strokePaint)

                val s = dpF(3.8f)
                val path = Path()
                when (dir) {
                    ChevronDir.LEFT -> {
                        path.moveTo(cx + s * 0.5f, cy - s)
                        path.lineTo(cx - s * 0.6f, cy)
                        path.lineTo(cx + s * 0.5f, cy + s)
                    }
                    ChevronDir.RIGHT -> {
                        path.moveTo(cx - s * 0.5f, cy - s)
                        path.lineTo(cx + s * 0.6f, cy)
                        path.lineTo(cx - s * 0.5f, cy + s)
                    }
                    ChevronDir.UP -> {
                        path.moveTo(cx - s, cy + s * 0.5f)
                        path.lineTo(cx, cy - s * 0.6f)
                        path.lineTo(cx + s, cy + s * 0.5f)
                    }
                    ChevronDir.DOWN -> {
                        path.moveTo(cx - s, cy - s * 0.5f)
                        path.lineTo(cx, cy + s * 0.6f)
                        path.lineTo(cx + s, cy - s * 0.5f)
                    }
                    ChevronDir.CENTER -> {
                        // Vòng tròn tâm
                        canvas.drawCircle(cx, cy, dpF(3.2f), iconPaint)
                        // 4 vạch ngắm chữ thập xung quanh (Target Reticle)
                        val cr1 = dpF(4.8f)
                        val cr2 = dpF(7.5f)
                        canvas.drawLine(cx - cr2, cy, cx - cr1, cy, iconPaint)
                        canvas.drawLine(cx + cr1, cy, cx + cr2, cy, iconPaint)
                        canvas.drawLine(cx, cy - cr2, cx, cy - cr1, iconPaint)
                        canvas.drawLine(cx, cy + cr1, cx, cy + cr2, iconPaint)
                    }
                }
                if (dir != ChevronDir.CENTER) {
                    canvas.drawPath(path, iconPaint)
                }
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                leftMargin = dp(3f)
                rightMargin = dp(3f)
            }
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        setGimbalHoldVisual(v, true)
                        onHold(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        setGimbalHoldVisual(v, false)
                        onHold(false)
                        true
                    }
                    else -> false
                }
            }
        }
        return view
    }

    private fun setGimbalHoldVisual(btn: View, held: Boolean) {
        try {
            val field = btn.javaClass.getDeclaredField("isHeldState")
            field.isAccessible = true
            field.setBoolean(btn, held)
            btn.invalidate()
        } catch (_: Exception) {
            btn.alpha = if (held) 0.7f else 1.0f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createBlueRoundedSquareTurnButton(isLeft: Boolean, onHoldStateChanged: (Boolean) -> Unit): FrameLayout {
        val w = dp(82f)
        val h = dp(72f)
        val container = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(w, h)
            elevation = dpF(5f)
        }

        val normalBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(20f)
            setColor(Color.parseColor("#1D5CE5"))
        }

        val pressedBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(20f)
            setColor(Color.parseColor("#1342A8"))
        }

        container.background = normalBg

        val iconView = object : View(context) {
            private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = dpF(2.6f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val r = dpF(12f)
                val rect = RectF(cx - r, cy - r, cx + r, cy + r)

                if (isLeft) {
                    // Vẽ cung tròn xoay ngược chiều kim đồng hồ (↺)
                    canvas.drawArc(rect, -40f, 285f, false, arcPaint)
                    // Đầu mũi tên ở góc trên trái
                    val endAngleRad = Math.toRadians(-40.0)
                    val ax = (cx + r * cos(endAngleRad)).toFloat()
                    val ay = (cy + r * sin(endAngleRad)).toFloat()
                    val arrowPath = Path().apply {
                        moveTo(ax - dpF(5.5f), ay - dpF(1f))
                        lineTo(ax, ay)
                        lineTo(ax - dpF(1f), ay + dpF(5.5f))
                    }
                    canvas.drawPath(arrowPath, arcPaint)
                } else {
                    // Vẽ cung tròn xoay thuận chiều kim đồng hồ (↻)
                    canvas.drawArc(rect, 220f, -285f, false, arcPaint)
                    val endAngleRad = Math.toRadians(220.0)
                    val ax = (cx + r * cos(endAngleRad)).toFloat()
                    val ay = (cy + r * sin(endAngleRad)).toFloat()
                    val arrowPath = Path().apply {
                        moveTo(ax + dpF(5.5f), ay - dpF(1f))
                        lineTo(ax, ay)
                        lineTo(ax + dpF(1f), ay + dpF(5.5f))
                    }
                    canvas.drawPath(arrowPath, arcPaint)
                }
            }
        }.apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        container.addView(iconView)

        container.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    v.background = pressedBg
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).start()
                    onHoldStateChanged(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.background = normalBg
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(70).start()
                    onHoldStateChanged(false)
                    true
                }
                else -> false
            }
        }

        return container
    }

    private fun createSmallCamPillButton(text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            isSingleLine = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(16f)
                setColor(Color.WHITE)
                setStroke(dp(1.2f), Color.parseColor("#CBD5E1"))
            }
            setPadding(dp(18f), dp(6f), dp(18f), dp(6f))
            setOnClickListener { onClick() }
        }
    }

    // =========================================================================
    // 5. DIALOG BẢNG THÔNG SỐ & CÀI ĐẶT ROBOT TẬP TRUNG (2 CỘT CHUẨN MOCKUP)
    // =========================================================================
    fun showParametersBottomSheet() {
        val dialog = android.app.Dialog(context)
        paramsDialog = dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.55f)

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(26f)
                setColor(Color.WHITE)
            }
            elevation = dpF(10f)
            setPadding(dp(24f), dp(20f), dp(24f), dp(20f))
        }

        // Tiêu đề & Nút Phân quyền + Đóng
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14f)
            }
        }

        val tvTitle = TextView(context).apply {
            text = "BẢNG THÔNG SỐ & CÀI ĐẶT XE"
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1D5CE5"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(tvTitle)

        val btnPerm = TextView(context).apply {
            fun labelFor(m: RobotUdpManager.ControlPermission) = when (m) {
                RobotUdpManager.ControlPermission.AUTO -> "Quyền: Tự động"
                RobotUdpManager.ControlPermission.MASTER -> "Quyền: Độc quyền"
                RobotUdpManager.ControlPermission.VIEWER -> "Quyền: Chỉ xem"
            }
            text = labelFor(udpManager.permissionMode)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(18f)
                setColor(Color.parseColor("#1D5CE5"))
            }
            setPadding(dp(16f), dp(7f), dp(16f), dp(7f))
            setOnClickListener {
                val next = udpManager.cyclePermissionMode()
                text = labelFor(next)
            }
        }
        titleRow.addView(btnPerm)
        titleRow.addView(createHorizontalSpacer(dp(8f)))

        val btnClose = TextView(context).apply {
            text = "Đóng"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(18f)
                setColor(Color.WHITE)
                setStroke(dp(1.2f), Color.parseColor("#CBD5E1"))
            }
            setPadding(dp(16f), dp(7f), dp(16f), dp(7f))
            setOnClickListener { dialog.dismiss() }
        }
        titleRow.addView(btnClose)
        rootCard.addView(titleRow)

        val bodyScroll = NestedScrollView(context).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Bố cục 2 cột ngang: Cột trái (Monitor) | Cột phải (Tốc độ bánh + Robot Setpoint)
        val twoColLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Cột Trái: Thông số xe thời gian thực
        val leftCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.45f)
        }
        leftCol.addView(createDialogTelemetryCard())
        twoColLayout.addView(leftCol)

        twoColLayout.addView(createHorizontalSpacer(dp(14f)))

        // Cột Phải: Cài đặt tốc độ bánh + Cài đặt vị trí & vận tốc mục tiêu
        val rightCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.55f)
        }
        rightCol.addView(createDialogWheelVelocityCard())
        rightCol.addView(createVerticalSpacer(dp(12f)))
        rightCol.addView(createDialogRobotSetpointCard())
        twoColLayout.addView(rightCol)

        bodyScroll.addView(twoColLayout)
        rootCard.addView(bodyScroll)

        dialog.setContentView(rootCard)

        val dm = resources.displayMetrics
        dialog.window?.setLayout(
            (dm.widthPixels * 0.92f).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        dialog.setOnDismissListener {
            dialogEtW1?.text?.toString()?.let { savedW1 = it }
            dialogEtW2?.text?.toString()?.let { savedW2 = it }
            dialogEtW3?.text?.toString()?.let { savedW3 = it }
            dialogEtW4?.text?.toString()?.let { savedW4 = it }

            dialogEtVx?.text?.toString()?.let { savedVx = it }
            dialogEtVy?.text?.toString()?.let { savedVy = it }
            dialogEtWSet?.text?.toString()?.let { savedWSet = it }
            dialogEtXSet?.text?.toString()?.let { savedXSet = it }
            dialogEtYSet?.text?.toString()?.let { savedYSet = it }
            dialogEtThetaSet?.text?.toString()?.let { savedThetaSet = it }

            dialogTvX = null
            dialogTvY = null
            dialogTvTheta = null
            dialogTvVx = null
            dialogTvVy = null
            dialogTvW = null
            dialogTvW1 = null
            dialogTvW2 = null
            dialogTvW3 = null
            dialogTvW4 = null

            dialogEtW1 = null
            dialogEtW2 = null
            dialogEtW3 = null
            dialogEtW4 = null

            dialogEtVx = null
            dialogEtVy = null
            dialogEtWSet = null
            dialogEtXSet = null
            dialogEtYSet = null
            dialogEtThetaSet = null

            paramsDialog = null
        }

        latestTelemetry?.let { t -> updateDialogTelemetry(t) }

        dialog.show()
    }

    private fun createDialogTelemetryCard(): View {
        val card = createCardContainer()
        card.addView(createSectionHeader("THÔNG SỐ XE THỜI GIAN THỰC (MONITOR)"))

        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        dialogTvX = createTelemetryPill(row1, "X (m)", "0.000")
        dialogTvY = createTelemetryPill(row1, "Y (m)", "0.000")
        dialogTvTheta = createTelemetryPill(row1, "θ (độ)", "0.00°")
        card.addView(row1)

        card.addView(createVerticalSpacer(dp(8f)))

        val row2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        dialogTvVx = createTelemetryPill(row2, "Vx (m/s)", "0.000")
        dialogTvVy = createTelemetryPill(row2, "Vy (m/s)", "0.000")
        dialogTvW = createTelemetryPill(row2, "W (rad/s)", "0.000")
        card.addView(row2)

        card.addView(createVerticalSpacer(dp(8f)))

        val row3 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        dialogTvW1 = createTelemetryPill(row3, "W_RL", "0.000")
        dialogTvW2 = createTelemetryPill(row3, "W_FL", "0.000")
        card.addView(row3)

        card.addView(createVerticalSpacer(dp(8f)))

        val row4 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        dialogTvW3 = createTelemetryPill(row4, "W_RR", "0.000")
        dialogTvW4 = createTelemetryPill(row4, "W_FR", "0.000")
        card.addView(row4)

        return card
    }

    private fun updateDialogTelemetry(t: RobotUdpManager.RobotTelemetry) {
        dialogTvX?.text = String.format(Locale.US, "%.3f", t.x)
        dialogTvY?.text = String.format(Locale.US, "%.3f", t.y)
        dialogTvTheta?.text = String.format(Locale.US, "%.2f°", t.thetaDeg)

        dialogTvVx?.text = String.format(Locale.US, "%.3f", t.vx)
        dialogTvVy?.text = String.format(Locale.US, "%.3f", t.vy)
        dialogTvW?.text = String.format(Locale.US, "%.3f", t.w)

        dialogTvW1?.text = String.format(Locale.US, "%.3f", t.w1)
        dialogTvW2?.text = String.format(Locale.US, "%.3f", t.w2)
        dialogTvW3?.text = String.format(Locale.US, "%.3f", t.w3)
        dialogTvW4?.text = String.format(Locale.US, "%.3f", t.w4)
    }

    private fun createDialogWheelVelocityCard(): View {
        val card = createCardContainer()
        card.addView(createSectionHeader("CÀI ĐẶT TỐC ĐỘ BÁNH XE (RAD/S)"))

        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val r1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        dialogEtW1 = createLabeledInput(r1, "W Rear Left:", savedW1)
        dialogEtW2 = createLabeledInput(r1, "W Front Left:", savedW2)
        grid.addView(r1)

        grid.addView(createVerticalSpacer(dp(6f)))

        val r2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        dialogEtW3 = createLabeledInput(r2, "W Rear Right:", savedW3)
        dialogEtW4 = createLabeledInput(r2, "W Front Right:", savedW4)
        grid.addView(r2)

        card.addView(grid)
        card.addView(createVerticalSpacer(dp(10f)))

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val btnReset = createOutlineActionButton("Reset") {
            if (!checkDrivingPermission(true)) return@createOutlineActionButton
            savedW1 = "0"; savedW2 = "0"; savedW3 = "0"; savedW4 = "0"
            dialogEtW1?.setText("0")
            dialogEtW2?.setText("0")
            dialogEtW3?.setText("0")
            dialogEtW4?.setText("0")
            udpManager.resetWheelVelocities()
            ButtonSyncManager.sendButtonEvent("RESET_WHEELS", "CLICK")
            Toast.makeText(context, "Đã reset tốc độ bánh về 0", Toast.LENGTH_SHORT).show()
        }
        btnRow.addView(btnReset)

        btnRow.addView(createHorizontalSpacer(dp(8f)))

        val btnSet = createSmallActionButton("Set Vận Tốc", Color.parseColor("#1D5CE5")) {
            if (!checkDrivingPermission(true)) return@createSmallActionButton
            val w1 = dialogEtW1?.text?.toString()?.toFloatOrNull() ?: 0f
            val w2 = dialogEtW2?.text?.toString()?.toFloatOrNull() ?: 0f
            val w3 = dialogEtW3?.text?.toString()?.toFloatOrNull() ?: 0f
            val w4 = dialogEtW4?.text?.toString()?.toFloatOrNull() ?: 0f
            savedW1 = w1.toString(); savedW2 = w2.toString(); savedW3 = w3.toString(); savedW4 = w4.toString()
            udpManager.setWheelVelocities(w1, w2, w3, w4)
            ButtonSyncManager.sendButtonEvent("SET_WHEELS", "CLICK", "$w1,$w2,$w3,$w4")
            Toast.makeText(context, "Đã set tốc độ: RL=$w1, FL=$w2, RR=$w3, FR=$w4", Toast.LENGTH_SHORT).show()
        }
        btnRow.addView(btnSet)

        card.addView(btnRow)
        return card
    }

    private fun createDialogRobotSetpointCard(): View {
        val card = createCardContainer()
        card.addView(createSectionHeader("CÀI ĐẶT VỊ TRÍ & VẬN TỐC MỤC TIÊU (ROBOT SETPOINT)"))

        val setGrid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val r1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        dialogEtVx = createLabeledInput(r1, "Vx (m/s):", savedVx)
        dialogEtVy = createLabeledInput(r1, "Vy (m/s):", savedVy)
        dialogEtWSet = createLabeledInput(r1, "W (rad/s):", savedWSet)
        setGrid.addView(r1)

        setGrid.addView(createVerticalSpacer(dp(6f)))

        val r2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        dialogEtXSet = createLabeledInput(r2, "X (m):", savedXSet)
        dialogEtYSet = createLabeledInput(r2, "Y (m):", savedYSet)
        dialogEtThetaSet = createLabeledInput(r2, "θ (độ):", savedThetaSet)
        setGrid.addView(r2)

        card.addView(setGrid)
        card.addView(createVerticalSpacer(dp(10f)))

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val btnResetSetpoints = createOutlineActionButton("Reset") {
            if (!checkDrivingPermission(true)) return@createOutlineActionButton
            savedVx = "0"; savedVy = "0"; savedWSet = "0"
            savedXSet = "0"; savedYSet = "0"; savedThetaSet = "0"
            dialogEtVx?.setText("0"); dialogEtVy?.setText("0"); dialogEtWSet?.setText("0")
            dialogEtXSet?.setText("0"); dialogEtYSet?.setText("0"); dialogEtThetaSet?.setText("0")
            udpManager.resetRobotValues()
            ButtonSyncManager.sendButtonEvent("RESET_ROBOT", "CLICK")
            Toast.makeText(context, "Đã reset thông số về 0", Toast.LENGTH_SHORT).show()
        }
        btnRow.addView(btnResetSetpoints)

        btnRow.addView(createHorizontalSpacer(dp(8f)))

        val btnSetRobot = createSmallActionButton("Set Mục Tiêu", Color.parseColor("#1D5CE5")) {
            if (!checkDrivingPermission(true)) return@createSmallActionButton
            val vx = dialogEtVx?.text?.toString()?.toFloatOrNull() ?: 0f
            val vy = dialogEtVy?.text?.toString()?.toFloatOrNull() ?: 0f
            val w = dialogEtWSet?.text?.toString()?.toFloatOrNull() ?: 0f
            val x = dialogEtXSet?.text?.toString()?.toFloatOrNull() ?: 0f
            val y = dialogEtYSet?.text?.toString()?.toFloatOrNull() ?: 0f
            val th = dialogEtThetaSet?.text?.toString()?.toFloatOrNull() ?: 0f
            savedVx = vx.toString(); savedVy = vy.toString(); savedWSet = w.toString()
            savedXSet = x.toString(); savedYSet = y.toString(); savedThetaSet = th.toString()
            udpManager.setRobotValues(vx, vy, w, x, y, th)
            ButtonSyncManager.sendButtonEvent("SET_ROBOT", "CLICK", "$vx,$vy,$w,$x,$y,$th")
            Toast.makeText(context, "Đã gửi thông số Setpoint:\nVx=$vx, Vy=$vy, W=$w, X=$x, Y=$y, θ=$th°", Toast.LENGTH_SHORT).show()
        }
        btnRow.addView(btnSetRobot)

        card.addView(btnRow)
        return card
    }

    // =========================================================================
    // KIỂM TRA PHÂN QUYỀN & LẮNG NGHE SỰ KIỆN TỪ UDP MANAGER / BUTTON SYNC
    // =========================================================================

    private fun checkDrivingPermission(pressed: Boolean): Boolean {
        if (udpManager.permissionMode == RobotUdpManager.ControlPermission.VIEWER) {
            if (pressed) {
                Toast.makeText(
                    context,
                    "Đang ở chế độ CHỈ XEM! Mở [Cài đặt] để đổi quyền điều khiển.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }
        return true
    }

    fun updatePermissionStatusText(statusMsg: String) {
        post {
            if (::tvStatusBadge.isInitialized) {
                tvStatusBadge.text = statusMsg
                tvStatusBadge.setTextColor(Color.parseColor("#0284C7"))
            }
        }
    }

    fun updateUdpBannerState(connected: Boolean, statusText: String) {
        post {
            if (::tvStatusBadge.isInitialized) {
                tvStatusBadge.text = statusText
                tvStatusBadge.setTextColor(
                    if (connected) Color.parseColor("#15803D") else Color.parseColor("#64748B")
                )
            }
        }
    }

    private fun setupListeners() {
        udpManager.onTelemetryReceived = { t ->
            latestTelemetry = t
            post {
                tvMiniTelemetry.text = String.format(
                    Locale.US,
                    "Vx: %.3f m/s  ·  Vy: %.3f m/s  ·  θ: %.2f°",
                    t.vx, t.vy, t.thetaDeg
                )
                if (dialogTvX != null) {
                    updateDialogTelemetry(t)
                }
            }
        }

        ButtonSyncManager.addListener { btnId, action, extra ->
            post {
                when (btnId) {
                    "PERMISSION" -> {
                        if (action == "MASTER") {
                            udpManager.setPermission(
                                RobotUdpManager.ControlPermission.VIEWER,
                                "Chỉ xem (Máy khác đang giữ Độc quyền)"
                            )
                            Toast.makeText(context, "Thiết bị khác đã bật Độc quyền lái -> Chuyển sang Chỉ xem", Toast.LENGTH_SHORT).show()
                        } else if (action == "RELEASE") {
                            if (udpManager.permissionMode == RobotUdpManager.ControlPermission.VIEWER) {
                                udpManager.setPermission(
                                    RobotUdpManager.ControlPermission.AUTO,
                                    "Tự động (Đã mở khóa quyền lái)"
                                )
                                Toast.makeText(context, "Đã mở khóa quyền điều khiển -> Chế độ Tự động", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    "CLAIM_CONTROL" -> {
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote()
                            if (action == "DOWN") {
                                tvStatusBadge.text = "Thiết bị khác đang điều khiển..."
                                tvStatusBadge.setTextColor(Color.parseColor("#F59E0B"))
                            } else {
                                tvStatusBadge.text = "Đã kết nối UDP"
                                tvStatusBadge.setTextColor(Color.parseColor("#15803D"))
                            }
                        }
                    }
                    "UP" -> {
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote()
                        }
                        if (::joystickView.isInitialized) {
                            if (action == "DOWN") joystickView.setExternalKnob(0f, 1f) else joystickView.setExternalKnob(0f, 0f)
                        }
                    }
                    "DOWN" -> {
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote()
                        }
                        if (::joystickView.isInitialized) {
                            if (action == "DOWN") joystickView.setExternalKnob(0f, -1f) else joystickView.setExternalKnob(0f, 0f)
                        }
                    }
                    "LEFT" -> {
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote()
                        }
                        if (::joystickView.isInitialized) {
                            if (action == "DOWN") joystickView.setExternalKnob(-1f, 0f) else joystickView.setExternalKnob(0f, 0f)
                        }
                    }
                    "RIGHT" -> {
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote()
                        }
                        if (::joystickView.isInitialized) {
                            if (action == "DOWN") joystickView.setExternalKnob(1f, 0f) else joystickView.setExternalKnob(0f, 0f)
                        }
                    }
                    "TURN_LEFT" -> {
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote()
                        }
                        if (::btnTurnL.isInitialized) setTurnButtonHeld(btnTurnL, action == "DOWN")
                    }
                    "TURN_RIGHT" -> {
                        if (udpManager.permissionMode != RobotUdpManager.ControlPermission.MASTER) {
                            udpManager.yieldControlToRemote()
                        }
                        if (::btnTurnR.isInitialized) setTurnButtonHeld(btnTurnR, action == "DOWN")
                    }
                    "CIRCLE_LINE", "RETURN_HOME", "EMERGENCY" -> {
                        udpManager.yieldControlToRemote()
                    }
                    "RESET_WHEELS" -> {
                        savedW1 = "0"; savedW2 = "0"; savedW3 = "0"; savedW4 = "0"
                        dialogEtW1?.setText("0"); dialogEtW2?.setText("0"); dialogEtW3?.setText("0"); dialogEtW4?.setText("0")
                        udpManager.yieldControlToRemote()
                    }
                    "SET_WHEELS" -> {
                        if (!extra.isNullOrEmpty()) {
                            val nums = extra.split(",")
                            if (nums.size >= 4) {
                                savedW1 = nums[0]; savedW2 = nums[1]; savedW3 = nums[2]; savedW4 = nums[3]
                                dialogEtW1?.setText(nums[0]); dialogEtW2?.setText(nums[1]); dialogEtW3?.setText(nums[2]); dialogEtW4?.setText(nums[3])
                                udpManager.yieldControlToRemote()
                            }
                        }
                    }
                    "RESET_ROBOT" -> {
                        savedVx = "0"; savedVy = "0"; savedWSet = "0"
                        savedXSet = "0"; savedYSet = "0"; savedThetaSet = "0"
                        dialogEtVx?.setText("0"); dialogEtVy?.setText("0"); dialogEtWSet?.setText("0")
                        dialogEtXSet?.setText("0"); dialogEtYSet?.setText("0"); dialogEtThetaSet?.setText("0")
                        udpManager.yieldControlToRemote()
                    }
                    "SET_ROBOT" -> {
                        if (!extra.isNullOrEmpty()) {
                            val nums = extra.split(",")
                            if (nums.size >= 6) {
                                savedVx = nums[0]; savedVy = nums[1]; savedWSet = nums[2]
                                savedXSet = nums[3]; savedYSet = nums[4]; savedThetaSet = nums[5]
                                dialogEtVx?.setText(nums[0]); dialogEtVy?.setText(nums[1]); dialogEtWSet?.setText(nums[2])
                                dialogEtXSet?.setText(nums[3]); dialogEtYSet?.setText(nums[4]); dialogEtThetaSet?.setText(nums[5])
                                udpManager.yieldControlToRemote()
                            }
                        }
                    }
                    "GIMBAL_LEFT" -> if (::btnGimbalLeft.isInitialized) setGimbalHoldVisual(btnGimbalLeft, action == "DOWN")
                    "GIMBAL_UP" -> if (::btnGimbalUp.isInitialized) setGimbalHoldVisual(btnGimbalUp, action == "DOWN")
                    "GIMBAL_DOWN" -> if (::btnGimbalDown.isInitialized) setGimbalHoldVisual(btnGimbalDown, action == "DOWN")
                    "GIMBAL_RIGHT" -> if (::btnGimbalRight.isInitialized) setGimbalHoldVisual(btnGimbalRight, action == "DOWN")
                    "GIMBAL_RECENTER" -> if (::btnGimbalCenter.isInitialized) setGimbalHoldVisual(btnGimbalCenter, action == "DOWN" || action == "CLICK")
                    "GIMBAL_MODE" -> {
                        if (!extra.isNullOrBlank()) {
                            val m = GimbalBleManager.FollowMode.fromLabel(extra)
                            updateGimbalModeUi(m)
                        }
                    }
                    "AUTO_MODE" -> {
                        when (action) {
                            "MANUAL" -> {
                                autoFollowManager?.setOperatingMode(AutoFollowManager.OperatingMode.MANUAL)
                                updateOperatingModeUi(AutoFollowManager.OperatingMode.MANUAL)
                            }
                            "FOLLOW_50CM" -> {
                                autoFollowManager?.setOperatingMode(AutoFollowManager.OperatingMode.AUTO)
                                autoFollowManager?.setAutoSubMode(AutoFollowManager.AutoSubMode.FOLLOW_50CM)
                                updateOperatingModeUi(AutoFollowManager.OperatingMode.AUTO)
                                updateAutoSubModeUi(AutoFollowManager.AutoSubMode.FOLLOW_50CM)
                            }
                            "ORBIT_360" -> {
                                autoFollowManager?.setOperatingMode(AutoFollowManager.OperatingMode.AUTO)
                                autoFollowManager?.setAutoSubMode(AutoFollowManager.AutoSubMode.ORBIT_360)
                                updateOperatingModeUi(AutoFollowManager.OperatingMode.AUTO)
                                updateAutoSubModeUi(AutoFollowManager.AutoSubMode.ORBIT_360)
                            }
                        }
                    }
                    "AUTO_HOLD" -> {
                        autoFollowManager?.setHoldToRun(action == "DOWN")
                    }
                }
            }
        }

        autoFollowManager?.let { afm ->
            afm.onOperatingModeChanged = { mode ->
                post { updateOperatingModeUi(mode) }
            }
            afm.onAutoSubModeChanged = { subMode ->
                post { updateAutoSubModeUi(subMode) }
            }
            afm.onAutoStateChanged = { state, msg ->
                post { updateAutoStateUi(state, msg) }
            }
            afm.onTelemetryUpdated = { distanceCm, _, isSafe ->
                post {
                    if (::tvAutoDistance.isInitialized) {
                        tvAutoDistance.text = String.format(Locale.US, "Cự ly: %.1f cm", distanceCm)
                        when {
                            isSafe -> tvAutoDistance.setTextColor(Color.parseColor("#15803D")) // Green (40-60cm)
                            distanceCm < 38f -> tvAutoDistance.setTextColor(Color.parseColor("#DC2626")) // Red (<38cm)
                            else -> tvAutoDistance.setTextColor(Color.parseColor("#D97706")) // Amber
                        }
                    }
                }
            }
            afm.onOrbitProgress = { progressDeg ->
                post {
                    if (::tvAutoStatusBadge.isInitialized) {
                        tvAutoStatusBadge.text = String.format(Locale.US, "Quỹ đạo: %.0f° / 360°", progressDeg)
                        tvAutoStatusBadge.setTextColor(Color.parseColor("#1D4ED8"))
                    }
                }
            }
        }
    }

    fun updateOperatingModeUi(mode: AutoFollowManager.OperatingMode) {
        if (!::tabManual.isInitialized || !::tabAuto.isInitialized) return
        val isAuto = (mode == AutoFollowManager.OperatingMode.AUTO)

        tabManual.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(14f)
            setColor(if (!isAuto) Color.parseColor("#1D5CE5") else Color.TRANSPARENT)
        }
        tabManual.setTextColor(if (!isAuto) Color.WHITE else Color.parseColor("#64748B"))

        tabAuto.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(14f)
            setColor(if (isAuto) Color.parseColor("#1D5CE5") else Color.TRANSPARENT)
        }
        tabAuto.setTextColor(if (isAuto) Color.WHITE else Color.parseColor("#64748B"))

        if (::layoutManualContent.isInitialized) {
            layoutManualContent.visibility = if (!isAuto) View.VISIBLE else View.GONE
        }
        if (::layoutAutoContent.isInitialized) {
            layoutAutoContent.visibility = if (isAuto) View.VISIBLE else View.GONE
        }
    }

    fun updateAutoSubModeUi(subMode: AutoFollowManager.AutoSubMode) {
        if (!::btnAutoSubFollow.isInitialized || !::btnAutoSubOrbit.isInitialized) return
        val isFollow = (subMode == AutoFollowManager.AutoSubMode.FOLLOW_50CM)

        btnAutoSubFollow.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(14f)
            setColor(if (isFollow) Color.parseColor("#2563EB") else Color.parseColor("#F1F5F9"))
            if (!isFollow) setStroke(dp(1f), Color.parseColor("#CBD5E1"))
        }
        btnAutoSubFollow.setTextColor(if (isFollow) Color.WHITE else Color.parseColor("#475569"))

        btnAutoSubOrbit.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(14f)
            setColor(if (!isFollow) Color.parseColor("#2563EB") else Color.parseColor("#F1F5F9"))
            if (isFollow) setStroke(dp(1f), Color.parseColor("#CBD5E1"))
        }
        btnAutoSubOrbit.setTextColor(if (!isFollow) Color.WHITE else Color.parseColor("#475569"))
    }

    fun updateAutoStateUi(state: AutoFollowManager.AutoState, msg: String) {
        if (!::tvAutoStatusBadge.isInitialized) return
        tvAutoStatusBadge.text = msg
        val (bgCol, textCol) = when (state) {
            AutoFollowManager.AutoState.TRACKING, AutoFollowManager.AutoState.ORBITING ->
                Pair(Color.parseColor("#DCFCE7"), Color.parseColor("#15803D"))
            AutoFollowManager.AutoState.LOCKED ->
                Pair(Color.parseColor("#DBEAFE"), Color.parseColor("#1D4ED8"))
            AutoFollowManager.AutoState.SAFETY_STOP ->
                Pair(Color.parseColor("#FEE2E2"), Color.parseColor("#DC2626"))
            AutoFollowManager.AutoState.LOST ->
                Pair(Color.parseColor("#FEF3C7"), Color.parseColor("#D97706"))
            else ->
                Pair(Color.parseColor("#F1F5F9"), Color.parseColor("#475569"))
        }
        tvAutoStatusBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(12f)
            setColor(bgCol)
        }
        tvAutoStatusBadge.setTextColor(textCol)
    }

    private fun updateGimbalModeUi(mode: GimbalBleManager.FollowMode) {
        controllerGimbalMode = mode
        if (::btnGimbalMode.isInitialized) {
            btnGimbalMode.text = mode.label
        }
        val isFpv = (mode == GimbalBleManager.FollowMode.FPV)
        if (::btnGimbalLeft.isInitialized) {
            btnGimbalLeft.visibility = if (isFpv) View.GONE else View.VISIBLE
        }
        if (::btnGimbalRight.isInitialized) {
            btnGimbalRight.visibility = if (isFpv) View.GONE else View.VISIBLE
        }
    }

    private fun setTurnButtonHeld(button: View, isHeld: Boolean) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(20f)
            setColor(if (isHeld) Color.parseColor("#1342A8") else Color.parseColor("#1D5CE5"))
        }
        button.background = bg
        button.scaleX = if (isHeld) 0.94f else 1.0f
        button.scaleY = if (isHeld) 0.94f else 1.0f
    }

    // =========================================================================
    // UI BUILDER HELPERS
    // =========================================================================

    private fun createCardContainer(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(16f)
                setColor(Color.WHITE)
                setStroke(dp(1f), Color.parseColor("#E2E8F0"))
            }
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createSectionHeader(title: String): TextView {
        return TextView(context).apply {
            text = title
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1D5CE5"))
            letterSpacing = 0.03f
            setPadding(0, 0, 0, dp(8f))
        }
    }

    private fun createStyledEditText(defaultText: String): EditText {
        return EditText(context).apply {
            setText(defaultText)
            textSize = 11.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(10f)
                setColor(Color.parseColor("#EEF3FB"))
                setStroke(dp(1f), Color.parseColor("#CBD5E1"))
            }
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun createTelemetryPill(parent: LinearLayout, label: String, value: String): TextView {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(12f)
                setColor(Color.parseColor("#EEF3FB"))
            }
            setPadding(dp(4f), dp(8f), dp(4f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3f), 0, dp(3f), 0)
            }
        }

        val tvLbl = TextView(context).apply {
            text = label
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#475569"))
            gravity = Gravity.CENTER
        }
        pill.addView(tvLbl)

        val tvVal = TextView(context).apply {
            text = value
            textSize = 12f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#1D5CE5"))
            gravity = Gravity.CENTER
            setPadding(0, dp(2f), 0, 0)
        }
        pill.addView(tvVal)

        parent.addView(pill)
        return tvVal
    }

    private fun createLabeledInput(parent: LinearLayout, label: String, defaultVal: String): EditText {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3f), 0, dp(3f), 0)
            }
        }

        val lbl = TextView(context).apply {
            text = label
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, 0, 0, dp(3f))
        }
        container.addView(lbl)

        val et = createStyledEditText(defaultVal).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34f))
        }
        container.addView(et)

        parent.addView(container)
        return et
    }

    private fun createOutlineActionButton(text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(16f)
                setColor(Color.WHITE)
                setStroke(dp(1.2f), Color.parseColor("#CBD5E1"))
            }
            setPadding(dp(16f), dp(6f), dp(16f), dp(6f))
            setOnClickListener { onClick() }
        }
    }

    private fun createSmallActionButton(text: String, color: Int, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(16f)
                setColor(color)
            }
            setPadding(dp(16f), dp(6f), dp(16f), dp(6f))
            setOnClickListener { onClick() }
        }
    }

    private fun createHorizontalSpacer(w: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(w, 1)
    }

    private fun createVerticalSpacer(h: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, h)
    }

    // =========================================================================
    // MECANUM ANALOG JOYSTICK VIEW (360° OMNIDIRECTIONAL VIRTUAL JOYSTICK)
    // =========================================================================
    @SuppressLint("ClickableViewAccessibility")
    class MecanumJoystickView(context: Context) : View(context) {

        private fun dpF(v: Float): Float = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
        )

        var onJoystickMoved: ((nx: Float, ny: Float, active: Boolean) -> Unit)? = null

        private var knobOffsetX = 0f
        private var knobOffsetY = 0f
        private var isDragging = false
        private var activePointerId = MotionEvent.INVALID_POINTER_ID
        private var returnAnimator: ValueAnimator? = null

        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5FFFFFF")
            style = Paint.Style.FILL
        }

        private val baseBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D8E6FA")
            style = Paint.Style.STROKE
            strokeWidth = dpF(1.5f)
        }

        private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1D5CE5")
            style = Paint.Style.STROKE
            strokeWidth = dpF(2.5f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val knobShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#331D5CE5")
            style = Paint.Style.FILL
        }

        private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1D5CE5")
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val outerRadius = (width.coerceAtMost(height) / 2f) - dpF(3f)
            if (outerRadius <= 0f) return

            // 1. Vẽ đĩa tròn nền trắng
            canvas.drawCircle(cx, cy, outerRadius, basePaint)
            canvas.drawCircle(cx, cy, outerRadius, baseBorderPaint)

            // 2. Vẽ 4 mũi tên Chevron (^, v, <, >) ở 4 hướng
            val chevronDist = outerRadius * 0.74f
            val chevSize = dpF(5.5f)

            // Top (^)
            val upPath = Path().apply {
                val y = cy - chevronDist
                moveTo(cx - chevSize, y + chevSize * 0.45f)
                lineTo(cx, y - chevSize * 0.45f)
                lineTo(cx + chevSize, y + chevSize * 0.45f)
            }
            chevronPaint.alpha = if (knobOffsetY < -outerRadius * 0.15f) 255 else 210
            canvas.drawPath(upPath, chevronPaint)

            // Bottom (v)
            val downPath = Path().apply {
                val y = cy + chevronDist
                moveTo(cx - chevSize, y - chevSize * 0.45f)
                lineTo(cx, y + chevSize * 0.45f)
                lineTo(cx + chevSize, y - chevSize * 0.45f)
            }
            chevronPaint.alpha = if (knobOffsetY > outerRadius * 0.15f) 255 else 210
            canvas.drawPath(downPath, chevronPaint)

            // Left (<)
            val leftPath = Path().apply {
                val x = cx - chevronDist
                moveTo(x + chevSize * 0.45f, cy - chevSize)
                lineTo(x - chevSize * 0.45f, cy)
                lineTo(x + chevSize * 0.45f, cy + chevSize)
            }
            chevronPaint.alpha = if (knobOffsetX < -outerRadius * 0.15f) 255 else 210
            canvas.drawPath(leftPath, chevronPaint)

            // Right (>)
            val rightPath = Path().apply {
                val x = cx + chevronDist
                moveTo(x - chevSize * 0.45f, cy - chevSize)
                lineTo(x + chevSize * 0.45f, cy)
                lineTo(x - chevSize * 0.45f, cy + chevSize)
            }
            chevronPaint.alpha = if (knobOffsetX > outerRadius * 0.15f) 255 else 210
            canvas.drawPath(rightPath, chevronPaint)

            // 3. Vẽ núm điều khiển tròn màu xanh ở tâm (hoặc lệch theo ngón tay)
            val knobRadius = outerRadius * 0.39f
            val kx = cx + knobOffsetX
            val ky = cy + knobOffsetY
            canvas.drawCircle(kx, ky + dpF(2f), knobRadius + dpF(1.5f), knobShadowPaint)
            canvas.drawCircle(kx, ky, knobRadius, knobPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val cx = width / 2f
            val cy = height / 2f
            val outerRadius = (width.coerceAtMost(height) / 2f) - dpF(3f)
            val maxTravel = (outerRadius * 0.46f).coerceAtLeast(1f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    returnAnimator?.cancel()
                    isDragging = true
                    activePointerId = event.getPointerId(0)
                    updateKnobFromTouch(event.x, event.y, cx, cy, maxTravel)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging) return false
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    val idx = if (pointerIndex >= 0) pointerIndex else 0
                    updateKnobFromTouch(event.getX(idx), event.getY(idx), cx, cy, maxTravel)
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)
                    if (pointerId == activePointerId) {
                        val newPointerIndex = if (pointerIndex == 0) 1 else 0
                        if (newPointerIndex < event.pointerCount) {
                            activePointerId = event.getPointerId(newPointerIndex)
                            updateKnobFromTouch(
                                event.getX(newPointerIndex),
                                event.getY(newPointerIndex),
                                cx, cy, maxTravel
                            )
                        } else {
                            releaseJoystick()
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    releaseJoystick()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun updateKnobFromTouch(tx: Float, ty: Float, cx: Float, cy: Float, maxTravel: Float) {
            val dx = tx - cx
            val dy = ty - cy
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (dist > maxTravel) {
                val angle = atan2(dy.toDouble(), dx.toDouble())
                knobOffsetX = (cos(angle) * maxTravel).toFloat()
                knobOffsetY = (sin(angle) * maxTravel).toFloat()
            } else {
                knobOffsetX = dx
                knobOffsetY = dy
            }
            invalidate()

            val nx = (knobOffsetX / maxTravel).coerceIn(-1f, 1f)
            val ny = (-knobOffsetY / maxTravel).coerceIn(-1f, 1f)
            onJoystickMoved?.invoke(nx, ny, true)
        }

        private fun releaseJoystick() {
            isDragging = false
            activePointerId = MotionEvent.INVALID_POINTER_ID
            onJoystickMoved?.invoke(0f, 0f, false)
            animateReturnToCenter()
        }

        fun setExternalKnob(nx: Float, ny: Float) {
            if (isDragging) return
            val outerRadius = (width.coerceAtMost(height) / 2f) - dpF(3f)
            val maxTravel = (outerRadius * 0.46f).coerceAtLeast(1f)
            knobOffsetX = nx.coerceIn(-1f, 1f) * maxTravel
            knobOffsetY = -ny.coerceIn(-1f, 1f) * maxTravel
            invalidate()
        }

        private fun animateReturnToCenter() {
            val startX = knobOffsetX
            val startY = knobOffsetY
            returnAnimator?.cancel()
            returnAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 140
                interpolator = OvershootInterpolator(1.4f)
                addUpdateListener { anim ->
                    val f = anim.animatedValue as Float
                    knobOffsetX = startX * f
                    knobOffsetY = startY * f
                    invalidate()
                }
                start()
            }
        }
    }
}
