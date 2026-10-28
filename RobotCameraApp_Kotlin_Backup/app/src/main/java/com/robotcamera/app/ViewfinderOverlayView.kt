package com.robotcamera.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * ViewfinderOverlayView:
 * - Hiển thị khung ngắm trung tâm bo góc tinh tế chuẩn thiết kế mới.
 */
class ViewfinderOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), SensorEventListener {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#28FFFFFF")
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
    }

    private val centerBoxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#183B82F6")
        style = Paint.Style.FILL
    }

    private val centerBoxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B82F6")
        strokeWidth = dp(1.5f)
        style = Paint.Style.STROKE
    }

    private val centerBoxRect = RectF()
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    var isGridEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isLevelEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    init {
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } catch (_: Exception) {}
    }

    fun startSensors() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopSensors() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {}

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        if (isGridEnabled) {
            val col1 = w / 3f
            val col2 = col1 * 2f
            val row1 = h / 3f
            val row2 = row1 * 2f
            canvas.drawLine(col1, 0f, col1, h, gridPaint)
            canvas.drawLine(col2, 0f, col2, h, gridPaint)
            canvas.drawLine(0f, row1, w, row1, gridPaint)
            canvas.drawLine(0f, row2, w, row2, gridPaint)
        }

        if (isLevelEnabled) {
            val cx = w / 2f
            val cy = h / 2f
            val halfSize = dp(26f)
            val corner = dp(14f)
            centerBoxRect.set(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize)
            canvas.drawRoundRect(centerBoxRect, corner, corner, centerBoxFillPaint)
            canvas.drawRoundRect(centerBoxRect, corner, corner, centerBoxStrokePaint)
        }
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }
}
