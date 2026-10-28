package com.robotcamera.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Vai trò THIẾT BỊ STREAMING: máy chủ MJPEG 8080, IP WiFi, chia sẻ link stream.
 */
public class MainActivityStreamer {

    private static final String TAG = "MainActivityStreamer";

    public static void startStreamerRole(MainActivity act) {
        if (act.remoteMjpegView != null) {
            act.remoteMjpegView.stopStream();
            act.remoteMjpegView.setVisibility(View.GONE);
        }
        if (act.controlDashboardView != null) {
            act.controlDashboardView.setVisibility(View.GONE);
        }
        if (act.controllerLocalCamBar != null) {
            act.controllerLocalCamBar.setVisibility(View.GONE);
        }
        if (act.controllerTopLeftBar != null) {
            act.controllerTopLeftBar.setVisibility(View.GONE);
        }

        // Camera toàn màn hình
        if (act.previewView != null) {
            act.previewView.setLayoutParams(new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
            ));
            act.previewView.setVisibility(View.VISIBLE);
        }
        if (act.viewfinderOverlay != null) act.viewfinderOverlay.setVisibility(View.VISIBLE);
        if (act.overlayContainer != null) act.overlayContainer.setVisibility(View.VISIBLE);
        if (act.fpvHudOverlay != null) act.fpvHudOverlay.setVisibility(View.GONE);

        if (act.tvModeControl != null) {
            act.tvModeControl.setVisibility(View.GONE);
        }
        act.switchMode(MainActivity.CameraMode.PHOTO);

        act.requestPermissions();
        startStreaming(act);
        updateIpDisplay(act);
        GimbalBleManager.autoReconnectIfSaved(act);
        Toast.makeText(act, "Đã kích hoạt Thiết Bị Streaming!!!", Toast.LENGTH_SHORT).show();
    }

    public static void updateIpDisplay(MainActivity act) {
        String ip = getWifiIpAddress(act);
        act.runOnUiThread(() -> {
            if (act.tvIpDisplayBadge != null) {
                if (ip != null) {
                    act.tvIpDisplayBadge.setText("IP: " + ip);
                    act.tvIpDisplayBadge.setTextColor(Color.parseColor("#0F172A"));
                } else {
                    act.tvIpDisplayBadge.setText("Chưa kết nối WiFi!");
                    act.tvIpDisplayBadge.setTextColor(Color.parseColor("#DC2626"));
                }
            }
        });
    }

    public static void showStreamInfoDialog(MainActivity act) {
        String ip = getWifiIpAddress(act);
        if (ip == null) ip = "Chưa kết nối WiFi";
        String url = "http://" + ip + ":" + MainActivity.STREAM_PORT + "/video";
        int fps = (act.mjpegServer != null) ? act.mjpegServer.currentFps : 0;
        int clients = (act.mjpegServer != null) ? act.mjpegServer.getClientCount() : 0;

        String msg = "Liên kết truyền video:\n" + url + "\n\n" +
                "Tốc độ khung hình: " + fps + " FPS\n" +
                "Số máy tính đang xem: " + clients + "\n" +
                "Trạng thái máy chủ: " + (act.isStreaming ? "Đang hoạt động" : "Tạm dừng");

        new AlertDialog.Builder(act)
                .setTitle("Thông tin truyền video")
                .setMessage(msg)
                .setPositiveButton("Sao chép liên kết", (d, w) -> copyStreamUrlToClipboard(act))
                .setNegativeButton("Đóng", null)
                .show();
    }

    public static void copyStreamUrlToClipboard(MainActivity act) {
        String ip = getWifiIpAddress(act);
        if (ip != null) {
            String url = "http://" + ip + ":" + MainActivity.STREAM_PORT + "/video.mjpeg";
            ClipboardManager clipboard = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("Robot Camera Stream URL", url);
                clipboard.setPrimaryClip(clip);
            }
            Toast.makeText(act, "Đã sao chép liên kết stream: " + url, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(act, "Thiết bị chưa kết nối WiFi", Toast.LENGTH_SHORT).show();
        }
    }

    public static void startStreaming(MainActivity act) {
        try {
            if (act.mjpegServer != null && act.mjpegServer.isAlive()) {
                act.isStreaming = true;
                if (act.tvMotionLiveText != null) {
                    act.tvMotionLiveText.setText("• LIVE");
                    act.tvMotionLiveText.setTextColor(Color.parseColor("#15803D"));
                }
                return;
            }

            try {
                if (act.mjpegServer != null) act.mjpegServer.stop();
            } catch (Exception ignored) {}
            act.mjpegServer = null;

            boolean started = false;
            for (int attempt = 1; attempt <= 4; attempt++) {
                try {
                    act.mjpegServer = new MjpegServer(MainActivity.STREAM_PORT);
                    act.mjpegServer.commandListener = act;
                    act.mjpegServer.targetOutputResolution = act.targetResolution;
                    act.mjpegServer.setOnFrameBitmap(bmp -> {
                        if (act.visualDistanceDetector != null && !bmp.isRecycled()) {
                            act.visualDistanceDetector.processBitmap(bmp);
                        }
                    });
                    act.mjpegServer.start();
                    started = true;
                    break;
                } catch (Exception e) {
                    Log.w(TAG, "Lỗi bind server cổng " + MainActivity.STREAM_PORT + " lần " + attempt + ": " + e.getMessage() + ", thử lại sau 200ms...");
                    try {
                        Thread.sleep(200);
                    } catch (Exception ignored) {}
                }
            }

            if (started) {
                act.isStreaming = true;
                try {
                    act.startService(new Intent(act, CameraStreamService.class));
                } catch (Throwable e) {
                    Log.w(TAG, "Không thể khởi động CameraStreamService: " + e.getMessage());
                }
                if (act.tvMotionLiveText != null) {
                    act.tvMotionLiveText.setText("• LIVE");
                    act.tvMotionLiveText.setTextColor(Color.parseColor("#15803D"));
                }
                Log.i(TAG, "MJPEG Server đã mở thành công trên cổng " + MainActivity.STREAM_PORT);
            } else {
                Log.e(TAG, "Không thể mở MJPEG Server trên cổng " + MainActivity.STREAM_PORT + " sau 4 lần thử");
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi mở server: " + e.getMessage());
        }
    }

    public static void stopStreaming(MainActivity act) {
        try {
            if (act.mjpegServer != null) {
                act.mjpegServer.stop();
                act.mjpegServer = null;
            }
            act.isStreaming = false;
            try {
                act.stopService(new Intent(act, CameraStreamService.class));
            } catch (Throwable e) {
                Log.w(TAG, "Không thể dừng CameraStreamService: " + e.getMessage());
            }

            if (act.tvMotionLiveText != null) {
                act.tvMotionLiveText.setTextColor(Color.parseColor("#F5C518"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi tắt server: " + e.getMessage());
        }
    }

    public static String getWifiIpAddress(Context ctx) {
        try {
            WifiManager wifiManager = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipInt = wifiInfo != null ? wifiInfo.getIpAddress() : 0;
                if (ipInt != 0) {
                    String ip = String.format(
                            Locale.US,
                            "%d.%d.%d.%d",
                            ipInt & 0xFF,
                            (ipInt >> 8) & 0xFF,
                            (ipInt >> 16) & 0xFF,
                            (ipInt >> 24) & 0xFF
                    );
                    if (!"0.0.0.0".equals(ip)) return ip;
                }
            }

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void showGimbalBleDialog(MainActivity act) {
        String[] blePerms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blePerms = new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        } else {
            blePerms = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        }

        List<String> missingPerms = new ArrayList<>();
        for (String perm : blePerms) {
            if (ContextCompat.checkSelfPermission(act, perm) != PackageManager.PERMISSION_GRANTED) {
                missingPerms.add(perm);
            }
        }
        if (!missingPerms.isEmpty()) {
            ActivityCompat.requestPermissions(act, missingPerms.toArray(new String[0]), 99);
            Toast.makeText(act, "Vui lòng cấp quyền Bluetooth rồi bấm lại nút Gimbal", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(act);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0.55f);
        }

        NestedScrollView rootScroll = new NestedScrollView(act);
        GradientDrawable scrollBg = new GradientDrawable();
        scrollBg.setShape(GradientDrawable.RECTANGLE);
        scrollBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 26f));
        scrollBg.setColor(Color.WHITE);
        rootScroll.setBackground(scrollBg);
        rootScroll.setElevation((float) MainActivityUiHelpers.dp(act, 10f));
        rootScroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                MainActivityUiHelpers.dp(act, 24f),
                MainActivityUiHelpers.dp(act, 18f),
                MainActivityUiHelpers.dp(act, 24f),
                MainActivityUiHelpers.dp(act, 20f)
        );

        // 1. Header
        LinearLayout headerRow = new LinearLayout(act);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(act);
        tvTitle.setText("KẾT NỐI GIMBAL DJI RS 3 MINI (BLE)");
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(Color.parseColor("#1D5CE5"));
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(tvTitle);

        TextView btnClose = new TextView(act);
        btnClose.setText("Đóng");
        btnClose.setTextSize(11f);
        btnClose.setTypeface(Typeface.DEFAULT_BOLD);
        btnClose.setTextColor(Color.parseColor("#0F172A"));
        btnClose.setGravity(Gravity.CENTER);

        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.RECTANGLE);
        closeBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 18f));
        closeBg.setColor(Color.WHITE);
        closeBg.setStroke(MainActivityUiHelpers.dp(act, 1.2f), Color.parseColor("#CBD5E1"));
        btnClose.setBackground(closeBg);
        btnClose.setPadding(
                MainActivityUiHelpers.dp(act, 16f),
                MainActivityUiHelpers.dp(act, 6f),
                MainActivityUiHelpers.dp(act, 16f),
                MainActivityUiHelpers.dp(act, 6f)
        );
        btnClose.setOnClickListener(v -> dialog.dismiss());
        headerRow.addView(btnClose);
        content.addView(headerRow);

        // 2. Trạng thái hiện tại + Nút Quét / Ngắt kết nối
        LinearLayout statusRow = new LinearLayout(act);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, MainActivityUiHelpers.dp(act, 10f), 0, MainActivityUiHelpers.dp(act, 6f));

        TextView tvStatus = new TextView(act);
        tvStatus.setText("Trạng thái: " + GimbalBleManager.statusMessage);
        tvStatus.setTextSize(12f);
        tvStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatus.setTextColor(GimbalBleManager.isConnected ? Color.parseColor("#15803D") : Color.parseColor("#0F172A"));
        tvStatus.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        statusRow.addView(tvStatus);

        TextView btnScan = new TextView(act);
        btnScan.setText("Quét BLE");
        btnScan.setTextSize(11f);
        btnScan.setTypeface(Typeface.DEFAULT_BOLD);
        btnScan.setTextColor(Color.WHITE);
        btnScan.setGravity(Gravity.CENTER);

        GradientDrawable scanBg = new GradientDrawable();
        scanBg.setShape(GradientDrawable.RECTANGLE);
        scanBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 18f));
        scanBg.setColor(Color.parseColor("#1D5CE5"));
        btnScan.setBackground(scanBg);
        btnScan.setPadding(
                MainActivityUiHelpers.dp(act, 16f),
                MainActivityUiHelpers.dp(act, 7f),
                MainActivityUiHelpers.dp(act, 16f),
                MainActivityUiHelpers.dp(act, 7f)
        );
        btnScan.setOnClickListener(v -> GimbalBleManager.startScan(act, false));
        statusRow.addView(btnScan);

        statusRow.addView(MainActivityUiHelpers.createHorizontalSpacer(act, MainActivityUiHelpers.dp(act, 8f)));

        TextView btnDisconnect = new TextView(act);
        btnDisconnect.setText("Ngắt BLE");
        btnDisconnect.setTextSize(11f);
        btnDisconnect.setTypeface(Typeface.DEFAULT_BOLD);
        btnDisconnect.setTextColor(Color.WHITE);
        btnDisconnect.setGravity(Gravity.CENTER);

        GradientDrawable disBg = new GradientDrawable();
        disBg.setShape(GradientDrawable.RECTANGLE);
        disBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 18f));
        disBg.setColor(Color.parseColor("#DC2626"));
        btnDisconnect.setBackground(disBg);
        btnDisconnect.setPadding(
                MainActivityUiHelpers.dp(act, 16f),
                MainActivityUiHelpers.dp(act, 7f),
                MainActivityUiHelpers.dp(act, 16f),
                MainActivityUiHelpers.dp(act, 7f)
        );
        btnDisconnect.setOnClickListener(v -> GimbalBleManager.disconnect());
        statusRow.addView(btnDisconnect);
        content.addView(statusRow);

        // 3. Danh sách thiết bị BLE tìm thấy
        TextView tvListHint = new TextView(act);
        tvListHint.setText("Chạm vào tên Gimbal RS 3 Mini bên dưới để kết nối:");
        tvListHint.setTextSize(10.5f);
        tvListHint.setTextColor(Color.parseColor("#64748B"));
        tvListHint.setPadding(0, 0, 0, MainActivityUiHelpers.dp(act, 6f));
        content.addView(tvListHint);

        LinearLayout deviceListContainer = new LinearLayout(act);
        deviceListContainer.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable devListBg = new GradientDrawable();
        devListBg.setShape(GradientDrawable.RECTANGLE);
        devListBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 16f));
        devListBg.setColor(Color.parseColor("#F4F7FB"));
        devListBg.setStroke(MainActivityUiHelpers.dp(act, 1f), Color.parseColor("#E2E8F0"));
        deviceListContainer.setBackground(devListBg);
        deviceListContainer.setPadding(
                MainActivityUiHelpers.dp(act, 10f),
                MainActivityUiHelpers.dp(act, 8f),
                MainActivityUiHelpers.dp(act, 10f),
                MainActivityUiHelpers.dp(act, 4f)
        );
        content.addView(deviceListContainer);

        // 4. Cụm nút Điều khiển thử Gimbal trực tiếp
        TextView testHeader = new TextView(act);
        testHeader.setText("Điều khiển thử Gimbal trực tiếp:");
        testHeader.setTextSize(11.5f);
        testHeader.setTypeface(Typeface.DEFAULT_BOLD);
        testHeader.setTextColor(Color.parseColor("#0F172A"));
        testHeader.setPadding(0, MainActivityUiHelpers.dp(act, 12f), 0, MainActivityUiHelpers.dp(act, 8f));
        content.addView(testHeader);

        LinearLayout controlsBar = new LinearLayout(act);
        controlsBar.setOrientation(LinearLayout.HORIZONTAL);
        controlsBar.setGravity(Gravity.CENTER_VERTICAL);
        controlsBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout btnLeft = makeHoldPillBtn(act, "‹", "Trái", pressed -> GimbalBleManager.setPanDirection(pressed ? -1 : 0));
        LinearLayout btnUp = makeHoldPillBtn(act, "⌃", "Lên", pressed -> GimbalBleManager.setTiltDirection(pressed ? 1 : 0));
        LinearLayout btnDown = makeHoldPillBtn(act, "⌄", "Xuống", pressed -> GimbalBleManager.setTiltDirection(pressed ? -1 : 0));
        LinearLayout btnRight = makeHoldPillBtn(act, "›", "Phải", pressed -> GimbalBleManager.setPanDirection(pressed ? 1 : 0));

        controlsBar.addView(btnLeft);
        controlsBar.addView(btnUp);
        controlsBar.addView(btnDown);
        controlsBar.addView(btnRight);

        controlsBar.addView(MainActivityUiHelpers.createFlexSpacer(act));

        TextView btnMode = new TextView(act);
        btnMode.setTextSize(11f);
        btnMode.setTypeface(Typeface.DEFAULT_BOLD);
        btnMode.setTextColor(Color.WHITE);
        btnMode.setGravity(Gravity.CENTER);

        GradientDrawable modePillBg = new GradientDrawable();
        modePillBg.setShape(GradientDrawable.RECTANGLE);
        modePillBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 18f));
        modePillBg.setColor(Color.parseColor("#1D5CE5"));
        btnMode.setBackground(modePillBg);
        btnMode.setPadding(
                MainActivityUiHelpers.dp(act, 16f),
                MainActivityUiHelpers.dp(act, 7f),
                MainActivityUiHelpers.dp(act, 16f),
                MainActivityUiHelpers.dp(act, 7f)
        );

        TextView btnInvert = new TextView(act);
        btnInvert.setTextSize(9.5f);
        btnInvert.setTypeface(Typeface.DEFAULT_BOLD);
        btnInvert.setGravity(Gravity.CENTER);
        btnInvert.setPadding(
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 4f),
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 4f)
        );
        LinearLayout.LayoutParams invLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        invLp.leftMargin = MainActivityUiHelpers.dp(act, 6f);
        btnInvert.setLayoutParams(invLp);

        Runnable updateInvertBtn = () -> {
            boolean inv = GimbalBleManager.invertPan;
            btnInvert.setText(inv ? "Đảo chiều:\nBẬT" : "Đảo chiều:\nTẮT");
            btnInvert.setTextColor(inv ? Color.parseColor("#DC2626") : Color.parseColor("#0F172A"));

            GradientDrawable invBg = new GradientDrawable();
            invBg.setShape(GradientDrawable.RECTANGLE);
            invBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 18f));
            invBg.setColor(inv ? Color.parseColor("#FEE2E2") : Color.WHITE);
            invBg.setStroke(MainActivityUiHelpers.dp(act, 1.2f), inv ? Color.parseColor("#FCA5A5") : Color.parseColor("#CBD5E1"));
            btnInvert.setBackground(invBg);
        };

        btnInvert.setOnClickListener(v -> {
            GimbalBleManager.invertPan = !GimbalBleManager.invertPan;
            GimbalBleManager.invertTilt = !GimbalBleManager.invertTilt;
            updateInvertBtn.run();
            Toast.makeText(act, GimbalBleManager.invertPan ? "Đã bật đảo chiều điều khiển" : "Đã tắt đảo chiều", Toast.LENGTH_SHORT).show();
        });
        updateInvertBtn.run();

        GimbalBleManager.FollowModeListener updateModeView = mode -> {
            btnMode.setText("Mode: " + mode.getLabel());
            boolean isFpv = (mode == GimbalBleManager.FollowMode.FPV);
            btnLeft.setVisibility(isFpv ? View.GONE : View.VISIBLE);
            btnRight.setVisibility(isFpv ? View.GONE : View.VISIBLE);
        };

        btnMode.setOnClickListener(v -> {
            GimbalBleManager.FollowMode next = GimbalBleManager.currentFollowMode.next();
            GimbalBleManager.setFollowMode(next);
            updateModeView.onFollowModeChanged(next);
        });
        updateModeView.onFollowModeChanged(GimbalBleManager.currentFollowMode);

        GimbalBleManager.followModeListener = m -> act.runOnUiThread(() -> updateModeView.onFollowModeChanged(m));

        controlsBar.addView(btnMode);
        controlsBar.addView(btnInvert);
        content.addView(controlsBar);

        GimbalBleManager.StateListener stateListener = (connected, msg) -> {
            tvStatus.setText("Trạng thái: " + msg);
            tvStatus.setTextColor(connected ? Color.parseColor("#15803D") : Color.parseColor("#0F172A"));
        };

        GimbalBleManager.ScanListener scanListener = devices -> {
            deviceListContainer.removeAllViews();
            if (devices.isEmpty()) {
                TextView tvEmpty = new TextView(act);
                tvEmpty.setText("Đang tìm thiết bị Bluetooth BLE quanh đây...");
                tvEmpty.setTextSize(11.5f);
                tvEmpty.setTextColor(Color.parseColor("#94A3B8"));
                tvEmpty.setPadding(
                        MainActivityUiHelpers.dp(act, 4f),
                        MainActivityUiHelpers.dp(act, 6f),
                        MainActivityUiHelpers.dp(act, 4f),
                        MainActivityUiHelpers.dp(act, 6f)
                );
                deviceListContainer.addView(tvEmpty);
            } else {
                int limit = Math.min(6, devices.size());
                for (int i = 0; i < limit; i++) {
                    GimbalBleManager.ScannedBleDevice item = devices.get(i);
                    LinearLayout row = new LinearLayout(act);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);

                    GradientDrawable rBg = new GradientDrawable();
                    rBg.setShape(GradientDrawable.RECTANGLE);
                    rBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 12f));
                    rBg.setColor(item.isLikelyDjiGimbal() ? Color.parseColor("#EFF6FF") : Color.WHITE);
                    rBg.setStroke(
                            MainActivityUiHelpers.dp(act, 1f),
                            item.isLikelyDjiGimbal() ? Color.parseColor("#60A5FA") : Color.parseColor("#E2E8F0")
                    );
                    row.setBackground(rBg);
                    row.setPadding(
                            MainActivityUiHelpers.dp(act, 14f),
                            MainActivityUiHelpers.dp(act, 6f),
                            MainActivityUiHelpers.dp(act, 10f),
                            MainActivityUiHelpers.dp(act, 6f)
                    );

                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    rowLp.bottomMargin = MainActivityUiHelpers.dp(act, 5f);
                    row.setLayoutParams(rowLp);
                    row.setOnClickListener(v -> GimbalBleManager.connectToDevice(act, item.getDevice()));

                    LinearLayout nameCol = new LinearLayout(act);
                    nameCol.setOrientation(LinearLayout.HORIZONTAL);
                    nameCol.setGravity(Gravity.CENTER_VERTICAL);
                    nameCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                    TextView tvName = new TextView(act);
                    tvName.setText(item.isLikelyDjiGimbal() ? "★ " + item.getName() + " " : item.getName() + " ");
                    tvName.setTextSize(11.5f);
                    tvName.setTypeface(Typeface.DEFAULT_BOLD);
                    tvName.setTextColor(item.isLikelyDjiGimbal() ? Color.parseColor("#1D5CE5") : Color.parseColor("#0F172A"));
                    nameCol.addView(tvName);

                    TextView tvAddr = new TextView(act);
                    tvAddr.setText("(" + item.getAddress() + ")");
                    tvAddr.setTextSize(10f);
                    tvAddr.setTypeface(Typeface.MONOSPACE);
                    tvAddr.setTextColor(Color.parseColor("#64748B"));
                    nameCol.addView(tvAddr);

                    row.addView(nameCol);

                    TextView tvConnect = new TextView(act);
                    tvConnect.setText("Kết nối");
                    tvConnect.setTextSize(10.5f);
                    tvConnect.setTypeface(Typeface.DEFAULT_BOLD);
                    tvConnect.setTextColor(Color.parseColor("#1D5CE5"));
                    tvConnect.setGravity(Gravity.CENTER);

                    GradientDrawable connPillBg = new GradientDrawable();
                    connPillBg.setShape(GradientDrawable.RECTANGLE);
                    connPillBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 14f));
                    connPillBg.setColor(Color.parseColor("#E8F0FE"));
                    tvConnect.setBackground(connPillBg);
                    tvConnect.setPadding(
                            MainActivityUiHelpers.dp(act, 12f),
                            MainActivityUiHelpers.dp(act, 5f),
                            MainActivityUiHelpers.dp(act, 12f),
                            MainActivityUiHelpers.dp(act, 5f)
                    );
                    row.addView(tvConnect);
                    deviceListContainer.addView(row);
                }
            }
        };

        GimbalBleManager.addStateListener(stateListener);
        GimbalBleManager.addScanListener(scanListener);

        dialog.setOnDismissListener(d -> {
            GimbalBleManager.removeStateListener(stateListener);
            GimbalBleManager.removeScanListener(scanListener);
            GimbalBleManager.stopScan(act);
        });

        rootScroll.addView(content);
        dialog.setContentView(rootScroll);

        DisplayMetrics dm = act.getResources().getDisplayMetrics();
        int modalW = Math.max(
                MainActivityUiHelpers.dp(act, 520f),
                Math.min(dm.widthPixels - MainActivityUiHelpers.dp(act, 32f), (int) (dm.widthPixels * 0.64f))
        );
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(modalW, LinearLayout.LayoutParams.WRAP_CONTENT);
        }

        dialog.show();

        if (!GimbalBleManager.isConnected) {
            GimbalBleManager.startScan(act, false);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private static LinearLayout makeHoldPillBtn(Context ctx, String chevron, String label, HoldListener onHold) {
        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.RECTANGLE);
        normalBg.setCornerRadius((float) MainActivityUiHelpers.dp(ctx, 18f));
        normalBg.setColor(Color.WHITE);
        normalBg.setStroke(MainActivityUiHelpers.dp(ctx, 1.2f), Color.parseColor("#CBD5E1"));
        pill.setBackground(normalBg);
        pill.setPadding(
                MainActivityUiHelpers.dp(ctx, 12f),
                MainActivityUiHelpers.dp(ctx, 6f),
                MainActivityUiHelpers.dp(ctx, 12f),
                MainActivityUiHelpers.dp(ctx, 6f)
        );

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.rightMargin = MainActivityUiHelpers.dp(ctx, 6f);
        pill.setLayoutParams(lp);

        TextView tvChev = new TextView(ctx);
        tvChev.setText(chevron);
        tvChev.setTextSize(11f);
        tvChev.setTypeface(Typeface.DEFAULT_BOLD);
        tvChev.setTextColor(Color.parseColor("#1D5CE5"));
        tvChev.setPadding(0, 0, MainActivityUiHelpers.dp(ctx, 5f), 0);
        pill.addView(tvChev);

        TextView tvLbl = new TextView(ctx);
        tvLbl.setText(label);
        tvLbl.setTextSize(10.5f);
        tvLbl.setTypeface(Typeface.DEFAULT_BOLD);
        tvLbl.setTextColor(Color.parseColor("#0F172A"));
        pill.addView(tvLbl);

        GradientDrawable pressedBg = new GradientDrawable();
        pressedBg.setShape(GradientDrawable.RECTANGLE);
        pressedBg.setCornerRadius((float) MainActivityUiHelpers.dp(ctx, 18f));
        pressedBg.setColor(Color.parseColor("#1D5CE5"));

        pill.setOnTouchListener((v, ev) -> {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    pill.setBackground(pressedBg);
                    tvChev.setTextColor(Color.WHITE);
                    tvLbl.setTextColor(Color.WHITE);
                    onHold.onHold(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    pill.setBackground(normalBg);
                    tvChev.setTextColor(Color.parseColor("#1D5CE5"));
                    tvLbl.setTextColor(Color.parseColor("#0F172A"));
                    onHold.onHold(false);
                    return true;
                default:
                    return false;
            }
        });
        return pill;
    }

    public interface HoldListener {
        void onHold(boolean isHeld);
    }
}
