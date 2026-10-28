package com.robotcamera.app

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.ImageProxy
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * MJPEG HTTP Server Siêu Tốc (Zero-Latency / Anti-Buffer-Bloat) cho Robot Camera
 */
class MjpegServer(port: Int = 8080) : NanoHTTPD(port) {

    data class FramePacket(val bytes: ByteArray, val timestamp: Long)

    interface CommandListener {
        fun onToggleTorch(): Boolean
        fun onSwitchCamera()
        fun onSetZoom(ratio: Float)
        fun onTakePhoto()
        fun onStartRecord()
        fun onStopRecord()
    }

    var commandListener: CommandListener? = null
    var onFrameBitmap: ((Bitmap) -> Unit)? = null

    // Lưu frame JPEG mới nhất cho ảnh snapshot
    @Volatile
    private var latestFrame: ByteArray? = null

    // Danh sách hàng đợi của từng client (Mỗi client có hàng đợi dung lượng 1 để chống đọng dữ liệu)
    private val clientQueues = CopyOnWriteArrayList<ArrayBlockingQueue<FramePacket>>()
    private val activeStreams = CopyOnWriteArrayList<ZeroLatencyMjpegInputStream>()

    // Chất lượng JPEG (1 - 100), mặc định 50% để tối ưu độ trễ siêu tốc và chống nghẽn WiFi
    var jpegQuality: Int = 50

    // Độ phân giải đích xuất ra client (chuẩn hóa 16:9 chống bè hình)
    var targetOutputResolution: Size? = null

    // Bộ nhớ đệm tái sử dụng chống Garbage Collection (GC) giật lag
    private val reusableBaos = ByteArrayOutputStream(64 * 1024)

    // Đo FPS thời gian thực
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()
    @Volatile
    var currentFps: Int = 0
        private set

    override fun createClientHandler(finalAccept: Socket, inputStream: InputStream): ClientHandler {
        try {
            // Tối ưu socket mạng: Gửi tức thì không chờ Nagle, giới hạn buffer send 64KB để không bao giờ tồn đọng dữ liệu cũ!
            finalAccept.tcpNoDelay = true
            finalAccept.sendBufferSize = 64 * 1024
            finalAccept.receiveBufferSize = 32 * 1024
            finalAccept.inetAddress?.hostAddress?.let { ip ->
                ButtonSyncManager.targetUnicastIp = ip
            }
        } catch (_: Exception) {}
        return super.createClientHandler(finalAccept, inputStream)
    }

    /**
     * Nhận frame từ CameraX ImageAnalysis
     * Xoay đúng chiều, chuẩn hóa tỷ lệ 16:9, nén JPEG siêu tốc và đẩy cho các client
     */
    fun pushFrame(image: ImageProxy) {
        try {
            val rotationDegrees = image.imageInfo.rotationDegrees
            val bitmap = image.toBitmap()

            var finalBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) bitmap.recycle()
                rotated
            } else {
                bitmap
            }

            // 1. Chuẩn hóa tỷ lệ: Đảm bảo hình ảnh luôn là 16:9 (khi ngang) hoặc 9:16 (khi dọc)
            // Triệt tiêu hoàn toàn lỗi màn hình bị bè ngang hoặc đen 2 bên
            val isLandscape = finalBitmap.width >= finalBitmap.height
            val currentRatio = finalBitmap.width.toFloat() / finalBitmap.height.toFloat()

            if (isLandscape && currentRatio < 1.65f) {
                // Nếu nguồn là 4:3 (1.33) -> Cắt tâm về chuẩn 16:9 (1.778)
                val targetHeight = (finalBitmap.width * 9) / 16
                if (targetHeight < finalBitmap.height) {
                    val cropY = (finalBitmap.height - targetHeight) / 2
                    val cropped = Bitmap.createBitmap(finalBitmap, 0, cropY, finalBitmap.width, targetHeight)
                    if (cropped != finalBitmap) {
                        finalBitmap.recycle()
                        finalBitmap = cropped
                    }
                }
            } else if (!isLandscape && currentRatio > 0.62f) {
                // Nếu nguồn là 3:4 (0.75) -> Cắt tâm về chuẩn 9:16 (0.5625)
                val targetWidth = (finalBitmap.height * 9) / 16
                if (targetWidth < finalBitmap.width) {
                    val cropX = (finalBitmap.width - targetWidth) / 2
                    val cropped = Bitmap.createBitmap(finalBitmap, cropX, 0, targetWidth, finalBitmap.height)
                    if (cropped != finalBitmap) {
                        finalBitmap.recycle()
                        finalBitmap = cropped
                    }
                }
            }

            // 2. Thu nhỏ độ phân giải theo targetOutputResolution nếu cần (VD: 480p 854x480)
            val targetRes = targetOutputResolution
            if (targetRes != null) {
                val targetW = if (isLandscape) targetRes.width else targetRes.height
                val targetH = if (isLandscape) targetRes.height else targetRes.width
                if (finalBitmap.width > targetW || finalBitmap.height > targetH) {
                    val scaled = Bitmap.createScaledBitmap(finalBitmap, targetW, targetH, true)
                    if (scaled != finalBitmap) {
                        finalBitmap.recycle()
                        finalBitmap = scaled
                    }
                }
            }

            try {
                onFrameBitmap?.invoke(finalBitmap)
            } catch (_: Exception) {}

            val jpegBytes = synchronized(reusableBaos) {
                reusableBaos.reset()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, reusableBaos)
                reusableBaos.toByteArray()
            }

            latestFrame = jpegBytes
            val now = System.currentTimeMillis()
            val packet = FramePacket(jpegBytes, now)

            // Tính toán FPS
            frameCount++
            if (now - lastFpsTime >= 1000) {
                currentFps = frameCount
                frameCount = 0
                lastFpsTime = now
            }

            // Đẩy frame tới từng client theo cơ chế ZERO-LATENCY:
            // Nếu client chưa kịp đọc frame trước -> XÓA SẠCH FRAME CŨ, NẠP FRAME MỚI NHẤT!
            for (queue in clientQueues) {
                while (queue.remainingCapacity() == 0) {
                    queue.poll() // Bỏ frame cũ bị nghẽn
                }
                queue.offer(packet)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    fun getClientCount(): Int = clientQueues.size

    fun getLatestSnapshot(): ByteArray? = latestFrame

    /**
     * Đẩy trực tiếp mảng byte JPEG (dùng khi nhận luồng FPV từ xa chuyển tiếp cho PC)
     */
    fun pushJpegBytes(jpegBytes: ByteArray) {
        try {
            latestFrame = jpegBytes
            val now = System.currentTimeMillis()
            val packet = FramePacket(jpegBytes, now)

            frameCount++
            if (now - lastFpsTime >= 1000) {
                currentFps = frameCount
                frameCount = 0
                lastFpsTime = now
            }

            for (queue in clientQueues) {
                while (queue.remainingCapacity() == 0) {
                    queue.poll()
                }
                queue.offer(packet)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val params = session.parms

        return when {
            uri == "/video" || uri == "/video.mjpeg" || uri == "/stream.mjpeg" || uri == "/" -> serveZeroLatencyMjpegStream()

            uri == "/shot.jpg" || uri == "/photo.jpg" -> serveSnapshot()

            uri == "/status" -> newFixedLengthResponse(
                Response.Status.OK, "application/json",
                """{"app":"RobotCameraApp","status":"streaming","fps":$currentFps,"clients":${clientQueues.size},"quality":$jpegQuality}"""
            )

            uri == "/torch" -> {
                val on = commandListener?.onToggleTorch() ?: false
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"torch":$on}""")
            }

            uri == "/switch" -> {
                commandListener?.onSwitchCamera()
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"result":"switched"}""")
            }

            uri == "/zoom" -> {
                val ratio = params["ratio"]?.toFloatOrNull() ?: 1.0f
                commandListener?.onSetZoom(ratio)
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"zoom":$ratio}""")
            }

            uri == "/snap" -> {
                commandListener?.onTakePhoto()
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"result":"captured"}""")
            }

            uri == "/record/start" -> {
                commandListener?.onStartRecord()
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"result":"recording"}""")
            }

            uri == "/record/stop" -> {
                commandListener?.onStopRecord()
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"result":"stopped"}""")
            }

            uri == "/gimbal/move" -> {
                val pan = params["pan"]?.toFloatOrNull() ?: 0f
                val tilt = params["tilt"]?.toFloatOrNull() ?: 0f
                GimbalBleManager.setPanTilt(pan, tilt)
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"gimbal":"move","pan":$pan,"tilt":$tilt}""")
            }

            uri == "/gimbal/recenter" -> {
                GimbalBleManager.recenter()
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"gimbal":"recenter"}""")
            }

            uri == "/gimbal/mode" -> {
                val modeStr = params["m"] ?: ""
                val mode = if (modeStr.isNotEmpty()) GimbalBleManager.FollowMode.fromLabel(modeStr) else GimbalBleManager.currentFollowMode.next()
                GimbalBleManager.setFollowMode(mode)
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"gimbal":"mode","mode":"${mode.label}"}""")
            }

            uri == "/gimbal/status" -> {
                newFixedLengthResponse(
                    Response.Status.OK, "application/json",
                    """{"connected":${GimbalBleManager.isConnected},"device":"${GimbalBleManager.connectedDeviceName}","mode":"${GimbalBleManager.currentFollowMode.label}"}"""
                )
            }

            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"
            )
        }
    }

    /**
     * Tạo luồng Stream MJPEG thời gian thực không bộ đệm (Zero Latency)
     */
    private fun serveZeroLatencyMjpegStream(): Response {
        val clientQueue = ArrayBlockingQueue<FramePacket>(1)
        clientQueues.add(clientQueue)

        // Đẩy frame hiện tại vào để client thấy hình ảnh ngay tức thì
        latestFrame?.let { clientQueue.offer(FramePacket(it, System.currentTimeMillis())) }

        var streamRef: ZeroLatencyMjpegInputStream? = null
        val stream = ZeroLatencyMjpegInputStream(clientQueue) {
            clientQueues.remove(clientQueue)
            streamRef?.let { activeStreams.remove(it) }
        }
        streamRef = stream
        activeStreams.add(stream)

        val response = newChunkedResponse(
            Response.Status.OK,
            "multipart/x-mixed-replace; boundary=frame",
            stream
        )
        response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate, pre-check=0, post-check=0, max-age=0")
        response.addHeader("Pragma", "no-cache")
        response.addHeader("Expires", "0")
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Connection", "close")
        return response
    }

    private fun serveSnapshot(): Response {
        val frame = latestFrame
        return if (frame != null) {
            newFixedLengthResponse(
                Response.Status.OK, "image/jpeg",
                frame.inputStream(), frame.size.toLong()
            )
        } else {
            newFixedLengthResponse(
                Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT,
                "Camera chưa sẵn sàng"
            )
        }
    }

    fun cleanup() {
        for (s in activeStreams) {
            try { s.closeStream() } catch (_: Exception) {}
        }
        activeStreams.clear()
        clientQueues.clear()
        latestFrame = null
    }

    override fun stop() {
        cleanup()
        super.stop()
    }

    /**
     * InputStream tự tạo đóng gói trực tiếp MJPEG boundary từ hàng đợi nạp mới nhất
     */
    private inner class ZeroLatencyMjpegInputStream(
        private val queue: ArrayBlockingQueue<FramePacket>,
        private val onCloseCallback: () -> Unit
    ) : InputStream() {

        private var currentBuffer: ByteArray? = null
        private var bufferPos = 0
        @Volatile
        private var isClosed = false

        fun closeStream() {
            isClosed = true
            queue.clear()
        }

        override fun read(): Int {
            if (isClosed) return -1
            if (!prepareBuffer()) return -1
            return currentBuffer!![bufferPos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (isClosed) return -1
            if (!prepareBuffer()) return -1
            val available = currentBuffer!!.size - bufferPos
            val toRead = Math.min(len, available)
            System.arraycopy(currentBuffer!!, bufferPos, b, off, toRead)
            bufferPos += toRead
            return toRead
        }

        private fun prepareBuffer(): Boolean {
            while (!isClosed && (currentBuffer == null || bufferPos >= currentBuffer!!.size)) {
                try {
                    if (isClosed) return false
                    // Chờ frame mới tối đa 500ms; khi camera hoạt động bình thường ở 30fps sẽ nhận ngay trong 33ms
                    var packet = queue.poll(500, TimeUnit.MILLISECONDS)
                    if (isClosed) return false
                    if (packet == null) {
                        // Nếu camera tạm thời chưa có frame (đổi góc, chuyển cam), gửi lại frame gần nhất để duy trì socket HTTP
                        val fallback = latestFrame
                        if (fallback != null && !isClosed) {
                            packet = FramePacket(fallback, System.currentTimeMillis())
                        } else {
                            if (isClosed) return false
                            continue
                        }
                    }

                    // Lấy frame mới nhất trong hàng đợi (bỏ frame cũ nếu có dồn toa)
                    while (!queue.isEmpty()) {
                        packet = queue.poll() ?: packet
                    }

                    val nextFrame = packet.bytes
                    val header = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${nextFrame.size}\r\n\r\n"
                    val headerBytes = header.toByteArray(Charsets.US_ASCII)
                    val footerBytes = "\r\n".toByteArray(Charsets.US_ASCII)

                    val packetBytes = ByteArray(headerBytes.size + nextFrame.size + footerBytes.size)
                    System.arraycopy(headerBytes, 0, packetBytes, 0, headerBytes.size)
                    System.arraycopy(nextFrame, 0, packetBytes, headerBytes.size, nextFrame.size)
                    System.arraycopy(footerBytes, 0, packetBytes, headerBytes.size + nextFrame.size, footerBytes.size)

                    currentBuffer = packetBytes
                    bufferPos = 0
                } catch (_: InterruptedException) {
                    return false
                }
            }
            return !isClosed
        }

        override fun close() {
            isClosed = true
            super.close()
            onCloseCallback()
        }
    }
}
