package com.robotcamera.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * Nút bấm mũi tên điều khiển hình cánh cung/tam giác vát khí động học (Chevron Wedge Button)
 * - Thiết kế mô phỏng chính xác hình ảnh mẫu UI game đua xe / robot (Tone màu Xanh Dương - Trắng).
 * - Hỗ trợ 4 hướng: UP, DOWN, LEFT, RIGHT.
 * - Hiệu ứng chạm nhún (spring scale) và đổi màu phát sáng khi nhấn giữ.
 */
@SuppressLint("ClickableViewAccessibility")
public class ChevronArrowButton extends View {

    public enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    public interface OnHoldStateChangedListener {
        void onHoldStateChanged(boolean isHeld);
    }

    private Direction direction = Direction.UP;
    private OnHoldStateChangedListener onHoldStateChanged;

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
        invalidate();
    }

    public OnHoldStateChangedListener getOnHoldStateChanged() {
        return onHoldStateChanged;
    }

    public void setOnHoldStateChanged(OnHoldStateChangedListener onHoldStateChanged) {
        this.onHoldStateChanged = onHoldStateChanged;
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private final Path bgPath = new Path();
    private final Path chevronPath = new Path();

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chevronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean isPressedState = false;

    public ChevronArrowButton(Context context) {
        this(context, null);
    }

    public ChevronArrowButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChevronArrowButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClickable(true);
        setFocusable(true);

        fillPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1.8f));

        chevronPaint.setStyle(Paint.Style.STROKE);
        chevronPaint.setStrokeCap(Paint.Cap.ROUND);
        chevronPaint.setStrokeJoin(Paint.Join.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setHeld(boolean held) {
        isPressedState = held;
        setScaleX(held ? 0.93f : 1.0f);
        setScaleY(held ? 0.93f : 1.0f);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isPressedState = true;
                animate().scaleX(0.93f).scaleY(0.93f).setDuration(70).start();
                invalidate();
                if (onHoldStateChanged != null) {
                    onHoldStateChanged.onHoldStateChanged(true);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isPressedState = false;
                animate().scaleX(1.0f).scaleY(1.0f).setDuration(70).start();
                invalidate();
                if (onHoldStateChanged != null) {
                    onHoldStateChanged.onHoldStateChanged(false);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updatePaths((float) w, (float) h);
    }

    private void updatePaths(float w, float h) {
        bgPath.reset();
        chevronPath.reset();
        if (w <= 0f || h <= 0f) return;

        float pad = dp(4f);
        float r = dp(12f); // Độ cong góc vát

        switch (direction) {
            case LEFT: {
                float tipX = pad + dp(2f);
                float tipY = h / 2f;
                float rightX = w - pad;
                float topY = pad;
                float botY = h - pad;

                bgPath.moveTo(rightX, topY + r);
                bgPath.quadTo(rightX, topY, rightX - r, topY);
                bgPath.lineTo(tipX + r, tipY - dp(6f));
                bgPath.quadTo(tipX, tipY, tipX + r, tipY + dp(6f));
                bgPath.lineTo(rightX - r, botY);
                bgPath.quadTo(rightX, botY, rightX, botY - r);
                bgPath.close();

                float cTipX = w * 0.38f;
                float cTipY = h / 2f;
                float cArmX = w * 0.65f;
                float cArmSpread = h * 0.24f;
                chevronPath.moveTo(cArmX, cTipY - cArmSpread);
                chevronPath.lineTo(cTipX, cTipY);
                chevronPath.lineTo(cArmX, cTipY + cArmSpread);
                break;
            }
            case RIGHT: {
                float tipX = w - pad - dp(2f);
                float tipY = h / 2f;
                float leftX = pad;
                float topY = pad;
                float botY = h - pad;

                bgPath.moveTo(leftX, topY + r);
                bgPath.quadTo(leftX, topY, leftX + r, topY);
                bgPath.lineTo(tipX - r, tipY - dp(6f));
                bgPath.quadTo(tipX, tipY, tipX - r, tipY + dp(6f));
                bgPath.lineTo(leftX + r, botY);
                bgPath.quadTo(leftX, botY, leftX, botY - r);
                bgPath.close();

                float cTipX = w * 0.62f;
                float cTipY = h / 2f;
                float cArmX = w * 0.35f;
                float cArmSpread = h * 0.24f;
                chevronPath.moveTo(cArmX, cTipY - cArmSpread);
                chevronPath.lineTo(cTipX, cTipY);
                chevronPath.lineTo(cArmX, cTipY + cArmSpread);
                break;
            }
            case UP: {
                float tipX = w / 2f;
                float tipY = pad + dp(2f);
                float botY = h - pad;
                float leftX = pad;
                float rightX = w - pad;

                bgPath.moveTo(leftX, botY - r);
                bgPath.quadTo(leftX, botY, leftX + r, botY);
                bgPath.lineTo(rightX - r, botY);
                bgPath.quadTo(rightX, botY, rightX, botY - r);
                bgPath.lineTo(tipX + dp(6f), tipY + r);
                bgPath.quadTo(tipX, tipY, tipX - dp(6f), tipY + r);
                bgPath.close();

                float cTipX = w / 2f;
                float cTipY = h * 0.38f;
                float cArmY = h * 0.65f;
                float cArmSpread = w * 0.24f;
                chevronPath.moveTo(cTipX - cArmSpread, cArmY);
                chevronPath.lineTo(cTipX, cTipY);
                chevronPath.lineTo(cTipX + cArmSpread, cArmY);
                break;
            }
            case DOWN: {
                float tipX = w / 2f;
                float tipY = h - pad - dp(2f);
                float topY = pad;
                float leftX = pad;
                float rightX = w - pad;

                bgPath.moveTo(leftX, topY + r);
                bgPath.quadTo(leftX, topY, leftX + r, topY);
                bgPath.lineTo(rightX - r, topY);
                bgPath.quadTo(rightX, topY, rightX, topY + r);
                bgPath.lineTo(tipX + dp(6f), tipY - r);
                bgPath.quadTo(tipX, tipY, tipX - dp(6f), tipY - r);
                bgPath.close();

                float cTipX = w / 2f;
                float cTipY = h * 0.62f;
                float cArmY = h * 0.35f;
                float cArmSpread = w * 0.24f;
                chevronPath.moveTo(cTipX - cArmSpread, cArmY);
                chevronPath.lineTo(cTipX, cTipY);
                chevronPath.lineTo(cTipX + cArmSpread, cArmY);
                break;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = (float) getWidth();
        float h = (float) getHeight();
        if (w <= 0f || h <= 0f) return;

        if (bgPath.isEmpty()) updatePaths(w, h);

        if (isPressedState) {
            fillPaint.setShader(new LinearGradient(
                0f, 0f, w, h,
                Color.parseColor("#0066FF"),
                Color.parseColor("#0044CC"),
                Shader.TileMode.CLAMP
            ));
            borderPaint.setColor(Color.parseColor("#60A5FA"));
            borderPaint.setStrokeWidth(dp(2.2f));
        } else {
            fillPaint.setShader(new LinearGradient(
                0f, 0f, w, h,
                Color.parseColor("#2E7BE6"),
                Color.parseColor("#1A5AC7"),
                Shader.TileMode.CLAMP
            ));
            borderPaint.setColor(Color.parseColor("#93C5FD"));
            borderPaint.setStrokeWidth(dp(1.8f));
        }

        canvas.drawPath(bgPath, fillPaint);
        canvas.drawPath(bgPath, borderPaint);

        glowPaint.setStrokeWidth(dp(7.5f));
        glowPaint.setColor(isPressedState ? Color.parseColor("#8000E5FF") : Color.parseColor("#4D00E5FF"));
        canvas.drawPath(chevronPath, glowPaint);

        chevronPaint.setStrokeWidth(dp(4.5f));
        chevronPaint.setColor(Color.WHITE);
        canvas.drawPath(chevronPath, chevronPaint);
    }
}
