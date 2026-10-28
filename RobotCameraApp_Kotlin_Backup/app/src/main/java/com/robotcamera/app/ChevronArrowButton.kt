package com.robotcamera.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * Nút bấm mũi tên điều khiển hình cánh cung/tam giác vát khí động học (Chevron Wedge Button)
 * - Thiết kế mô phỏng chính xác hình ảnh mẫu UI game đua xe / robot (Tone màu Xanh Dương - Trắng).
 * - Hỗ trợ 4 hướng: UP, DOWN, LEFT, RIGHT.
 * - Hiệu ứng chạm nhún (spring scale) và đổi màu phát sáng khi nhấn giữ.
 */
@SuppressLint("ClickableViewAccessibility")
class ChevronArrowButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Direction {
        UP, DOWN, LEFT, RIGHT
    }

    var direction: Direction = Direction.UP
        set(value) {
            field = value
            invalidate()
        }

    var onHoldStateChanged: ((Boolean) -> Unit)? = null

    private fun dp(v: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    )

    private val bgPath = Path()
    private val chevronPath = Path()

    // Paints
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
    }

    private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var isPressedState = false

    init {
        isClickable = true
        isFocusable = true
    }

    fun setHeld(held: Boolean) {
        isPressedState = held
        scaleX = if (held) 0.93f else 1.0f
        scaleY = if (held) 0.93f else 1.0f
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressedState = true
                animate().scaleX(0.93f).scaleY(0.93f).setDuration(70).start()
                invalidate()
                onHoldStateChanged?.invoke(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPressedState = false
                animate().scaleX(1.0f).scaleY(1.0f).setDuration(70).start()
                invalidate()
                onHoldStateChanged?.invoke(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePaths(w.toFloat(), h.toFloat())
    }

    private fun updatePaths(w: Float, h: Float) {
        bgPath.reset()
        chevronPath.reset()
        if (w <= 0f || h <= 0f) return

        val pad = dp(4f)
        val r = dp(12f) // Độ cong góc vát

        when (direction) {
            Direction.LEFT -> {
                // Đỉnh nhọn hướng sang trái
                val tipX = pad + dp(2f)
                val tipY = h / 2f
                val rightX = w - pad
                val topY = pad
                val botY = h - pad

                bgPath.moveTo(rightX, topY + r)
                bgPath.quadTo(rightX, topY, rightX - r, topY)
                bgPath.lineTo(tipX + r, tipY - dp(6f))
                bgPath.quadTo(tipX, tipY, tipX + r, tipY + dp(6f))
                bgPath.lineTo(rightX - r, botY)
                bgPath.quadTo(rightX, botY, rightX, botY - r)
                bgPath.close()

                // Mũi tên < ở giữa
                val cTipX = w * 0.38f
                val cTipY = h / 2f
                val cArmX = w * 0.65f
                val cArmSpread = h * 0.24f
                chevronPath.moveTo(cArmX, cTipY - cArmSpread)
                chevronPath.lineTo(cTipX, cTipY)
                chevronPath.lineTo(cArmX, cTipY + cArmSpread)
            }
            Direction.RIGHT -> {
                // Đỉnh nhọn hướng sang phải
                val tipX = w - pad - dp(2f)
                val tipY = h / 2f
                val leftX = pad
                val topY = pad
                val botY = h - pad

                bgPath.moveTo(leftX, topY + r)
                bgPath.quadTo(leftX, topY, leftX + r, topY)
                bgPath.lineTo(tipX - r, tipY - dp(6f))
                bgPath.quadTo(tipX, tipY, tipX - r, tipY + dp(6f))
                bgPath.lineTo(leftX + r, botY)
                bgPath.quadTo(leftX, botY, leftX, botY - r)
                bgPath.close()

                // Mũi tên > ở giữa
                val cTipX = w * 0.62f
                val cTipY = h / 2f
                val cArmX = w * 0.35f
                val cArmSpread = h * 0.24f
                chevronPath.moveTo(cArmX, cTipY - cArmSpread)
                chevronPath.lineTo(cTipX, cTipY)
                chevronPath.lineTo(cArmX, cTipY + cArmSpread)
            }
            Direction.UP -> {
                // Đỉnh nhọn hướng lên trên
                val tipX = w / 2f
                val tipY = pad + dp(2f)
                val botY = h - pad
                val leftX = pad
                val rightX = w - pad

                bgPath.moveTo(leftX, botY - r)
                bgPath.quadTo(leftX, botY, leftX + r, botY)
                bgPath.lineTo(rightX - r, botY)
                bgPath.quadTo(rightX, botY, rightX, botY - r)
                bgPath.lineTo(tipX + dp(6f), tipY + r)
                bgPath.quadTo(tipX, tipY, tipX - dp(6f), tipY + r)
                bgPath.close()

                // Mũi tên ^ ở giữa
                val cTipX = w / 2f
                val cTipY = h * 0.38f
                val cArmY = h * 0.65f
                val cArmSpread = w * 0.24f
                chevronPath.moveTo(cTipX - cArmSpread, cArmY)
                chevronPath.lineTo(cTipX, cTipY)
                chevronPath.lineTo(cTipX + cArmSpread, cArmY)
            }
            Direction.DOWN -> {
                // Đỉnh nhọn hướng xuống dưới
                val tipX = w / 2f
                val tipY = h - pad - dp(2f)
                val topY = pad
                val leftX = pad
                val rightX = w - pad

                bgPath.moveTo(leftX, topY + r)
                bgPath.quadTo(leftX, topY, leftX + r, topY)
                bgPath.lineTo(rightX - r, topY)
                bgPath.quadTo(rightX, topY, rightX, topY + r)
                bgPath.lineTo(tipX + dp(6f), tipY - r)
                bgPath.quadTo(tipX, tipY, tipX - dp(6f), tipY - r)
                bgPath.close()

                // Mũi tên v ở giữa
                val cTipX = w / 2f
                val cTipY = h * 0.62f
                val cArmY = h * 0.35f
                val cArmSpread = w * 0.24f
                chevronPath.moveTo(cTipX - cArmSpread, cArmY)
                chevronPath.lineTo(cTipX, cTipY)
                chevronPath.lineTo(cTipX + cArmSpread, cArmY)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (bgPath.isEmpty) updatePaths(w, h)

        // Màu nền Gradient
        if (isPressedState) {
            // Khi nhấn giữ: Phát sáng rực rỡ (Deep Tech Blue)
            fillPaint.shader = LinearGradient(
                0f, 0f, w, h,
                Color.parseColor("#0066FF"),
                Color.parseColor("#0044CC"),
                Shader.TileMode.CLAMP
            )
            borderPaint.color = Color.parseColor("#60A5FA")
            borderPaint.strokeWidth = dp(2.2f)
        } else {
            // Trạng thái bình thường: Xanh lam chuyển màu nhẹ nhàng như ảnh mẫu
            fillPaint.shader = LinearGradient(
                0f, 0f, w, h,
                Color.parseColor("#2E7BE6"),
                Color.parseColor("#1A5AC7"),
                Shader.TileMode.CLAMP
            )
            borderPaint.color = Color.parseColor("#93C5FD")
            borderPaint.strokeWidth = dp(1.8f)
        }

        // Vẽ nền cánh cung vát
        canvas.drawPath(bgPath, fillPaint)
        canvas.drawPath(bgPath, borderPaint)

        // Vẽ viền sáng phát quang cho mũi tên Chevron
        glowPaint.strokeWidth = dp(7.5f)
        glowPaint.color = if (isPressedState) Color.parseColor("#8000E5FF") else Color.parseColor("#4D00E5FF")
        canvas.drawPath(chevronPath, glowPaint)

        // Vẽ mũi tên Chevron màu trắng chính giữa
        chevronPaint.strokeWidth = dp(4.5f)
        chevronPaint.color = Color.WHITE
        canvas.drawPath(chevronPath, chevronPaint)
    }
}
