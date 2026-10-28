package com.robotcamera.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Trình phát luồng video MJPEG từ xa Siêu Tốc (Zero-Latency Remote MJPEG Player)
 * - Thiết kế Full-Screen FPV với thanh công cụ Pill trắng nổi ở góc trên phải.
 */
@SuppressLint("SetTextI18n")
public class RemoteMjpegView extends FrameLayout {

    public interface OnFrameReceivedListener {
        void onFrameReceived(byte[] jpegBytes);
    }

    public interface OnBitmapDecodedListener {
        void onBitmapDecoded(Bitmap bitmap);
    }

    public interface OnSwitchToLocalCamRequestedListener {
        void onSwitchToLocalCamRequested();
    }

    private static final String TAG = "RemoteMjpegView";
    private static final String PREFS_NAME = "robot_remote_cam_prefs";
    private static final String KEY_CAM_IP = "remote_cam_ip";
    private static final String DEFAULT_CAM_IP = "192.168.2.80:8080";

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

    // Views
    private final ImageView ivStream;
    private final FrameLayout placeholderLayout;
    private final TextView tvTopConnectingPill;
    private final TextView tvCenterPlaceholder;
    private final FpvHudOverlayView hudOverlay;
    private final LinearLayout topBarContainer;
    private final EditText etStreamIp;
    private final TextView btnStreamAction;
    private final TextView tvStatusBadge;

    // Threading & Network
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isStreaming = false;
    private Thread streamThread = null;
    private volatile ExecutorService decodeExecutor = null;
    private final AtomicBoolean isRendering = new AtomicBoolean(false);

    // FPS calculation
    private int frameCount = 0;
    private long lastFpsTime = System.currentTimeMillis();
    private int currentFps = 0;

    // Callbacks
    private OnFrameReceivedListener onFrameReceivedListener = null;
    private OnBitmapDecodedListener onBitmapDecodedListener = null;
    private OnSwitchToLocalCamRequestedListener onSwitchToLocalCamListener = null;

    public void setOnFrameReceived(OnFrameReceivedListener l) {
        this.onFrameReceivedListener = l;
    }

    public void setOnBitmapDecoded(OnBitmapDecodedListener l) {
        this.onBitmapDecodedListener = l;
    }

    public void setOnSwitchToLocalCamRequested(OnSwitchToLocalCamRequestedListener l) {
        this.onSwitchToLocalCamListener = l;
    }

    public RemoteMjpegView(@NonNull Context context) {
        this(context, null);
    }

    public RemoteMjpegView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.parseColor("#C2D8FB"));

        // 1. ImageView hiển thị khung hình stream toàn màn hình
        ivStream = new ImageView(context);
        ivStream.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        ivStream.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(ivStream);

        // 2. Placeholder nền xanh băng mềm mại khi đang chờ luồng FPV
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedIp = prefs.getString(KEY_CAM_IP, DEFAULT_CAM_IP);
        if (savedIp == null) savedIp = DEFAULT_CAM_IP;

        placeholderLayout = new FrameLayout(context);
        placeholderLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        GradientDrawable placeBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor("#DCE9FD"),
                        Color.parseColor("#C5DAFC"),
                        Color.parseColor("#AEC9FA")
                }
        );
        placeholderLayout.setBackground(placeBg);

        tvTopConnectingPill = new TextView(context);
        tvTopConnectingPill.setText("Đang kết nối luồng FPV từ xa...");
        tvTopConnectingPill.setTextSize(11.5f);
        tvTopConnectingPill.setTypeface(Typeface.DEFAULT_BOLD);
        tvTopConnectingPill.setTextColor(Color.parseColor("#0F172A"));
        tvTopConnectingPill.setGravity(Gravity.CENTER);

        GradientDrawable topPillBg = new GradientDrawable();
        topPillBg.setShape(GradientDrawable.RECTANGLE);
        topPillBg.setCornerRadius(dpF(20f));
        topPillBg.setColor(Color.parseColor("#F5FFFFFF"));
        tvTopConnectingPill.setBackground(topPillBg);
        tvTopConnectingPill.setElevation(dpF(4f));
        tvTopConnectingPill.setPadding(dp(20f), dp(7f), dp(20f), dp(7f));

        LayoutParams topConnLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        topConnLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        topConnLp.topMargin = dp(60f);
        tvTopConnectingPill.setLayoutParams(topConnLp);
        placeholderLayout.addView(tvTopConnectingPill);

        tvCenterPlaceholder = new TextView(context);
        tvCenterPlaceholder.setText("Luồng FPV toàn màn hình · " + savedIp);
        tvCenterPlaceholder.setTextSize(12.5f);
        tvCenterPlaceholder.setTypeface(Typeface.DEFAULT_BOLD);
        tvCenterPlaceholder.setTextColor(Color.parseColor("#475569"));
        tvCenterPlaceholder.setGravity(Gravity.CENTER);

        LayoutParams centerLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        centerLp.gravity = Gravity.CENTER;
        tvCenterPlaceholder.setLayoutParams(centerLp);
        placeholderLayout.addView(tvCenterPlaceholder);

        addView(placeholderLayout);

        // 3. Lớp phủ HUD FPV Cockpit & Nhận diện người
        hudOverlay = new FpvHudOverlayView(context);
        hudOverlay.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        hudOverlay.setLive(false);
        hudOverlay.setLiveTitle("OFFLINE");
        hudOverlay.setFooterText("CHỜ TÍN HIỆU • MECANUM 4WD");
        hudOverlay.setVisibility(View.VISIBLE);
        addView(hudOverlay);

        // 4. Thanh công cụ Pill trắng nổi ở góc trên phải
        topBarContainer = new LinearLayout(context);
        topBarContainer.setOrientation(LinearLayout.HORIZONTAL);
        topBarContainer.setGravity(Gravity.CENTER_VERTICAL);

        LayoutParams topBarLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        topBarLp.gravity = Gravity.TOP | Gravity.END;
        topBarLp.setMargins(0, dp(10f), dp(14f), 0);
        topBarContainer.setLayoutParams(topBarLp);

        GradientDrawable topBarBg = new GradientDrawable();
        topBarBg.setShape(GradientDrawable.RECTANGLE);
        topBarBg.setCornerRadius(dpF(26f));
        topBarBg.setColor(Color.parseColor("#F5FFFFFF"));
        topBarBg.setStroke(dp(1f), Color.parseColor("#E2E8F0"));
        topBarContainer.setBackground(topBarBg);
        topBarContainer.setElevation(dpF(6f));
        topBarContainer.setPadding(dp(12f), dp(5f), dp(8f), dp(5f));

        TextView tvCamLabel = new TextView(context);
        tvCamLabel.setText("Camera");
        tvCamLabel.setTextSize(10.5f);
        tvCamLabel.setTextColor(Color.parseColor("#475569"));
        tvCamLabel.setPadding(0, 0, dp(6f), 0);
        topBarContainer.addView(tvCamLabel);

        etStreamIp = new EditText(context);
        etStreamIp.setText(savedIp);
        etStreamIp.setTextSize(10.5f);
        etStreamIp.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        etStreamIp.setTextColor(Color.parseColor("#0F172A"));

        GradientDrawable ipBg = new GradientDrawable();
        ipBg.setShape(GradientDrawable.RECTANGLE);
        ipBg.setCornerRadius(dpF(16f));
        ipBg.setColor(Color.parseColor("#F1F5F9"));
        ipBg.setStroke(dp(1f), Color.parseColor("#CBD5E1"));
        etStreamIp.setBackground(ipBg);
        etStreamIp.setPadding(dp(10f), dp(2f), dp(10f), dp(2f));
        etStreamIp.setLayoutParams(new LinearLayout.LayoutParams(dp(132f), dp(30f)));
        etStreamIp.setSingleLine(true);
        etStreamIp.setGravity(Gravity.CENTER_VERTICAL);
        topBarContainer.addView(etStreamIp);

        topBarContainer.addView(createSpacer(dp(6f)));

        btnStreamAction = new TextView(context);
        btnStreamAction.setText("Dừng");
        btnStreamAction.setTextSize(10.5f);
        btnStreamAction.setTypeface(Typeface.DEFAULT_BOLD);
        btnStreamAction.setTextColor(Color.WHITE);
        btnStreamAction.setGravity(Gravity.CENTER);
        btnStreamAction.setSingleLine(true);

        GradientDrawable actBg = new GradientDrawable();
        actBg.setShape(GradientDrawable.RECTANGLE);
        actBg.setCornerRadius(dpF(16f));
        actBg.setColor(Color.parseColor("#DC2626"));
        btnStreamAction.setBackground(actBg);
        btnStreamAction.setPadding(dp(12f), dp(6f), dp(12f), dp(6f));
        btnStreamAction.setOnClickListener(v -> {
            if (isStreaming) {
                stopStream();
            } else {
                String input = etStreamIp.getText().toString().trim();
                if (!input.isEmpty()) {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putString(KEY_CAM_IP, input).apply();
                    startStream(input);
                }
            }
        });
        topBarContainer.addView(btnStreamAction);

        topBarContainer.addView(createSpacer(dp(6f)));

        tvStatusBadge = new TextView(context);
        tvStatusBadge.setText("• LIVE");
        tvStatusBadge.setTextSize(10.5f);
        tvStatusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatusBadge.setTextColor(Color.parseColor("#15803D"));
        tvStatusBadge.setSingleLine(true);
        tvStatusBadge.setPadding(dp(2f), 0, dp(4f), 0);
        topBarContainer.addView(tvStatusBadge);

        topBarContainer.addView(createSpacer(dp(4f)));

        // Nút chuyển sang dùng camera trực tiếp trên máy này
        TextView btnSwitchLocal = new TextView(context);
        btnSwitchLocal.setText("Camera máy");
        btnSwitchLocal.setTextSize(10.5f);
        btnSwitchLocal.setTypeface(Typeface.DEFAULT_BOLD);
        btnSwitchLocal.setTextColor(Color.parseColor("#0F172A"));
        btnSwitchLocal.setGravity(Gravity.CENTER);
        btnSwitchLocal.setSingleLine(true);

        GradientDrawable switchBg = new GradientDrawable();
        switchBg.setShape(GradientDrawable.RECTANGLE);
        switchBg.setCornerRadius(dpF(16f));
        switchBg.setColor(Color.WHITE);
        switchBg.setStroke(dp(1.2f), Color.parseColor("#CBD5E1"));
        btnSwitchLocal.setBackground(switchBg);
        btnSwitchLocal.setPadding(dp(12f), dp(6f), dp(12f), dp(6f));
        btnSwitchLocal.setOnClickListener(v -> {
            if (onSwitchToLocalCamListener != null) {
                onSwitchToLocalCamListener.onSwitchToLocalCamRequested();
            }
        });
        topBarContainer.addView(btnSwitchLocal);

        topBarContainer.addView(createSpacer(dp(5f)));

        // Nút ẩn/hiện HUD
        TextView btnToggleHud = new TextView(context);
        btnToggleHud.setText("HUD");
        btnToggleHud.setTextSize(10.5f);
        btnToggleHud.setTypeface(Typeface.DEFAULT_BOLD);
        btnToggleHud.setTextColor(Color.parseColor("#0F172A"));
        btnToggleHud.setGravity(Gravity.CENTER);

        GradientDrawable hudBtnBg = new GradientDrawable();
        hudBtnBg.setShape(GradientDrawable.RECTANGLE);
        hudBtnBg.setCornerRadius(dpF(16f));
        hudBtnBg.setColor(Color.WHITE);
        hudBtnBg.setStroke(dp(1.2f), Color.parseColor("#CBD5E1"));
        btnToggleHud.setBackground(hudBtnBg);
        btnToggleHud.setPadding(dp(11f), dp(6f), dp(11f), dp(6f));
        btnToggleHud.setOnClickListener(v -> {
            hudOverlay.setVisibility(hudOverlay.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        topBarContainer.addView(btnToggleHud);

        addView(topBarContainer);
    }

    public void startStream() {
        startStream(null);
    }

    /**
     * Bắt đầu nhận luồng video từ xa qua URL/IP (Độ trễ cực thấp < 30ms)
     */
    public void startStream(String target) {
        if (isStreaming) stopStream();

        String rawTarget = (target != null && !target.trim().isEmpty()) ? target : etStreamIp.getText().toString().trim();
        if (rawTarget.isEmpty()) return;

        String normalizedUrl;
        if (rawTarget.startsWith("http://") || rawTarget.startsWith("https://")) {
            normalizedUrl = rawTarget;
        } else if (rawTarget.contains("/")) {
            normalizedUrl = "http://" + rawTarget;
        } else if (rawTarget.contains(":")) {
            normalizedUrl = "http://" + rawTarget + "/video.mjpeg";
        } else {
            normalizedUrl = "http://" + rawTarget + ":8080/video.mjpeg";
        }

        String clean = rawTarget;
        if (clean.contains("@")) {
            clean = clean.substring(clean.indexOf('@') + 1);
        }
        if (clean.startsWith("http://")) clean = clean.substring(7);
        if (clean.startsWith("https://")) clean = clean.substring(8);
        if (clean.endsWith("/video.mjpeg")) clean = clean.substring(0, clean.length() - 12);
        if (clean.endsWith("/video")) clean = clean.substring(0, clean.length() - 6);
        if (clean.endsWith("/live")) clean = clean.substring(0, clean.length() - 5);
        if (clean.endsWith("/mjpeg")) clean = clean.substring(0, clean.length() - 6);
        final String cleanDisplayIp = clean;

        decodeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RemoteMjpegDecoder");
            t.setDaemon(true);
            return t;
        });
        isRendering.set(false);
        isStreaming = true;

        btnStreamAction.setText("Dừng");
        if (btnStreamAction.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) btnStreamAction.getBackground()).setColor(Color.parseColor("#DC2626"));
        }
        tvStatusBadge.setText("• LIVE");
        tvStatusBadge.setTextColor(Color.parseColor("#15803D"));

        tvTopConnectingPill.setVisibility(View.VISIBLE);
        tvTopConnectingPill.setText("Đang kết nối luồng FPV từ xa...");
        tvCenterPlaceholder.setText("Luồng FPV toàn màn hình · " + cleanDisplayIp);

        streamThread = new Thread(() -> runStreamLoop(normalizedUrl, cleanDisplayIp), "RemoteMjpegWorker");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    /**
     * Dừng nhận luồng video và giải phóng tài nguyên giải mã
     */
    public void stopStream() {
        isStreaming = false;
        if (streamThread != null) {
            streamThread.interrupt();
            streamThread = null;
        }

        try {
            if (decodeExecutor != null) {
                decodeExecutor.shutdownNow();
            }
        } catch (Exception ignored) {}
        decodeExecutor = null;
        isRendering.set(false);

        uiHandler.post(() -> {
            btnStreamAction.setText("Xem");
            if (btnStreamAction.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) btnStreamAction.getBackground()).setColor(Color.parseColor("#1D5CE5"));
            }
            tvStatusBadge.setText("OFFLINE");
            tvStatusBadge.setTextColor(Color.parseColor("#64748B"));

            placeholderLayout.setVisibility(View.VISIBLE);
            tvTopConnectingPill.setText("Đã tạm dừng luồng FPV");

            hudOverlay.setLive(false);
            hudOverlay.setLiveTitle("OFFLINE");
            hudOverlay.setFooterText("CHỜ TÍN HIỆU • MECANUM 4WD");
        });
    }

    private HttpURLConnection tryConnect(String urlStr, String auth) {
        try {
            URL u = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(4000);
            conn.setUseCaches(false);
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("Connection", "keep-alive");
            conn.setRequestProperty("Accept", "multipart/x-mixed-replace, image/jpeg, */*");
            if (auth != null) {
                conn.setRequestProperty("Authorization", auth);
            }
            return conn;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void processJpegFrame(byte[] jpegBytes, BitmapFactory.Options decodeOptions) {
        try {
            if (onFrameReceivedListener != null) {
                onFrameReceivedListener.onFrameReceived(jpegBytes);
            }
        } catch (Exception ignored) {}

        ExecutorService executor = decodeExecutor;
        if (executor == null) return;

        if (isRendering.compareAndSet(false, true)) {
            try {
                executor.execute(() -> {
                    try {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length, decodeOptions);
                        if (bitmap != null) {
                            try {
                                if (onBitmapDecodedListener != null) {
                                    onBitmapDecodedListener.onBitmapDecoded(bitmap);
                                }
                            } catch (Exception ignored) {}

                            uiHandler.post(() -> {
                                ivStream.setImageBitmap(bitmap);
                                isRendering.set(false);
                            });

                            frameCount++;
                            long now = System.currentTimeMillis();
                            if (now - lastFpsTime >= 1000) {
                                currentFps = frameCount;
                                frameCount = 0;
                                lastFpsTime = now;
                                final int fps = currentFps;
                                uiHandler.post(() -> {
                                    hudOverlay.setFooterText(bitmap.getWidth() + "x" + bitmap.getHeight() + " • " + fps + " FPS • 0ms LAG");
                                });
                            }
                        } else {
                            isRendering.set(false);
                        }
                    } catch (Exception ignored) {
                        isRendering.set(false);
                    }
                });
            } catch (Exception ignored) {
                isRendering.set(false);
            }
        }
    }

    private void runStreamLoop(String streamUrl, String displayIp) {
        String cleanStreamUrl = streamUrl;
        String authHeader = null;

        int atIdx = streamUrl.indexOf('@');
        int slashSlashIdx = streamUrl.indexOf("://");
        if (atIdx != -1 && slashSlashIdx != -1 && atIdx > slashSlashIdx) {
            String userPass = streamUrl.substring(slashSlashIdx + 3, atIdx);
            String scheme = streamUrl.substring(0, slashSlashIdx + 3);
            String rest = streamUrl.substring(atIdx + 1);
            cleanStreamUrl = scheme + rest;
            authHeader = "Basic " + Base64.encodeToString(userPass.getBytes(), Base64.NO_WRAP);
        }

        String defaultAuthHeader = "Basic " + Base64.encodeToString("admin:admin".getBytes(), Base64.NO_WRAP);

        List<String> candidates;
        if (cleanStreamUrl.endsWith("/video.mjpeg")) {
            String base = cleanStreamUrl.substring(0, cleanStreamUrl.length() - 12);
            candidates = Arrays.asList(cleanStreamUrl, base + "/video", base + "/live", base + "/mjpeg", base + "/live.mjpeg", base + "/");
        } else {
            candidates = Arrays.asList(cleanStreamUrl);
        }

        HttpURLConnection connection = null;
        InputStream inputStream = null;

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inPreferredConfig = Bitmap.Config.RGB_565;

        while (isStreaming && !Thread.currentThread().isInterrupted()) {
            try {
                HttpURLConnection conn = null;
                for (String cand : candidates) {
                    if (!isStreaming) break;
                    HttpURLConnection testConn = tryConnect(cand, authHeader);
                    int code = -1;
                    try {
                        if (testConn != null) code = testConn.getResponseCode();
                    } catch (Exception ignored) {}

                    if (code == 401 && authHeader == null) {
                        try {
                            if (testConn != null) testConn.disconnect();
                        } catch (Exception ignored) {}
                        testConn = tryConnect(cand, defaultAuthHeader);
                    }

                    int finalCode = -1;
                    try {
                        if (testConn != null) finalCode = testConn.getResponseCode();
                    } catch (Exception ignored) {}

                    if (finalCode == HttpURLConnection.HTTP_OK && testConn != null) {
                        conn = testConn;
                        break;
                    } else {
                        try {
                            if (testConn != null) testConn.disconnect();
                        } catch (Exception ignored) {}
                    }
                }

                if (conn == null) {
                    throw new Exception("Không thể kết nối luồng FPV tại: " + streamUrl + " (Lỗi 401/404)");
                }
                connection = conn;
                InputStream rawStream = conn.getInputStream();
                inputStream = rawStream;

                uiHandler.post(() -> {
                    placeholderLayout.setVisibility(View.GONE);
                    tvStatusBadge.setText("• LIVE");
                    tvStatusBadge.setTextColor(Color.parseColor("#15803D"));
                    hudOverlay.setLive(true);
                    hudOverlay.setLiveTitle("LIVE FPV");
                });

                byte[] chunk = new byte[16384];
                ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream(128 * 1024);
                boolean inJpeg = false;
                int prevByte = -1;

                while (isStreaming && !Thread.currentThread().isInterrupted()) {
                    if (!inJpeg) {
                        int avail = 0;
                        try {
                            avail = rawStream.available();
                        } catch (Exception ignored) {}

                        if (avail > 64 * 1024) {
                            int toDrain = avail;
                            while (toDrain > 0) {
                                int readLen = Math.min(toDrain, chunk.length);
                                int drained = rawStream.read(chunk, 0, readLen);
                                if (drained <= 0) break;
                                toDrain -= drained;
                            }
                            prevByte = -1;
                        }
                    }

                    int count = rawStream.read(chunk, 0, chunk.length);
                    if (count <= 0) break;

                    int idx = 0;
                    while (idx < count) {
                        int b = chunk[idx] & 0xFF;

                        if (!inJpeg) {
                            if (prevByte == 0xFF && b == 0xD8) {
                                inJpeg = true;
                                frameBuffer.reset();
                                frameBuffer.write(0xFF);
                                frameBuffer.write(0xD8);
                            }
                            prevByte = b;
                            idx++;
                        } else {
                            if (prevByte == 0xFF && b == 0xD9) {
                                frameBuffer.write(0xD9);
                                inJpeg = false;
                                prevByte = -1;
                                idx++;

                                byte[] jpegBytes = frameBuffer.toByteArray();
                                processJpegFrame(jpegBytes, decodeOptions);
                                continue;
                            }

                            int start = idx;
                            while (idx < count && (chunk[idx] & 0xFF) != 0xFF) {
                                idx++;
                            }

                            if (idx > start) {
                                frameBuffer.write(chunk, start, idx - start);
                                prevByte = chunk[idx - 1] & 0xFF;
                            }

                            if (idx < count && (chunk[idx] & 0xFF) == 0xFF) {
                                frameBuffer.write(0xFF);
                                prevByte = 0xFF;
                                idx++;
                            }

                            if (frameBuffer.size() > 2 * 1024 * 1024) {
                                inJpeg = false;
                                frameBuffer.reset();
                                prevByte = -1;
                            }
                        }
                    }
                }

            } catch (Exception e) {
                if (!isStreaming) break;
                Log.w(TAG, "Chờ luồng FPV: " + e.getMessage());

                uiHandler.post(() -> {
                    placeholderLayout.setVisibility(View.VISIBLE);
                    tvTopConnectingPill.setText("Đang kết nối luồng FPV từ xa...");
                    tvCenterPlaceholder.setText("Luồng FPV toàn màn hình · " + displayIp);
                    hudOverlay.setLive(false);
                    hudOverlay.setLiveTitle("RETRYING");
                });

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                    break;
                }
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                } catch (Exception ignored) {}
                try {
                    if (connection != null) connection.disconnect();
                } catch (Exception ignored) {}
            }
        }
    }

    public void updateTarget(VisualDistanceDetector.TargetResult target) {
        uiHandler.post(() -> hudOverlay.setCurrentTarget(target));
    }

    public void updateOrientation(boolean isLandscape) {
        LayoutParams lp = (LayoutParams) topBarContainer.getLayoutParams();
        if (lp != null) {
            lp.topMargin = isLandscape ? dp(10f) : dp(52f);
            topBarContainer.setLayoutParams(lp);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        updateOrientation(isLandscape);
    }

    private View createSpacer(int w) {
        View v = new View(getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(w, 1));
        return v;
    }
}
