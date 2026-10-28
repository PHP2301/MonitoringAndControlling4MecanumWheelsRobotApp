// =========================================================================================
// MODULE: TAY CẦM ĐIỀU KHIỂN (GAMEPAD / XBOX 360 CONTROLLER)
// DỰ ÁN: MONITORING AND CONTROLLING 4 MECANUM WHEELS ROBOT APP (C# WINFORMS)
// =========================================================================================
// Tính năng này được tách riêng do không nằm trong phạm vi đặc tả yêu cầu Nhiệm vụ 3 ban đầu.
// Sử dụng thư viện SharpDX.XInput để giao tiếp với tay cầm Xbox 360 / Xbox One / Gamepad tương thích.
// Chu kỳ quét trạng thái: 40Hz (25ms), độ trễ 0ms, không phụ thuộc kết nối UDP.
// =========================================================================================

using System;
using System.Drawing;
using System.Windows.Forms;
using SharpDX.XInput;

namespace MonitoringAndControllingRobotsApp
{
    /// <summary>
    /// Partial class bổ sung tính năng điều khiển xe Mecanum bằng tay cầm Gamepad
    /// </summary>
    public partial class Form1
    {
        // ---------------------------------------------------------------------------------
        // KHAI BÁO BIẾN ĐIỀU KHIỂN GAMEPAD (SHARPDX.XINPUT)
        // ---------------------------------------------------------------------------------
        private Controller _controller = null;
        private bool _isControllerConnected = false;
        private System.Windows.Forms.Timer gamepadTimer;
        private int _gamepadScanCounter = 0;
        private const int DEADZONE = 7000;              // Vùng chết chống trôi cần Analog (±7000 / 32767)
        private float maxGamepadSpeed = 10.0f;          // Tốc độ góc tối đa mặc định (rad/s)
        private GamepadButtonFlags previousButtons;     // Trạng thái nút bấm chu kỳ trước
        private bool isGamepadDriving = false;          // Cờ báo hiệu đang lái bằng Gamepad
        private string _gamepadMotionText = "Sẵn sàng"; // Nhãn trạng thái hiển thị động trên Top Bar

        /// <summary>
        /// Khởi tạo hệ thống Gamepad: tự động quét tay cầm và khởi chạy Timer 40Hz
        /// </summary>
        private void InitGamepad()
        {
            DetectGamepad();

            gamepadTimer = new System.Windows.Forms.Timer();
            gamepadTimer.Interval = 25; // 40Hz (25ms) tương đương chu kỳ truyền thông UDP của xe
            gamepadTimer.Tick += GamepadTimer_Tick;
            gamepadTimer.Start();
        }

        /// <summary>
        /// Quét cắm nóng (Hot-plugging) kiểm tra kết nối tay cầm qua 4 cổng UserIndex (One -> Four)
        /// </summary>
        private void DetectGamepad()
        {
            for (int i = 0; i < 4; i++)
            {
                try
                {
                    var testCtrl = new Controller((UserIndex)i);
                    if (testCtrl.IsConnected)
                    {
                        _controller = testCtrl;
                        _isControllerConnected = true;
                        UpdateGamepadUI();
                        return;
                    }
                }
                catch { }
            }
            _isControllerConnected = false;
            _controller = null;
            UpdateGamepadUI();
        }

        /// <summary>
        /// Vòng lặp đọc trạng thái cần Analog & Nút bấm Gamepad ở tần số 40Hz (Zero-Latency)
        /// </summary>
        private void GamepadTimer_Tick(object sender, EventArgs e)
        {
            try
            {
                // 1. Quét cắm nóng định kỳ mỗi 1 giây (40 ticks) khi chưa cắm hoặc mất kết nối
                _gamepadScanCounter++;
                if (_controller == null || !_controller.IsConnected)
                {
                    if (_isControllerConnected || _gamepadScanCounter >= 40)
                    {
                        _gamepadScanCounter = 0;
                        DetectGamepad();
                    }
                    return;
                }
                else if (_gamepadScanCounter >= 40)
                {
                    _gamepadScanCounter = 0;
                    if (!_controller.IsConnected)
                    {
                        DetectGamepad();
                        return;
                    }
                }

                // 2. Đọc trạng thái Gamepad qua XInput
                State state;
                try
                {
                    state = _controller.GetState();
                }
                catch
                {
                    _isControllerConnected = false;
                    _controller = null;
                    UpdateGamepadUI();
                    return;
                }

                var gamepad = state.Gamepad;

                // -------------------------------------------------------------
                // A. ĐỌC CẦN JOYSTICK ANALOG
                // -------------------------------------------------------------
                float leftY = gamepad.LeftThumbY;   // Tiến (+) / Lùi (-)
                float leftX = gamepad.LeftThumbX;   // Trượt ngang Phải (+) / Trái (-)
                float rightX = gamepad.RightThumbX; // Xoay phải (+) / Xoay trái (-)
                float rightY = gamepad.RightThumbY; // Điều chỉnh tốc độ vô cấp (Gear Speed)

                // Khử trôi Deadzone
                if (Math.Abs(leftY) < DEADZONE) leftY = 0;
                if (Math.Abs(leftX) < DEADZONE) leftX = 0;
                if (Math.Abs(rightX) < DEADZONE) rightX = 0;

                // Cần phải Y (Lên/Xuống) điều chỉnh trần tốc độ mượt mà [2.0 - 30.0 rad/s]
                if (rightY > 20000)
                {
                    maxGamepadSpeed = Math.Min(maxGamepadSpeed + 0.15f, 30.0f);
                    UpdateGamepadUI();
                }
                else if (rightY < -20000)
                {
                    maxGamepadSpeed = Math.Max(maxGamepadSpeed - 0.15f, 2.0f);
                    UpdateGamepadUI();
                }

                // -------------------------------------------------------------
                // B. LÁI XE MECANUM ZERO-LATENCY & CẬP NHẬT REALTIME GIAO DIỆN
                // -------------------------------------------------------------
                if (_permissionMode != ControlPermissionMode.Viewer)
                {
                    if (leftY != 0 || leftX != 0 || rightX != 0)
                    {
                        if (!isGamepadDriving)
                        {
                            SendButtonSyncEvent("CLAIM_CONTROL", "DOWN", "PC_GAMEPAD");
                        }
                        isGamepadDriving = true;
                        ActivateLocalDriving();
                        state_run = 0x03;

                        float normY = leftY / 32767.0f;
                        float normX = leftX / 32767.0f;
                        float normR = rightX / 32767.0f;

                        // Động học nghịch Mecanum (Kinematics):
                        // w1 (Trước-Trái), w2 (Trước-Phải), w3 (Sau-Trái), w4 (Sau-Phải)
                        float w1 = normY + normX + normR;
                        float w2 = normY - normX + normR;
                        float w3 = normY - normX - normR;
                        float w4 = normY + normX - normR;

                        float maxVal = Math.Max(1.0f, Math.Max(Math.Max(Math.Abs(w1), Math.Abs(w2)), Math.Max(Math.Abs(w3), Math.Abs(w4))));
                        set_w1 = (w1 / maxVal) * maxGamepadSpeed;
                        set_w2 = (w2 / maxVal) * maxGamepadSpeed;
                        set_w3 = (w3 / maxVal) * maxGamepadSpeed;
                        set_w4 = (w4 / maxVal) * maxGamepadSpeed;

                        // Cập nhật Realtime tốc độ 4 bánh xe lên giao diện Manual
                        SetW1TextBox.Text = set_w1.ToString("F1");
                        SetW2TextBox.Text = set_w2.ToString("F1");
                        SetW3TextBox.Text = set_w3.ToString("F1");
                        SetW4TextBox.Text = set_w4.ToString("F1");

                        // Cập nhật Realtime vận tốc mong muốn lên bảng Auto (Vx, Vy, W)
                        Vx_dTextBox.Text = (normY * (maxGamepadSpeed * 0.05f)).ToString("F2");
                        Vy_dTextBox.Text = (normX * (maxGamepadSpeed * 0.05f)).ToString("F2");
                        W_dTextBox.Text = (normR * (maxGamepadSpeed * 0.2f)).ToString("F2");

                        // Xác định hướng di chuyển để cập nhật Badge trạng thái Realtime
                        if (Math.Abs(normR) > 0.22f && Math.Abs(normY) < 0.35f && Math.Abs(normX) < 0.35f)
                        {
                            _gamepadMotionText = normR > 0 ? $"Xoay Phải ↻ (+{Math.Abs(set_w1):F1})" : $"Xoay Trái ↺ (-{Math.Abs(set_w1):F1})";
                        }
                        else if (normY > 0.35f && Math.Abs(normX) < 0.35f)
                        {
                            _gamepadMotionText = $"Tiến ▲ ({set_w1:F1})";
                        }
                        else if (normY < -0.35f && Math.Abs(normX) < 0.35f)
                        {
                            _gamepadMotionText = $"Lùi ▼ ({Math.Abs(set_w1):F1})";
                        }
                        else if (normX < -0.35f && Math.Abs(normY) < 0.35f)
                        {
                            _gamepadMotionText = $"Trượt Trái ◀ ({Math.Abs(set_w2):F1})";
                        }
                        else if (normX > 0.35f && Math.Abs(normY) < 0.35f)
                        {
                            _gamepadMotionText = $"Trượt Phải ▶ ({Math.Abs(set_w1):F1})";
                        }
                        else
                        {
                            _gamepadMotionText = $"Đang lái ({maxGamepadSpeed:F1})";
                        }
                        UpdateGamepadUI();
                    }
                    else if (isGamepadDriving)
                    {
                        isGamepadDriving = false;
                        state_run = 0x01;
                        set_w1 = 0; set_w2 = 0; set_w3 = 0; set_w4 = 0;
                        StopLocalDrivingWithBurst();
                        SendButtonSyncEvent("CLAIM_CONTROL", "UP", "PC_GAMEPAD");

                        // Đưa các ô hiển thị về 0 khi thả cần
                        SetW1TextBox.Text = "0";
                        SetW2TextBox.Text = "0";
                        SetW3TextBox.Text = "0";
                        SetW4TextBox.Text = "0";
                        Vx_dTextBox.Text = "0";
                        Vy_dTextBox.Text = "0";
                        W_dTextBox.Text = "0";

                        _gamepadMotionText = "Sẵn sàng";
                        UpdateGamepadUI();
                    }

                    // Đồng bộ nút điều hướng và nút xoay sáng đèn theo chiều gạt cần Joystick
                    bool isFwd = leftY > 7000;
                    bool isBwd = leftY < -7000;
                    bool isStrafeL = leftX < -7000;
                    bool isStrafeR = leftX > 7000;
                    bool isTurnL = rightX < -7000;
                    bool isTurnR = rightX > 7000;

                    SetButtonHeldState(UpButton, isFwd);
                    SetButtonHeldState(DownButton, isBwd);
                    SetButtonHeldState(LeftButton, isStrafeL);
                    SetButtonHeldState(RightButton, isStrafeR);
                    SetButtonHeldState(TurnLeftButton, isTurnL);
                    SetButtonHeldState(TurnRightButton, isTurnR);
                }

                // -------------------------------------------------------------
                // C. CÁC NÚT BẤM CHỨC NĂNG (BUTTON SHORTCUTS)
                // -------------------------------------------------------------
                var currentButtons = gamepad.Buttons;

                // Nút vai LB (Quay Video), RB (Dừng Video)
                if (currentButtons.HasFlag(GamepadButtonFlags.LeftShoulder) && !previousButtons.HasFlag(GamepadButtonFlags.LeftShoulder))
                {
                    StartRecordButton.PerformClick();
                }
                if (currentButtons.HasFlag(GamepadButtonFlags.RightShoulder) && !previousButtons.HasFlag(GamepadButtonFlags.RightShoulder))
                {
                    StopRecordButton.PerformClick();
                }

                // Cò Analog LT (Chụp ảnh), RT (Đổi Camera Trước/Sau)
                if (gamepad.LeftTrigger > 180)
                {
                    TakePhotoButton.PerformClick();
                }
                if (gamepad.RightTrigger > 180)
                {
                    SwitchCameraButton.PerformClick();
                }

                // Nút Start (Connect/Disconnect UDP)
                if (currentButtons.HasFlag(GamepadButtonFlags.Start) && !previousButtons.HasFlag(GamepadButtonFlags.Start))
                {
                    if (is_connected) DisconnectButton.PerformClick();
                    else ConnectButton.PerformClick();
                }

                // Nút Back (Đổi chế độ phân quyền Master / Viewer)
                if (currentButtons.HasFlag(GamepadButtonFlags.Back) && !previousButtons.HasFlag(GamepadButtonFlags.Back))
                {
                    _btnPermissionMode?.PerformClick();
                }

                // Nút X (Dừng khẩn cấp Emergency Stop)
                if (currentButtons.HasFlag(GamepadButtonFlags.X) && !previousButtons.HasFlag(GamepadButtonFlags.X))
                {
                    EmergencyStopButton.PerformClick();
                }

                previousButtons = currentButtons;
            }
            catch { }
        }

        /// <summary>
        /// Cập nhật hiển thị Badge trạng thái Gamepad trên thanh tiêu đề Top Bar
        /// </summary>
        private void UpdateGamepadUI()
        {
            if (InvokeRequired)
            {
                Invoke(new Action(UpdateGamepadUI));
                return;
            }

            UpdateModernGamepadPill();
        }
    }
}
