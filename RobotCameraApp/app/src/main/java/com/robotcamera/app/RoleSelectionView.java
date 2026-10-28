package com.robotcamera.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Màn hình Khởi động Chọn Vai trò Thiết bị (Device Role Selection Screen)
 * - Thiết kế Split-Screen 50/50 hiện đại:
 *   + Nửa trái: Nền Xanh Hoàng Gia (#1554D1) cho Thiết bị điều khiển.
 *   + Nửa phải: Nền Trắng Băng (#F3F6FB) cho Thiết bị streaming.
 *   + Pill tiêu đề nổi ở chính giữa đỉnh và Pill ghi nhớ nổi ở chính giữa đáy.
 */
@SuppressLint("ClickableViewAccessibility")
public class RoleSelectionView extends FrameLayout {

    public enum Role {
        CONTROLLER,
        STREAMER
    }

    public interface OnRoleSelectedListener {
        void onRoleSelected(Role role, boolean remember);
    }

    private static final int ID_CHECKBOX_REMEMBER = 998811;

    private OnRoleSelectedListener onRoleSelectedListener = null;

    private boolean isRememberChecked = false;

    public RoleSelectionView(@NonNull Context context) {
        this(context, null);
    }

    public RoleSelectionView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        buildUI(isLandscape);
    }

    public void setOnRoleSelected(OnRoleSelectedListener listener) {
        this.onRoleSelectedListener = listener;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()
        );
    }

    private float dpF(float v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()
        );
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        buildUI(isLandscape);
    }

    public void refreshLayout() {
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        buildUI(isLandscape);
    }

    public void setRememberChecked(boolean checked) {
        isRememberChecked = checked;
        CheckBox cb = findViewById(ID_CHECKBOX_REMEMBER);
        if (cb != null) {
            cb.setChecked(checked);
        }
    }

    private void dispatchRoleSelection(Role role) {
        if (onRoleSelectedListener != null) {
            onRoleSelectedListener.onRoleSelected(role, isRememberChecked);
        }
    }

    private void buildUI(boolean isLandscape) {
        CheckBox existingCb = findViewById(ID_CHECKBOX_REMEMBER);
        if (existingCb != null) {
            isRememberChecked = existingCb.isChecked();
        }

        removeAllViews();

        // =========================================================================
        // 1. NỀN CHIA ĐÔI 50/50 (TRÁI: XANH #1554D1 | PHẢI: TRẮNG XANH #F3F6FB)
        // =========================================================================
        LinearLayout splitContainer = new LinearLayout(getContext());
        splitContainer.setOrientation(isLandscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        splitContainer.setWeightSum(2f);
        splitContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // --- NỬA TRÁI: THIẾT BỊ ĐIỀU KHIỂN ---
        View leftPane = createRoleHalfPane(
                Role.CONTROLLER,
                Color.parseColor("#1554D1"),
                Color.parseColor("#3973E6"),
                Color.WHITE,
                true,
                "Thiết bị điều khiển",
                Color.WHITE,
                "Cầm trên tay điều khiển xe robot từ xa",
                Color.parseColor("#D9E6FF"),
                Arrays.asList(
                        "Màn hình FPV Cockpit nhận luồng hình ảnh xe",
                        "Xem video từ xa truyền về từ robot qua WiFi",
                        "Cụm phím D-Pad 6 hướng Mecanum nhạy bén",
                        "Bảng giám sát thông số & Cài đặt xe tập trung"
                ),
                Color.WHITE,
                Color.parseColor("#F1F5F9"),
                Color.WHITE,
                Color.parseColor("#1554D1"),
                "Vào chế độ điều khiển",
                isLandscape
        );
        splitContainer.addView(leftPane);

        // --- NỬA PHẢI: THIẾT BỊ STREAMING ---
        View rightPane = createRoleHalfPane(
                Role.STREAMER,
                Color.parseColor("#F3F6FB"),
                Color.parseColor("#DCE8FD"),
                Color.parseColor("#1D5CE5"),
                false,
                "Thiết bị streaming",
                Color.parseColor("#0F172A"),
                "Gắn trên xe robot làm mắt thần truyền hình ảnh",
                Color.parseColor("#64748B"),
                Arrays.asList(
                        "Camera độ nét cao (1080p / 720p / 480p)",
                        "Máy chủ MJPEG 8080 Zero-Latency (<50ms)",
                        "Chụp ảnh & Quay video HD lưu thư viện",
                        "Tự động hiển thị IP WiFi kết nối tiện lợi"
                ),
                Color.parseColor("#1D5CE5"),
                Color.parseColor("#334155"),
                Color.parseColor("#1D5CE5"),
                Color.WHITE,
                "Vào chế độ streaming",
                isLandscape
        );
        splitContainer.addView(rightPane);

        addView(splitContainer);

        // =========================================================================
        // 2. PILL TIÊU ĐỀ NỔI Ở ĐỈNH GIỮA (TOP CENTER FLOATING BADGE)
        // =========================================================================
        LinearLayout topHeaderPill = new LinearLayout(getContext());
        topHeaderPill.setOrientation(LinearLayout.VERTICAL);
        topHeaderPill.setGravity(Gravity.CENTER);

        GradientDrawable topPillBg = new GradientDrawable();
        topPillBg.setShape(GradientDrawable.RECTANGLE);
        topPillBg.setCornerRadius(dpF(28f));
        topPillBg.setColor(Color.WHITE);
        topHeaderPill.setBackground(topPillBg);
        topHeaderPill.setElevation(dpF(8f));
        topHeaderPill.setPadding(dp(28f), dp(9f), dp(28f), dp(10f));

        LayoutParams topPillLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        topPillLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        topPillLp.topMargin = isLandscape ? dp(18f) : dp(14f);
        topHeaderPill.setLayoutParams(topPillLp);

        TextView tvSysBadge = new TextView(getContext());
        tvSysBadge.setText("ROBOTICS 4WD MECANUM SYSTEM");
        tvSysBadge.setTextSize(9.5f);
        tvSysBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvSysBadge.setTextColor(Color.parseColor("#1D5CE5"));
        tvSysBadge.setLetterSpacing(0.14f);
        tvSysBadge.setGravity(Gravity.CENTER);
        topHeaderPill.addView(tvSysBadge);

        TextView tvMainTitle = new TextView(getContext());
        tvMainTitle.setText("Chọn vai trò thiết bị");
        tvMainTitle.setTextSize(15.5f);
        tvMainTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvMainTitle.setTextColor(Color.parseColor("#0F172A"));
        tvMainTitle.setGravity(Gravity.CENTER);
        tvMainTitle.setPadding(0, dp(1f), 0, 0);
        topHeaderPill.addView(tvMainTitle);

        addView(topHeaderPill);

        // =========================================================================
        // 3. PILL GHI NHỚ NỔI Ở ĐÁY GIỮA (BOTTOM CENTER FLOATING PILL)
        // =========================================================================
        LinearLayout bottomRememberPill = new LinearLayout(getContext());
        bottomRememberPill.setOrientation(LinearLayout.HORIZONTAL);
        bottomRememberPill.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable bottomPillBg = new GradientDrawable();
        bottomPillBg.setShape(GradientDrawable.RECTANGLE);
        bottomPillBg.setCornerRadius(dpF(28f));
        bottomPillBg.setColor(Color.WHITE);
        bottomRememberPill.setBackground(bottomPillBg);
        bottomRememberPill.setElevation(dpF(8f));
        bottomRememberPill.setPadding(dp(18f), dp(9f), dp(24f), dp(9f));

        LayoutParams bottomPillLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        bottomPillLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        bottomPillLp.bottomMargin = isLandscape ? dp(18f) : dp(12f);
        bottomPillLp.leftMargin = dp(16f);
        bottomPillLp.rightMargin = dp(16f);
        bottomRememberPill.setLayoutParams(bottomPillLp);

        CheckBox cbRemember = new CheckBox(getContext());
        cbRemember.setId(ID_CHECKBOX_REMEMBER);
        cbRemember.setChecked(isRememberChecked);
        cbRemember.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#1D5CE5")));
        cbRemember.setOnCheckedChangeListener((buttonView, isChecked) -> isRememberChecked = isChecked);
        bottomRememberPill.addView(cbRemember);

        LinearLayout rememberTextCol = new LinearLayout(getContext());
        rememberTextCol.setOrientation(LinearLayout.VERTICAL);
        rememberTextCol.setPadding(dp(6f), 0, 0, 0);
        rememberTextCol.setOnClickListener(v -> cbRemember.setChecked(!cbRemember.isChecked()));

        TextView tvRememberTitle = new TextView(getContext());
        tvRememberTitle.setText("Ghi nhớ lựa chọn này cho các lần mở app sau");
        tvRememberTitle.setTextSize(11.5f);
        tvRememberTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvRememberTitle.setTextColor(Color.parseColor("#0F172A"));
        rememberTextCol.addView(tvRememberTitle);

        TextView tvRememberSub = new TextView(getContext());
        tvRememberSub.setText("Lưu ý: Bạn luôn có thể bấm nút [Chọn vai trò] bất kỳ lúc nào để chuyển đổi lại.");
        tvRememberSub.setTextSize(10f);
        tvRememberSub.setTextColor(Color.parseColor("#64748B"));
        tvRememberSub.setPadding(0, dp(1f), 0, 0);
        rememberTextCol.addView(tvRememberSub);

        bottomRememberPill.addView(rememberTextCol);
        addView(bottomRememberPill);
    }

    private View createRoleHalfPane(
            Role role,
            int bgColor,
            int iconBoxColor,
            int iconTint,
            boolean isGamepadIcon,
            String title,
            int titleColor,
            String subtitle,
            int subtitleColor,
            List<String> features,
            int checkColor,
            int featureTextColor,
            int btnBgColor,
            int btnTextColor,
            String btnText,
            boolean isLandscape
    ) {
        FrameLayout wrapper = new FrameLayout(getContext());
        wrapper.setBackgroundColor(bgColor);
        if (isLandscape) {
            wrapper.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        } else {
            wrapper.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        LinearLayout contentCol = new LinearLayout(getContext());
        contentCol.setOrientation(LinearLayout.VERTICAL);
        contentCol.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        int padH = isLandscape ? dp(52f) : dp(32f);
        contentCol.setPadding(padH, dp(24f), dp(28f), dp(24f));
        LayoutParams contentLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        contentLp.gravity = Gravity.CENTER_VERTICAL;
        contentCol.setLayoutParams(contentLp);

        // 1. Icon Box bo góc vuông mềm
        int iconBoxSize = dp(48f);
        FrameLayout iconBox = new FrameLayout(getContext());
        LinearLayout.LayoutParams iconBoxLp = new LinearLayout.LayoutParams(iconBoxSize, iconBoxSize);
        iconBoxLp.bottomMargin = dp(14f);
        iconBox.setLayoutParams(iconBoxLp);

        GradientDrawable iconBoxBg = new GradientDrawable();
        iconBoxBg.setShape(GradientDrawable.RECTANGLE);
        iconBoxBg.setCornerRadius(dpF(14f));
        iconBoxBg.setColor(iconBoxColor);
        iconBox.setBackground(iconBoxBg);

        View iconView = new View(getContext()) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                paint.setColor(iconTint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dpF(2f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);

                dotPaint.setColor(iconTint);
                dotPaint.setStyle(Paint.Style.FILL);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                if (isGamepadIcon) {
                    // Vẽ tay cầm Gamepad chuẩn mockup
                    float w = dpF(22f);
                    float h = dpF(13f);
                    RectF rect = new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
                    canvas.drawRoundRect(rect, dpF(5f), dpF(5f), paint);
                    // D-pad dấu cộng bên trái
                    float dx = cx - dpF(4.8f);
                    float arm = dpF(2.5f);
                    canvas.drawLine(dx - arm, cy, dx + arm, cy, paint);
                    canvas.drawLine(dx, cy - arm, dx, cy + arm, paint);
                    // 2 nút chấm bên phải
                    canvas.drawCircle(cx + dpF(4.2f), cy - dpF(1.6f), dpF(1.2f), dotPaint);
                    canvas.drawCircle(cx + dpF(6.5f), cy + dpF(1.6f), dpF(1.2f), dotPaint);
                } else {
                    // Vẽ máy ảnh Camera chuẩn mockup
                    float w = dpF(21f);
                    float h = dpF(14.5f);
                    float topY = cy - h / 2f + dpF(1.5f);
                    RectF rect = new RectF(cx - w / 2f, topY, cx + w / 2f, topY + h);
                    canvas.drawRoundRect(rect, dpF(3.2f), dpF(3.2f), paint);
                    // Gù ngắm trên đỉnh máy ảnh
                    Path path = new Path();
                    path.moveTo(cx - dpF(4.5f), topY);
                    path.lineTo(cx - dpF(2.8f), topY - dpF(3f));
                    path.lineTo(cx + dpF(2.8f), topY - dpF(3f));
                    path.lineTo(cx + dpF(4.5f), topY);
                    canvas.drawPath(path, paint);
                    // Ống kính tròn ở tâm
                    canvas.drawCircle(cx, topY + h / 2f, dpF(3.5f), paint);
                }
            }
        };
        iconView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        iconBox.addView(iconView);
        contentCol.addView(iconBox);

        // 2. Tiêu đề lớn
        TextView tvTitle = new TextView(getContext());
        tvTitle.setText(title);
        tvTitle.setTextSize(isLandscape ? 24f : 21f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(titleColor);
        contentCol.addView(tvTitle);

        // 3. Phụ đề
        TextView tvSub = new TextView(getContext());
        tvSub.setText(subtitle);
        tvSub.setTextSize(12.5f);
        tvSub.setTextColor(subtitleColor);
        tvSub.setPadding(0, dp(4f), 0, dp(14f));
        contentCol.addView(tvSub);

        // 4. Danh sách 4 dòng tính năng với dấu tích ✓
        for (String feat : features) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowLp.bottomMargin = dp(7f);
            row.setLayoutParams(rowLp);

            TextView tvCheck = new TextView(getContext());
            tvCheck.setText("✓");
            tvCheck.setTextSize(12f);
            tvCheck.setTypeface(Typeface.DEFAULT_BOLD);
            tvCheck.setTextColor(checkColor);
            tvCheck.setPadding(0, 0, dp(10f), 0);

            TextView tvFeat = new TextView(getContext());
            tvFeat.setText(feat);
            tvFeat.setTextSize(12f);
            tvFeat.setTextColor(featureTextColor);

            row.addView(tvCheck);
            row.addView(tvFeat);
            contentCol.addView(row);
        }

        // 5. Nút bấm Pill CTA
        TextView btnAction = new TextView(getContext());
        btnAction.setText(btnText);
        btnAction.setTextSize(12.5f);
        btnAction.setTypeface(Typeface.DEFAULT_BOLD);
        btnAction.setTextColor(btnTextColor);
        btnAction.setGravity(Gravity.CENTER);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setCornerRadius(dpF(24f));
        btnBg.setColor(btnBgColor);
        btnAction.setBackground(btnBg);
        btnAction.setElevation(dpF(3f));
        btnAction.setPadding(dp(26f), 0, dp(26f), 0);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(42f)
        );
        btnLp.topMargin = dp(12f);
        btnAction.setLayoutParams(btnLp);

        btnAction.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start();
                    break;
            }
            return false;
        });

        btnAction.setOnClickListener(v -> dispatchRoleSelection(role));
        contentCol.addView(btnAction);

        wrapper.addView(contentCol);
        wrapper.setOnClickListener(v -> dispatchRoleSelection(role));
        return wrapper;
    }
}
