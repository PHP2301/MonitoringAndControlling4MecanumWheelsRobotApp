@file:Suppress("unused")

package com.robotcamera.app

import android.app.Dialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.widget.*
import androidx.core.widget.NestedScrollView
import com.robotcamera.app.MainActivity.ScreenOrientationOption

/**
 * Hộp thoại Cài đặt máy ảnh (Giao diện Modal Card Trắng - Xanh Dương chuẩn thiết kế mới).
 */

internal fun MainActivity.showSettingsDialog() {
    val dialog = Dialog(this)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setDimAmount(0.55f)

    val rootScrollView = NestedScrollView(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(26f).toFloat()
            setColor(Color.WHITE)
        }
        elevation = dp(10f).toFloat()
        isFillViewport = true
    }

    val contentLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24f), dp(12f), dp(24f), dp(22f))
    }

    // 1. Thanh kéo tiêu chuẩn (Handle Bar)
    val handleBar = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(36f), dp(4f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(12f)
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(2f).toFloat()
            setColor(Color.parseColor("#CBD5E1"))
        }
    }
    contentLayout.addView(handleBar)

    // 2. Tiêu đề chính
    val tvHeaderTitle = TextView(this).apply {
        text = "Cài đặt máy ảnh"
        textSize = 17.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#0F172A"))
    }
    contentLayout.addView(tvHeaderTitle)

    val tvHeaderSub = TextView(this).apply {
        text = "Tùy chỉnh thông số luồng video và khung ngắm robot"
        textSize = 11.5f
        setTextColor(Color.parseColor("#64748B"))
        setPadding(0, dp(2f), 0, dp(14f))
    }
    contentLayout.addView(tvHeaderSub)

    // 3. Nhóm 1: CHẾ ĐỘ TRUYỀN STREAM
    val tvSec1Title = TextView(this).apply {
        text = "CHẾ ĐỘ TRUYỀN STREAM"
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#64748B"))
        letterSpacing = 0.04f
        setPadding(dp(2f), 0, 0, dp(6f))
    }
    contentLayout.addView(tvSec1Title)

    val isFast = targetResolution.width <= 960 && targetResolution.width != 1280 && targetResolution.width != 1920
    val isBalanced = targetResolution.width == 1280
    val isHd = targetResolution.width == 1920

    val cardFast = createSettingsOptionCard(
        title = "Siêu tốc (Chống trễ)",
        subtitle = "480p SD (16:9) - Tối ưu điều khiển xe robot, độ trễ cực thấp",
        isSelected = isFast
    ) {
        applyUltraFastLowLatencyMode()
        dialog.dismiss()
    }
    contentLayout.addView(cardFast)
    contentLayout.addView(createVerticalSpacer(dp(7f)))

    val cardBalanced = createSettingsOptionCard(
        title = "Cân bằng",
        subtitle = "720p HD - Cân bằng giữa độ nét và độ mượt mà",
        isSelected = isBalanced
    ) {
        applyBalancedMode()
        dialog.dismiss()
    }
    contentLayout.addView(cardBalanced)
    contentLayout.addView(createVerticalSpacer(dp(7f)))

    val cardHd = createSettingsOptionCard(
        title = "Sắc nét",
        subtitle = "1080p Full HD - Chi tiết cao, yêu cầu kết nối mạng mạnh",
        isSelected = isHd
    ) {
        applyHighQualityMode()
        dialog.dismiss()
    }
    contentLayout.addView(cardHd)

    contentLayout.addView(createVerticalSpacer(dp(14f)))

    // 4. Nhóm 2: HƯỚNG XOAY CAMERA (MÀN HÌNH)
    val tvSecRotTitle = TextView(this).apply {
        text = "HƯỚNG XOAY CAMERA (MÀN HÌNH)"
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#64748B"))
        letterSpacing = 0.04f
        setPadding(dp(2f), 0, 0, dp(6f))
    }
    contentLayout.addView(tvSecRotTitle)

    val cardRotAuto = createSettingsOptionCard(
        title = "Tự động (Theo cảm biến xoay)",
        subtitle = "Xoay ngang/dọc tự động theo hướng cầm máy hoặc gắn trên xe",
        isSelected = orientationOption == ScreenOrientationOption.AUTO
    ) {
        setOrientationMode(ScreenOrientationOption.AUTO)
        dialog.dismiss()
    }
    contentLayout.addView(cardRotAuto)
    contentLayout.addView(createVerticalSpacer(dp(7f)))

    val cardRotLand = createSettingsOptionCard(
        title = "Khóa xoay ngang 16:9 (Xe Robot)",
        subtitle = "Luôn xuất video ngang Full hình 16:9 lên màn hình máy tính",
        isSelected = orientationOption == ScreenOrientationOption.LANDSCAPE
    ) {
        setOrientationMode(ScreenOrientationOption.LANDSCAPE)
        dialog.dismiss()
    }
    contentLayout.addView(cardRotLand)
    contentLayout.addView(createVerticalSpacer(dp(7f)))

    val cardRotPort = createSettingsOptionCard(
        title = "Khóa xoay dọc 9:16",
        subtitle = null,
        isSelected = orientationOption == ScreenOrientationOption.PORTRAIT
    ) {
        setOrientationMode(ScreenOrientationOption.PORTRAIT)
        dialog.dismiss()
    }
    contentLayout.addView(cardRotPort)

    rootScrollView.addView(contentLayout)
    dialog.setContentView(rootScrollView)

    val dm = resources.displayMetrics
    val modalW = (dm.widthPixels * 0.64f).toInt().coerceAtLeast(dp(520f)).coerceAtMost(dm.widthPixels - dp(32f))
    dialog.window?.setLayout(modalW, LinearLayout.LayoutParams.WRAP_CONTENT)

    dialog.show()
}

internal fun MainActivity.createSettingsOptionCard(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit
): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14f).toFloat()
            if (isSelected) {
                setColor(Color.parseColor("#EEF4FF"))
                setStroke(dp(1.4f), Color.parseColor("#3B82F6"))
            } else {
                setColor(Color.WHITE)
                setStroke(dp(1.2f), Color.parseColor("#E2E8F0"))
            }
        }
        setPadding(dp(16f), dp(10f), dp(16f), dp(10f))

        val row = LinearLayout(this@createSettingsOptionCard).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvTitle = TextView(this@createSettingsOptionCard).apply {
            text = title
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isSelected) Color.parseColor("#1D5CE5") else Color.parseColor("#0F172A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(tvTitle)

        if (isSelected) {
            val tvBadge = TextView(this@createSettingsOptionCard).apply {
                text = "Đang chọn"
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12f).toFloat()
                    setColor(Color.parseColor("#1D5CE5"))
                }
                setPadding(dp(10f), dp(3f), dp(10f), dp(3f))
            }
            row.addView(tvBadge)
        }

        addView(row)

        if (!subtitle.isNullOrEmpty()) {
            val tvSub = TextView(this@createSettingsOptionCard).apply {
                text = subtitle
                textSize = 10.5f
                setTextColor(if (isSelected) Color.parseColor("#475569") else Color.parseColor("#64748B"))
                setPadding(0, dp(2f), 0, 0)
            }
            addView(tvSub)
        }

        applySpringTouchEffect(this)
        setOnClickListener { onClick() }
    }
}

internal fun MainActivity.applyUltraFastLowLatencyMode() {
    targetResolution = Size(854, 480)
    mjpegServer?.targetOutputResolution = targetResolution
    mjpegServer?.jpegQuality = 55
    tvResolutionBadge.text = "480p"
    startCamera()
    Toast.makeText(this, "Đã kích hoạt chế độ Siêu tốc (480p SD 16:9 - Giảm tối đa độ trễ)", Toast.LENGTH_LONG).show()
}

internal fun MainActivity.applyBalancedMode() {
    targetResolution = Size(1280, 720)
    mjpegServer?.targetOutputResolution = targetResolution
    mjpegServer?.jpegQuality = 65
    tvResolutionBadge.text = "720p"
    startCamera()
    Toast.makeText(this, "Đã kích hoạt chế độ Cân bằng (720p HD 16:9)", Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.applyHighQualityMode() {
    targetResolution = Size(1920, 1080)
    mjpegServer?.targetOutputResolution = targetResolution
    mjpegServer?.jpegQuality = 85
    tvResolutionBadge.text = "1080p"
    startCamera()
    Toast.makeText(this, "Đã kích hoạt chế độ Sắc nét (1080p Full HD 16:9)", Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.setOrientationMode(option: ScreenOrientationOption) {
    orientationOption = option
    when (option) {
        ScreenOrientationOption.AUTO -> {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            orientationEventListener?.enable()
            Toast.makeText(this, "Chế độ xoay: Tự động theo cảm biến", Toast.LENGTH_SHORT).show()
        }
        ScreenOrientationOption.LANDSCAPE -> {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            currentTargetRotation = Surface.ROTATION_90
            Toast.makeText(this, "Chế độ xoay: Khóa ngang (Tối ưu cho xe Robot)", Toast.LENGTH_SHORT).show()
        }
        ScreenOrientationOption.PORTRAIT -> {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            currentTargetRotation = Surface.ROTATION_0
            Toast.makeText(this, "Chế độ xoay: Khóa dọc", Toast.LENGTH_SHORT).show()
        }
    }
    startCamera()
}
