package com.robotcamera.app;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Đồng bộ trạng thái các nút bấm (Nhấn/Giữ/Click) Real-time giữa Tablet và Ứng dụng PC
 * Sử dụng UDP Broadcast + Direct Unicast với độ trễ siêu thấp (< 1ms)
 */
public final class ButtonSyncManager {
    private static final String TAG = "ButtonSyncManager";
    public static final int SYNC_PORT = 5005;

    public interface OnButtonEventListener {
        void onButtonEvent(String btnId, String action, String extra);
    }

    private static final ButtonSyncManager INSTANCE = new ButtonSyncManager();

    public static ButtonSyncManager getInstance() {
        return INSTANCE;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private DatagramSocket socket;

    // IP Unicast trực tiếp tới PC (tự động nhận khi PC kết nối Stream hoặc UDP heartbeat)
    public static volatile String targetUnicastIp = "192.168.2.117";

    private final CopyOnWriteArrayList<OnButtonEventListener> listeners = new CopyOnWriteArrayList<>();

    public static volatile OnButtonEventListener onRemoteButtonEvent;

    private ButtonSyncManager() {
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(SYNC_PORT));
            socket.setBroadcast(true);
            startListener();
        } catch (Exception e) {
            Log.w(TAG, "Không thể bind cổng " + SYNC_PORT + " cho listener: " + e.getMessage() + ", dùng socket phát thông thường");
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);
            } catch (Exception ex) {
                Log.e(TAG, "Lỗi tạo DatagramSocket: " + ex.getMessage());
            }
        }
    }

    public static void addListener(OnButtonEventListener listener) {
        INSTANCE.listeners.add(listener);
    }

    public static void removeListener(OnButtonEventListener listener) {
        INSTANCE.listeners.remove(listener);
    }

    private boolean isLocalIpAddress(InetAddress addr) {
        if (addr == null || addr.isLoopbackAddress()) return true;
        try {
            String hostAddr = addr.getHostAddress();
            if (hostAddr == null) return false;
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return false;
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                java.util.Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress item = addrs.nextElement();
                    if (hostAddr.equals(item.getHostAddress())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void startListener() {
        Thread thread = new Thread(() -> {
            byte[] buf = new byte[1024];
            while (true) {
                try {
                    DatagramSocket sock = socket;
                    if (sock == null) break;
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    sock.receive(packet);
                    if (isLocalIpAddress(packet.getAddress())) continue;
                    String fromIp = packet.getAddress() != null ? packet.getAddress().getHostAddress() : null;
                    if (fromIp == null) continue;

                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    if (msg.startsWith("HELLO_PC")) {
                        targetUnicastIp = fromIp;
                        Log.d(TAG, "Đã tự động bắt tay với PC tại IP: " + fromIp);
                    } else if (msg.startsWith("BTN:")) {
                        targetUnicastIp = fromIp;
                        String[] parts = msg.split(":");
                        if (parts.length >= 3) {
                            String btnId = parts[1].trim().toUpperCase();
                            String action = parts[2].trim().toUpperCase();
                            String extra = parts.length > 3 ? parts[3] : null;

                            OnButtonEventListener globalListener = onRemoteButtonEvent;
                            if (globalListener != null) {
                                globalListener.onButtonEvent(btnId, action, extra);
                            }
                            for (OnButtonEventListener l : listeners) {
                                try {
                                    l.onButtonEvent(btnId, action, extra);
                                } catch (Exception e) {
                                    Log.e(TAG, "Lỗi listener: " + e.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                }
            }
        }, "ButtonSync-Listener");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Gửi sự kiện nút bấm tới ứng dụng PC
     */
    public static void sendButtonEvent(String btnId, String action) {
        sendButtonEvent(btnId, action, null);
    }

    public static void sendButtonEvent(String btnId, String action, String extra) {
        final String payload = (extra != null) ? ("BTN:" + btnId + ":" + action + ":" + extra) : ("BTN:" + btnId + ":" + action);
        INSTANCE.executor.execute(() -> {
            try {
                byte[] data = payload.getBytes(StandardCharsets.UTF_8);
                DatagramSocket sock = INSTANCE.socket;
                if (sock == null) {
                    sock = new DatagramSocket();
                    sock.setBroadcast(true);
                    INSTANCE.socket = sock;
                }

                // 1. Unicast trực tiếp tới PC
                String ip = targetUnicastIp;
                if (ip != null && !ip.isEmpty() && !ip.equals("127.0.0.1")) {
                    try {
                        DatagramPacket pUnicast = new DatagramPacket(data, data.length, InetAddress.getByName(ip), SYNC_PORT);
                        sock.send(pUnicast);
                    } catch (Exception ignored) {}
                }

                // 2. Broadcast subnet 192.168.2.255
                try {
                    DatagramPacket pSubnet = new DatagramPacket(data, data.length, InetAddress.getByName("192.168.2.255"), SYNC_PORT);
                    sock.send(pSubnet);
                } catch (Exception ignored) {}

                // 3. Broadcast toàn mạng 255.255.255.255
                try {
                    DatagramPacket pBroadcast = new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), SYNC_PORT);
                    sock.send(pBroadcast);
                } catch (Exception ignored) {}
            } catch (Exception e) {
                Log.e(TAG, "Lỗi phát broadcast button event: " + e.getMessage());
            }
        });
    }
}
