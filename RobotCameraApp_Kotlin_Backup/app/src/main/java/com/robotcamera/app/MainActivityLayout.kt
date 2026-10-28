@file:Suppress("unused")

package com.robotcamera.app

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.camera.view.PreviewView
import com.robotcamera.app.MainActivity.AppRole
import com.robotcamera.app.MainActivity.CameraMode

/**
 * Dựng giao diện gốc (setupUI) và các cụm nút của màn hình Streaming:
 * - Góc trên trái: Thanh Pill trắng hợp nhất [Cài đặt] [Chọn vai trò] IP: ... [Gimbal: BLE]
 * - Góc trên phải: Pill trắng [• LIVE]
 * - Cạnh phải: Nút vuông trắng bo góc [T]
 * - Chính giữa dưới: Thanh [ẢNH | VIDEO] + Dock trắng nổi chứa Zoom Pill (.5 | 1x | 2) và 3 nút tròn (Thư viện, Chụp xanh, Đổi camera).
 */

internal fun MainActivity.setupUI() {
    rootLayout = RelativeLayout(this).apply {
        setBackgroundColor(Color.parseColor("#D6E6FC"))
    }

    // 1. Khung xem trước Camera toàn màn hình
    previewView = PreviewView(this).apply {
        layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
    rootLayout.addView(previewView)

    // 2. Viewfinder Overlay: Khung ngắm bo góc trung tâm tinh tế
    viewfinderOverlay = ViewfinderOverlayView(this).apply {
        layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
    }
    rootLayout.addView(viewfinderOverlay)

    // 3. Lớp chớp sáng màn trập (Shutter Flash)
    shutterFlashOverlay = View(this).apply {
        layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.WHITE)
        alpha = 0f
        visibility = View.GONE
    }
    rootLayout.addView(shutterFlashOverlay)

    // 4. Vòng tròn lấy nét chạm màn hình (Focus Reticle)
    focusReticle = View(this).apply {
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16f).toFloat()
            setStroke(dp(2f), Color.parseColor("#1D5CE5"))
            setColor(Color.parseColor("#181D5CE5"))
        }
        background = ring
        layoutParams = RelativeLayout.LayoutParams(dp(64f), dp(64f))
        visibility = View.GONE
    }
    rootLayout.addView(focusReticle)

    // 4b. Lớp phủ FPV HUD
    fpvHudOverlay = FpvHudOverlayView(this).apply {
        layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        visibility = View.GONE
    }
    rootLayout.addView(fpvHudOverlay)

    // 5. Container chứa toàn bộ các nút điều khiển của màn hình Streaming
    overlayContainer = RelativeLayout(this).apply {
        layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
    }

    // 6b. Khung xem trước FPV từ xa (Remote MJPEG View) cho chế độ Điều khiển
    remoteMjpegView = RemoteMjpegView(this).apply {
        layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        visibility = View.GONE
        onFrameReceived = { jpegBytes ->
            if (isStreaming && mjpegServer != null) {
                mjpegServer?.pushJpegBytes(jpegBytes)
            }
        }
        onSwitchToLocalCamRequested = {
            runOnUiThread {
                switchToLocalCamMode()
            }
        }
    }
    rootLayout.addView(remoteMjpegView)

    // 6. Quản lý kết nối Robot Mecanum qua UDP & Chế độ Tự hành (Auto Follow)
    udpManager = RobotUdpManager().apply {
        onConnectionStateChanged = { connected, status ->
            updateUdpUiState(connected, status)
        }
    }

    autoFollowManager = AutoFollowManager(udpManager)
    visualDistanceDetector = VisualDistanceDetector().apply {
        onTargetDetected = { target ->
            autoFollowManager.onTargetDetected(target)
            remoteMjpegView.updateTarget(target)
            fpvHudOverlay.currentTarget = target
        }
        onTargetLost = {
            autoFollowManager.onTargetLost()
            remoteMjpegView.updateTarget(null)
            fpvHudOverlay.currentTarget = null
        }
    }

    // Kết nối giải mã Bitmap FPV từ xa vào động cơ thị giác máy tính
    remoteMjpegView.onBitmapDecoded = { bmp: Bitmap ->
        visualDistanceDetector.processBitmap(bmp)
    }

    // 6b. Bảng điều khiển Robot Mecanum (Full-Screen Floating Cockpit)
    controlDashboardView = RobotControlDashboardView(this, udpManager, autoFollowManager).apply {
        layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        visibility = View.GONE
        onSwitchRoleRequested = {
            runOnUiThread {
                showRoleSelection()
            }
        }
        onTakePhotoRequested = {
            runOnUiThread {
                if (currentRole == AppRole.STREAMER || controllerCamSource == "LOCAL") {
                    triggerShutterFlash()
                    takePhoto()
                } else {
                    sendHttpCommandToStreamer("/snap")
                }
            }
        }
        onStartRecordRequested = {
            runOnUiThread {
                if (currentRole == AppRole.STREAMER || controllerCamSource == "LOCAL") {
                    if (!isRecordingVideo) {
                        startVideoRecording()
                    }
                } else {
                    sendHttpCommandToStreamer("/record/start")
                }
            }
        }
        onStopRecordRequested = {
            runOnUiThread {
                if (currentRole == AppRole.STREAMER || controllerCamSource == "LOCAL") {
                    if (isRecordingVideo) {
                        stopVideoRecording()
                    }
                } else {
                    sendHttpCommandToStreamer("/record/stop")
                }
            }
        }
        onSwitchCameraRequested = {
            runOnUiThread {
                if (currentRole == AppRole.STREAMER || controllerCamSource == "LOCAL") {
                    switchCamera()
                } else {
                    sendHttpCommandToStreamer("/switch")
                }
            }
        }
    }
    rootLayout.addView(controlDashboardView)

    // 6c. Thanh công cụ nổi IP cho Camera máy này (khi ở chế độ Điều khiển)
    controllerLocalCamBar = createControllerLocalCamBar()
    rootLayout.addView(controllerLocalCamBar)

    // 6d. Thanh công cụ nổi góc trái trên (Cài đặt, Chọn vai trò, IP UDP)
    controllerTopLeftBar = createControllerTopLeftBar()
    rootLayout.addView(controllerTopLeftBar)

    setupTopBar(overlayContainer)
    setupSmartScanButton(overlayContainer)
    setupZoomBar(overlayContainer)
    setupBottomControls(overlayContainer)

    rootLayout.addView(overlayContainer)

    // 7. Màn hình Khởi động Chọn Vai trò (Role Selection View)
    roleSelectionView = RoleSelectionView(this).apply {
        layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        visibility = View.GONE
        onRoleSelected = { role, remember ->
            runOnUiThread {
                val appRole = when (role) {
                    RoleSelectionView.Role.CONTROLLER -> AppRole.CONTROLLER
                    RoleSelectionView.Role.STREAMER -> AppRole.STREAMER
                }
                applyRole(appRole, remember)
            }
        }
    }
    rootLayout.addView(roleSelectionView)

    setContentView(rootLayout)

    updateIpDisplay()
}

/**
 * Thanh công cụ đỉnh (Top Bar) của màn hình Streaming:
 * - Góc trái trên: Pill trắng hợp nhất [Cài đặt] [Chọn vai trò] IP: 192.168.x.x [Gimbal: BLE]
 * - Góc phải trên: Pill trắng [• LIVE]
 */
internal fun MainActivity.setupTopBar(container: RelativeLayout) {
    topBarLayout = LinearLayout(this).apply {
        id = View.generateViewId()
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14f), dp(10f), dp(14f), dp(4f))
        layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
        }
    }

    // 1. PILL TRẮNG HỢP NHẤT GÓC TRÊN TRÁI
    val leftUnifiedPill = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(26f).toFloat()
            setColor(Color.parseColor("#F5FFFFFF"))
            setStroke(dp(1f), Color.parseColor("#E2E8F0"))
        }
        elevation = dp(6f).toFloat()
        setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
    }

    // [Cài đặt]
    val btnSettingsPill = TextView(this).apply {
        text = "Cài đặt"
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#0F172A"))
        gravity = Gravity.CENTER
        isSingleLine = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16f).toFloat()
            setColor(Color.WHITE)
            setStroke(dp(1.2f), Color.parseColor("#CBD5E1"))
        }
        setPadding(dp(14f), dp(6f), dp(14f), dp(6f))
        applySpringTouchEffect(this)
        setOnClickListener { showSettingsDialog() }
    }
    leftUnifiedPill.addView(btnSettingsPill)

    leftUnifiedPill.addView(createHorizontalSpacer(dp(8f)))

    // [Chọn vai trò]
    val btnSwitchRolePill = TextView(this).apply {
        text = "Chọn vai trò"
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#0F172A"))
        gravity = Gravity.CENTER
        isSingleLine = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16f).toFloat()
            setColor(Color.WHITE)
            setStroke(dp(1.2f), Color.parseColor("#CBD5E1"))
        }
        setPadding(dp(14f), dp(6f), dp(14f), dp(6f))
        applySpringTouchEffect(this)
        setOnClickListener { showRoleSelection() }
    }
    leftUnifiedPill.addView(btnSwitchRolePill)

    leftUnifiedPill.addView(createHorizontalSpacer(dp(12f)))

    // IP: 192.168.x.x nằm gọn bên trong thanh Pill trắng
    setupIpBadge(leftUnifiedPill)

    leftUnifiedPill.addView(createHorizontalSpacer(dp(12f)))

    // [Gimbal: BLE]
    val btnGimbalPill = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16f).toFloat()
            setColor(Color.parseColor("#DCE8FD"))
        }
        setPadding(dp(14f), dp(6f), dp(14f), dp(6f))

        tvGimbalStatusBadge = TextView(this@setupTopBar).apply {
            text = "Gimbal: BLE"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1D5CE5"))
            gravity = Gravity.CENTER
            isSingleLine = true
        }
        addView(tvGimbalStatusBadge)

        applySpringTouchEffect(this)
        setOnClickListener { showGimbalBleDialog() }
    }
    leftUnifiedPill.addView(btnGimbalPill)

    GimbalBleManager.addStateListener { connected, _ ->
        if (tvGimbalStatusBadgeInitialized) {
            if (connected) {
                val rx = GimbalBleManager.rxPacketCount
                tvGimbalStatusBadge.text = if (rx > 0) "● Gimbal: Đã nối" else "● Gimbal: RS3 Mini"
                tvGimbalStatusBadge.setTextColor(Color.parseColor("#15803D"))
                (btnGimbalPill.background as? GradientDrawable)?.setColor(Color.parseColor("#DCFCE7"))
            } else {
                tvGimbalStatusBadge.text = "Gimbal: BLE"
                tvGimbalStatusBadge.setTextColor(Color.parseColor("#1D5CE5"))
                (btnGimbalPill.background as? GradientDrawable)?.setColor(Color.parseColor("#DCE8FD"))
            }
        }
    }

    topBarLayout.addView(leftUnifiedPill)

    // Giữ tham chiếu tvFlashIcon và tvResolutionBadge (ẩn để giao diện sạch đúng mockup)
    tvFlashIcon = TextView(this).apply {
        text = "Flash"
        visibility = View.GONE
    }
    topBarLayout.addView(tvFlashIcon)

    tvResolutionBadge = TextView(this).apply {
        text = "720p"
        visibility = View.GONE
    }
    topBarLayout.addView(tvResolutionBadge)

    topBarLayout.addView(createFlexSpacer())

    // Timer đếm giờ quay phim (chỉ hiện khi đang quay video)
    tvRecTimerBadge = TextView(this).apply {
        text = "● REC 00:00"
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#DC2626"))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16f).toFloat()
            setColor(Color.parseColor("#F5FFFFFF"))
            setStroke(dp(1f), Color.parseColor("#FCA5A5"))
        }
        elevation = dp(4f).toFloat()
        setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        visibility = View.GONE
    }
    topBarLayout.addView(tvRecTimerBadge)

    topBarLayout.addView(createHorizontalSpacer(dp(8f)))

    // 2. PILL TRẮNG [• LIVE] Ở GÓC TRÊN PHẢI
    btnMotionLiveIcon = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18f).toFloat()
            setColor(Color.parseColor("#F5FFFFFF"))
            setStroke(dp(1f), Color.parseColor("#E2E8F0"))
        }
        elevation = dp(6f).toFloat()
        setPadding(dp(14f), dp(6f), dp(14f), dp(6f))

        tvMotionLiveText = TextView(this@setupTopBar).apply {
            text = "• LIVE"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#15803D"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        addView(tvMotionLiveText)
        setOnClickListener { showStreamInfoDialog() }
    }
    topBarLayout.addView(btnMotionLiveIcon)

    container.addView(topBarLayout)
}

internal fun MainActivity.setupIpBadge(container: LinearLayout) {
    val ipContainer = LinearLayout(this).apply {
        id = View.generateViewId()
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(2f), dp(4f), dp(2f), dp(4f))

        tvIpDisplayBadge = TextView(this@setupIpBadge).apply {
            text = "IP: Đang kết nối..."
            textSize = 10.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            maxLines = 1
        }
        addView(tvIpDisplayBadge)

        applySpringTouchEffect(this)
        setOnClickListener {
            copyStreamUrlToClipboard()
        }
    }
    ipBadgeContainer = ipContainer
    container.addView(ipContainer)
}

/**
 * Nút Scan thông minh [T] nền trắng bo góc ở cạnh phải màn hình
 */
internal fun MainActivity.setupSmartScanButton(container: RelativeLayout) {
    btnSmartScan = FrameLayout(this).apply {
        val size = dp(44f)
        val params = RelativeLayout.LayoutParams(size, size).apply {
            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            addRule(RelativeLayout.CENTER_VERTICAL)
            rightMargin = dp(18f)
        }
        layoutParams = params

        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14f).toFloat()
            setColor(Color.parseColor("#F5FFFFFF"))
            setStroke(dp(1f), Color.parseColor("#E2E8F0"))
        }
        elevation = dp(6f).toFloat()

        val tvIcon = TextView(this@setupSmartScanButton).apply {
            text = "[T]"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        addView(tvIcon)

        applySpringTouchEffect(this)
        setOnClickListener {
            copyStreamUrlToClipboard()
        }
    }
    container.addView(btnSmartScan)
}

/**
 * Khởi tạo cụm Zoom Segmented Pill (.5 | 1x | 2) để đặt vào trong Dock trắng ở đáy
 */
internal fun MainActivity.setupZoomBar(container: RelativeLayout) {
    zoomContainer = LinearLayout(this).apply {
        id = View.generateViewId()
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(10f)
        }
    }

    zoomPillContainer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18f).toFloat()
            setColor(Color.parseColor("#EEF4FF"))
        }
        setPadding(dp(4f), dp(3f), dp(4f), dp(3f))
    }

    val zoomLevels = listOf(0.6f, 1.0f, 2.0f)
    for (zoom in zoomLevels) {
        val label = when (zoom) {
            0.6f -> ".5"
            1.0f -> "1x"
            else -> "2"
        }
        val chip = TextView(this).apply {
            text = label
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(34f), dp(28f)).apply {
                setMargins(dp(2f), 0, dp(2f), 0)
            }
            setOnClickListener { selectZoomRatio(zoom) }
        }
        zoomChips[zoom] = chip
        zoomPillContainer.addView(chip)
    }
    zoomContainer.addView(zoomPillContainer)

    updateZoomChipsUi(1.0f)
}

/**
 * Cụm điều khiển đáy với DOCK TRẮNG NỔI (Floating White Control Dock):
 * - Phía trên Dock: Dải chọn chế độ [ẢNH]  [VIDEO]
 * - Bên trong Dock trắng:
 *   + Hàng 1: Viên thuốc Zoom (.5 | 1x | 2)
 *   + Hàng 2: Nút Thư viện tròn xanh nhạt - Nút Chụp tròn xanh đậm - Nút Đổi Camera tròn xanh nhạt
 */
internal fun MainActivity.setupBottomControls(container: RelativeLayout) {
    val bottomWrapper = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            addRule(RelativeLayout.CENTER_HORIZONTAL)
            bottomMargin = dp(14f)
        }
    }

    // 1. DẢI CHỌN CHẾ ĐỘ [ẢNH] & [VIDEO] NẰM TRÊN DOCK TRẮNG
    val modeBar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(6f)
        }
    }

    tvModePhoto = TextView(this).apply {
        text = "ẢNH"
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#1D5CE5"))
        setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
        setOnClickListener { switchMode(CameraMode.PHOTO) }
    }
    modeBar.addView(tvModePhoto)

    modeBar.addView(createHorizontalSpacer(dp(8f)))

    tvModeVideo = TextView(this).apply {
        text = "VIDEO"
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#64748B"))
        setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
        setOnClickListener { switchMode(CameraMode.VIDEO) }
    }
    modeBar.addView(tvModeVideo)

    tvModeControl = TextView(this).apply {
        text = "ĐIỀU KHIỂN"
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#64748B"))
        setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
        visibility = View.GONE
        setOnClickListener { switchMode(CameraMode.CONTROL) }
    }
    modeBar.addView(tvModeControl)

    bottomWrapper.addView(modeBar)

    // 2. DOCK TRẮNG BO TRÒN NỔI (FLOATING WHITE DOCK)
    val whiteDock = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(26f).toFloat()
            setColor(Color.parseColor("#F5FFFFFF"))
            setStroke(dp(1f), Color.parseColor("#E2E8F0"))
        }
        elevation = dp(8f).toFloat()
        setPadding(dp(28f), dp(10f), dp(28f), dp(12f))
        minimumWidth = dp(430f)
        layoutParams = LinearLayout.LayoutParams(
            dp(430f),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // Đưa thanh Zoom (.5 | 1x | 2) vào hàng trên của Dock trắng
    (zoomContainer.parent as? android.view.ViewGroup)?.removeView(zoomContainer)
    whiteDock.addView(zoomContainer)

    // Hàng 3 nút chính: Gallery (trái) - Shutter (giữa) - Switch Camera (phải)
    mainActionRow = RelativeLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            dp(240f),
            dp(60f)
        )
    }

    // --- NÚT BỘ SƯU TẬP (GALLERY THUMBNAIL) ---
    btnGalleryThumbnail = ImageView(this).apply {
        val size = dp(42f)
        layoutParams = RelativeLayout.LayoutParams(size, size).apply {
            addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#EEF4FF"))
        }
        setImageDrawable(createBlueGalleryIconDrawable())
        applySpringTouchEffect(this)
        setOnClickListener { openGallery() }
    }
    mainActionRow.addView(btnGalleryThumbnail)

    // --- NÚT SHUTTER TRÒN XANH HOÀNG GIA ---
    shutterContainer = RelativeLayout(this).apply {
        val size = dp(58f)
        layoutParams = RelativeLayout.LayoutParams(size, size).apply {
            addRule(RelativeLayout.CENTER_IN_PARENT)
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(2.8f), Color.parseColor("#1D5CE5"))
        }
    }

    shutterCore = View(this).apply {
        val coreSize = dp(44f)
        layoutParams = RelativeLayout.LayoutParams(coreSize, coreSize).apply {
            addRule(RelativeLayout.CENTER_IN_PARENT)
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#1D5CE5"))
        }
    }
    shutterContainer.addView(shutterCore)

    applyShutterTouchAnimation(shutterContainer, shutterCore) {
        handleShutterClick()
    }
    mainActionRow.addView(shutterContainer)

    // --- NÚT ĐỔI CAMERA TRƯỚC / SAU ---
    btnSwitchCamera = FrameLayout(this).apply {
        val size = dp(42f)
        layoutParams = RelativeLayout.LayoutParams(size, size).apply {
            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#EEF4FF"))
        }

        ivSwitchCameraIcon = ImageView(this@setupBottomControls).apply {
            setImageDrawable(createBlueFlipCameraDrawable())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        addView(ivSwitchCameraIcon)

        applySpringTouchEffect(this)
        setOnClickListener {
            ivSwitchCameraIcon.animate().rotationBy(180f).setDuration(280).start()
            switchCamera()
            ButtonSyncManager.sendButtonEvent("SWITCH_CAMERA", "CLICK")
        }
    }
    mainActionRow.addView(btnSwitchCamera)

    whiteDock.addView(mainActionRow)
    bottomWrapper.addView(whiteDock)
    container.addView(bottomWrapper)
}

private fun MainActivity.createBlueGalleryIconDrawable(): BitmapDrawable {
    val sizePx = dp(22f).coerceAtLeast(24)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D5CE5")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val pad = dp(3f).toFloat()
    val rect = RectF(pad, pad + dp(1f), sizePx - pad, sizePx - pad - dp(1f))
    c.drawRoundRect(rect, dp(3f).toFloat(), dp(3f).toFloat(), p)

    // Mặt trời nhỏ
    val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D5CE5")
        style = Paint.Style.FILL
    }
    c.drawCircle(rect.left + rect.width() * 0.32f, rect.top + rect.height() * 0.34f, dp(1.5f).toFloat(), sunPaint)

    // Đường núi
    val mountain = Path().apply {
        moveTo(rect.left + dp(2f), rect.bottom - dp(2.5f))
        lineTo(rect.left + rect.width() * 0.45f, rect.top + rect.height() * 0.52f)
        lineTo(rect.left + rect.width() * 0.68f, rect.top + rect.height() * 0.72f)
        lineTo(rect.right - dp(2f), rect.top + rect.height() * 0.56f)
    }
    c.drawPath(mountain, p)
    return BitmapDrawable(resources, bmp)
}

private fun MainActivity.createBlueFlipCameraDrawable(): BitmapDrawable {
    val sizePx = dp(22f).coerceAtLeast(24)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D5CE5")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val r = dp(6.5f).toFloat()
    val oval = RectF(cx - r, cy - r, cx + r, cy + r)
    c.drawArc(oval, -150f, 120f, false, p)
    c.drawArc(oval, 30f, 120f, false, p)

    // Mũi tên 1 (góc trên phải)
    val a1 = Path().apply {
        val x = cx + r * 0.86f
        val y = cy - r * 0.5f
        moveTo(x - dp(3.2f), y - dp(1f))
        lineTo(x, y)
        lineTo(x + dp(0.8f), y - dp(3.2f))
    }
    c.drawPath(a1, p)

    // Mũi tên 2 (góc dưới trái)
    val a2 = Path().apply {
        val x = cx - r * 0.86f
        val y = cy + r * 0.5f
        moveTo(x + dp(3.2f), y + dp(1f))
        lineTo(x, y)
        lineTo(x - dp(0.8f), y + dp(3.2f))
    }
    c.drawPath(a2, p)
    return BitmapDrawable(resources, bmp)
}

internal fun MainActivity.switchMode(mode: CameraMode) {
    currentMode = mode

    val normalColor = Color.parseColor("#64748B")
    val activeColor = Color.parseColor("#1D5CE5")

    tvModePhoto.setTextColor(if (mode == CameraMode.PHOTO) activeColor else normalColor)
    tvModeVideo.setTextColor(if (mode == CameraMode.VIDEO) activeColor else normalColor)
    tvModeControl.setTextColor(if (mode == CameraMode.CONTROL) activeColor else normalColor)

    val activeTv = when (mode) {
        CameraMode.PHOTO -> tvModePhoto
        CameraMode.VIDEO -> tvModeVideo
        CameraMode.CONTROL -> tvModeControl
    }
    activeTv.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).withEndAction {
        activeTv.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
    }.start()

    if (mode == CameraMode.CONTROL) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        topBarLayout.visibility = View.GONE
        viewfinderOverlay.visibility = View.GONE
        mainActionRow.visibility = View.GONE
        if (zoomContainerInitialized) zoomContainer.visibility = View.GONE
        if (btnSmartScanInitialized) btnSmartScan.visibility = View.GONE
        if (ipBadgeContainerInitialized) ipBadgeContainer.visibility = View.GONE
        tvRecTimerBadge.visibility = View.GONE
        if (isRecordingVideo) stopVideoRecording()

        rootLayout.post { updateControlLayoutDimensions() }
    } else {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

        previewView.layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        previewView.visibility = View.VISIBLE
        if (fpvHudOverlayInitialized) fpvHudOverlay.visibility = View.GONE
        controlDashboardView.visibility = View.GONE

        topBarLayout.visibility = View.VISIBLE
        viewfinderOverlay.visibility = View.VISIBLE
        mainActionRow.visibility = View.VISIBLE
        if (zoomContainerInitialized) zoomContainer.visibility = View.VISIBLE
        if (btnSmartScanInitialized) btnSmartScan.visibility = View.VISIBLE
        if (ipBadgeContainerInitialized) ipBadgeContainer.visibility = View.VISIBLE

        if (mode == CameraMode.PHOTO) {
            shutterContainer.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2.8f), Color.parseColor("#1D5CE5"))
            }
            shutterCore.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1D5CE5"))
            }
            shutterCore.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            tvRecTimerBadge.visibility = View.GONE
            if (isRecordingVideo) stopVideoRecording()

            if (imageCapture?.let { cameraProvider?.isBound(it) } != true) {
                startCamera()
            }
        } else {
            shutterContainer.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2.8f), Color.parseColor("#DC2626"))
            }
            shutterCore.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#DC2626"))
            }
            shutterCore.animate().scaleX(0.88f).scaleY(0.88f).setDuration(150).start()

            if (videoCapture?.let { cameraProvider?.isBound(it) } != true) {
                startCamera()
            }
        }
    }
}

internal fun MainActivity.updateControlLayoutDimensions() {
    if (currentMode != CameraMode.CONTROL) return
    val fullLp = RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.MATCH_PARENT,
        RelativeLayout.LayoutParams.MATCH_PARENT
    )
    previewView.layoutParams = fullLp
    previewView.visibility = View.VISIBLE

    controlDashboardView.layoutParams = fullLp
    controlDashboardView.visibility = View.VISIBLE
    controlDashboardView.bringToFront()
    overlayContainer.bringToFront()
}
