package com.robotcamera.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * ViewfinderOverlayView:
 * - Hiển thị khung ngắm trung tâm bo góc tinh tế chuẩn thiết kế mới.
 */
public class ViewfinderOverlayView extends View implements SensorEventListener {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerBoxFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerBoxStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF centerBoxRect = new RectF();
    private SensorManager sensorManager;
    private Sensor accelerometer;

    private boolean isGridEnabled = false;
    private boolean isLevelEnabled = true;

    public boolean isGridEnabled() {
        return isGridEnabled;
    }

    public void setGridEnabled(boolean gridEnabled) {
        isGridEnabled = gridEnabled;
        invalidate();
    }

    public boolean isLevelEnabled() {
        return isLevelEnabled;
    }

    public void setLevelEnabled(boolean levelEnabled) {
        isLevelEnabled = levelEnabled;
        invalidate();
    }

    public ViewfinderOverlayView(Context context) {
        this(context, null);
    }

    public ViewfinderOverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewfinderOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        gridPaint.setColor(Color.parseColor("#28FFFFFF"));
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setStyle(Paint.Style.STROKE);

        centerBoxFillPaint.setColor(Color.parseColor("#183B82F6"));
        centerBoxFillPaint.setStyle(Paint.Style.FILL);

        centerBoxStrokePaint.setColor(Color.parseColor("#3B82F6"));
        centerBoxStrokePaint.setStrokeWidth(dp(1.5f));
        centerBoxStrokePaint.setStyle(Paint.Style.STROKE);

        try {
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        } catch (Exception ignored) {}
    }

    public void startSensors() {
        if (accelerometer != null && sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public void stopSensors() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {}

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = (float) getWidth();
        float h = (float) getHeight();
        if (w <= 0 || h <= 0) return;

        if (isGridEnabled) {
            float col1 = w / 3f;
            float col2 = col1 * 2f;
            float row1 = h / 3f;
            float row2 = row1 * 2f;
            canvas.drawLine(col1, 0f, col1, h, gridPaint);
            canvas.drawLine(col2, 0f, col2, h, gridPaint);
            canvas.drawLine(0f, row1, w, row1, gridPaint);
            canvas.drawLine(0f, row2, w, row2, gridPaint);
        }

        if (isLevelEnabled) {
            float cx = w / 2f;
            float cy = h / 2f;
            float halfSize = dp(26f);
            float corner = dp(14f);
            centerBoxRect.set(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize);
            canvas.drawRoundRect(centerBoxRect, corner, corner, centerBoxFillPaint);
            canvas.drawRoundRect(centerBoxRect, corner, corner, centerBoxStrokePaint);
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
