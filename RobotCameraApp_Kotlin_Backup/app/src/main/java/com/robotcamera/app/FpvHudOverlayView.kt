package com.robotcamera.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import java.util.Locale
import kotlin.math.abs

/**
 * Lớp phủ HUD FPV hiển thị đè lên màn hình Camera khi ở chế độ Điều khiển (CameraMode.CONTROL)
 * Mang phong cách Cockpit điều khiển xe robot / FPV drone chuyên nghiệp:
 * 1. Tâm ngắm ngắm chuẩn mục tiêu (Crosshair + Corner Brackets)
 * 2. Huy hiệu [● LIVE FPV] phát sáng xanh lá
 * 3. Huy hiệu thông số luồng [720p • 30 FPS • MECANUM 4WD]
 * 4. Vạch chia biên giới công nghệ ngăn cách giữa màn hình Camera và bảng điều khiển
 */
class FpvHudOverlayView(context: Context) : View(context) {

    private fun dp(v: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    )

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#990066FF") // Xanh Tech Blue công nghệ
        strokeWidth = dp(1.5f)
        style = Paint.Style.STROKE
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E60066FF") // Xanh Tech Blue viền góc
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0066FF") // Tâm ngắm Tech Blue
        style = Paint.Style.FILL
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6FFFFFF") // Nền trắng kính mờ 90%
        style = Paint.Style.FILL
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#93C5FD")
        strokeWidth = dp(1.2f)
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = dp(11f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val liveDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0066FF")
        style = Paint.Style.FILL
    }

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#93C5FD")
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
    }

    var isLive: Boolean = true
        set(value) {
            field = value
            postInvalidate()
        }

    var liveTitle: String = "LIVE FPV"
        set(value) {
            field = value
            postInvalidate()
        }

    var footerText: String = "720p • 30 FPS • MECANUM 4WD"
        set(value) {
            field = value
            postInvalidate()
        }

    var currentTarget: VisualDistanceDetector.TargetResult? = null
        set(value) {
            field = value
            postInvalidate()
        }

    init {
        // Không chặn các sự kiện chạm vào camera (để vẫn lấy nét khi chạm)
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f

        // --- 1. TÂM NGẮM FPV CHÍNH GIỮA (CROSSHAIR) ---
        canvas.drawCircle(cx, cy, dp(3f), centerDotPaint)

        // 4 vạch ngắm chữ thập
        val gap = dp(10f)
        val len = dp(18f)
        canvas.drawLine(cx - gap - len, cy, cx - gap, cy, linePaint)
        canvas.drawLine(cx + gap, cy, cx + gap + len, cy, linePaint)
        canvas.drawLine(cx, cy - gap - len, cx, cy - gap, linePaint)
        canvas.drawLine(cx, cy + gap, cx, cy + gap + len, linePaint)

        // Khung ngắm chữ nhật 4 góc (Corner Brackets)
        val boxW = dp(56f)
        val boxH = dp(42f)
        val arm = dp(10f)

        canvas.drawLine(cx - boxW, cy - boxH + arm, cx - boxW, cy - boxH, cornerPaint)
        canvas.drawLine(cx - boxW, cy - boxH, cx - boxW + arm, cy - boxH, cornerPaint)
        canvas.drawLine(cx + boxW - arm, cy - boxH, cx + boxW, cy - boxH, cornerPaint)
        canvas.drawLine(cx + boxW, cy - boxH, cx + boxW, cy - boxH + arm, cornerPaint)
        canvas.drawLine(cx - boxW, cy + boxH - arm, cx - boxW, cy + boxH, cornerPaint)
        canvas.drawLine(cx - boxW, cy + boxH, cx - boxW + arm, cy + boxH, cornerPaint)
        canvas.drawLine(cx + boxW - arm, cy + boxH, cx + boxW, cy + boxH, cornerPaint)
        canvas.drawLine(cx + boxW, cy + boxH, cx + boxW, cy + boxH - arm, cornerPaint)

        // --- 2. HUY HIỆU GÓC TRÁI TRÊN: [● LIVE FPV / OFFLINE] ---
        val badgeLeft = dp(16f)
        val badgeTop = dp(56f)
        val badgeW = dp(106f)
        val badgeH = dp(26f)
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH)
        val radius = dp(8f)

        canvas.drawRoundRect(badgeRect, radius, radius, badgeBgPaint)
        canvas.drawRoundRect(badgeRect, radius, radius, badgeBorderPaint)

        // Chấm tròn LIVE (xanh lá khi LIVE, đỏ/xám khi OFFLINE)
        liveDotPaint.color = if (isLive) Color.parseColor("#30D158") else Color.parseColor("#8E8E93")
        canvas.drawCircle(badgeLeft + dp(12f), badgeTop + badgeH / 2f, dp(4f), liveDotPaint)

        // Chữ LIVE FPV / OFFLINE
        textPaint.color = if (isLive) Color.WHITE else Color.parseColor("#BBBBBB")
        canvas.drawText(liveTitle, badgeLeft + dp(22f), badgeTop + badgeH / 2f + dp(3.8f), textPaint)

        // --- 3. HUY HIỆU THÔNG SỐ LUỒNG (NGAY DƯỚI HUY HIỆU LIVE) ---
        val subW = dp(220f)
        val subH = dp(24f)
        val subLeft = dp(16f)
        val subTop = badgeTop + badgeH + dp(6f)
        val subRect = RectF(subLeft, subTop, subLeft + subW, subTop + subH)

        canvas.drawRoundRect(subRect, radius, radius, badgeBgPaint)
        canvas.drawRoundRect(subRect, radius, radius, badgeBorderPaint)

        val subTextPaint = Paint(textPaint).apply {
            textSize = dp(9.5f)
            color = if (isLive) Color.parseColor("#CCFFFFFF") else Color.parseColor("#88AAAAAA")
        }
        canvas.drawText(footerText, subLeft + dp(10f), subTop + subH / 2f + dp(3.2f), subTextPaint)

        // --- 4. HIỂN THỊ MỤC TIÊU NHẬN DIỆN & CÁC ĐIỂM CHUẨN ĐO MÉT (VISUAL DETECTION OVERLAY) ---
        currentTarget?.let { target ->
            val box = target.boundingBox
            val bxLeft = box.left * w
            val bxTop = box.top * h
            val bxRight = box.right * w
            val bxBottom = box.bottom * h

            // Màu sắc theo độ an toàn khoảng cách:
            // Xanh lá = đúng khoảng cách 50cm (40cm - 60cm); Đỏ = Quá gần (<38cm); Cam vàng = Đang ở xa
            val targetColor = when {
                target.isTooClose -> Color.parseColor("#EF4444")
                target.isSafeZone -> Color.parseColor("#22C55E")
                else -> Color.parseColor("#F59E0B")
            }

            // A. Khung Corner Brackets quanh người / Marker
            val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = targetColor
                strokeWidth = dp(2.5f)
                style = Paint.Style.STROKE
            }
            val bLen = dp(16f)
            canvas.drawLine(bxLeft, bxTop + bLen, bxLeft, bxTop, bracketPaint)
            canvas.drawLine(bxLeft, bxTop, bxLeft + bLen, bxTop, bracketPaint)
            canvas.drawLine(bxRight - bLen, bxTop, bxRight, bxTop, bracketPaint)
            canvas.drawLine(bxRight, bxTop, bxRight, bxTop + bLen, bracketPaint)
            canvas.drawLine(bxLeft, bxBottom - bLen, bxLeft, bxBottom, bracketPaint)
            canvas.drawLine(bxLeft, bxBottom, bxLeft + bLen, bxBottom, bracketPaint)
            canvas.drawLine(bxRight - bLen, bxBottom, bxRight, bxBottom, bracketPaint)
            canvas.drawLine(bxRight, bxBottom, bxRight, bxBottom - bLen, bracketPaint)

            // B. Vẽ các điểm mốc cơ thể (Skeleton Landmark Dots)
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1.5f)
            }
            val lmTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = dp(9.5f)
                typeface = Typeface.DEFAULT_BOLD
                color = Color.WHITE
                setShadowLayer(dp(2.5f), 0f, 0f, Color.BLACK)
            }

            for (lm in target.landmarks) {
                val lx = lm.xNorm * w
                val ly = lm.yNorm * h
                if (lm.isReference) {
                    // Điểm mốc dùng để tính mét: Màu vàng chanh rực rỡ + vòng tròn kép
                    dotPaint.color = Color.parseColor("#FACC15")
                    ringPaint.color = Color.parseColor("#FEF08A")
                    canvas.drawCircle(lx, ly, dp(5f), dotPaint)
                    canvas.drawCircle(lx, ly, dp(8.5f), ringPaint)
                } else {
                    // Điểm dáng thông thường: Màu xanh cyan
                    dotPaint.color = Color.parseColor("#38BDF8")
                    canvas.drawCircle(lx, ly, dp(3.5f), dotPaint)
                }
                canvas.drawText(lm.name, lx + dp(7f), ly + dp(3.5f), lmTextPaint)
            }

            // C. Vẽ đường nối giữa 2 điểm mốc đo khoảng cách (Reference Measurement Line)
            val sPt = target.refStartPoint
            val ePt = target.refEndPoint
            if (sPt != null && ePt != null) {
                val sx = sPt.x * w
                val sy = sPt.y * h
                val ex = ePt.x * w
                val ey = ePt.y * h

                val refLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FACC15") // Vàng chanh rực rỡ
                    strokeWidth = dp(2.2f)
                    style = Paint.Style.STROKE
                }
                canvas.drawLine(sx, sy, ex, ey, refLinePaint)

                // Nhãn nhỏ gắn ngay giữa đường đo
                val midLineX = (sx + ex) / 2f
                val midLineY = (sy + ey) / 2f
                val tagBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#E6000000")
                    style = Paint.Style.FILL
                }
                val tagText = target.referenceDescription
                val tagTextW = lmTextPaint.measureText(tagText)
                val tagRect = RectF(
                    midLineX - tagTextW / 2f - dp(6f),
                    midLineY - dp(10f),
                    midLineX + tagTextW / 2f + dp(6f),
                    midLineY + dp(10f)
                )
                canvas.drawRoundRect(tagRect, dp(4f), dp(4f), tagBg)
                canvas.drawText(tagText, midLineX - tagTextW / 2f, midLineY + dp(3.5f), lmTextPaint)
            }

            // D. Bảng thông tin nổi trên đầu người (Floating Target HUD Card)
            val cardW = dp(230f)
            val cardH = dp(40f)
            val cardLeft = ((bxLeft + bxRight) / 2f - cardW / 2f).coerceIn(dp(10f), w - cardW - dp(10f))
            val cardTop = (bxTop - cardH - dp(12f)).coerceAtLeast(dp(10f))
            val cardRect = RectF(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)

            val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#EE0F172A") // Kính tối 93%
                style = Paint.Style.FILL
            }
            val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = targetColor
                strokeWidth = dp(1.5f)
                style = Paint.Style.STROKE
            }
            canvas.drawRoundRect(cardRect, dp(8f), dp(8f), cardBgPaint)
            canvas.drawRoundRect(cardRect, dp(8f), dp(8f), cardBorderPaint)

            // Dòng 1: Cự ly & Trạng thái
            val statusText = when {
                target.isTooClose -> "⚠ QUÁ GẦN"
                target.isSafeZone -> "✓ ĐÚNG 50CM"
                target.filteredDistanceM > 0.60f -> "➔ TIẾN LẠI"
                else -> "➔ LÙI AN TOÀN"
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = targetColor
                textSize = dp(12f)
                typeface = Typeface.DEFAULT_BOLD
            }
            val distStr = String.format(Locale.US, "%.2f m (%.0f cm)", target.filteredDistanceM, target.distanceCm)
            canvas.drawText("👤 $distStr", cardLeft + dp(10f), cardTop + dp(17f), titlePaint)

            val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dp(10f)
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(statusText, cardLeft + cardW - statPaint.measureText(statusText) - dp(10f), cardTop + dp(17f), statPaint)

            // Dòng 2: Mốc nhận diện & Độ lệch góc
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = dp(9.5f)
            }
            val deg = target.azimuthNorm * 35f
            val degStr = if (abs(deg) < 3f) "Chính giữa" else if (deg > 0) "Lệch Phải ${String.format(Locale.US, "%.1f°", deg)}" else "Lệch Trái ${String.format(Locale.US, "%.1f°", -deg)}"
            canvas.drawText("${target.referenceDescription} • $degStr", cardLeft + dp(10f), cardTop + dp(32f), subPaint)
        }

        // --- 5. ĐƯỜNG PHÂN CÁCH PHẢI (DIVIDER) ---
        canvas.drawLine(w - dp(1f), 0f, w - dp(1f), h, dividerPaint)
    }
}
