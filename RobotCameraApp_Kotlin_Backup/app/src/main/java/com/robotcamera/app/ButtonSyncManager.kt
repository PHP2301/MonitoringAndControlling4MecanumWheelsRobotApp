package com.robotcamera.app

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

/**
 * Đồng bộ trạng thái các nút bấm (Nhấn/Giữ/Click) Real-time giữa Tablet và Ứng dụng PC
 * Sử dụng UDP Broadcast + Direct Unicast với độ trễ siêu thấp (< 1ms)
 */
object ButtonSyncManager {
    private const val TAG = "ButtonSyncManager"
    const val SYNC_PORT = 5005

    private val executor = Executors.newSingleThreadExecutor()
    private var socket: DatagramSocket? = null

    // IP Unicast trực tiếp tới PC (tự động nhận khi PC kết nối Stream hoặc UDP heartbeat)
    @Volatile
    var targetUnicastIp: String? = "192.168.2.117"

    init {
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(SYNC_PORT))
                broadcast = true
            }
            startListener()
        } catch (e: Exception) {
            Log.w(TAG, "Không thể bind cổng $SYNC_PORT cho listener: ${e.message}, dùng socket phát thông thường")
            try {
                socket = DatagramSocket().apply { broadcast = true }
            } catch (ex: Exception) {
                Log.e(TAG, "Lỗi tạo DatagramSocket: ${ex.message}")
            }
        }
    }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(btnId: String, action: String, extra: String?) -> Unit>()

    @Volatile
    var onRemoteButtonEvent: ((btnId: String, action: String, extra: String?) -> Unit)? = null

    fun addListener(listener: (btnId: String, action: String, extra: String?) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (btnId: String, action: String, extra: String?) -> Unit) {
        listeners.remove(listener)
    }

    private fun isLocalIpAddress(addr: InetAddress?): Boolean {
        if (addr == null || addr.isLoopbackAddress) return true
        return try {
            val hostAddr = addr.hostAddress ?: return false
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    if (addrs.nextElement().hostAddress == hostAddr) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun startListener() {
        Thread {
            val buf = ByteArray(1024)
            while (true) {
                try {
                    val sock = socket ?: break
                    val packet = DatagramPacket(buf, buf.size)
                    sock.receive(packet)
                    // Bỏ qua gói tin broadcast lặp lại từ chính thiết bị này
                    if (isLocalIpAddress(packet.address)) continue
                    val fromIp = packet.address?.hostAddress ?: continue
                    val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (msg.startsWith("HELLO_PC")) {
                        targetUnicastIp = fromIp
                        Log.d(TAG, "Đã tự động bắt tay với PC tại IP: $fromIp")
                    } else if (msg.startsWith("BTN:")) {
                        targetUnicastIp = fromIp
                        val parts = msg.split(":")
                        if (parts.size >= 3) {
                            val btnId = parts[1].trim().uppercase()
                            val action = parts[2].trim().uppercase()
                            val extra = if (parts.size > 3) parts[3] else null
                            onRemoteButtonEvent?.invoke(btnId, action, extra)
                            for (l in listeners) {
                                try {
                                    l(btnId, action, extra)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Lỗi listener: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    try { Thread.sleep(1000) } catch (_: Exception) {}
                }
            }
        }.apply {
            isDaemon = true
            name = "ButtonSync-Listener"
            start()
        }
    }

    /**
     * Gửi sự kiện nút bấm tới ứng dụng PC
     * @param btnId: Tên nút (UP, DOWN, LEFT, RIGHT, TURN_LEFT, TURN_RIGHT, EMERGENCY, etc.)
     * @param action: Trạng thái (DOWN = Giữ nút, UP = Nhả nút, CLICK = Nhấn click)
     * @param extra: Thông số bổ sung tùy chọn (tốc độ, setpoint...)
     */
    fun sendButtonEvent(btnId: String, action: String, extra: String? = null) {
        val payload = if (extra != null) "BTN:$btnId:$action:$extra" else "BTN:$btnId:$action"
        executor.execute {
            try {
                val data = payload.toByteArray(Charsets.UTF_8)
                val sock = socket ?: DatagramSocket().apply { broadcast = true }.also { socket = it }

                // 1. Unicast trực tiếp tới PC (nếu có IP)
                targetUnicastIp?.let { ip ->
                    if (ip.isNotEmpty() && ip != "127.0.0.1") {
                        try {
                            val pUnicast = DatagramPacket(
                                data, data.size,
                                InetAddress.getByName(ip), SYNC_PORT
                            )
                            sock.send(pUnicast)
                        } catch (_: Exception) {}
                    }
                }

                // 2. Broadcast subnet 192.168.2.255
                try {
                    val pSubnet = DatagramPacket(
                        data, data.size,
                        InetAddress.getByName("192.168.2.255"), SYNC_PORT
                    )
                    sock.send(pSubnet)
                } catch (_: Exception) {}

                // 3. Broadcast toàn mạng 255.255.255.255
                try {
                    val pBroadcast = DatagramPacket(
                        data, data.size,
                        InetAddress.getByName("255.255.255.255"), SYNC_PORT
                    )
                    sock.send(pBroadcast)
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi phát broadcast button event: ${e.message}")
            }
        }
    }
}
