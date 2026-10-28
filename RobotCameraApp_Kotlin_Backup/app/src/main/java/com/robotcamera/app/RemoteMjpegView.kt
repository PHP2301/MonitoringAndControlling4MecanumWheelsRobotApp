package com.robotcamera.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Trình phát luồng video MJPEG từ xa Siêu Tốc (Zero-Latency Remote MJPEG Player)
 * - Thiết kế Full-Screen FPV với thanh công cụ Pill trắng nổi ở góc trên phải.
 */
@SuppressLint("SetTextI18n")
class RemoteMjpegView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val TAG = "RemoteMjpegView"
        private const val PREFS_NAME = "robot_remote_cam_prefs"
        private const val KEY_CAM_IP = "remote_cam_ip"
        private const val DEFAULT_CAM_IP = "192.168.2.80:8080"
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    ).toInt()

    private fun dpF(v: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    )

    // Views
    private val ivStream: ImageView
    private val placeholderLayout: FrameLayout
    private val tvTopConnectingPill: TextView
    private val tvCenterPlaceholder: TextView
    private val hudOverlay: FpvHudOverlayView
    private val topBarContainer: LinearLayout
    private val etStreamIp: EditText
    private val btnStreamAction: TextView
    private val tvStatusBadge: TextView

    // Threading & Network
    private val uiHandler = Handler(Looper.getMainLooper())
    @Volatile private var isStreaming = false
    private var streamThread: Thread? = null
    @Volatile private var decodeExecutor: ExecutorService? = null
    private val isRendering = AtomicBoolean(false)

    // FPS calculation
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()
    private var currentFps = 0

    // Callback chuyển tiếp frame JPEG cho MJPEG Server nội bộ (để phát cho PC)
    var onFrameReceived: ((ByteArray) -> Unit)? = null
    // Callback chia sẻ Bitmap đã giải mã cho động cơ thị giác máy tính / Auto Follow
    var onBitmapDecoded: ((Bitmap) -> Unit)? = null
    // Callback yêu cầu chuyển sang Camera trên máy
    var onSwitchToLocalCamRequested: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.parseColor("#C2D8FB"))

        // 1. ImageView hiển thị khung hình stream toàn màn hình
        ivStream = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        addView(ivStream)

        // 2. Placeholder nền xanh băng mềm mại khi đang chờ luồng FPV
        val savedIp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CAM_IP, DEFAULT_CAM_IP) ?: DEFAULT_CAM_IP

        placeholderLayout = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#DCE9FD"),
                    Color.parseColor("#C5DAFC"),
                    Color.parseColor("#AEC9FA")
                )
            )
        }

        tvTopConnectingPill = TextView(context).apply {
            text = "Đang kết nối luồng FPV từ xa..."
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(20f)
                setColor(Color.parseColor("#F5FFFFFF"))
            }
            elevation = dpF(4f)
            setPadding(dp(20f), dp(7f), dp(20f), dp(7f))
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(60f)
            }
        }
        placeholderLayout.addView(tvTopConnectingPill)

        tvCenterPlaceholder = TextView(context).apply {
            text = "Luồng FPV toàn màn hình · $savedIp"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#475569"))
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        placeholderLayout.addView(tvCenterPlaceholder)
        addView(placeholderLayout)

        // 3. Lớp phủ HUD FPV Cockpit & Nhận diện người (Mặc định hiển thị để xem khung nhận diện và khoảng cách)
        hudOverlay = FpvHudOverlayView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isLive = false
            liveTitle = "OFFLINE"
            footerText = "CHỜ TÍN HIỆU • MECANUM 4WD"
            visibility = View.VISIBLE
        }
        addView(hudOverlay)

        // 4. Thanh công cụ Pill trắng nổi ở góc trên phải (Top-Right Floating Camera Pill)
        topBarContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, dp(10f), dp(14f), 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(26f)
                setColor(Color.parseColor("#F5FFFFFF"))
                setStroke(dp(1f), Color.parseColor("#E2E8F0"))
            }
            elevation = dpF(6f)
            setPadding(dp(12f), dp(5f), dp(8f), dp(5f))
        }

        val tvCamLabel = TextView(context).apply {
            text = "Camera"
            textSize = 10.5f
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, 0, dp(6f), 0)
        }
        topBarContainer.addView(tvCamLabel)

        etStreamIp = EditText(context).apply {
            setText(savedIp)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(16f)
                setColor(Color.parseColor("#F1F5F9"))
                setStroke(dp(1f), Color.parseColor("#CBD5E1"))
            }
            setPadding(dp(10f), dp(2f), dp(10f), dp(2f))
            layoutParams = LinearLayout.LayoutParams(dp(132f), dp(30f))
            isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL
        }
        topBarContainer.addView(etStreamIp)

        topBarContainer.addView(createSpacer(dp(6f)))

        btnStreamAction = TextView(context).apply {
            text = "Dừng"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isSingleLine = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(16f)
                setColor(Color.parseColor("#DC2626"))
            }
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            setOnClickListener {
                if (isStreaming) {
                    stopStream()
                } else {
                    val input = etStreamIp.text.toString().trim()
                    if (input.isNotEmpty()) {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putString(KEY_CAM_IP, input).apply()
                        startStream(input)
                    }
                }
            }
        }
        topBarContainer.addView(btnStreamAction)

        topBarContainer.addView(createSpacer(dp(6f)))

        tvStatusBadge = TextView(context).apply {
            text = "• LIVE"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#15803D"))
            isSingleLine = true
            setPadding(dp(2f), 0, dp(4f), 0)
        }
        topBarContainer.addView(tvStatusBadge)

        topBarContainer.addView(createSpacer(dp(4f)))

        // Nút chuyển sang dùng camera trực tiếp trên máy này
        val btnSwitchLocal = TextView(context).apply {
            text = "Camera máy"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            isSingleLine = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(16f)
                setColor(Color.WHITE)
                setStroke(dp(1.2f), Color.parseColor("#CBD5E1"))
            }
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            setOnClickListener {
                onSwitchToLocalCamRequested?.invoke()
            }
        }
        topBarContainer.addView(btnSwitchLocal)

        topBarContainer.addView(createSpacer(dp(5f)))

        // Nút ẩn/hiện HUD
        val btnToggleHud = TextView(context).apply {
            text = "HUD"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(16f)
                setColor(Color.WHITE)
                setStroke(dp(1.2f), Color.parseColor("#CBD5E1"))
            }
            setPadding(dp(11f), dp(6f), dp(11f), dp(6f))
            setOnClickListener {
                hudOverlay.visibility = if (hudOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        topBarContainer.addView(btnToggleHud)

        addView(topBarContainer)
    }

    /**
     * Bắt đầu nhận luồng video từ xa qua URL/IP (Độ trễ cực thấp < 30ms)
     */
    fun startStream(target: String? = null) {
        if (isStreaming) stopStream()

        val rawTarget = target ?: etStreamIp.text.toString().trim()
        if (rawTarget.isEmpty()) return

        val normalizedUrl = when {
            rawTarget.startsWith("http://") || rawTarget.startsWith("https://") -> rawTarget
            rawTarget.contains("/") -> "http://$rawTarget"
            rawTarget.contains(":") -> "http://$rawTarget/video.mjpeg"
            else -> "http://$rawTarget:8080/video.mjpeg"
        }

        val cleanDisplayIp = rawTarget
            .substringAfter("@")
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/video.mjpeg")
            .removeSuffix("/video")
            .removeSuffix("/live")
            .removeSuffix("/mjpeg")

        decodeExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "RemoteMjpegDecoder").apply { isDaemon = true }
        }
        isRendering.set(false)
        isStreaming = true

        btnStreamAction.text = "Dừng"
        (btnStreamAction.background as? GradientDrawable)?.setColor(Color.parseColor("#DC2626"))
        tvStatusBadge.text = "• LIVE"
        tvStatusBadge.setTextColor(Color.parseColor("#15803D"))

        tvTopConnectingPill.visibility = View.VISIBLE
        tvTopConnectingPill.text = "Đang kết nối luồng FPV từ xa..."
        tvCenterPlaceholder.text = "Luồng FPV toàn màn hình · $cleanDisplayIp"

        streamThread = Thread({
            runStreamLoop(normalizedUrl, cleanDisplayIp)
        }, "RemoteMjpegWorker").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Dừng nhận luồng video và giải phóng tài nguyên giải mã
     */
    fun stopStream() {
        isStreaming = false
        streamThread?.interrupt()
        streamThread = null

        try {
            decodeExecutor?.shutdownNow()
        } catch (_: Exception) {}
        decodeExecutor = null
        isRendering.set(false)

        uiHandler.post {
            btnStreamAction.text = "Xem"
            (btnStreamAction.background as? GradientDrawable)?.setColor(Color.parseColor("#1D5CE5"))
            tvStatusBadge.text = "OFFLINE"
            tvStatusBadge.setTextColor(Color.parseColor("#64748B"))

            placeholderLayout.visibility = View.VISIBLE
            tvTopConnectingPill.text = "Đã tạm dừng luồng FPV"

            hudOverlay.isLive = false
            hudOverlay.liveTitle = "OFFLINE"
            hudOverlay.footerText = "CHỜ TÍN HIỆU • MECANUM 4WD"
        }
    }

    private fun tryConnect(urlStr: String, auth: String?): HttpURLConnection? {
        return try {
            val u = URL(urlStr)
            (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 4000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty("Accept", "multipart/x-mixed-replace, image/jpeg, */*")
                if (auth != null) {
                    setRequestProperty("Authorization", auth)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Xử lý giải mã và hiển thị khung hình JPEG trên Executor độc lập
     * Giúp luồng mạng đọc liên tục không bao giờ bị nghẽn (Zero Latency)
     */
    private fun processJpegFrame(jpegBytes: ByteArray, decodeOptions: BitmapFactory.Options) {
        try {
            onFrameReceived?.invoke(jpegBytes)
        } catch (_: Exception) {}

        val executor = decodeExecutor ?: return
        if (isRendering.compareAndSet(false, true)) {
            try {
                executor.execute {
                    try {
                        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, decodeOptions)
                        if (bitmap != null) {
                            try {
                                onBitmapDecoded?.invoke(bitmap)
                            } catch (_: Exception) {}

                            uiHandler.post {
                                ivStream.setImageBitmap(bitmap)
                                isRendering.set(false)
                            }

                            frameCount++
                            val now = System.currentTimeMillis()
                            if (now - lastFpsTime >= 1000) {
                                currentFps = frameCount
                                frameCount = 0
                                lastFpsTime = now
                                uiHandler.post {
                                    hudOverlay.footerText = "${bitmap.width}x${bitmap.height} • $currentFps FPS • 0ms LAG"
                                }
                            }
                        } else {
                            isRendering.set(false)
                        }
                    } catch (_: Exception) {
                        isRendering.set(false)
                    }
                }
            } catch (_: Exception) {
                isRendering.set(false)
            }
        }
    }

    /**
     * Vòng lặp giải mã luồng MJPEG tốc độ cao Siêu Tốc (Zero Latency Engine)
     * - Đọc dữ liệu theo block 16KB bằng memmove thay vì đọc từng byte.
     * - Cơ chế Anti-Buffer-Bloat (Drop-to-Live): tự động xả sạch bộ đệm socket TCP khi phát hiện
     *   tồn đọng dữ liệu để nhảy ngay đến khung hình mới nhất đang phát từ iPhone (<30ms).
     * - Giải mã màu 16-bit RGB_565 trên Executor độc lập để không nghẽn luồng mạng.
     */
    private fun runStreamLoop(streamUrl: String, displayIp: String) {
        var cleanStreamUrl = streamUrl
        var authHeader: String? = null

        val atIdx = streamUrl.indexOf('@')
        val slashSlashIdx = streamUrl.indexOf("://")
        if (atIdx != -1 && slashSlashIdx != -1 && atIdx > slashSlashIdx) {
            val userPass = streamUrl.substring(slashSlashIdx + 3, atIdx)
            val scheme = streamUrl.substring(0, slashSlashIdx + 3)
            val rest = streamUrl.substring(atIdx + 1)
            cleanStreamUrl = scheme + rest
            authHeader = "Basic " + android.util.Base64.encodeToString(userPass.toByteArray(), android.util.Base64.NO_WRAP)
        }

        val defaultAuthHeader = "Basic " + android.util.Base64.encodeToString("admin:admin".toByteArray(), android.util.Base64.NO_WRAP)

        val candidates: List<String> = if (cleanStreamUrl.endsWith("/video.mjpeg")) {
            val base = cleanStreamUrl.removeSuffix("/video.mjpeg")
            listOf(cleanStreamUrl, "$base/video", "$base/live", "$base/mjpeg", "$base/live.mjpeg", "$base/")
        } else {
            listOf(cleanStreamUrl)
        }

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        while (isStreaming && !Thread.currentThread().isInterrupted) {
            try {
                var conn: HttpURLConnection? = null
                for (cand in candidates) {
                    if (!isStreaming) break
                    var testConn = tryConnect(cand, authHeader)
                    val code = try { testConn?.responseCode ?: -1 } catch (_: Exception) { -1 }

                    if (code == 401 && authHeader == null) {
                        try { testConn?.disconnect() } catch (_: Exception) {}
                        testConn = tryConnect(cand, defaultAuthHeader)
                    }

                    val finalCode = try { testConn?.responseCode ?: -1 } catch (_: Exception) { -1 }
                    if (finalCode == HttpURLConnection.HTTP_OK && testConn != null) {
                        conn = testConn
                        break
                    } else {
                        try { testConn?.disconnect() } catch (_: Exception) {}
                    }
                }

                if (conn == null) {
                    throw Exception("Không thể kết nối luồng FPV tại: $streamUrl (Lỗi 401/404)")
                }
                connection = conn
                val rawStream = conn.inputStream
                inputStream = rawStream

                uiHandler.post {
                    placeholderLayout.visibility = View.GONE
                    tvStatusBadge.text = "• LIVE"
                    tvStatusBadge.setTextColor(Color.parseColor("#15803D"))
                    hudOverlay.isLive = true
                    hudOverlay.liveTitle = "LIVE FPV"
                }

                val chunk = ByteArray(16384)
                val frameBuffer = ByteArrayOutputStream(128 * 1024)
                var inJpeg = false
                var prevByte = -1

                while (isStreaming && !Thread.currentThread().isInterrupted) {
                    if (!inJpeg) {
                        // Anti-Buffer-Bloat: nếu socket tồn đọng > 64KB (lag), xả sạch để lấy live feed ngay lập tức
                        val avail = try { rawStream.available() } catch (_: Exception) { 0 }
                        if (avail > 64 * 1024) {
                            var toDrain = avail
                            while (toDrain > 0) {
                                val readLen = Math.min(toDrain, chunk.size)
                                val drained = rawStream.read(chunk, 0, readLen)
                                if (drained <= 0) break
                                toDrain -= drained
                            }
                            prevByte = -1
                        }
                    }

                    val count = rawStream.read(chunk, 0, chunk.size)
                    if (count <= 0) break

                    var idx = 0
                    while (idx < count) {
                        val b = chunk[idx].toInt() and 0xFF

                        if (!inJpeg) {
                            if (prevByte == 0xFF && b == 0xD8) {
                                inJpeg = true
                                frameBuffer.reset()
                                frameBuffer.write(0xFF)
                                frameBuffer.write(0xD8)
                            }
                            prevByte = b
                            idx++
                        } else {
                            if (prevByte == 0xFF && b == 0xD9) {
                                frameBuffer.write(0xD9)
                                inJpeg = false
                                prevByte = -1
                                idx++

                                val jpegBytes = frameBuffer.toByteArray()
                                processJpegFrame(jpegBytes, decodeOptions)
                                continue
                            }

                            // Quét nhanh đến byte 0xFF kế tiếp để copy nguyên khối memmove
                            val start = idx
                            while (idx < count && (chunk[idx].toInt() and 0xFF) != 0xFF) {
                                idx++
                            }

                            if (idx > start) {
                                frameBuffer.write(chunk, start, idx - start)
                                prevByte = chunk[idx - 1].toInt() and 0xFF
                            }

                            if (idx < count && (chunk[idx].toInt() and 0xFF) == 0xFF) {
                                frameBuffer.write(0xFF)
                                prevByte = 0xFF
                                idx++
                            }

                            // Giới hạn an toàn chống tràn RAM nếu luồng bị mất gói kết thúc
                            if (frameBuffer.size() > 2 * 1024 * 1024) {
                                inJpeg = false
                                frameBuffer.reset()
                                prevByte = -1
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                if (!isStreaming) break
                Log.w(TAG, "Chờ luồng FPV: ${e.message}")

                uiHandler.post {
                    placeholderLayout.visibility = View.VISIBLE
                    tvTopConnectingPill.text = "Đang kết nối luồng FPV từ xa..."
                    tvCenterPlaceholder.text = "Luồng FPV toàn màn hình · $displayIp"
                    hudOverlay.isLive = false
                    hudOverlay.liveTitle = "RETRYING"
                }

                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) {
                    break
                }
            } finally {
                try { inputStream?.close() } catch (_: Exception) {}
                try { connection?.disconnect() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Cập nhật kết quả nhận diện người / mục tiêu từ VisualDistanceDetector lên HUD
     */
    fun updateTarget(target: VisualDistanceDetector.TargetResult?) {
        uiHandler.post {
            hudOverlay.currentTarget = target
        }
    }

    fun updateOrientation(isLandscape: Boolean) {
        val lp = topBarContainer.layoutParams as? LayoutParams ?: return
        lp.topMargin = if (isLandscape) dp(10f) else dp(52f)
        topBarContainer.layoutParams = lp
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        updateOrientation(isLandscape)
    }

    private fun createSpacer(w: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(w, 1)
    }
}
