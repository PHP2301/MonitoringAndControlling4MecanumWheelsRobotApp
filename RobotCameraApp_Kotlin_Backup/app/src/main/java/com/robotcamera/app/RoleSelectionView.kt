package com.robotcamera.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*

/**
 * Màn hình Khởi động Chọn Vai trò Thiết bị (Device Role Selection Screen)
 * - Thiết kế Split-Screen 50/50 hiện đại:
 *   + Nửa trái: Nền Xanh Hoàng Gia (#1554D1) cho Thiết bị điều khiển.
 *   + Nửa phải: Nền Trắng Băng (#F3F6FB) cho Thiết bị streaming.
 *   + Pill tiêu đề nổi ở chính giữa đỉnh và Pill ghi nhớ nổi ở chính giữa đáy.
 */
@SuppressLint("ClickableViewAccessibility")
class RoleSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    enum class Role {
        CONTROLLER,
        STREAMER
    }

    var onRoleSelected: ((role: Role, remember: Boolean) -> Unit)? = null

    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    ).toInt()

    private fun dpF(v: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    )

    private var isRememberChecked = false

    init {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        buildUI(isLandscape)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        buildUI(isLandscape)
    }

    fun refreshLayout() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        buildUI(isLandscape)
    }

    fun setRememberChecked(checked: Boolean) {
        isRememberChecked = checked
        findViewById<CheckBox>(ID_CHECKBOX_REMEMBER)?.isChecked = checked
    }

    private fun buildUI(isLandscape: Boolean) {
        findViewById<CheckBox>(ID_CHECKBOX_REMEMBER)?.let {
            isRememberChecked = it.isChecked
        }

        removeAllViews()

        // =========================================================================
        // 1. NỀN CHIA ĐÔI 50/50 (TRÁI: XANH #1554D1 | PHẢI: TRẮNG XANH #F3F6FB)
        // =========================================================================
        val splitContainer = LinearLayout(context).apply {
            orientation = if (isLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            weightSum = 2f
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // --- NỬA TRÁI: THIẾT BỊ ĐIỀU KHIỂN ---
        val leftPane = createRoleHalfPane(
            role = Role.CONTROLLER,
            bgColor = Color.parseColor("#1554D1"),
            iconBoxColor = Color.parseColor("#3973E6"),
            iconTint = Color.WHITE,
            isGamepadIcon = true,
            title = "Thiết bị điều khiển",
            titleColor = Color.WHITE,
            subtitle = "Cầm trên tay điều khiển xe robot từ xa",
            subtitleColor = Color.parseColor("#D9E6FF"),
            features = listOf(
                "Màn hình FPV Cockpit nhận luồng hình ảnh xe",
                "Xem video từ xa truyền về từ robot qua WiFi",
                "Cụm phím D-Pad 6 hướng Mecanum nhạy bén",
                "Bảng giám sát thông số & Cài đặt xe tập trung"
            ),
            checkColor = Color.WHITE,
            featureTextColor = Color.parseColor("#F1F5F9"),
            btnBgColor = Color.WHITE,
            btnTextColor = Color.parseColor("#1554D1"),
            btnText = "Vào chế độ điều khiển",
            isLandscape = isLandscape
        )
        splitContainer.addView(leftPane)

        // --- NỬA PHẢI: THIẾT BỊ STREAMING ---
        val rightPane = createRoleHalfPane(
            role = Role.STREAMER,
            bgColor = Color.parseColor("#F3F6FB"),
            iconBoxColor = Color.parseColor("#DCE8FD"),
            iconTint = Color.parseColor("#1D5CE5"),
            isGamepadIcon = false,
            title = "Thiết bị streaming",
            titleColor = Color.parseColor("#0F172A"),
            subtitle = "Gắn trên xe robot làm mắt thần truyền hình ảnh",
            subtitleColor = Color.parseColor("#64748B"),
            features = listOf(
                "Camera độ nét cao (1080p / 720p / 480p)",
                "Máy chủ MJPEG 8080 Zero-Latency (<50ms)",
                "Chụp ảnh & Quay video HD lưu thư viện",
                "Tự động hiển thị IP WiFi kết nối tiện lợi"
            ),
            checkColor = Color.parseColor("#1D5CE5"),
            featureTextColor = Color.parseColor("#334155"),
            btnBgColor = Color.parseColor("#1D5CE5"),
            btnTextColor = Color.WHITE,
            btnText = "Vào chế độ streaming",
            isLandscape = isLandscape
        )
        splitContainer.addView(rightPane)

        addView(splitContainer)

        // =========================================================================
        // 2. PILL TIÊU ĐỀ NỔI Ở ĐỈNH GIỮA (TOP CENTER FLOATING BADGE)
        // =========================================================================
        val topHeaderPill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(28f)
                setColor(Color.WHITE)
            }
            elevation = dpF(8f)
            setPadding(dp(28f), dp(9f), dp(28f), dp(10f))
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = if (isLandscape) dp(18f) else dp(14f)
            }
        }

        val tvSysBadge = TextView(context).apply {
            text = "ROBOTICS 4WD MECANUM SYSTEM"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1D5CE5"))
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
        }
        topHeaderPill.addView(tvSysBadge)

        val tvMainTitle = TextView(context).apply {
            text = "Chọn vai trò thiết bị"
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            setPadding(0, dp(1f), 0, 0)
        }
        topHeaderPill.addView(tvMainTitle)

        addView(topHeaderPill)

        // =========================================================================
        // 3. PILL GHI NHỚ NỔI Ở ĐÁY GIỮA (BOTTOM CENTER FLOATING PILL)
        // =========================================================================
        val bottomRememberPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(28f)
                setColor(Color.WHITE)
            }
            elevation = dpF(8f)
            setPadding(dp(18f), dp(9f), dp(24f), dp(9f))
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = if (isLandscape) dp(18f) else dp(12f)
                leftMargin = dp(16f)
                rightMargin = dp(16f)
            }
        }

        val cbRemember = CheckBox(context).apply {
            id = ID_CHECKBOX_REMEMBER
            isChecked = isRememberChecked
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#1D5CE5"))
            setOnCheckedChangeListener { _, isChecked ->
                isRememberChecked = isChecked
            }
        }
        bottomRememberPill.addView(cbRemember)

        val rememberTextCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6f), 0, 0, 0)
            setOnClickListener {
                cbRemember.isChecked = !cbRemember.isChecked
            }
        }

        val tvRememberTitle = TextView(context).apply {
            text = "Ghi nhớ lựa chọn này cho các lần mở app sau"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
        }
        rememberTextCol.addView(tvRememberTitle)

        val tvRememberSub = TextView(context).apply {
            text = "Lưu ý: Bạn luôn có thể bấm nút [Chọn vai trò] bất kỳ lúc nào để chuyển đổi lại."
            textSize = 10f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(1f), 0, 0)
        }
        rememberTextCol.addView(tvRememberSub)

        bottomRememberPill.addView(rememberTextCol)
        addView(bottomRememberPill)
    }

    private fun createRoleHalfPane(
        role: Role,
        bgColor: Int,
        iconBoxColor: Int,
        iconTint: Int,
        isGamepadIcon: Boolean,
        title: String,
        titleColor: Int,
        subtitle: String,
        subtitleColor: Int,
        features: List<String>,
        checkColor: Int,
        featureTextColor: Int,
        btnBgColor: Int,
        btnTextColor: Int,
        btnText: String,
        isLandscape: Boolean
    ): View {
        val wrapper = FrameLayout(context).apply {
            setBackgroundColor(bgColor)
            layoutParams = if (isLandscape) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            } else {
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
        }

        val contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            val padH = if (isLandscape) dp(52f) else dp(32f)
            setPadding(padH, dp(24f), dp(28f), dp(24f))
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }

        // 1. Icon Box bo góc vuông mềm
        val iconBoxSize = dp(48f)
        val iconBox = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconBoxSize, iconBoxSize).apply {
                bottomMargin = dp(14f)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(14f)
                setColor(iconBoxColor)
            }
        }

        val iconView = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = iconTint
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = iconTint
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                if (isGamepadIcon) {
                    // Vẽ tay cầm Gamepad chuẩn mockup
                    val w = dpF(22f)
                    val h = dpF(13f)
                    val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                    canvas.drawRoundRect(rect, dpF(5f), dpF(5f), paint)
                    // D-pad dấu cộng bên trái
                    val dx = cx - dpF(4.8f)
                    val arm = dpF(2.5f)
                    canvas.drawLine(dx - arm, cy, dx + arm, cy, paint)
                    canvas.drawLine(dx, cy - arm, dx, cy + arm, paint)
                    // 2 nút chấm bên phải
                    canvas.drawCircle(cx + dpF(4.2f), cy - dpF(1.6f), dpF(1.2f), dotPaint)
                    canvas.drawCircle(cx + dpF(6.5f), cy + dpF(1.6f), dpF(1.2f), dotPaint)
                } else {
                    // Vẽ máy ảnh Camera chuẩn mockup
                    val w = dpF(21f)
                    val h = dpF(14.5f)
                    val topY = cy - h / 2f + dpF(1.5f)
                    val rect = RectF(cx - w / 2f, topY, cx + w / 2f, topY + h)
                    canvas.drawRoundRect(rect, dpF(3.2f), dpF(3.2f), paint)
                    // Gù ngắm trên đỉnh máy ảnh
                    val path = Path().apply {
                        moveTo(cx - dpF(4.5f), topY)
                        lineTo(cx - dpF(2.8f), topY - dpF(3f))
                        lineTo(cx + dpF(2.8f), topY - dpF(3f))
                        lineTo(cx + dpF(4.5f), topY)
                    }
                    canvas.drawPath(path, paint)
                    // Ống kính tròn ở tâm
                    canvas.drawCircle(cx, topY + h / 2f, dpF(3.5f), paint)
                }
            }
        }.apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        iconBox.addView(iconView)
        contentCol.addView(iconBox)

        // 2. Tiêu đề lớn
        val tvTitle = TextView(context).apply {
            text = title
            textSize = if (isLandscape) 24f else 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(titleColor)
        }
        contentCol.addView(tvTitle)

        // 3. Phụ đề
        val tvSub = TextView(context).apply {
            text = subtitle
            textSize = 12.5f
            setTextColor(subtitleColor)
            setPadding(0, dp(4f), 0, dp(14f))
        }
        contentCol.addView(tvSub)

        // 4. Danh sách 4 dòng tính năng với dấu tích ✓
        for (feat in features) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(7f)
                }
            }
            val tvCheck = TextView(context).apply {
                text = "✓"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(checkColor)
                setPadding(0, 0, dp(10f), 0)
            }
            val tvFeat = TextView(context).apply {
                text = feat
                textSize = 12f
                setTextColor(featureTextColor)
            }
            row.addView(tvCheck)
            row.addView(tvFeat)
            contentCol.addView(row)
        }

        // 5. Nút bấm Pill CTA
        val btnAction = TextView(context).apply {
            text = btnText
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(btnTextColor)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(24f)
                setColor(btnBgColor)
            }
            elevation = dpF(3f)
            setPadding(dp(26f), 0, dp(26f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(42f)
            ).apply {
                topMargin = dp(12f)
            }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start()
                    }
                }
                false
            }

            setOnClickListener {
                onRoleSelected?.invoke(role, isRememberChecked)
            }
        }
        contentCol.addView(btnAction)

        wrapper.addView(contentCol)
        wrapper.setOnClickListener {
            onRoleSelected?.invoke(role, isRememberChecked)
        }
        return wrapper
    }

    companion object {
        private const val ID_CHECKBOX_REMEMBER = 998811
    }
}
