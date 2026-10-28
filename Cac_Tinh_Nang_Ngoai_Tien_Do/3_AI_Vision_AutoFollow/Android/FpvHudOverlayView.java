package com.robotcamera.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Lớp phủ HUD FPV hiển thị đè lên màn hình Camera khi ở chế độ Điều khiển (CameraMode.CONTROL)
 * Mang phong cách Cockpit điều khiển xe robot / FPV drone chuyên nghiệp:
 * 1. Tâm ngắm ngắm chuẩn mục tiêu (Crosshair + Corner Brackets)
 * 2. Huy hiệu [● LIVE FPV] phát sáng xanh lá
 * 3. Huy hiệu thông số luồng [720p • 30 FPS • MECANUM 4WD]
 * 4. Vạch chia biên giới công nghệ ngăn cách giữa màn hình Camera và bảng điều khiển
 */
public class FpvHudOverlayView extends View {

    private float dp(float v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()
        );
    }

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint liveDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean isLive = true;
    private String liveTitle = "LIVE FPV";
    private String footerText = "720p • 30 FPS • MECANUM 4WD";
    private VisualDistanceDetector.TargetResult currentTarget = null;

    public FpvHudOverlayView(Context context) {
        this(context, null);
    }

    public FpvHudOverlayView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FpvHudOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
        setClickable(false);
        setFocusable(false);
    }

    private void initPaints() {
        linePaint.setColor(Color.parseColor("#990066FF")); // Xanh Tech Blue công nghệ
        linePaint.setStrokeWidth(dp(1.5f));
        linePaint.setStyle(Paint.Style.STROKE);

        cornerPaint.setColor(Color.parseColor("#E60066FF")); // Xanh Tech Blue viền góc
        cornerPaint.setStrokeWidth(dp(2f));
        cornerPaint.setStyle(Paint.Style.STROKE);

        centerDotPaint.setColor(Color.parseColor("#0066FF")); // Tâm ngắm Tech Blue
        centerDotPaint.setStyle(Paint.Style.FILL);

        badgeBgPaint.setColor(Color.parseColor("#E6FFFFFF")); // Nền trắng kính mờ 90%
        badgeBgPaint.setStyle(Paint.Style.FILL);

        badgeBorderPaint.setColor(Color.parseColor("#93C5FD"));
        badgeBorderPaint.setStrokeWidth(dp(1.2f));
        badgeBorderPaint.setStyle(Paint.Style.STROKE);

        textPaint.setColor(Color.parseColor("#0F172A"));
        textPaint.setTextSize(dp(11f));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        liveDotPaint.setColor(Color.parseColor("#0066FF"));
        liveDotPaint.setStyle(Paint.Style.FILL);

        dividerPaint.setColor(Color.parseColor("#93C5FD"));
        dividerPaint.setStrokeWidth(dp(2f));
        dividerPaint.setStyle(Paint.Style.STROKE);
    }

    public boolean isLive() {
        return isLive;
    }

    public void setLive(boolean live) {
        this.isLive = live;
        postInvalidate();
    }

    public String getLiveTitle() {
        return liveTitle;
    }

    public void setLiveTitle(String liveTitle) {
        this.liveTitle = liveTitle != null ? liveTitle : "";
        postInvalidate();
    }

    public String getFooterText() {
        return footerText;
    }

    public void setFooterText(String footerText) {
        this.footerText = footerText != null ? footerText : "";
        postInvalidate();
    }

    public VisualDistanceDetector.TargetResult getCurrentTarget() {
        return currentTarget;
    }

    public void setCurrentTarget(VisualDistanceDetector.TargetResult currentTarget) {
        this.currentTarget = currentTarget;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = (float) getWidth();
        float h = (float) getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;

        // --- 1. TÂM NGẮM FPV CHÍNH GIỮA (CROSSHAIR) ---
        canvas.drawCircle(cx, cy, dp(3f), centerDotPaint);

        // 4 vạch ngắm chữ thập
        float gap = dp(10f);
        float len = dp(18f);
        canvas.drawLine(cx - gap - len, cy, cx - gap, cy, linePaint);
        canvas.drawLine(cx + gap, cy, cx + gap + len, cy, linePaint);
        canvas.drawLine(cx, cy - gap - len, cx, cy - gap, linePaint);
        canvas.drawLine(cx, cy + gap, cx, cy + gap + len, linePaint);

        // Khung ngắm chữ nhật 4 góc (Corner Brackets)
        float boxW = dp(56f);
        float boxH = dp(42f);
        float arm = dp(10f);

        canvas.drawLine(cx - boxW, cy - boxH + arm, cx - boxW, cy - boxH, cornerPaint);
        canvas.drawLine(cx - boxW, cy - boxH, cx - boxW + arm, cy - boxH, cornerPaint);
        canvas.drawLine(cx + boxW - arm, cy - boxH, cx + boxW, cy - boxH, cornerPaint);
        canvas.drawLine(cx + boxW, cy - boxH, cx + boxW, cy - boxH + arm, cornerPaint);
        canvas.drawLine(cx - boxW, cy + boxH - arm, cx - boxW, cy + boxH, cornerPaint);
        canvas.drawLine(cx - boxW, cy + boxH, cx - boxW + arm, cy + boxH, cornerPaint);
        canvas.drawLine(cx + boxW - arm, cy + boxH, cx + boxW, cy + boxH, cornerPaint);
        canvas.drawLine(cx + boxW, cy + boxH, cx + boxW, cy + boxH - arm, cornerPaint);

        // --- 2. HUY HIỆU GÓC TRÁI TRÊN: [● LIVE FPV / OFFLINE] ---
        float badgeLeft = dp(16f);
        float badgeTop = dp(56f);
        float badgeW = dp(106f);
        float badgeH = dp(26f);
        RectF badgeRect = new RectF(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH);
        float radius = dp(8f);

        canvas.drawRoundRect(badgeRect, radius, radius, badgeBgPaint);
        canvas.drawRoundRect(badgeRect, radius, radius, badgeBorderPaint);

        // Chấm tròn LIVE (xanh lá khi LIVE, đỏ/xám khi OFFLINE)
        liveDotPaint.setColor(isLive ? Color.parseColor("#30D158") : Color.parseColor("#8E8E93"));
        canvas.drawCircle(badgeLeft + dp(12f), badgeTop + badgeH / 2f, dp(4f), liveDotPaint);

        // Chữ LIVE FPV / OFFLINE
        textPaint.setColor(isLive ? Color.WHITE : Color.parseColor("#BBBBBB"));
        canvas.drawText(liveTitle, badgeLeft + dp(22f), badgeTop + badgeH / 2f + dp(3.8f), textPaint);

        // --- 3. HUY HIỆU THÔNG SỐ LUỒNG (NGAY DƯỚI HUY HIỆU LIVE) ---
        float subW = dp(220f);
        float subH = dp(24f);
        float subLeft = dp(16f);
        float subTop = badgeTop + badgeH + dp(6f);
        RectF subRect = new RectF(subLeft, subTop, subLeft + subW, subTop + subH);

        canvas.drawRoundRect(subRect, radius, radius, badgeBgPaint);
        canvas.drawRoundRect(subRect, radius, radius, badgeBorderPaint);

        Paint subTextPaint = new Paint(textPaint);
        subTextPaint.setTextSize(dp(9.5f));
        subTextPaint.setColor(isLive ? Color.parseColor("#CCFFFFFF") : Color.parseColor("#88AAAAAA"));
        canvas.drawText(footerText, subLeft + dp(10f), subTop + subH / 2f + dp(3.2f), subTextPaint);

        // --- 4. HIỂN THỊ MỤC TIÊU NHẬN DIỆN & CÁC ĐIỂM CHUẨN ĐO MÉT (VISUAL DETECTION OVERLAY) ---
        VisualDistanceDetector.TargetResult target = currentTarget;
        if (target != null) {
            RectF box = target.boundingBox;
            float bxLeft = box.left * w;
            float bxTop = box.top * h;
            float bxRight = box.right * w;
            float bxBottom = box.bottom * h;

            // Màu sắc theo độ an toàn khoảng cách:
            // Xanh lá = đúng khoảng cách 50cm (40cm - 60cm); Đỏ = Quá gần (<38cm); Cam vàng = Đang ở xa
            int targetColor;
            if (target.isTooClose()) {
                targetColor = Color.parseColor("#EF4444");
            } else if (target.isSafeZone()) {
                targetColor = Color.parseColor("#22C55E");
            } else {
                targetColor = Color.parseColor("#F59E0B");
            }

            // A. Khung Corner Brackets quanh người / Marker
            Paint bracketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bracketPaint.setColor(targetColor);
            bracketPaint.setStrokeWidth(dp(2.5f));
            bracketPaint.setStyle(Paint.Style.STROKE);

            float bLen = dp(16f);
            canvas.drawLine(bxLeft, bxTop + bLen, bxLeft, bxTop, bracketPaint);
            canvas.drawLine(bxLeft, bxTop, bxLeft + bLen, bxTop, bracketPaint);
            canvas.drawLine(bxRight - bLen, bxTop, bxRight, bxTop, bracketPaint);
            canvas.drawLine(bxRight, bxTop, bxRight, bxTop + bLen, bracketPaint);
            canvas.drawLine(bxLeft, bxBottom - bLen, bxLeft, bxBottom, bracketPaint);
            canvas.drawLine(bxLeft, bxBottom, bxLeft + bLen, bxBottom, bracketPaint);
            canvas.drawLine(bxRight - bLen, bxBottom, bxRight, bxBottom, bracketPaint);
            canvas.drawLine(bxRight, bxBottom, bxRight, bxBottom - bLen, bracketPaint);

            // B. Vẽ các điểm mốc cơ thể (Skeleton Landmark Dots)
            Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotPaint.setStyle(Paint.Style.FILL);

            Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(1.5f));

            Paint lmTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            lmTextPaint.setTextSize(dp(9.5f));
            lmTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
            lmTextPaint.setColor(Color.WHITE);
            lmTextPaint.setShadowLayer(dp(2.5f), 0f, 0f, Color.BLACK);

            if (target.landmarks != null) {
                for (VisualDistanceDetector.LandmarkPoint lm : target.landmarks) {
                    float lx = lm.xNorm * w;
                    float ly = lm.yNorm * h;
                    if (lm.isReference) {
                        // Điểm mốc dùng để tính mét: Màu vàng chanh rực rỡ + vòng tròn kép
                        dotPaint.setColor(Color.parseColor("#FACC15"));
                        ringPaint.setColor(Color.parseColor("#FEF08A"));
                        canvas.drawCircle(lx, ly, dp(5f), dotPaint);
                        canvas.drawCircle(lx, ly, dp(8.5f), ringPaint);
                    } else {
                        // Điểm dáng thông thường: Màu xanh cyan
                        dotPaint.setColor(Color.parseColor("#38BDF8"));
                        canvas.drawCircle(lx, ly, dp(3.5f), dotPaint);
                    }
                    if (lm.name != null) {
                        canvas.drawText(lm.name, lx + dp(7f), ly + dp(3.5f), lmTextPaint);
                    }
                }
            }

            // C. Vẽ đường nối giữa 2 điểm mốc đo khoảng cách (Reference Measurement Line)
            PointF sPt = target.refStartPoint;
            PointF ePt = target.refEndPoint;
            if (sPt != null && ePt != null) {
                float sx = sPt.x * w;
                float sy = sPt.y * h;
                float ex = ePt.x * w;
                float ey = ePt.y * h;

                Paint refLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                refLinePaint.setColor(Color.parseColor("#FACC15")); // Vàng chanh rực rỡ
                refLinePaint.setStrokeWidth(dp(2.2f));
                refLinePaint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(sx, sy, ex, ey, refLinePaint);

                // Nhãn nhỏ gắn ngay giữa đường đo
                float midLineX = (sx + ex) / 2f;
                float midLineY = (sy + ey) / 2f;
                Paint tagBg = new Paint(Paint.ANTI_ALIAS_FLAG);
                tagBg.setColor(Color.parseColor("#E6000000"));
                tagBg.setStyle(Paint.Style.FILL);

                String tagText = target.referenceDescription != null ? target.referenceDescription : "";
                float tagTextW = lmTextPaint.measureText(tagText);
                RectF tagRect = new RectF(
                        midLineX - tagTextW / 2f - dp(6f),
                        midLineY - dp(10f),
                        midLineX + tagTextW / 2f + dp(6f),
                        midLineY + dp(10f)
                );
                canvas.drawRoundRect(tagRect, dp(4f), dp(4f), tagBg);
                canvas.drawText(tagText, midLineX - tagTextW / 2f, midLineY + dp(3.5f), lmTextPaint);
            }

            // D. Bảng thông tin nổi trên đầu người (Floating Target HUD Card)
            float cardW = dp(230f);
            float cardH = dp(40f);
            float cardLeft = Math.max(dp(10f), Math.min(w - cardW - dp(10f), (bxLeft + bxRight) / 2f - cardW / 2f));
            float cardTop = Math.max(dp(10f), bxTop - cardH - dp(12f));
            RectF cardRect = new RectF(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH);

            Paint cardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            cardBgPaint.setColor(Color.parseColor("#EE0F172A")); // Kính tối 93%
            cardBgPaint.setStyle(Paint.Style.FILL);

            Paint cardBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            cardBorderPaint.setColor(targetColor);
            cardBorderPaint.setStrokeWidth(dp(1.5f));
            cardBorderPaint.setStyle(Paint.Style.STROKE);

            canvas.drawRoundRect(cardRect, dp(8f), dp(8f), cardBgPaint);
            canvas.drawRoundRect(cardRect, dp(8f), dp(8f), cardBorderPaint);

            // Dòng 1: Cự ly & Trạng thái
            String statusText;
            if (target.isTooClose()) {
                statusText = "⚠ QUÁ GẦN";
            } else if (target.isSafeZone()) {
                statusText = "✓ ĐÚNG 50CM";
            } else if (target.filteredDistanceM > 0.60f) {
                statusText = "➔ TIẾN LẠI";
            } else {
                statusText = "➔ LÙI AN TOÀN";
            }

            Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setColor(targetColor);
            titlePaint.setTextSize(dp(12f));
            titlePaint.setTypeface(Typeface.DEFAULT_BOLD);

            String distStr = String.format(Locale.US, "%.2f m (%.0f cm)", target.filteredDistanceM, target.getDistanceCm());
            canvas.drawText("👤 " + distStr, cardLeft + dp(10f), cardTop + dp(17f), titlePaint);

            Paint statPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            statPaint.setColor(Color.WHITE);
            statPaint.setTextSize(dp(10f));
            statPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(statusText, cardLeft + cardW - statPaint.measureText(statusText) - dp(10f), cardTop + dp(17f), statPaint);

            // Dòng 2: Mốc nhận diện & Độ lệch góc
            Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            subPaint.setColor(Color.parseColor("#94A3B8"));
            subPaint.setTextSize(dp(9.5f));

            float deg = target.azimuthNorm * 35f;
            String degStr;
            if (Math.abs(deg) < 3f) {
                degStr = "Chính giữa";
            } else if (deg > 0) {
                degStr = "Lệch Phải " + String.format(Locale.US, "%.1f°", deg);
            } else {
                degStr = "Lệch Trái " + String.format(Locale.US, "%.1f°", -deg);
            }
            String desc = target.referenceDescription != null ? target.referenceDescription : "";
            canvas.drawText(desc + " • " + degStr, cardLeft + dp(10f), cardTop + dp(32f), subPaint);
        }

        // --- 5. ĐƯỜNG PHÂN CÁCH PHẢI (DIVIDER) ---
        canvas.drawLine(w - dp(1f), 0f, w - dp(1f), h, dividerPaint);
    }
}
