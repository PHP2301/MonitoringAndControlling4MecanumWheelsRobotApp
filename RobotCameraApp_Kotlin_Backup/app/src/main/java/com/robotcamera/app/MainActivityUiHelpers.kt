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

/**
 * Tiện ích giao diện dùng chung: dp(), spacer, hiệu ứng chạm lò xo, chớp màn trập.
 * (Extension functions của MainActivity - tách từ MainActivity.kt)
 */

@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.applySpringTouchEffect(view: View) {
    view.setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(70).start()
            }
            MotionEvent.ACTION_UP -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(110).withEndAction {
                    v.performClick()
                }.start()
            }
            MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(110).start()
            }
        }
        true
    }
}

@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.applyShutterTouchAnimation(container: View, innerCore: View, onTrigger: () -> Unit) {
    container.setOnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                innerCore.animate().scaleX(0.80f).scaleY(0.80f).setDuration(70).start()
                container.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).start()
            }
            MotionEvent.ACTION_UP -> {
                val targetScale = if (currentMode == CameraMode.VIDEO && isRecordingVideo) 0.55f else if (currentMode == CameraMode.VIDEO) 0.85f else 1.0f
                innerCore.animate().scaleX(targetScale).scaleY(targetScale).setDuration(120).start()
                container.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).withEndAction {
                    onTrigger()
                }.start()
            }
            MotionEvent.ACTION_CANCEL -> {
                val targetScale = if (currentMode == CameraMode.VIDEO && isRecordingVideo) 0.55f else if (currentMode == CameraMode.VIDEO) 0.85f else 1.0f
                innerCore.animate().scaleX(targetScale).scaleY(targetScale).setDuration(100).start()
                container.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }
        }
        true
    }
}

internal fun MainActivity.triggerShutterFlash() {
    shutterFlashOverlay.visibility = View.VISIBLE
    shutterFlashOverlay.alpha = 0.65f
    shutterFlashOverlay.animate()
        .alpha(0.0f)
        .setDuration(180)
        .withEndAction {
            shutterFlashOverlay.visibility = View.GONE
        }
        .start()
}

internal fun MainActivity.dp(value: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics
    ).toInt()
}

internal fun MainActivity.createFlexSpacer(): View {
    return View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
    }
}

internal fun MainActivity.createHorizontalSpacer(sizeDp: Int): View {
    return View(this).apply {
        layoutParams = LinearLayout.LayoutParams(sizeDp, 1)
    }
}

internal fun MainActivity.createVerticalSpacer(sizeDp: Int): View {
    return View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, sizeDp)
    }
}

// =========================================================================
// KÍNH MỜ (FROSTED GLASS) CHO CÁC THANH NỔI TRÊN CAMERA
// Nền trắng trong suốt + viền trắng mảnh => nhìn xuyên thấu hình ảnh phía sau
// =========================================================================

/** Nền kính mờ cho khung chứa (thanh IP UDP / Camera IP). */
internal fun MainActivity.glassContainerBg(radiusDp: Float = 14f): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(Color.parseColor("#2EFFFFFF"))
        setStroke(dp(1f), Color.parseColor("#66FFFFFF"))
    }

/** Nền kính mờ cho ô nhập / nút phụ nằm bên trong thanh kính. */
internal fun MainActivity.glassChipBg(radiusDp: Float = 6f): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(Color.parseColor("#33FFFFFF"))
        setStroke(dp(1f), Color.parseColor("#55FFFFFF"))
    }

/** Chữ trắng có bóng đổ để luôn đọc được trên mọi nền hình ảnh. */
internal fun TextView.applyGlassTextStyle() {
    setTextColor(Color.WHITE)
    setShadowLayer(3f, 0f, 1f, Color.parseColor("#99000000"))
}
