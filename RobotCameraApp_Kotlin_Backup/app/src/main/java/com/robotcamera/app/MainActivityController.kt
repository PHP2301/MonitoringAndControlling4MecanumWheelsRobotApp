@file:Suppress("unused")

package com.robotcamera.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import com.robotcamera.app.MainActivity.AppRole
import com.robotcamera.app.MainActivity.Companion.TAG

/**
 * Vai trò THIẾT BỊ ĐIỀU KHIỂN:
 * - Bố cục Full-Screen FPV Cockpit (Video toàn màn hình + lớp phủ Joystick & các thanh Pill trắng nổi).
 */

internal fun MainActivity.startControllerRole() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

    viewfinderOverlay.visibility = View.GONE
    overlayContainer.visibility = View.GONE
    if (fpvHudOverlayInitialized) fpvHudOverlay.visibility = View.GONE

    controlDashboardView.visibility = View.VISIBLE
    if (controllerTopLeftBarInitialized) {
        controllerTopLeftBar.visibility = View.VISIBLE
        controllerTopLeftBar.bringToFront()
    }

    // Tự động kết nối UDP tới Robot nếu chưa kết nối
    val prefs = getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE)
    val savedUdp = prefs.getString("pref_udp_ip_port", "192.168.2.93:5001") ?: "192.168.2.93:5001"
    if (!udpManager.isConnected) {
        val parts = savedUdp.split(":")
        val ip = parts[0].trim()
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 5001
        udpManager.connect(ip, port)
    } else {
        updateUdpUiState(true, "Đã kết nối UDP tới $savedUdp")
    }
    if (etUdpAddressInitialized && etUdpAddress.text.isNullOrEmpty()) {
        etUdpAddress.setText(savedUdp)
    }

    // Mặc định vào chế độ nhận luồng FPV từ xa (REMOTE) đúng chuẩn thiết kế Cockpit
    val savedSource = prefs.getString("pref_controller_cam_source", "REMOTE")

    if (savedSource == "LOCAL") {
        switchToLocalCamMode()
    } else {
        switchToRemoteFpvMode()
    }
}

fun MainActivity.switchToLocalCamMode() {
    controllerCamSource = "LOCAL"
    getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE)
        .edit().putString("pref_controller_cam_source", "LOCAL").apply()

    if (remoteMjpegViewInitialized) {
        remoteMjpegView.stopStream()
        remoteMjpegView.visibility = View.GONE
    }

    previewView.visibility = View.VISIBLE
    if (controllerLocalCamBarInitialized) {
        controllerLocalCamBar.visibility = View.VISIBLE
        controllerLocalCamBar.bringToFront()
    }
    if (controllerTopLeftBarInitialized) {
        controllerTopLeftBar.visibility = View.VISIBLE
        controllerTopLeftBar.bringToFront()
    }

    updateControllerLayoutDimensions()

    if (allPermissionsGranted()) {
        startCamera()
    } else {
        requestPermissions()
    }
    if (!isStreaming || mjpegServer == null || mjpegServer?.isAlive != true) {
        startStreaming()
    }
    updateControllerLocalIpDisplay()
    Toast.makeText(this, "Đang dùng Camera máy & phát stream cổng 8080", Toast.LENGTH_SHORT).show()
}

fun MainActivity.switchToRemoteFpvMode(targetIp: String? = null) {
    controllerCamSource = "REMOTE"
    getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE)
        .edit().putString("pref_controller_cam_source", "REMOTE").apply()

    try {
        cameraProvider?.unbindAll()
        camera = null
    } catch (_: Exception) {}
    previewView.visibility = View.GONE
    if (controllerLocalCamBarInitialized) {
        controllerLocalCamBar.visibility = View.GONE
    }
    if (controllerTopLeftBarInitialized) {
        controllerTopLeftBar.visibility = View.VISIBLE
        controllerTopLeftBar.bringToFront()
    }

    if (!isStreaming || mjpegServer == null || mjpegServer?.isAlive != true) {
        startStreaming()
    }

    remoteMjpegView.visibility = View.VISIBLE
    updateControllerLayoutDimensions()
    remoteMjpegView.startStream(targetIp)
}

internal fun MainActivity.createControllerLocalCamBar(): LinearLayout {
    return LinearLayout(this).apply {
        id = View.generateViewId()
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val lp = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
            addRule(RelativeLayout.ALIGN_PARENT_END)
            setMargins(0, dp(10f), dp(14f), 0)
        }
        layoutParams = lp
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(26f).toFloat()
            setColor(Color.parseColor("#F5FFFFFF"))
            setStroke(dp(1f), Color.parseColor("#E2E8F0"))
        }
        elevation = dp(6f).toFloat()
        setPadding(dp(12f), dp(5f), dp(10f), dp(5f))
        visibility = View.GONE

        tvControllerLocalIp = TextView(this@createControllerLocalCamBar).apply {
            text = "Camera IP: Đang lấy..."
            textSize = 10.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, 0, dp(8f), 0)
        }
        addView(tvControllerLocalIp)

        val btnSwitchToFpv = TextView(this@createControllerLocalCamBar).apply {
            text = "FPV từ xa"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16f).toFloat()
                setColor(Color.parseColor("#1D5CE5"))
            }
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
            setOnClickListener {
                switchToRemoteFpvMode()
            }
        }
        addView(btnSwitchToFpv)

        val spacer = View(this@createControllerLocalCamBar).apply {
            layoutParams = LinearLayout.LayoutParams(dp(6f), 1)
        }
        addView(spacer)

        val btnFlipCam = TextView(this@createControllerLocalCamBar).apply {
            text = "Đổi camera"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16f).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1.2f), Color.parseColor("#CBD5E1"))
            }
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
            setOnClickListener {
                switchCamera()
            }
        }
        addView(btnFlipCam)
    }
}

internal fun MainActivity.createControllerTopLeftBar(): LinearLayout {
    return LinearLayout(this).apply {
        id = View.generateViewId()
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val lp = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
            addRule(RelativeLayout.ALIGN_PARENT_START)
            setMargins(dp(14f), dp(10f), 0, 0)
        }
        layoutParams = lp
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(26f).toFloat()
            setColor(Color.parseColor("#F5FFFFFF"))
            setStroke(dp(1f), Color.parseColor("#E2E8F0"))
        }
        elevation = dp(6f).toFloat()
        setPadding(dp(10f), dp(6f), dp(14f), dp(6f))
        visibility = View.GONE

        // 1. Nút Cài đặt (Mở BottomSheet thông số xe)
        val btnSettings = TextView(this@createControllerTopLeftBar).apply {
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
            setOnClickListener {
                if (controlDashboardViewInitialized) {
                    controlDashboardView.showParametersBottomSheet()
                }
            }
        }
        addView(btnSettings)

        addView(View(this@createControllerTopLeftBar).apply {
            layoutParams = LinearLayout.LayoutParams(dp(6f), 1)
        })

        // 2. Nút Chọn vai trò thiết bị
        val btnRole = TextView(this@createControllerTopLeftBar).apply {
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
            setOnClickListener {
                showRoleSelection()
            }
        }
        addView(btnRole)

        addView(View(this@createControllerTopLeftBar).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10f), 1)
        })

        // 3. Cụm điều khiển IP UDP của Robot
        val tvUdpLabel = TextView(this@createControllerTopLeftBar).apply {
            text = "UDP"
            textSize = 10.5f
            setTextColor(Color.parseColor("#475569"))
            isSingleLine = true
            setPadding(0, 0, dp(6f), 0)
        }
        addView(tvUdpLabel)

        val savedUdp = getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE)
            .getString("pref_udp_ip_port", "192.168.2.93:5001") ?: "192.168.2.93:5001"

        etUdpAddress = EditText(this@createControllerTopLeftBar).apply {
            setText(savedUdp)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16f).toFloat()
                setColor(Color.parseColor("#F1F5F9"))
                setStroke(dp(1f), Color.parseColor("#CBD5E1"))
            }
            setPadding(dp(12f), dp(2f), dp(12f), dp(2f))
            layoutParams = LinearLayout.LayoutParams(dp(136f), dp(30f))
            isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(etUdpAddress)

        addView(View(this@createControllerTopLeftBar).apply {
            layoutParams = LinearLayout.LayoutParams(dp(6f), 1)
        })

        btnConnectUdp = TextView(this@createControllerTopLeftBar).apply {
            text = "Nối"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isSingleLine = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16f).toFloat()
                setColor(Color.parseColor("#1D5CE5"))
            }
            setPadding(dp(14f), dp(6f), dp(14f), dp(6f))
            setOnClickListener {
                toggleUdpConnection()
            }
        }
        addView(btnConnectUdp)

        addView(View(this@createControllerTopLeftBar).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8f), 1)
        })

        tvUdpStatusBadge = TextView(this@createControllerTopLeftBar).apply {
            text = "Chưa nối"
            textSize = 10.5f
            setTextColor(Color.parseColor("#475569"))
            isSingleLine = true
            setPadding(dp(4f), 0, dp(4f), 0)
            setOnClickListener {
                val nextMode = udpManager.cyclePermissionMode()
                val msg = when (nextMode) {
                    RobotUdpManager.ControlPermission.AUTO -> "Quyền Tự động"
                    RobotUdpManager.ControlPermission.MASTER -> "Quyền Độc quyền (Master)"
                    RobotUdpManager.ControlPermission.VIEWER -> "Quyền Chỉ xem (Viewer)"
                }
                Toast.makeText(this@createControllerTopLeftBar, msg, Toast.LENGTH_SHORT).show()
            }
        }
        addView(tvUdpStatusBadge)

        udpManager.onPermissionChanged = { _, reason ->
            if (reason.isNotEmpty() && controlDashboardViewInitialized) {
                controlDashboardView.updatePermissionStatusText(reason)
            }
        }
    }
}

internal fun MainActivity.toggleUdpConnection() {
    if (udpManager.isConnected) {
        udpManager.disconnect()
        updateUdpUiState(false, "Đã ngắt kết nối UDP")
        Toast.makeText(this, "Đã ngắt kết nối UDP", Toast.LENGTH_SHORT).show()
    } else {
        val raw = etUdpAddress.text.toString().trim()
        if (raw.isEmpty()) return
        val parts = raw.split(":")
        val ip = parts[0].trim()
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 5001

        getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE)
            .edit().putString("pref_udp_ip_port", "$ip:$port").apply()

        udpManager.connect(ip, port)
        updateUdpUiState(true, "Đã kết nối UDP tới $ip:$port")
        Toast.makeText(this, "Đang kết nối UDP tới $ip:$port", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.updateUdpUiState(connected: Boolean, statusText: String) {
    runOnUiThread {
        if (btnConnectUdpInitialized) {
            btnConnectUdp.text = if (connected) "Ngắt" else "Nối"
            (btnConnectUdp.background as? GradientDrawable)?.setColor(
                if (connected) Color.parseColor("#DC2626") else Color.parseColor("#1D5CE5")
            )
        }
        if (tvUdpStatusBadgeInitialized) {
            tvUdpStatusBadge.text = if (connected) "Đã nối" else "Chưa nối"
            tvUdpStatusBadge.setTextColor(
                if (connected) Color.parseColor("#15803D") else Color.parseColor("#475569")
            )
        }
        if (controlDashboardViewInitialized) {
            controlDashboardView.updateUdpBannerState(connected, statusText)
        }
    }
}

internal fun MainActivity.updateControllerLocalIpDisplay() {
    val ip = getWifiIpAddress()
    runOnUiThread {
        if (tvControllerLocalIpInitialized) {
            if (ip != null) {
                tvControllerLocalIp.text = "Camera: $ip:8080"
                tvControllerLocalIp.setTextColor(Color.parseColor("#0F172A"))
            } else {
                tvControllerLocalIp.text = "Chưa có WiFi"
                tvControllerLocalIp.setTextColor(Color.parseColor("#DC2626"))
            }
        }
    }
}

internal fun MainActivity.updateControllerLayoutDimensions() {
    if (currentRole != AppRole.CONTROLLER) return
    val dm = resources.displayMetrics
    val w = dm.widthPixels
    val h = dm.heightPixels
    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Luồng video FPV trải rộng TOÀN MÀN HÌNH (Full-Screen FPV Cockpit)
    val fullScreenLp = RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.MATCH_PARENT,
        RelativeLayout.LayoutParams.MATCH_PARENT
    )

    if (controllerCamSource == "LOCAL") {
        previewView.layoutParams = fullScreenLp
        previewView.visibility = View.VISIBLE
        remoteMjpegView.visibility = View.GONE
        if (controllerLocalCamBarInitialized) {
            controllerLocalCamBar.visibility = View.VISIBLE
            controllerLocalCamBar.bringToFront()
        }
    } else {
        remoteMjpegView.layoutParams = fullScreenLp
        remoteMjpegView.visibility = View.VISIBLE
        previewView.visibility = View.GONE
        if (controllerLocalCamBarInitialized) {
            controllerLocalCamBar.visibility = View.GONE
        }
    }

    if (remoteMjpegViewInitialized) {
        remoteMjpegView.updateOrientation(isLandscape)
    }

    val dashLp = RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.MATCH_PARENT,
        RelativeLayout.LayoutParams.MATCH_PARENT
    )
    controlDashboardView.layoutParams = dashLp
    controlDashboardView.applyControlSizes(w, h)
    controlDashboardView.visibility = View.VISIBLE
    controlDashboardView.bringToFront()

    if (controllerTopLeftBarInitialized) {
        controllerTopLeftBar.visibility = View.VISIBLE
        controllerTopLeftBar.bringToFront()
    }
    if (controllerCamSource == "LOCAL" && controllerLocalCamBarInitialized) {
        controllerLocalCamBar.visibility = View.VISIBLE
        controllerLocalCamBar.bringToFront()
    }
}

internal fun MainActivity.sendHttpCommandToStreamer(endpoint: String) {
    val camIp = getSharedPreferences("robot_remote_cam_prefs", Context.MODE_PRIVATE)
        .getString("remote_cam_ip", "192.168.2.80:8080") ?: "192.168.2.80:8080"
    val host = if (camIp.startsWith("http://") || camIp.startsWith("https://")) camIp else "http://$camIp"
    val cleanHost = host.removeSuffix("/video.mjpeg").removeSuffix("/video").trimEnd('/')
    Thread {
        try {
            val url = URL("$cleanHost$endpoint")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "sendHttpCommandToStreamer error: ${e.message}")
        }
    }.start()
}
