package com.robotcamera.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tiện ích giao diện dùng chung: dp(), spacer, hiệu ứng chạm lò xo, chớp màn trập.
 */
public class MainActivityUiHelpers {

    @SuppressLint("ClickableViewAccessibility")
    public static void applySpringTouchEffect(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(70).start();
                    break;
                case MotionEvent.ACTION_UP:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(110).withEndAction(v::performClick).start();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(110).start();
                    break;
            }
            return true;
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void applyShutterTouchAnimation(
            MainActivity act,
            View container,
            View innerCore,
            Runnable onTrigger
    ) {
        container.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    innerCore.animate().scaleX(0.80f).scaleY(0.80f).setDuration(70).start();
                    container.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).start();
                    break;
                case MotionEvent.ACTION_UP: {
                    float targetScale = (act.currentMode == MainActivity.CameraMode.VIDEO && act.isRecordingVideo) ? 0.55f :
                            (act.currentMode == MainActivity.CameraMode.VIDEO) ? 0.85f : 1.0f;
                    innerCore.animate().scaleX(targetScale).scaleY(targetScale).setDuration(120).start();
                    container.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).withEndAction(onTrigger).start();
                    break;
                }
                case MotionEvent.ACTION_CANCEL: {
                    float targetScale = (act.currentMode == MainActivity.CameraMode.VIDEO && act.isRecordingVideo) ? 0.55f :
                            (act.currentMode == MainActivity.CameraMode.VIDEO) ? 0.85f : 1.0f;
                    innerCore.animate().scaleX(targetScale).scaleY(targetScale).setDuration(100).start();
                    container.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    break;
                }
            }
            return true;
        });
    }

    public static void triggerShutterFlash(MainActivity act) {
        if (act.shutterFlashOverlay != null) {
            act.shutterFlashOverlay.setVisibility(View.VISIBLE);
            act.shutterFlashOverlay.setAlpha(0.65f);
            act.shutterFlashOverlay.animate()
                    .alpha(0.0f)
                    .setDuration(180)
                    .withEndAction(() -> act.shutterFlashOverlay.setVisibility(View.GONE))
                    .start();
        }
    }

    public static int dp(Context ctx, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                ctx.getResources().getDisplayMetrics()
        );
    }

    public static View createFlexSpacer(Context ctx) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    public static View createHorizontalSpacer(Context ctx, int sizeDp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(sizeDp, 1));
        return v;
    }

    public static View createVerticalSpacer(Context ctx, int sizeDp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, sizeDp));
        return v;
    }

    public static GradientDrawable glassContainerBg(Context ctx, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius((float) dp(ctx, radiusDp));
        gd.setColor(Color.parseColor("#2EFFFFFF"));
        gd.setStroke(dp(ctx, 1f), Color.parseColor("#66FFFFFF"));
        return gd;
    }

    public static GradientDrawable glassChipBg(Context ctx, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius((float) dp(ctx, radiusDp));
        gd.setColor(Color.parseColor("#33FFFFFF"));
        gd.setStroke(dp(ctx, 1f), Color.parseColor("#55FFFFFF"));
        return gd;
    }

    public static void applyGlassTextStyle(TextView tv) {
        tv.setTextColor(Color.WHITE);
        tv.setShadowLayer(3f, 0f, 1f, Color.parseColor("#99000000"));
    }
}
