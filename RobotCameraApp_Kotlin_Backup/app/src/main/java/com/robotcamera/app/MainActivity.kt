package com.robotcamera.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.*
import android.view.OrientationEventListener
import android.view.Surface
import android.content.res.Configuration
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.core.widget.NestedScrollView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Robot Camera App - Giao diện Samsung Galaxy Camera (One UI) chuẩn 100%
 *
 * Tính năng thiết kế:
 * 1. Viewfinder chuẩn 4:3 với Lưới 3x3 và Thước cân bằng chân trời (Level Indicator) màu vàng ở tâm.
 * 2. Nút Scan thông minh [T] màu vàng ở góc phải màn hình xem trước (Bấm là xem & copy IP Stream).
 * 3. Cụm chọn Zoom Segmented Pill: [.5], [1x], [2] với chip tròn trắng nổi bật + nút hiệu ứng 4 chấm.
 * 4. Dải chọn chế độ rút gọn ĐÚNG YÊU CẦU: Chỉ có 2 chế độ [ẢNH] và [VIDEO].
 * 5. Cụm điều khiển chính:
 *    - Trái: Vòng tròn xem ảnh thư viện (Gallery Thumbnail).
 *    - Giữa: Nút Shutter cơ học (Trắng tinh tế khi ở [ẢNH] / Đỏ quay phim khi ở [VIDEO]).
 *    - Phải: Nút đảo Camera trước/sau xoay 180°.
 * 6. Vẫn giữ nguyên máy chủ phát MJPEG Server 8080 kết nối siêu mượt với app điều khiển xe Robot trên PC!
 *
 * Ghi chú cấu trúc: MainActivity được tách thành nhiều file (extension functions):
 *  - MainActivity.kt: Trạng thái (fields), vòng đời Activity, chọn vai trò, đồng bộ nút PC, CommandListener.
 *  - MainActivityController.kt: Vai trò THIẾT BỊ ĐIỀU KHIỂN: bố cục chia đôi Camera/Bảng lái, thanh UDP góc trái, thanh Camera IP góc phải.
 *  - MainActivityStreamer.kt: Vai trò THIẾT BỊ STREAMING: máy chủ MJPEG 8080, IP WiFi, chia sẻ link stream.
 *  - MainActivityLayout.kt: Dựng giao diện gốc (setupUI) và các cụm nút của màn hình Camera (Top bar, Zoom, Shutter...).
 *  - MainActivityCamera.kt: CameraX: khởi động camera, chụp ảnh, quay video, zoom, lấy nét, flash, xoay màn hình, quyền truy cập.
 *  - MainActivitySettings.kt: Hộp thoại Cài đặt (chế độ độ trễ, chất lượng, khóa xoay màn hình).
 *  - MainActivityUiHelpers.kt: Tiện ích giao diện dùng chung: dp(), spacer, hiệu ứng chạm lò xo, chớp màn trập.
 */
class MainActivity : AppCompatActivity(), MjpegServer.CommandListener {

    companion object {
        internal const val TAG = "RobotCamera"
        internal const val REQUEST_CODE_PERMISSIONS = 10
        internal const val STREAM_PORT = 8080

        internal val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        }
    }

    enum class AppRole {
        NONE, CONTROLLER, STREAMER
    }
    internal var currentRole = AppRole.NONE

    internal lateinit var roleSelectionView: RoleSelectionView
    internal lateinit var remoteMjpegView: RemoteMjpegView

    enum class CameraMode {
        PHOTO, VIDEO, CONTROL
    }

    // CameraX components
    internal var camera: Camera? = null
    internal var preview: Preview? = null
    internal var imageCapture: ImageCapture? = null
    internal var imageAnalysis: ImageAnalysis? = null
    internal var videoCapture: VideoCapture<Recorder>? = null
    internal var recording: Recording? = null
    internal var cameraProvider: ProcessCameraProvider? = null
    internal var lensFacing = CameraSelector.LENS_FACING_BACK
    internal var targetResolution = Size(1280, 720)

    // Chế độ xoay màn hình
    enum class ScreenOrientationOption {
        AUTO,           // Tự động xoay theo cảm biến
        LANDSCAPE,      // Khóa xoay ngang 16:9 (Tối ưu cho xe Robot)
        PORTRAIT        // Khóa xoay dọc 9:16
    }
    internal var orientationOption = ScreenOrientationOption.AUTO

    // Xoay màn hình tự động (Orientation sensor)
    internal var orientationEventListener: OrientationEventListener? = null
    internal var currentTargetRotation = Surface.ROTATION_0

    // MJPEG Stream Server
    internal var mjpegServer: MjpegServer? = null
    internal var isStreaming = false
    internal val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Mode hiện tại: Mặc định là ẢNH
    internal var currentMode = CameraMode.PHOTO
    internal var isRecordingVideo = false
    internal var recordingStartTime = 0L

    // UI Root
    internal lateinit var rootLayout: RelativeLayout
    internal lateinit var previewView: PreviewView
    internal lateinit var viewfinderOverlay: ViewfinderOverlayView
    internal lateinit var focusReticle: View
    internal lateinit var shutterFlashOverlay: View
    internal lateinit var fpvHudOverlay: FpvHudOverlayView
    internal lateinit var overlayContainer: RelativeLayout

    // Top Bar Controls
    internal lateinit var topBarLayout: LinearLayout
    internal lateinit var tvFlashIcon: TextView
    internal lateinit var tvResolutionBadge: TextView
    internal lateinit var btnMotionLiveIcon: View
    internal lateinit var tvMotionLiveText: TextView
    internal lateinit var tvRecTimerBadge: TextView
    internal lateinit var tvIpDisplayBadge: TextView
    internal lateinit var ipBadgeContainer: View
    internal lateinit var tvGimbalStatusBadge: TextView
    internal var isTorchOn = false

    // Zoom Controls
    internal lateinit var zoomContainer: LinearLayout
    internal lateinit var zoomPillContainer: LinearLayout
    internal val zoomChips = mutableMapOf<Float, TextView>()
    internal var currentZoomRatio = 1.0f

    // Smart Scan Button [T]
    internal lateinit var btnSmartScan: View

    // Bottom Action Controls
    internal lateinit var mainActionRow: RelativeLayout
    internal lateinit var btnGalleryThumbnail: ImageView
    internal lateinit var shutterContainer: RelativeLayout
    internal lateinit var shutterCore: View
    internal lateinit var btnSwitchCamera: View
    internal lateinit var ivSwitchCameraIcon: ImageView

    // Mode Selector Texts
    internal lateinit var tvModePhoto: TextView
    internal lateinit var tvModeVideo: TextView
    internal lateinit var tvModeControl: TextView

    // Robot UDP Control Dashboard
    internal lateinit var udpManager: RobotUdpManager
    internal lateinit var autoFollowManager: AutoFollowManager
    internal lateinit var visualDistanceDetector: VisualDistanceDetector
    internal lateinit var controlDashboardView: RobotControlDashboardView

    // Controller Mode Camera Components
    internal lateinit var controllerLocalCamBar: LinearLayout
    internal lateinit var tvControllerLocalIp: TextView
    internal var controllerCamSource: String = "LOCAL" // "LOCAL" or "REMOTE"

    // Controller Mode Top-Left Bar & UDP Controls
    internal lateinit var controllerTopLeftBar: LinearLayout
    internal lateinit var etUdpAddress: EditText
    internal lateinit var btnConnectUdp: TextView
    internal lateinit var tvUdpStatusBadge: TextView

    // Gestures
    internal lateinit var scaleGestureDetector: ScaleGestureDetector
    internal lateinit var gestureDetector: GestureDetector

    // Handlers
    internal val uiHandler = Handler(Looper.getMainLooper())
    internal val videoTimerRunnable = object : Runnable {
        override fun run() {
            if (isRecordingVideo) {
                val elapsed = (SystemClock.elapsedRealtime() - recordingStartTime) / 1000
                val mins = elapsed / 60
                val secs = elapsed % 60
                tvRecTimerBadge.text = String.format("● REC %02d:%02d", mins, secs)
                uiHandler.postDelayed(this, 1000)
            }
        }
    }


    // =========================================================================
    // Kiểm tra lateinit (dùng cho các file extension MainActivity*.kt)
    // =========================================================================
    internal val autoFollowManagerInitialized: Boolean get() = ::autoFollowManager.isInitialized
    internal val btnConnectUdpInitialized: Boolean get() = ::btnConnectUdp.isInitialized
    internal val btnSmartScanInitialized: Boolean get() = ::btnSmartScan.isInitialized
    internal val controlDashboardViewInitialized: Boolean get() = ::controlDashboardView.isInitialized
    internal val controllerLocalCamBarInitialized: Boolean get() = ::controllerLocalCamBar.isInitialized
    internal val controllerTopLeftBarInitialized: Boolean get() = ::controllerTopLeftBar.isInitialized
    internal val etUdpAddressInitialized: Boolean get() = ::etUdpAddress.isInitialized
    internal val fpvHudOverlayInitialized: Boolean get() = ::fpvHudOverlay.isInitialized
    internal val ipBadgeContainerInitialized: Boolean get() = ::ipBadgeContainer.isInitialized
    internal val remoteMjpegViewInitialized: Boolean get() = ::remoteMjpegView.isInitialized
    internal val tvControllerLocalIpInitialized: Boolean get() = ::tvControllerLocalIp.isInitialized
    internal val tvGimbalStatusBadgeInitialized: Boolean get() = ::tvGimbalStatusBadge.isInitialized
    internal val tvIpDisplayBadgeInitialized: Boolean get() = ::tvIpDisplayBadge.isInitialized
    internal val tvModeControlInitialized: Boolean get() = ::tvModeControl.isInitialized
    internal val tvMotionLiveTextInitialized: Boolean get() = ::tvMotionLiveText.isInitialized
    internal val tvUdpStatusBadgeInitialized: Boolean get() = ::tvUdpStatusBadge.isInitialized
    internal val visualDistanceDetectorInitialized: Boolean get() = ::visualDistanceDetector.isInitialized
    internal val zoomContainerInitialized: Boolean get() = ::zoomContainer.isInitialized

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

        setupUI()
        setupGestures()
        setupButtonSyncListener()
        checkInitialRole()
    }

    internal fun checkInitialRole() {
        val prefs = getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE)
        val remember = prefs.getBoolean("pref_remember_role", false)
        val savedRoleStr = prefs.getString("pref_saved_role", null)
        if (remember && savedRoleStr != null) {
            val role = if (savedRoleStr == "CONTROLLER") AppRole.CONTROLLER else AppRole.STREAMER
            applyRole(role, true)
        } else {
            showRoleSelection()
        }
    }

    fun showRoleSelection() {
        currentRole = AppRole.NONE

        // 1. Dừng stream từ xa nếu đang chạy
        if (::remoteMjpegView.isInitialized) {
            remoteMjpegView.stopStream()
            remoteMjpegView.visibility = View.GONE
        }

        // 2. Dừng CameraX và Server MJPEG nếu đang chạy
        stopStreaming()
        try {
            cameraProvider?.unbindAll()
            camera = null
        } catch (_: Exception) {}

        // 3. Khôi phục xoay tự do
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

        // 4. Ẩn toàn bộ các giao diện con
        if (::previewView.isInitialized) previewView.visibility = View.GONE
        if (::viewfinderOverlay.isInitialized) viewfinderOverlay.visibility = View.GONE
        if (::overlayContainer.isInitialized) overlayContainer.visibility = View.GONE
        if (::controlDashboardView.isInitialized) controlDashboardView.visibility = View.GONE
        if (::fpvHudOverlay.isInitialized) fpvHudOverlay.visibility = View.GONE
        if (::controllerLocalCamBar.isInitialized) controllerLocalCamBar.visibility = View.GONE
        if (::controllerTopLeftBar.isInitialized) controllerTopLeftBar.visibility = View.GONE
        if (::udpManager.isInitialized) udpManager.disconnect()

        // 5. Hiển thị màn hình chọn vai trò
        if (::roleSelectionView.isInitialized) {
            val prefs = getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE)
            val wasRemember = prefs.getBoolean("pref_remember_role", false)
            roleSelectionView.setRememberChecked(wasRemember)
            roleSelectionView.visibility = View.VISIBLE
            roleSelectionView.bringToFront()
        }
    }

    internal fun applyRole(role: AppRole, remember: Boolean) {
        currentRole = role
        val prefs = getSharedPreferences("robot_app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("pref_remember_role", remember)
            .putString("pref_saved_role", if (role == AppRole.CONTROLLER) "CONTROLLER" else "STREAMER")
            .apply()

        if (::roleSelectionView.isInitialized) {
            roleSelectionView.visibility = View.GONE
        }

        if (role == AppRole.CONTROLLER) {
            startControllerRole()
        } else {
            startStreamerRole()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentRole != AppRole.NONE) {
            showRoleSelection()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    internal var buttonSyncListener: ((String, String, String?) -> Unit)? = null

    internal fun setupButtonSyncListener() {
        buttonSyncListener?.let { ButtonSyncManager.removeListener(it) }
        val listener: (String, String, String?) -> Unit = { btnId, action, extra ->
            // Nếu máy này đang nối Bluetooth BLE với Gimbal RS 3 Mini -> Chuyển tiếp lệnh ngay lập tức
            if (btnId.startsWith("GIMBAL_")) {
                GimbalBleManager.handleIncomingNetworkCommand(btnId, action, extra)
            }
            runOnUiThread {
                when (btnId) {
                    "TAKE_PHOTO" -> {
                        if (currentMode != CameraMode.PHOTO && currentMode != CameraMode.CONTROL) {
                            switchMode(CameraMode.PHOTO)
                        }
                        triggerShutterFlash()
                        takePhoto()
                        Toast.makeText(this@MainActivity, "PC vừa kích hoạt Chụp ảnh!", Toast.LENGTH_SHORT).show()
                    }
                    "START_RECORD" -> {
                        if (currentMode != CameraMode.VIDEO && currentMode != CameraMode.CONTROL) {
                            switchMode(CameraMode.VIDEO)
                        }
                        if (!isRecordingVideo) {
                            startVideoRecording()
                            Toast.makeText(this@MainActivity, "PC vừa kích hoạt Bắt đầu quay video!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "STOP_RECORD" -> {
                        if (isRecordingVideo) {
                            stopVideoRecording()
                            Toast.makeText(this@MainActivity, "PC vừa dừng quay video!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "SWITCH_CAMERA" -> {
                        if (::ivSwitchCameraIcon.isInitialized) {
                            ivSwitchCameraIcon.animate().rotationBy(180f).setDuration(280).start()
                        }
                        switchCamera()
                    }
                    "START_STREAM" -> {
                        if (!isStreaming || mjpegServer == null || mjpegServer?.isAlive != true) {
                            startStreaming()
                        }
                        Toast.makeText(this@MainActivity, "PC vừa bật Live Stream!", Toast.LENGTH_SHORT).show()
                    }
                    "STOP_STREAM" -> {
                        if (isStreaming) {
                            stopStreaming()
                        }
                        Toast.makeText(this@MainActivity, "PC vừa dừng Live Stream!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        buttonSyncListener = listener
        ButtonSyncManager.addListener(listener)
    }

    override fun onResume() {
        super.onResume()
        if (currentRole == AppRole.STREAMER) {
            viewfinderOverlay.startSensors()
            orientationEventListener?.enable()
            updateIpDisplay()

            // Tự động kiểm tra và phục hồi CameraX / MJPEG Server nếu bị gián đoạn do thoát app tạm thời
            if (allPermissionsGranted()) {
                if (cameraProvider == null || camera == null) {
                    Log.i(TAG, "onResume: Tự động phục hồi CameraX...")
                    startCamera()
                } else if (!isStreaming || mjpegServer == null || mjpegServer?.isAlive != true) {
                    Log.i(TAG, "onResume: Tự động phục hồi MJPEG Stream Server...")
                    startStreaming()
                }
            }
        } else if (currentRole == AppRole.CONTROLLER) {
            if (controllerCamSource == "REMOTE") {
                if (::remoteMjpegView.isInitialized) {
                    remoteMjpegView.startStream()
                }
                if (!isStreaming || mjpegServer == null || mjpegServer?.isAlive != true) {
                    startStreaming()
                }
            } else {
                updateControllerLocalIpDisplay()
                if (allPermissionsGranted()) {
                    if (cameraProvider == null || camera == null) {
                        startCamera()
                    }
                    if (!isStreaming || mjpegServer == null || mjpegServer?.isAlive != true) {
                        startStreaming()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (currentRole == AppRole.STREAMER) {
            viewfinderOverlay.stopSensors()
            orientationEventListener?.disable()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (currentRole == AppRole.NONE) {
            if (::roleSelectionView.isInitialized) {
                roleSelectionView.refreshLayout()
            }
            return
        }
        if (currentRole == AppRole.CONTROLLER) {
            rootLayout.post { updateControllerLayoutDimensions() }
            return
        }
        if (currentRole != AppRole.STREAMER) return
        if (orientationOption != ScreenOrientationOption.AUTO) return

        val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

        if (displayRotation != currentTargetRotation) {
            val wasLandscape = (currentTargetRotation == Surface.ROTATION_90 || currentTargetRotation == Surface.ROTATION_270)
            val isLandscape = (displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270)

            currentTargetRotation = displayRotation

            if (wasLandscape != isLandscape) {
                // Đổi giữa chế độ Đứng và Ngang: Rebind camera để phân bổ buffer 16:9 / 9:16 chuẩn xác
                startCamera()
            } else {
                try {
                    preview?.targetRotation = displayRotation
                    imageAnalysis?.targetRotation = displayRotation
                    imageCapture?.targetRotation = displayRotation
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi onConfigurationChanged: ${e.message}")
                }
            }
        }
    }

    // =========================================================================
    // XÂY DỰNG GIAO DIỆN SAMSUNG ONE UI CAMERA CHUẨN XÁC
    // =========================================================================

    // =========================================================================
    // XỬ LÝ CHUYỂN ĐỔI CHẾ ĐỘ CAMERA: ẢNH <-> VIDEO <-> ĐIỀU KHIỂN
    // =========================================================================

    // =========================================================================
    // HỘP THOẠI POPUP THÔNG TIN STREAM ROBOT & SAO CHÉP IP
    // =========================================================================

    // =========================================================================
    // TOUCH ANIMATIONS & GESTURES
    // =========================================================================

    // =========================================================================
    // ZOOM CONTROL
    // =========================================================================

    // =========================================================================
    // KHỞI CHẠY CAMERA VÀ MJPEG STREAM
    // =========================================================================

    // =========================================================================
    // CHỤP ẢNH TĨNH VÀ CẬP NHẬT GALLERY THUMBNAIL
    // =========================================================================

    // =========================================================================
    // MJPEG STREAMING SERVER
    // =========================================================================

    // =========================================================================
    // COMMAND LISTENER
    // =========================================================================

    override fun onToggleTorch(): Boolean {
        var state = false
        runOnUiThread { state = toggleTorch() }
        return state
    }

    override fun onSwitchCamera() {
        runOnUiThread {
            if (::ivSwitchCameraIcon.isInitialized) {
                ivSwitchCameraIcon.animate().rotationBy(180f).setDuration(280).start()
            }
            switchCamera()
        }
    }

    override fun onSetZoom(ratio: Float) {
        runOnUiThread { selectZoomRatio(ratio) }
    }

    override fun onTakePhoto() {
        runOnUiThread {
            if (currentMode != CameraMode.PHOTO && currentMode != CameraMode.CONTROL) {
                switchMode(CameraMode.PHOTO)
            }
            triggerShutterFlash()
            takePhoto()
        }
    }

    override fun onStartRecord() {
        runOnUiThread {
            if (currentMode != CameraMode.VIDEO && currentMode != CameraMode.CONTROL) {
                switchMode(CameraMode.VIDEO)
            }
            if (!isRecordingVideo) {
                startVideoRecording()
            }
        }
    }

    override fun onStopRecord() {
        runOnUiThread {
            if (isRecordingVideo) {
                stopVideoRecording()
            }
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                if (currentRole == AppRole.STREAMER || (currentRole == AppRole.CONTROLLER && controllerCamSource == "LOCAL")) {
                    startCamera()
                }
            } else {
                Toast.makeText(this, "Ứng dụng cần cấp quyền Camera & Micro để hoạt động!", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecordingVideo || recording != null) {
            stopVideoRecording()
        }
        buttonSyncListener?.let { ButtonSyncManager.removeListener(it) }
        buttonSyncListener = null
        if (::udpManager.isInitialized) {
            udpManager.disconnect()
        }
        if (::visualDistanceDetector.isInitialized) {
            visualDistanceDetector.close()
        }
        orientationEventListener?.disable()
        uiHandler.removeCallbacks(videoTimerRunnable)
        stopStreaming()
        cameraExecutor.shutdown()
    }
}
