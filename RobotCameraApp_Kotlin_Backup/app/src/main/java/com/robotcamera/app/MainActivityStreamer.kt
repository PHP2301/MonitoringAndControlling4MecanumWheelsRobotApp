@file:Suppress("unused")

package com.robotcamera.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.*
import android.view.OrientationEventListener
import android.view.Surface
import android.content.res.Configuration
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.core.widget.NestedScrollView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.robotcamera.app.MainActivity.CameraMode
import com.robotcamera.app.MainActivity.Companion.TAG
import com.robotcamera.app.MainActivity.Companion.STREAM_PORT

/**
 * Vai trò THIẾT BỊ STREAMING: máy chủ MJPEG 8080, IP WiFi, chia sẻ link stream.
 * (Extension functions của MainActivity - tách từ MainActivity.kt)
 */

internal fun MainActivity.startStreamerRole() {
    if (remoteMjpegViewInitialized) {
        remoteMjpegView.stopStream()
        remoteMjpegView.visibility = View.GONE
    }
    if (controlDashboardViewInitialized) {
        controlDashboardView.visibility = View.GONE
    }
    if (controllerLocalCamBarInitialized) {
        controllerLocalCamBar.visibility = View.GONE
    }
    if (controllerTopLeftBarInitialized) {
        controllerTopLeftBar.visibility = View.GONE
    }

    // Camera toàn màn hình
    previewView.layoutParams = RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.MATCH_PARENT,
        RelativeLayout.LayoutParams.MATCH_PARENT
    )
    previewView.visibility = View.VISIBLE
    viewfinderOverlay.visibility = View.VISIBLE
    overlayContainer.visibility = View.VISIBLE
    if (fpvHudOverlayInitialized) fpvHudOverlay.visibility = View.GONE

    // Chỉ hiển thị ẢNH và VIDEO (ẩn tab ĐIỀU KHIỂN ở đáy vì đây là máy phát trên robot)
    if (tvModeControlInitialized) {
        tvModeControl.visibility = View.GONE
    }
    switchMode(CameraMode.PHOTO)

    requestPermissions()
    startStreaming()
    updateIpDisplay()
    GimbalBleManager.autoReconnectIfSaved(this)
    Toast.makeText(this, "Đã kích hoạt Thiết Bị Streaming!!!", Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.updateIpDisplay() {
    val ip = getWifiIpAddress()
    runOnUiThread {
        if (tvIpDisplayBadgeInitialized) {
            if (ip != null) {
                tvIpDisplayBadge.text = "IP: $ip"
                tvIpDisplayBadge.setTextColor(Color.parseColor("#0F172A"))
            } else {
                tvIpDisplayBadge.text = "Chưa kết nối WiFi!"
                tvIpDisplayBadge.setTextColor(Color.parseColor("#DC2626"))
            }
        }
    }
}

internal fun MainActivity.showStreamInfoDialog() {
    val ip = getWifiIpAddress() ?: "Chưa kết nối WiFi"
    val url = "http://$ip:$STREAM_PORT/video"
    val fps = mjpegServer?.currentFps ?: 0
    val clients = mjpegServer?.getClientCount() ?: 0

    val msg = "Liên kết truyền video:\n$url\n\n" +
            "Tốc độ khung hình: $fps FPS\n" +
            "Số máy tính đang xem: $clients\n" +
            "Trạng thái máy chủ: ${if (isStreaming) "Đang hoạt động" else "Tạm dừng"}"

    AlertDialog.Builder(this)
        .setTitle("Thông tin truyền video")
        .setMessage(msg)
        .setPositiveButton("Sao chép liên kết") { _, _ ->
            copyStreamUrlToClipboard()
        }
        .setNegativeButton("Đóng", null)
        .show()
}

internal fun MainActivity.copyStreamUrlToClipboard() {
    val ip = getWifiIpAddress()
    if (ip != null) {
        val url = "http://$ip:$STREAM_PORT/video.mjpeg"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Robot Camera Stream URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Đã sao chép liên kết stream: $url", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(this, "Thiết bị chưa kết nối WiFi", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.startStreaming() {
    try {
        if (mjpegServer != null && mjpegServer?.isAlive == true) {
            isStreaming = true
            tvMotionLiveText.text = "• LIVE"
            tvMotionLiveText.setTextColor(Color.parseColor("#15803D"))
            return
        }

        try {
            mjpegServer?.stop()
        } catch (_: Exception) {}
        mjpegServer = null

        var started = false
        for (attempt in 1..4) {
            try {
                mjpegServer = MjpegServer(STREAM_PORT).apply {
                    commandListener = this@startStreaming
                    targetOutputResolution = this@startStreaming.targetResolution
                    onFrameBitmap = { bmp ->
                        if (visualDistanceDetectorInitialized && !bmp.isRecycled) {
                            visualDistanceDetector.processBitmap(bmp)
                        }
                    }
                    start()
                }
                started = true
                break
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi bind server cổng $STREAM_PORT lần $attempt: ${e.message}, thử lại sau 200ms...")
                try { Thread.sleep(200) } catch (_: Exception) {}
            }
        }

        if (started) {
            isStreaming = true
            try {
                startService(Intent(this, CameraStreamService::class.java))
            } catch (e: Throwable) {
                Log.w(TAG, "Không thể khởi động CameraStreamService: ${e.message}")
            }
            if (tvMotionLiveTextInitialized) {
                tvMotionLiveText.text = "• LIVE"
                tvMotionLiveText.setTextColor(Color.parseColor("#15803D"))
            }
            Log.i(TAG, "MJPEG Server đã mở thành công trên cổng $STREAM_PORT")
        } else {
            Log.e(TAG, "Không thể mở MJPEG Server trên cổng $STREAM_PORT sau 4 lần thử")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Lỗi mở server: ${e.message}")
    }
}

internal fun MainActivity.stopStreaming() {
    try {
        mjpegServer?.stop()
        mjpegServer = null
        isStreaming = false
        try {
            stopService(Intent(this, CameraStreamService::class.java))
        } catch (e: Throwable) {
            Log.w(TAG, "Không thể dừng CameraStreamService: ${e.message}")
        }

        if (tvMotionLiveTextInitialized) {
            tvMotionLiveText.setTextColor(Color.parseColor("#F5C518"))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Lỗi tắt server: ${e.message}")
    }
}

internal fun MainActivity.getWifiIpAddress(): String? {
    try {
        @Suppress("DEPRECATION")
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager?.connectionInfo
        @Suppress("DEPRECATION")
        val ipInt = wifiInfo?.ipAddress ?: 0
        if (ipInt != 0) {
            val ip = String.format(
                "%d.%d.%d.%d",
                ipInt and 0xFF,
                (ipInt shr 8) and 0xFF,
                (ipInt shr 16) and 0xFF,
                (ipInt shr 24) and 0xFF
            )
            if (ip != "0.0.0.0") return ip
        }

        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address.hostAddress
                }
            }
        }
    } catch (_: Exception) {}
    return null
}

/**
 * Hộp thoại quét & kết nối Bluetooth BLE tới Gimbal DJI RS 3 Mini trên thiết bị Streaming
 * (Giao diện Modal Card Trắng - Xanh Dương chuẩn thiết kế mới)
 */
@SuppressLint("ClickableViewAccessibility", "MissingPermission")
internal fun MainActivity.showGimbalBleDialog() {
    val blePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val missingPerms = blePerms.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }
    if (missingPerms.isNotEmpty()) {
        ActivityCompat.requestPermissions(this, missingPerms.toTypedArray(), 99)
        Toast.makeText(this, "Vui lòng cấp quyền Bluetooth rồi bấm lại nút Gimbal", Toast.LENGTH_SHORT).show()
        return
    }

    val dialog = android.app.Dialog(this)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setDimAmount(0.55f)

    val rootScroll = NestedScrollView(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(26f).toFloat()
            setColor(Color.WHITE)
        }
        elevation = dp(10f).toFloat()
        isFillViewport = true
    }

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24f), dp(18f), dp(24f), dp(20f))
    }

    // 1. Header
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val tvTitle = TextView(this).apply {
        text = "KẾT NỐI GIMBAL DJI RS 3 MINI (BLE)"
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#1D5CE5"))
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    headerRow.addView(tvTitle)

    val btnClose = TextView(this).apply {
        text = "Đóng"
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#0F172A"))
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18f).toFloat()
            setColor(Color.WHITE)
            setStroke(dp(1.2f), Color.parseColor("#CBD5E1"))
        }
        setPadding(dp(16f), dp(6f), dp(16f), dp(6f))
        setOnClickListener { dialog.dismiss() }
    }
    headerRow.addView(btnClose)
    content.addView(headerRow)

    // 2. Trạng thái hiện tại + Nút Quét / Ngắt kết nối
    val statusRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10f), 0, dp(6f))
    }

    val tvStatus = TextView(this).apply {
        text = "Trạng thái: ${GimbalBleManager.statusMessage}"
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (GimbalBleManager.isConnected) Color.parseColor("#15803D") else Color.parseColor("#0F172A"))
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    statusRow.addView(tvStatus)

    val btnScan = TextView(this).apply {
        text = "Quét BLE"
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18f).toFloat()
            setColor(Color.parseColor("#1D5CE5"))
        }
        setPadding(dp(16f), dp(7f), dp(16f), dp(7f))
        setOnClickListener {
            GimbalBleManager.startScan(this@showGimbalBleDialog, autoConnectFirstDji = false)
        }
    }
    statusRow.addView(btnScan)

    statusRow.addView(createHorizontalSpacer(dp(8f)))

    val btnDisconnect = TextView(this).apply {
        text = "Ngắt BLE"
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18f).toFloat()
            setColor(Color.parseColor("#DC2626"))
        }
        setPadding(dp(16f), dp(7f), dp(16f), dp(7f))
        setOnClickListener {
            GimbalBleManager.disconnect()
        }
    }
    statusRow.addView(btnDisconnect)
    content.addView(statusRow)

    // 3. Danh sách thiết bị BLE tìm thấy
    val tvListHint = TextView(this).apply {
        text = "Chạm vào tên Gimbal RS 3 Mini bên dưới để kết nối:"
        textSize = 10.5f
        setTextColor(Color.parseColor("#64748B"))
        setPadding(0, 0, 0, dp(6f))
    }
    content.addView(tvListHint)

    val deviceListContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16f).toFloat()
            setColor(Color.parseColor("#F4F7FB"))
            setStroke(dp(1f), Color.parseColor("#E2E8F0"))
        }
        setPadding(dp(10f), dp(8f), dp(10f), dp(4f))
    }
    content.addView(deviceListContainer)

    // 4. Cụm nút Điều khiển thử Gimbal trực tiếp (1 hàng ngang chuẩn mockup)
    val testHeader = TextView(this).apply {
        text = "Điều khiển thử Gimbal trực tiếp:"
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#0F172A"))
        setPadding(0, dp(12f), 0, dp(8f))
    }
    content.addView(testHeader)

    fun makeHoldPillBtn(chevron: String, label: String, onHold: (Boolean) -> Unit): LinearLayout {
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18f).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1.2f), Color.parseColor("#CBD5E1"))
            }
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6f)
            }
        }

        val tvChev = TextView(this).apply {
            text = chevron
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1D5CE5"))
            setPadding(0, 0, dp(5f), 0)
        }
        pill.addView(tvChev)

        val tvLbl = TextView(this).apply {
            text = label
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
        }
        pill.addView(tvLbl)

        pill.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    pill.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(18f).toFloat()
                        setColor(Color.parseColor("#1D5CE5"))
                    }
                    tvChev.setTextColor(Color.WHITE)
                    tvLbl.setTextColor(Color.WHITE)
                    onHold(true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pill.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(18f).toFloat()
                        setColor(Color.WHITE)
                        setStroke(dp(1.2f), Color.parseColor("#CBD5E1"))
                    }
                    tvChev.setTextColor(Color.parseColor("#1D5CE5"))
                    tvLbl.setTextColor(Color.parseColor("#0F172A"))
                    onHold(false)
                    true
                }
                else -> false
            }
        }
        return pill
    }

    val controlsBar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    val btnLeft = makeHoldPillBtn("‹", "Trái") { GimbalBleManager.setPanDirection(if (it) -1 else 0) }
    val btnUp = makeHoldPillBtn("⌃", "Lên") { GimbalBleManager.setTiltDirection(if (it) 1 else 0) }
    val btnDown = makeHoldPillBtn("⌄", "Xuống") { GimbalBleManager.setTiltDirection(if (it) -1 else 0) }
    val btnRight = makeHoldPillBtn("›", "Phải") { GimbalBleManager.setPanDirection(if (it) 1 else 0) }

    controlsBar.addView(btnLeft)
    controlsBar.addView(btnUp)
    controlsBar.addView(btnDown)
    controlsBar.addView(btnRight)

    controlsBar.addView(View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
    })

    val btnMode = TextView(this).apply {
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18f).toFloat()
            setColor(Color.parseColor("#1D5CE5"))
        }
        setPadding(dp(16f), dp(7f), dp(16f), dp(7f))
    }

    val btnInvert = TextView(this).apply {
        textSize = 9.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(14f), dp(4f), dp(14f), dp(4f))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dp(6f)
        }
    }

    fun updateInvertBtn() {
        val inv = GimbalBleManager.invertPan
        btnInvert.text = if (inv) "Đảo chiều:\nBẬT" else "Đảo chiều:\nTẮT"
        btnInvert.setTextColor(if (inv) Color.parseColor("#DC2626") else Color.parseColor("#0F172A"))
        btnInvert.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18f).toFloat()
            setColor(if (inv) Color.parseColor("#FEE2E2") else Color.WHITE)
            setStroke(dp(1.2f), if (inv) Color.parseColor("#FCA5A5") else Color.parseColor("#CBD5E1"))
        }
    }
    btnInvert.setOnClickListener {
        GimbalBleManager.invertPan = !GimbalBleManager.invertPan
        GimbalBleManager.invertTilt = !GimbalBleManager.invertTilt
        updateInvertBtn()
        Toast.makeText(this, if (GimbalBleManager.invertPan) "Đã bật đảo chiều điều khiển" else "Đã tắt đảo chiều", Toast.LENGTH_SHORT).show()
    }
    updateInvertBtn()

    fun updateModeView(mode: GimbalBleManager.FollowMode) {
        btnMode.text = "Mode: ${mode.label}"
        val isFpv = (mode == GimbalBleManager.FollowMode.FPV)
        btnLeft.visibility = if (isFpv) View.GONE else View.VISIBLE
        btnRight.visibility = if (isFpv) View.GONE else View.VISIBLE
    }

    btnMode.setOnClickListener {
        val next = GimbalBleManager.currentFollowMode.next()
        GimbalBleManager.setFollowMode(next)
        updateModeView(next)
    }
    updateModeView(GimbalBleManager.currentFollowMode)

    GimbalBleManager.onFollowModeChanged = { m ->
        runOnUiThread { updateModeView(m) }
    }

    controlsBar.addView(btnMode)
    controlsBar.addView(btnInvert)
    content.addView(controlsBar)

    val stateListener: (Boolean, String) -> Unit = { connected, msg ->
        tvStatus.text = "Trạng thái: $msg"
        tvStatus.setTextColor(if (connected) Color.parseColor("#15803D") else Color.parseColor("#0F172A"))
    }
    val scanListener: (List<GimbalBleManager.ScannedBleDevice>) -> Unit = { devices ->
        deviceListContainer.removeAllViews()
        if (devices.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "Đang tìm thiết bị Bluetooth BLE quanh đây..."
                textSize = 11.5f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(dp(4f), dp(6f), dp(4f), dp(6f))
            }
            deviceListContainer.addView(tvEmpty)
        } else {
            devices.take(6).forEach { item ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(12f).toFloat()
                        setColor(if (item.isLikelyDjiGimbal) Color.parseColor("#EFF6FF") else Color.WHITE)
                        setStroke(dp(1f), if (item.isLikelyDjiGimbal) Color.parseColor("#60A5FA") else Color.parseColor("#E2E8F0"))
                    }
                    setPadding(dp(14f), dp(6f), dp(10f), dp(6f))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(5f) }
                    setOnClickListener {
                        GimbalBleManager.connectToDevice(this@showGimbalBleDialog, item.device)
                    }
                }

                val nameCol = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvName = TextView(this).apply {
                    text = if (item.isLikelyDjiGimbal) "★ ${item.name} " else "${item.name} "
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (item.isLikelyDjiGimbal) Color.parseColor("#1D5CE5") else Color.parseColor("#0F172A"))
                }
                nameCol.addView(tvName)

                val tvAddr = TextView(this).apply {
                    text = "(${item.address})"
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#64748B"))
                }
                nameCol.addView(tvAddr)

                row.addView(nameCol)

                val tvConnect = TextView(this).apply {
                    text = "Kết nối"
                    textSize = 10.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#1D5CE5"))
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(14f).toFloat()
                        setColor(Color.parseColor("#E8F0FE"))
                    }
                    setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
                }
                row.addView(tvConnect)
                deviceListContainer.addView(row)
            }
        }
    }

    GimbalBleManager.addStateListener(stateListener)
    GimbalBleManager.addScanListener(scanListener)

    dialog.setOnDismissListener {
        GimbalBleManager.removeStateListener(stateListener)
        GimbalBleManager.removeScanListener(scanListener)
        GimbalBleManager.stopScan(this)
    }

    rootScroll.addView(content)
    dialog.setContentView(rootScroll)

    val dm = resources.displayMetrics
    val modalW = (dm.widthPixels * 0.64f).toInt().coerceAtLeast(dp(520f)).coerceAtMost(dm.widthPixels - dp(32f))
    dialog.window?.setLayout(modalW, LinearLayout.LayoutParams.WRAP_CONTENT)

    dialog.show()

    if (!GimbalBleManager.isConnected) {
        GimbalBleManager.startScan(this, autoConnectFirstDji = false)
    }
}
