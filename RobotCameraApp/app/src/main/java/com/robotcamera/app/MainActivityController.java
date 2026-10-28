package com.robotcamera.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Vai trò THIẾT BỊ ĐIỀU KHIỂN:
 * - Bố cục Full-Screen FPV Cockpit (Video toàn màn hình + lớp phủ Joystick & các thanh Pill trắng nổi).
 */
public class MainActivityController {

    private static final String TAG = "MainActivityController";

    public static void startControllerRole(MainActivity act) {
        act.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        if (act.viewfinderOverlay != null) act.viewfinderOverlay.setVisibility(View.GONE);
        if (act.overlayContainer != null) act.overlayContainer.setVisibility(View.GONE);
        if (act.fpvHudOverlay != null) act.fpvHudOverlay.setVisibility(View.GONE);

        if (act.controlDashboardView != null) act.controlDashboardView.setVisibility(View.VISIBLE);
        if (act.controllerTopLeftBar != null) {
            act.controllerTopLeftBar.setVisibility(View.VISIBLE);
            act.controllerTopLeftBar.bringToFront();
        }

        // Tự động kết nối UDP tới Robot nếu chưa kết nối
        SharedPreferences prefs = act.getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE);
        String savedUdp = prefs.getString("pref_udp_ip_port", "192.168.2.93:5001");
        if (savedUdp == null) savedUdp = "192.168.2.93:5001";

        if (!act.udpManager.isConnected) {
            String[] parts = savedUdp.split(":");
            String ip = parts[0].trim();
            int port = 5001;
            if (parts.length > 1) {
                try {
                    port = Integer.parseInt(parts[1].trim());
                } catch (Exception ignored) {}
            }
            act.udpManager.connect(ip, port);
        } else {
            updateUdpUiState(act, true, "Đã kết nối UDP tới " + savedUdp);
        }
        if (act.etUdpAddress != null && act.etUdpAddress.getText().toString().isEmpty()) {
            act.etUdpAddress.setText(savedUdp);
        }

        // Mặc định vào chế độ nhận luồng FPV từ xa (REMOTE)
        String savedSource = prefs.getString("pref_controller_cam_source", "REMOTE");
        if ("LOCAL".equals(savedSource)) {
            switchToLocalCamMode(act);
        } else {
            switchToRemoteFpvMode(act, null);
        }
    }

    public static void switchToLocalCamMode(MainActivity act) {
        act.controllerCamSource = "LOCAL";
        act.getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE)
                .edit().putString("pref_controller_cam_source", "LOCAL").apply();

        if (act.remoteMjpegView != null) {
            act.remoteMjpegView.stopStream();
            act.remoteMjpegView.setVisibility(View.GONE);
        }

        if (act.previewView != null) act.previewView.setVisibility(View.VISIBLE);
        if (act.controllerLocalCamBar != null) {
            act.controllerLocalCamBar.setVisibility(View.VISIBLE);
            act.controllerLocalCamBar.bringToFront();
        }
        if (act.controllerTopLeftBar != null) {
            act.controllerTopLeftBar.setVisibility(View.VISIBLE);
            act.controllerTopLeftBar.bringToFront();
        }

        updateControllerLayoutDimensions(act);

        if (act.allPermissionsGranted()) {
            act.startCamera();
        } else {
            act.requestPermissions();
        }
        if (!act.isStreaming || act.mjpegServer == null || !act.mjpegServer.isAlive()) {
            act.startStreaming();
        }
        updateControllerLocalIpDisplay(act);
        Toast.makeText(act, "Đang dùng Camera máy & phát stream cổng 8080", Toast.LENGTH_SHORT).show();
    }

    public static void switchToRemoteFpvMode(MainActivity act, String targetIp) {
        act.controllerCamSource = "REMOTE";
        act.getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE)
                .edit().putString("pref_controller_cam_source", "REMOTE").apply();

        try {
            if (act.cameraProvider != null) act.cameraProvider.unbindAll();
            act.camera = null;
        } catch (Exception ignored) {}

        if (act.previewView != null) act.previewView.setVisibility(View.GONE);
        if (act.controllerLocalCamBar != null) {
            act.controllerLocalCamBar.setVisibility(View.GONE);
        }
        if (act.controllerTopLeftBar != null) {
            act.controllerTopLeftBar.setVisibility(View.VISIBLE);
            act.controllerTopLeftBar.bringToFront();
        }

        if (!act.isStreaming || act.mjpegServer == null || !act.mjpegServer.isAlive()) {
            act.startStreaming();
        }

        if (act.remoteMjpegView != null) {
            act.remoteMjpegView.setVisibility(View.VISIBLE);
            updateControllerLayoutDimensions(act);
            act.remoteMjpegView.startStream(targetIp);
        }
    }

    public static LinearLayout createControllerLocalCamBar(MainActivity act) {
        LinearLayout bar = new LinearLayout(act);
        bar.setId(View.generateViewId());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.addRule(RelativeLayout.ALIGN_PARENT_END);
        lp.setMargins(0, MainActivityUiHelpers.dp(act, 10f), MainActivityUiHelpers.dp(act, 14f), 0);
        bar.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 26f));
        bg.setColor(Color.parseColor("#F5FFFFFF"));
        bg.setStroke(MainActivityUiHelpers.dp(act, 1f), Color.parseColor("#E2E8F0"));
        bar.setBackground(bg);
        bar.setElevation((float) MainActivityUiHelpers.dp(act, 6f));
        bar.setPadding(
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 5f),
                MainActivityUiHelpers.dp(act, 10f),
                MainActivityUiHelpers.dp(act, 5f)
        );
        bar.setVisibility(View.GONE);

        act.tvControllerLocalIp = new TextView(act);
        act.tvControllerLocalIp.setText("Camera IP: Đang lấy...");
        act.tvControllerLocalIp.setTextSize(10.5f);
        act.tvControllerLocalIp.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        act.tvControllerLocalIp.setTextColor(Color.parseColor("#0F172A"));
        act.tvControllerLocalIp.setPadding(0, 0, MainActivityUiHelpers.dp(act, 8f), 0);
        bar.addView(act.tvControllerLocalIp);

        TextView btnSwitchToFpv = new TextView(act);
        btnSwitchToFpv.setText("FPV từ xa");
        btnSwitchToFpv.setTextSize(10.5f);
        btnSwitchToFpv.setTypeface(Typeface.DEFAULT_BOLD);
        btnSwitchToFpv.setTextColor(Color.WHITE);
        btnSwitchToFpv.setGravity(Gravity.CENTER);

        GradientDrawable fpvBg = new GradientDrawable();
        fpvBg.setShape(GradientDrawable.RECTANGLE);
        fpvBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 16f));
        fpvBg.setColor(Color.parseColor("#1D5CE5"));
        btnSwitchToFpv.setBackground(fpvBg);
        btnSwitchToFpv.setPadding(
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 5f),
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 5f)
        );
        btnSwitchToFpv.setOnClickListener(v -> switchToRemoteFpvMode(act, null));
        bar.addView(btnSwitchToFpv);

        View spacer = new View(act);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(MainActivityUiHelpers.dp(act, 6f), 1));
        bar.addView(spacer);

        TextView btnFlipCam = new TextView(act);
        btnFlipCam.setText("Đổi camera");
        btnFlipCam.setTextSize(10.5f);
        btnFlipCam.setTypeface(Typeface.DEFAULT_BOLD);
        btnFlipCam.setTextColor(Color.parseColor("#0F172A"));
        btnFlipCam.setGravity(Gravity.CENTER);

        GradientDrawable flipBg = new GradientDrawable();
        flipBg.setShape(GradientDrawable.RECTANGLE);
        flipBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 16f));
        flipBg.setColor(Color.WHITE);
        flipBg.setStroke(MainActivityUiHelpers.dp(act, 1.2f), Color.parseColor("#CBD5E1"));
        btnFlipCam.setBackground(flipBg);
        btnFlipCam.setPadding(
                MainActivityUiHelpers.dp(act, 10f),
                MainActivityUiHelpers.dp(act, 5f),
                MainActivityUiHelpers.dp(act, 10f),
                MainActivityUiHelpers.dp(act, 5f)
        );
        btnFlipCam.setOnClickListener(v -> act.switchCamera());
        bar.addView(btnFlipCam);

        return bar;
    }

    public static LinearLayout createControllerTopLeftBar(MainActivity act) {
        LinearLayout bar = new LinearLayout(act);
        bar.setId(View.generateViewId());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.addRule(RelativeLayout.ALIGN_PARENT_START);
        lp.setMargins(MainActivityUiHelpers.dp(act, 14f), MainActivityUiHelpers.dp(act, 10f), 0, 0);
        bar.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 26f));
        bg.setColor(Color.parseColor("#F5FFFFFF"));
        bg.setStroke(MainActivityUiHelpers.dp(act, 1f), Color.parseColor("#E2E8F0"));
        bar.setBackground(bg);
        bar.setElevation((float) MainActivityUiHelpers.dp(act, 6f));
        bar.setPadding(
                MainActivityUiHelpers.dp(act, 10f),
                MainActivityUiHelpers.dp(act, 6f),
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f)
        );
        bar.setVisibility(View.GONE);

        // 1. Nút Cài đặt
        TextView btnSettings = new TextView(act);
        btnSettings.setText("Cài đặt");
        btnSettings.setTextSize(10.5f);
        btnSettings.setTypeface(Typeface.DEFAULT_BOLD);
        btnSettings.setTextColor(Color.parseColor("#0F172A"));
        btnSettings.setGravity(Gravity.CENTER);
        btnSettings.setSingleLine(true);

        GradientDrawable setBg = new GradientDrawable();
        setBg.setShape(GradientDrawable.RECTANGLE);
        setBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 16f));
        setBg.setColor(Color.WHITE);
        setBg.setStroke(MainActivityUiHelpers.dp(act, 1.2f), Color.parseColor("#CBD5E1"));
        btnSettings.setBackground(setBg);
        btnSettings.setPadding(
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f),
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f)
        );
        btnSettings.setOnClickListener(v -> {
            if (act.controlDashboardView != null) {
                act.controlDashboardView.showParametersBottomSheet();
            }
        });
        bar.addView(btnSettings);

        View sp1 = new View(act);
        sp1.setLayoutParams(new LinearLayout.LayoutParams(MainActivityUiHelpers.dp(act, 6f), 1));
        bar.addView(sp1);

        // 2. Nút Chọn vai trò thiết bị
        TextView btnRole = new TextView(act);
        btnRole.setText("Chọn vai trò");
        btnRole.setTextSize(10.5f);
        btnRole.setTypeface(Typeface.DEFAULT_BOLD);
        btnRole.setTextColor(Color.parseColor("#0F172A"));
        btnRole.setGravity(Gravity.CENTER);
        btnRole.setSingleLine(true);

        GradientDrawable roleBg = new GradientDrawable();
        roleBg.setShape(GradientDrawable.RECTANGLE);
        roleBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 16f));
        roleBg.setColor(Color.WHITE);
        roleBg.setStroke(MainActivityUiHelpers.dp(act, 1.2f), Color.parseColor("#CBD5E1"));
        btnRole.setBackground(roleBg);
        btnRole.setPadding(
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f),
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f)
        );
        btnRole.setOnClickListener(v -> act.showRoleSelection());
        bar.addView(btnRole);

        View sp2 = new View(act);
        sp2.setLayoutParams(new LinearLayout.LayoutParams(MainActivityUiHelpers.dp(act, 10f), 1));
        bar.addView(sp2);

        // 3. Cụm điều khiển IP UDP của Robot
        TextView tvUdpLabel = new TextView(act);
        tvUdpLabel.setText("UDP");
        tvUdpLabel.setTextSize(10.5f);
        tvUdpLabel.setTextColor(Color.parseColor("#475569"));
        tvUdpLabel.setSingleLine(true);
        tvUdpLabel.setPadding(0, 0, MainActivityUiHelpers.dp(act, 6f), 0);
        bar.addView(tvUdpLabel);

        SharedPreferences prefs = act.getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE);
        String savedUdp = prefs.getString("pref_udp_ip_port", "192.168.2.93:5001");
        if (savedUdp == null) savedUdp = "192.168.2.93:5001";

        act.etUdpAddress = new EditText(act);
        act.etUdpAddress.setText(savedUdp);
        act.etUdpAddress.setTextSize(10.5f);
        act.etUdpAddress.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        act.etUdpAddress.setTextColor(Color.parseColor("#0F172A"));

        GradientDrawable ipEtBg = new GradientDrawable();
        ipEtBg.setShape(GradientDrawable.RECTANGLE);
        ipEtBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 16f));
        ipEtBg.setColor(Color.parseColor("#F1F5F9"));
        ipEtBg.setStroke(MainActivityUiHelpers.dp(act, 1f), Color.parseColor("#CBD5E1"));
        act.etUdpAddress.setBackground(ipEtBg);
        act.etUdpAddress.setPadding(
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 2f),
                MainActivityUiHelpers.dp(act, 12f),
                MainActivityUiHelpers.dp(act, 2f)
        );
        act.etUdpAddress.setLayoutParams(new LinearLayout.LayoutParams(
                MainActivityUiHelpers.dp(act, 136f),
                MainActivityUiHelpers.dp(act, 30f)
        ));
        act.etUdpAddress.setSingleLine(true);
        act.etUdpAddress.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(act.etUdpAddress);

        View sp3 = new View(act);
        sp3.setLayoutParams(new LinearLayout.LayoutParams(MainActivityUiHelpers.dp(act, 6f), 1));
        bar.addView(sp3);

        act.btnConnectUdp = new TextView(act);
        act.btnConnectUdp.setText("Nối");
        act.btnConnectUdp.setTextSize(10.5f);
        act.btnConnectUdp.setTypeface(Typeface.DEFAULT_BOLD);
        act.btnConnectUdp.setTextColor(Color.WHITE);
        act.btnConnectUdp.setGravity(Gravity.CENTER);
        act.btnConnectUdp.setSingleLine(true);

        GradientDrawable connBg = new GradientDrawable();
        connBg.setShape(GradientDrawable.RECTANGLE);
        connBg.setCornerRadius((float) MainActivityUiHelpers.dp(act, 16f));
        connBg.setColor(Color.parseColor("#1D5CE5"));
        act.btnConnectUdp.setBackground(connBg);
        act.btnConnectUdp.setPadding(
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f),
                MainActivityUiHelpers.dp(act, 14f),
                MainActivityUiHelpers.dp(act, 6f)
        );
        act.btnConnectUdp.setOnClickListener(v -> toggleUdpConnection(act));
        bar.addView(act.btnConnectUdp);

        View sp4 = new View(act);
        sp4.setLayoutParams(new LinearLayout.LayoutParams(MainActivityUiHelpers.dp(act, 8f), 1));
        bar.addView(sp4);

        act.tvUdpStatusBadge = new TextView(act);
        act.tvUdpStatusBadge.setText("Chưa nối");
        act.tvUdpStatusBadge.setTextSize(10.5f);
        act.tvUdpStatusBadge.setTextColor(Color.parseColor("#475569"));
        act.tvUdpStatusBadge.setSingleLine(true);
        act.tvUdpStatusBadge.setPadding(MainActivityUiHelpers.dp(act, 4f), 0, MainActivityUiHelpers.dp(act, 4f), 0);
        act.tvUdpStatusBadge.setOnClickListener(v -> {
            RobotUdpManager.ControlPermission nextMode = act.udpManager.cyclePermissionMode();
            String msg;
            switch (nextMode) {
                case MASTER:
                    msg = "Quyền Độc quyền (Master)";
                    break;
                case VIEWER:
                    msg = "Quyền Chỉ xem (Viewer)";
                    break;
                case AUTO:
                default:
                    msg = "Quyền Tự động";
                    break;
            }
            Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
        });
        bar.addView(act.tvUdpStatusBadge);

        act.udpManager.setOnPermissionChanged((mode, reason) -> {
            if (reason != null && !reason.isEmpty() && act.controlDashboardView != null) {
                act.controlDashboardView.updatePermissionStatusText(reason);
            }
        });

        return bar;
    }

    public static void toggleUdpConnection(MainActivity act) {
        if (act.udpManager.isConnected) {
            act.udpManager.disconnect();
            updateUdpUiState(act, false, "Đã ngắt kết nối UDP");
            Toast.makeText(act, "Đã ngắt kết nối UDP", Toast.LENGTH_SHORT).show();
        } else {
            String raw = act.etUdpAddress != null ? act.etUdpAddress.getText().toString().trim() : "";
            if (raw.isEmpty()) return;
            String[] parts = raw.split(":");
            String ip = parts[0].trim();
            int port = 5001;
            if (parts.length > 1) {
                try {
                    port = Integer.parseInt(parts[1].trim());
                } catch (Exception ignored) {}
            }

            act.getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE)
                    .edit().putString("pref_udp_ip_port", ip + ":" + port).apply();

            act.udpManager.connect(ip, port);
            updateUdpUiState(act, true, "Đã kết nối UDP tới " + ip + ":" + port);
            Toast.makeText(act, "Đang kết nối UDP tới " + ip + ":" + port, Toast.LENGTH_SHORT).show();
        }
    }

    public static void updateUdpUiState(MainActivity act, boolean connected, String statusText) {
        act.runOnUiThread(() -> {
            if (act.btnConnectUdp != null) {
                act.btnConnectUdp.setText(connected ? "Ngắt" : "Nối");
                if (act.btnConnectUdp.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) act.btnConnectUdp.getBackground()).setColor(
                            connected ? Color.parseColor("#DC2626") : Color.parseColor("#1D5CE5")
                    );
                }
            }
            if (act.tvUdpStatusBadge != null) {
                act.tvUdpStatusBadge.setText(connected ? "Đã nối" : "Chưa nối");
                act.tvUdpStatusBadge.setTextColor(
                        connected ? Color.parseColor("#15803D") : Color.parseColor("#475569")
                );
            }
            if (act.controlDashboardView != null) {
                act.controlDashboardView.updateUdpBannerState(connected, statusText);
            }
        });
    }

    public static void updateControllerLocalIpDisplay(MainActivity act) {
        String ip = MainActivityStreamer.getWifiIpAddress(act);
        act.runOnUiThread(() -> {
            if (act.tvControllerLocalIp != null) {
                if (ip != null) {
                    act.tvControllerLocalIp.setText("Camera: " + ip + ":8080");
                    act.tvControllerLocalIp.setTextColor(Color.parseColor("#0F172A"));
                } else {
                    act.tvControllerLocalIp.setText("Chưa có WiFi");
                    act.tvControllerLocalIp.setTextColor(Color.parseColor("#DC2626"));
                }
            }
        });
    }

    public static void updateControllerLayoutDimensions(MainActivity act) {
        if (act.currentRole != MainActivity.AppRole.CONTROLLER) return;
        DisplayMetrics dm = act.getResources().getDisplayMetrics();
        int w = dm.widthPixels;
        int h = dm.heightPixels;
        boolean isLandscape = act.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        RelativeLayout.LayoutParams fullScreenLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );

        if ("LOCAL".equals(act.controllerCamSource)) {
            if (act.previewView != null) {
                act.previewView.setLayoutParams(fullScreenLp);
                act.previewView.setVisibility(View.VISIBLE);
            }
            if (act.remoteMjpegView != null) {
                act.remoteMjpegView.setVisibility(View.GONE);
            }
            if (act.controllerLocalCamBar != null) {
                act.controllerLocalCamBar.setVisibility(View.VISIBLE);
                act.controllerLocalCamBar.bringToFront();
            }
        } else {
            if (act.remoteMjpegView != null) {
                act.remoteMjpegView.setLayoutParams(fullScreenLp);
                act.remoteMjpegView.setVisibility(View.VISIBLE);
            }
            if (act.previewView != null) {
                act.previewView.setVisibility(View.GONE);
            }
            if (act.controllerLocalCamBar != null) {
                act.controllerLocalCamBar.setVisibility(View.GONE);
            }
        }

        if (act.remoteMjpegView != null) {
            act.remoteMjpegView.updateOrientation(isLandscape);
        }

        RelativeLayout.LayoutParams dashLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );
        if (act.controlDashboardView != null) {
            act.controlDashboardView.setLayoutParams(dashLp);
            act.controlDashboardView.applyControlSizes(w, h);
            act.controlDashboardView.setVisibility(View.VISIBLE);
            act.controlDashboardView.bringToFront();
        }

        if (act.controllerTopLeftBar != null) {
            act.controllerTopLeftBar.setVisibility(View.VISIBLE);
            act.controllerTopLeftBar.bringToFront();
        }
        if ("LOCAL".equals(act.controllerCamSource) && act.controllerLocalCamBar != null) {
            act.controllerLocalCamBar.setVisibility(View.VISIBLE);
            act.controllerLocalCamBar.bringToFront();
        }
    }

    public static void sendHttpCommandToStreamer(MainActivity act, String endpoint) {
        SharedPreferences prefs = act.getSharedPreferences("robot_remote_cam_prefs", Context.MODE_PRIVATE);
        String camIp = prefs.getString("remote_cam_ip", "192.168.2.80:8080");
        if (camIp == null) camIp = "192.168.2.80:8080";

        String host = (camIp.startsWith("http://") || camIp.startsWith("https://")) ? camIp : "http://" + camIp;
        if (host.endsWith("/video.mjpeg")) host = host.substring(0, host.length() - 12);
        if (host.endsWith("/video")) host = host.substring(0, host.length() - 6);
        while (host.endsWith("/")) host = host.substring(0, host.length() - 1);
        final String cleanHost = host;

        new Thread(() -> {
            try {
                URL url = new URL(cleanHost + endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(1500);
                conn.setReadTimeout(1500);
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "sendHttpCommandToStreamer error: " + e.getMessage());
            }
        }).start();
    }
}
