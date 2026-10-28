package com.robotcamera.app;

import android.app.Dialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.NestedScrollView;

/**
 * Hộp thoại Cài đặt máy ảnh (Giao diện Modal Card Trắng - Xanh Dương chuẩn thiết kế mới).
 */
public class MainActivitySettings {

    public static void showSettingsDialog(MainActivity act) {
        Dialog dialog = new Dialog(act);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0.55f);
        }

        NestedScrollView rootScrollView = new NestedScrollView(act);
        GradientDrawable scrollBg = new GradientDrawable();
        scrollBg.setShape(GradientDrawable.RECTANGLE);
        scrollBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 26f));
        scrollBg.setColor(Color.WHITE);
        rootScrollView.setBackground(scrollBg);
        rootScrollView.setElevation((float) MainActivityUiHelpers.dp(act, 10f));
        rootScrollView.setFillViewport(true);

        LinearLayout contentLayout = new LinearLayout(act);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(
                MainActivityUiHelpers.dp(act, 24f),
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 24f),
                MainActivityUiHelpers.dp(act, 22f)
        );

        // 1. Thanh kéo tiêu chuẩn (Handle Bar)
        View handleBar = new View(act);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                MainActivityUiHelpers.dp(act, 36f),
                MainActivityUiHelpers.dp(act, 4f)
        );
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = MainActivityUiHelpers.dp(act, 12f);
        handleBar.setLayoutParams(handleLp);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setShape(GradientDrawable.RECTANGLE);
        handleBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 2f));
        handleBg.setColor(Color.parseColor("#CBD5E1"));
        handleBar.setBackground(handleBg);
        contentLayout.addView(handleBar);

        // 2. Tiêu đề chính
        TextView tvHeaderTitle = new TextView(act);
        tvHeaderTitle.setText("Cài đặt máy ảnh");
        tvHeaderTitle.setTextSize(17.5f);
        tvHeaderTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeaderTitle.setTextColor(Color.parseColor("#0F172A"));
        contentLayout.addView(tvHeaderTitle);

        TextView tvHeaderSub = new TextView(act);
        tvHeaderSub.setText("Tùy chỉnh thông số luồng video và khung ngắm robot");
        tvHeaderSub.setTextSize(11.5f);
        tvHeaderSub.setTextColor(Color.parseColor("#64748B"));
        tvHeaderSub.setPadding(0, MainActivityUiHelpers.dp(act, 2f), 0, MainActivityUiHelpers.dp(act, 14f));
        contentLayout.addView(tvHeaderSub);

        // 3. Nhóm 1: CHẾ ĐỘ TRUYỀN STREAM
        TextView tvSec1Title = new TextView(act);
        tvSec1Title.setText("CHẾ ĐỘ TRUYỀN STREAM");
        tvSec1Title.setTextSize(10.5f);
        tvSec1Title.setTypeface(Typeface.DEFAULT_BOLD);
        tvSec1Title.setTextColor(Color.parseColor("#64748B"));
        tvSec1Title.setLetterSpacing(0.04f);
        tvSec1Title.setPadding(MainActivityUiHelpers.dp(act, 2f), 0, 0, MainActivityUiHelpers.dp(act, 6f));
        contentLayout.addView(tvSec1Title);

        boolean isFast = act.targetResolution.getWidth() <= 960 && act.targetResolution.getWidth() != 1280 && act.targetResolution.getWidth() != 1920;
        boolean isBalanced = act.targetResolution.getWidth() == 1280;
        boolean isHd = act.targetResolution.getWidth() == 1920;

        View cardFast = createSettingsOptionCard(
                act,
                "Siêu tốc (Chống trễ)",
                "480p SD (16:9) - Tối ưu điều khiển xe robot, độ trễ cực thấp",
                isFast,
                () -> {
                    applyUltraFastLowLatencyMode(act);
                    dialog.dismiss();
                }
        );
        contentLayout.addView(cardFast);
        contentLayout.addView(MainActivityUiHelpers.createVerticalSpacer(act, MainActivityUiHelpers.dp(act, 7f)));

        View cardBalanced = createSettingsOptionCard(
                act,
                "Cân bằng",
                "720p HD - Cân bằng giữa độ nét và độ mượt mà",
                isBalanced,
                () -> {
                    applyBalancedMode(act);
                    dialog.dismiss();
                }
        );
        contentLayout.addView(cardBalanced);
        contentLayout.addView(MainActivityUiHelpers.createVerticalSpacer(act, MainActivityUiHelpers.dp(act, 7f)));

        View cardHd = createSettingsOptionCard(
                act,
                "Sắc nét",
                "1080p Full HD - Chi tiết cao, yêu cầu kết nối mạng mạnh",
                isHd,
                () -> {
                    applyHighQualityMode(act);
                    dialog.dismiss();
                }
        );
        contentLayout.addView(cardHd);
        contentLayout.addView(MainActivityUiHelpers.createVerticalSpacer(act, MainActivityUiHelpers.dp(act, 14f)));

        // 4. Nhóm 2: HƯỚNG XOAY CAMERA (MÀN HÌNH)
        TextView tvSecRotTitle = new TextView(act);
        tvSecRotTitle.setText("HƯỚNG XOAY CAMERA (MÀN HÌNH)");
        tvSecRotTitle.setTextSize(10.5f);
        tvSecRotTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvSecRotTitle.setTextColor(Color.parseColor("#64748B"));
        tvSecRotTitle.setLetterSpacing(0.04f);
        tvSecRotTitle.setPadding(MainActivityUiHelpers.dp(act, 2f), 0, 0, MainActivityUiHelpers.dp(act, 6f));
        contentLayout.addView(tvSecRotTitle);

        View cardRotAuto = createSettingsOptionCard(
                act,
                "Tự động (Theo cảm biến xoay)",
                "Xoay ngang/dọc tự động theo hướng cầm máy hoặc gắn trên xe",
                act.orientationOption == MainActivity.ScreenOrientationOption.AUTO,
                () -> {
                    setOrientationMode(act, MainActivity.ScreenOrientationOption.AUTO);
                    dialog.dismiss();
                }
        );
        contentLayout.addView(cardRotAuto);
        contentLayout.addView(MainActivityUiHelpers.createVerticalSpacer(act, MainActivityUiHelpers.dp(act, 7f)));

        View cardRotLand = createSettingsOptionCard(
                act,
                "Khóa xoay ngang 16:9 (Xe Robot)",
                "Luôn xuất video ngang Full hình 16:9 lên màn hình máy tính",
                act.orientationOption == MainActivity.ScreenOrientationOption.LANDSCAPE,
                () -> {
                    setOrientationMode(act, MainActivity.ScreenOrientationOption.LANDSCAPE);
                    dialog.dismiss();
                }
        );
        contentLayout.addView(cardRotLand);
        contentLayout.addView(MainActivityUiHelpers.createVerticalSpacer(act, MainActivityUiHelpers.dp(act, 7f)));

        View cardRotPort = createSettingsOptionCard(
                act,
                "Khóa xoay dọc 9:16",
                null,
                act.orientationOption == MainActivity.ScreenOrientationOption.PORTRAIT,
                () -> {
                    setOrientationMode(act, MainActivity.ScreenOrientationOption.PORTRAIT);
                    dialog.dismiss();
                }
        );
        contentLayout.addView(cardRotPort);

        rootScrollView.addView(contentLayout);
        dialog.setContentView(rootScrollView);

        DisplayMetrics dm = act.getResources().getDisplayMetrics();
        int modalW = Math.max(
                MainActivityUiHelpers.dp(act, 520f),
                Math.min(dm.widthPixels - MainActivityUiHelpers.dp(act, 32f), (int) (dm.widthPixels * 0.64f))
        );
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(modalW, LinearLayout.LayoutParams.WRAP_CONTENT);
        }

        dialog.show();
    }

    public static View createSettingsOptionCard(
            MainActivity act,
            String title,
            String subtitle,
            boolean isSelected,
            Runnable onClick
    ) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 14f));
        if (isSelected) {
            bg.setColor(Color.parseColor("#EEF4FF"));
            bg.setStroke(MainActivityUiHelpers.dp(act, 1.4f), Color.parseColor("#3B82F6"));
        } else {
            bg.setColor(Color.WHITE);
            bg.setStroke(MainActivityUiHelpers.dp(act, 1.2f), Color.parseColor("#E2E8F0"));
        }
        card.setBackground(bg);
        card.setPadding(
                MainActivityUiHelpers.dp(act, 16f),
                MainActivityUiHelpers.dp(act, 10f),
                MainActivityUiHelpers.dp(act, 16f),
                MainActivityUiHelpers.dp(act, 10f)
        );

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(act);
        tvTitle.setText(title);
        tvTitle.setTextSize(12.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(isSelected ? Color.parseColor("#1D5CE5") : Color.parseColor("#0F172A"));
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvTitle);

        if (isSelected) {
            TextView tvBadge = new TextView(act);
            tvBadge.setText("Đang chọn");
            tvBadge.setTextSize(9.5f);
            tvBadge.setTypeface(Typeface.DEFAULT_BOLD);
            tvBadge.setTextColor(Color.WHITE);

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            badgeBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 12f));
            badgeBg.setColor(Color.parseColor("#1D5CE5"));
            tvBadge.setBackground(badgeBg);
            tvBadge.setPadding(
                    MainActivityUiHelpers.dp(act, 10f),
                    MainActivityUiHelpers.dp(act, 3f),
                    MainActivityUiHelpers.dp(act, 10f),
                    MainActivityUiHelpers.dp(act, 3f)
            );
            row.addView(tvBadge);
        }
        card.addView(row);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = new TextView(act);
            tvSub.setText(subtitle);
            tvSub.setTextSize(10.5f);
            tvSub.setTextColor(isSelected ? Color.parseColor("#475569") : Color.parseColor("#64748B"));
            tvSub.setPadding(0, MainActivityUiHelpers.dp(act, 2f), 0, 0);
            card.addView(tvSub);
        }

        MainActivityUiHelpers.applySpringTouchEffect(card);
        card.setOnClickListener(v -> onClick.run());
        return card;
    }

    public static void applyUltraFastLowLatencyMode(MainActivity act) {
        act.targetResolution = new Size(854, 480);
        if (act.mjpegServer != null) {
            act.mjpegServer.targetOutputResolution = act.targetResolution;
            act.mjpegServer.jpegQuality = 55;
        }
        if (act.tvResolutionBadge != null) {
            act.tvResolutionBadge.setText("480p");
        }
        act.startCamera();
        Toast.makeText(act, "Đã kích hoạt chế độ Siêu tốc (480p SD 16:9 - Giảm tối đa độ trễ)", Toast.LENGTH_LONG).show();
    }

    public static void applyBalancedMode(MainActivity act) {
        act.targetResolution = new Size(1280, 720);
        if (act.mjpegServer != null) {
            act.mjpegServer.targetOutputResolution = act.targetResolution;
            act.mjpegServer.jpegQuality = 65;
        }
        if (act.tvResolutionBadge != null) {
            act.tvResolutionBadge.setText("720p");
        }
        act.startCamera();
        Toast.makeText(act, "Đã kích hoạt chế độ Cân bằng (720p HD 16:9)", Toast.LENGTH_SHORT).show();
    }

    public static void applyHighQualityMode(MainActivity act) {
        act.targetResolution = new Size(1920, 1080);
        if (act.mjpegServer != null) {
            act.mjpegServer.targetOutputResolution = act.targetResolution;
            act.mjpegServer.jpegQuality = 85;
        }
        if (act.tvResolutionBadge != null) {
            act.tvResolutionBadge.setText("1080p");
        }
        act.startCamera();
        Toast.makeText(act, "Đã kích hoạt chế độ Sắc nét (1080p Full HD 16:9)", Toast.LENGTH_SHORT).show();
    }

    public static void setOrientationMode(MainActivity act, MainActivity.ScreenOrientationOption option) {
        act.orientationOption = option;
        switch (option) {
            case AUTO:
                act.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
                if (act.orientationEventListener != null) act.orientationEventListener.enable();
                Toast.makeText(act, "Chế độ xoay: Tự động theo cảm biến", Toast.LENGTH_SHORT).show();
                break;
            case LANDSCAPE:
                act.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                act.currentTargetRotation = Surface.ROTATION_90;
                Toast.makeText(act, "Chế độ xoay: Khóa ngang (Tối ưu cho xe Robot)", Toast.LENGTH_SHORT).show();
                break;
            case PORTRAIT:
                act.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                act.currentTargetRotation = Surface.ROTATION_0;
                Toast.makeText(act, "Chế độ xoay: Khóa dọc", Toast.LENGTH_SHORT).show();
                break;
        }
        act.startCamera();
    }
}
