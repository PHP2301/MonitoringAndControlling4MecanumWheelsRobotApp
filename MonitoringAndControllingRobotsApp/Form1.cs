using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Net;
using System.Net.Sockets; // Thư viện cho UDP
using System.Net.NetworkInformation; // Thư viện cho Ping và lấy IP Local
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Net.Http;
using System.Windows.Forms;
using ScottPlot;
using System.Collections.Concurrent;
using SharpDX.XInput;
using MonitoringAndControlling4MecanumWheelsRobotsApp;
using System.Diagnostics;
using System.IO;

namespace MonitoringAndControllingRobotsApp
{
    public partial class AppForm : Form
    {
        // --- CÁC BIẾN TOÀN CỤC CHO UDP ---
        private UdpClient udpClient;
        private IPEndPoint serverEndPoint;
        private CancellationTokenSource cancellationTokenSource;
        private bool isConnected = false;
        private ushort expectedSeq = 0;
        private bool isFirstPacket = true;
        

        // --- BIẾN CHO SCOTTPLOT ---
        private List<double> trajX = new List<double>();
        private List<double> trajY = new List<double>();
        private ScottPlot.Plottables.Scatter scatterPath;
        private ScottPlot.Plottables.Arrow directionArrow;

        // Thêm biến cho Quỹ đạo mong muốn (Desired)
        private List<double> trajX_d = new List<double>();
        private List<double> trajY_d = new List<double>();
        private ScottPlot.Plottables.Scatter scatterPath_d;

        // --- DỮ LIỆU ĐÁP ỨNG THỜI GIAN ($x, y, \theta$) ---
        private List<double> timeData = new List<double>();
        private List<double> listX = new List<double>();
        private List<double> listXd = new List<double>();
        private List<double> listY = new List<double>();
        private List<double> listYd = new List<double>();
        private List<double> listTh = new List<double>();
        private List<double> listThd = new List<double>();
        // Các đối tượng plottable để quản lý đường vẽ của 3 đồ thị mới
        private ScottPlot.Plottables.Scatter scatterX, scatterXd;
        private ScottPlot.Plottables.Scatter scatterY, scatterYd;
        private ScottPlot.Plottables.Scatter scatterTh, scatterThd;

        // --- DỮ LIỆU VẬN TỐC ROBOT (Tab 3) ---
        private List<double> listVx = new List<double>(), listVxd = new List<double>();
        private List<double> listVy = new List<double>(), listVyd = new List<double>();
        private List<double> listW = new List<double>(), listWd = new List<double>();
        private ScottPlot.Plottables.Scatter scatterVx, scatterVxd;
        private ScottPlot.Plottables.Scatter scatterVy, scatterVyd;
        private ScottPlot.Plottables.Scatter scatterW, scatterWd;

        // --- DỮ LIỆU VẬN TỐC BÁNH XE (Tab 4) ---
        private List<double> listW1 = new List<double>(), listW1d = new List<double>();
        private List<double> listW2 = new List<double>(), listW2d = new List<double>();
        private List<double> listW3 = new List<double>(), listW3d = new List<double>();
        private List<double> listW4 = new List<double>(), listW4d = new List<double>();
        private ScottPlot.Plottables.Scatter scatterW1, scatterW1d, scatterW2, scatterW2d;
        private ScottPlot.Plottables.Scatter scatterW3, scatterW3d, scatterW4, scatterW4d;

        // --- BIẾN CHO PHẦN GỬI DỮ LIỆU (TX) ---
        private System.Windows.Forms.Timer TimerSend;
        private ushort txSeqCount = 0; // Biến đếm Sequence gửi đi
        private byte state_run = 0x01; // Mặc định là 0x01 (Stop)

        // 10 biến Float gửi xuống Robot
        private float set_x = 0.0f, set_y = 0.0f, set_theta = 0.0f;
        private float set_vx = 0.0f, set_vy = 0.0f, set_w = 0.0f;
        private float set_w1 = 0.0f, set_w2 = 0.0f, set_w3 = 0.0f, set_w4 = 0.0f;

        private ConcurrentQueue<RobotDataRow> dataQueue = new ConcurrentQueue<RobotDataRow>();
        private System.Windows.Forms.Timer renderTimer;
        private ushort lastSeq = 0;

        // Stopwatch để tính thời gian chính xác trong trường hợp Robot chưa có biến timestamp
        private System.Diagnostics.Stopwatch _robotTimer = new System.Diagnostics.Stopwatch();

        // --- BIẾN QUẢN LÝ TRẠNG THÁI VẼ ĐỒ THỊ ---
        private bool isPlotting = false; // Đang vẽ hay không
        private bool isStopped = true;   // Trạng thái dừng hẳn (để reset dữ liệu khi bấm Start lại)

        // --- BIẾN QUẢN LÝ TAY CẦM (GAMEPAD) ---
        private Controller _controller;
        private bool _isControllerConnected = false;
        private bool isGamepadDriving = false;     // Đánh dấu xem tay cầm có đang điều khiển không
        private const int DEADZONE = 5000;         // Khử nhiễu trục Analog (dải từ -32768 đến 32767)
        private float maxGamepadSpeed = 10.0f;     // Tốc độ tối đa khi gạt kịch cần Analog
        private GamepadButtonFlags previousButtons = GamepadButtonFlags.None; // Chống spam nút bấm
        private bool previousLT = false;
        private bool previousRT = false;
        private System.Windows.Forms.Timer gamepadTimer; // Timer đọc tay cầm độc lập 40Hz (25ms)
        private int _gamepadScanCounter = 0;             // Bộ đếm quét cắm nóng tay cầm
        private string _gamepadMotionText = "Sẵn sàng";  // Trạng thái vận động realtime của tay cầm

        private CameraStreamForm streamForm;

        // --- CẤU TRÚC LƯU DỮ LIỆU ĐỂ XUẤT CSV ---
        public class RobotDataRow
        {
            public double Time;
            public float X, Y, Theta, Vx, Vy, W, W1, W2, W3, W4;
            public float Xd, Yd, Thetad, Vxd, Vyd, Wd, W1d, W2d, W3d, W4d;
        }
        private List<RobotDataRow> csvExportList = new List<RobotDataRow>();

        // --- BIẾN ĐỒNG BỘ NÚT BẤM REAL-TIME VỚI TABLET (BUTTON SYNC) ---
        private UdpClient buttonSyncUdpListener;
        private CancellationTokenSource buttonSyncCts;
        private System.Windows.Forms.Timer buttonSyncHeartbeatTimer;
        private readonly System.Drawing.Color BTN_HELD_COLOR = System.Drawing.Color.FromArgb(10, 132, 255);
        private readonly System.Drawing.Color BTN_BLUE_COLOR = System.Drawing.Color.FromArgb(10, 132, 255);       // Xanh dương nổi bật (#0A84FF)
        private readonly System.Drawing.Color BTN_EMERGENCY_COLOR = System.Drawing.Color.FromArgb(255, 69, 58);   // Đỏ cảnh báo (#FF453A)
        private readonly System.Drawing.Color BTN_PURPLE_COLOR = System.Drawing.Color.FromArgb(94, 92, 230);      // Tím Circle Line (#5E5CE6)
        private readonly System.Drawing.Color BTN_GREEN_COLOR = System.Drawing.Color.FromArgb(48, 209, 88);       // Xanh lá Home (#30D158)
        private readonly System.Drawing.Color BTN_AMBER_COLOR = System.Drawing.Color.FromArgb(255, 159, 10);      // Cam Amber (#FF9F0A)
        private Dictionary<Button, (System.Drawing.Color back, System.Drawing.Color fore, FlatStyle style)> originalButtonStyles = new Dictionary<Button, (System.Drawing.Color, System.Drawing.Color, FlatStyle)>();

        // --- PHÂN QUYỀN THIẾT BỊ & NHƯỜNG QUYỀN TỰ ĐỘNG (MULTI-DEVICE ARBITRATION) ---
        public enum ControlPermissionMode
        {
            Auto,   // Tự động nhường quyền (Ai nhấn nút người đó lái, nhả ra ngừng phát để không chặn thiết bị khác)
            Master, // Độc quyền (Khóa quyền điều khiển tại PC này, ép các thiết bị khác sang Chỉ xem)
            Viewer  // Chỉ xem (Chỉ nhận telemetry & xem camera, không phát gói điều khiển)
        }

        private ControlPermissionMode _permissionMode = ControlPermissionMode.Auto;
        private bool _isLocalDrivingActive = false;
        private int _burstPacketsRemaining = 0;
        private Button _btnPermissionMode;

        // --- CÁC NÚT ĐIỀU KHIỂN GIMBAL DJI RS 3 MINI (GỬI QUA WIFI -> MÁY STREAMING -> BLE) ---
        private Button _btnGimbalUp, _btnGimbalDown, _btnGimbalLeft, _btnGimbalRight, _btnGimbalCenter, _btnGimbalMode;
        private readonly string[] _gimbalModes = { "PF", "PTF", "FPV" };
        private int _gimbalModeIndex = 0;

        public AppForm()
        {
            InitializeComponent();

            try
            {
                string iconPath = System.IO.Path.Combine(Application.StartupPath, "app_icon.ico");
                if (System.IO.File.Exists(iconPath))
                {
                    this.Icon = new System.Drawing.Icon(iconPath);
                }
                else
                {
                    this.Icon = System.Drawing.Icon.ExtractAssociatedIcon(Application.ExecutablePath);
                }
            }
            catch { }

            // Khởi tạo trạng thái ban đầu
            PortTextBox.Text = "5001";
            IPComboBox.Text = "192.168.2.93";
            ControlGroupBox.Enabled = true; // Luôn mở nhóm điều khiển để có thể thao tác và đồng bộ ngay cả khi chưa kết nối Robot
            LabelAnnouncement.Text = "Ready";
            LabelAnnouncement.ForeColor = System.Drawing.Color.Green;
            DisconnectButton.Enabled = false;

            // Khởi tạo hỗ trợ tay cầm Gamepad (Tự động nhận diện cắm nóng Plug-and-Play)
            InitGamepadSupport();

            streamForm = new CameraStreamForm();

            if (cboCameraMode.Items.Count > 0 && cboCameraMode.SelectedIndex < 0)
            {
                cboCameraMode.SelectedIndex = 0; // Mặc định: Robot Camera
            }

            SetupScottPlot();
            SetupAnalysisPlots();
            SetupVelocityRobotPlots();
            SetupVelocityMotorsPlots();

            // Đăng ký sự kiện tắt Form để đóng cổng mạng an toàn
            this.FormClosing += Form1_FormClosing;

            // Khởi tạo Timer Gửi dữ liệu (Chu kỳ 25ms)
            TimerSend = new System.Windows.Forms.Timer();
            TimerSend.Interval = 25;
            TimerSend.Tick += TimerSend_Tick;

            // Khởi tạo Timer Vẽ giao diện (Chu kỳ 50ms = 20 FPS)
            renderTimer = new System.Windows.Forms.Timer();
            renderTimer.Interval = 50;
            renderTimer.Tick += RenderTimer_Tick;
            renderTimer.Start();

            // Khởi động lắng nghe sự kiện đồng bộ nút bấm từ Tablet (Cổng UDP 5005)
            StartButtonSyncListener();
            SetupPermissionModeUI();
            SetupGimbalControlUI();
            ApplyModernDashboardTheme();
        }

        private void SetupGimbalControlUI()
        {
            try
            {
                // Thu gọn nhẹ 2 nút Set / Reset trong groupBox3 để nhường khoảng trống phía dưới cho cụm Gimbal RS3 Mini
                SetRobotButton.Location = new System.Drawing.Point(383, 18);
                SetRobotButton.Size = new System.Drawing.Size(99, 34);
                ResetRobotButton.Location = new System.Drawing.Point(383, 56);
                ResetRobotButton.Size = new System.Drawing.Size(99, 34);

                var lblGimbal = new System.Windows.Forms.Label
                {
                    Text = "Gimbal RS3 Mini",
                    Font = new System.Drawing.Font("Microsoft Sans Serif", 8.0F, System.Drawing.FontStyle.Bold),
                    ForeColor = System.Drawing.Color.FromArgb(0, 102, 255),
                    AutoSize = false,
                    TextAlign = ContentAlignment.MiddleCenter,
                    Location = new System.Drawing.Point(371, 94),
                    Size = new System.Drawing.Size(124, 16)
                };
                groupBox3.Controls.Add(lblGimbal);

                Button MakeGimbalBtn(string text, int x, int y, int w, int h)
                {
                    var b = new Button
                    {
                        Text = text,
                        Location = new System.Drawing.Point(x, y),
                        Size = new System.Drawing.Size(w, h),
                        Font = new System.Drawing.Font("Microsoft Sans Serif", 8.25F, System.Drawing.FontStyle.Bold),
                        UseVisualStyleBackColor = true,
                        Cursor = Cursors.Hand
                    };
                    groupBox3.Controls.Add(b);
                    return b;
                }

                // Hàng 1: [◀] [▲] [▶]
                _btnGimbalLeft = MakeGimbalBtn("◀", 373, 113, 38, 34);
                _btnGimbalUp = MakeGimbalBtn("▲", 414, 113, 38, 34);
                _btnGimbalRight = MakeGimbalBtn("▶", 455, 113, 38, 34);

                // Hàng 2: [⊙] [▼] [PF] (Nút Center căn giữa Gimbal)
                _btnGimbalCenter = MakeGimbalBtn("⊙", 373, 151, 38, 34);
                _btnGimbalDown = MakeGimbalBtn("▼", 414, 151, 38, 34);
                _btnGimbalMode = MakeGimbalBtn("PF", 455, 151, 38, 34);

                _btnGimbalCenter.Click += (s, e) =>
                {
                    FlashButtonClicked(_btnGimbalCenter, BTN_BLUE_COLOR);
                    SendButtonSyncEvent("GIMBAL_RECENTER", "CLICK");
                    SendCameraHttpCommand("gimbal/recenter");
                    LabelAnnouncement.Text = "Gimbal RS3 Mini: Đã căn giữa (Center)...";
                };

                _btnGimbalUp.MouseDown += (s, e) => TriggerGimbalMove("GIMBAL_UP", true, 0, 1, _btnGimbalUp);
                _btnGimbalUp.MouseUp += (s, e) => TriggerGimbalMove("GIMBAL_UP", false, 0, 0, _btnGimbalUp);

                _btnGimbalDown.MouseDown += (s, e) => TriggerGimbalMove("GIMBAL_DOWN", true, 0, -1, _btnGimbalDown);
                _btnGimbalDown.MouseUp += (s, e) => TriggerGimbalMove("GIMBAL_DOWN", false, 0, 0, _btnGimbalDown);

                _btnGimbalLeft.MouseDown += (s, e) => TriggerGimbalMove("GIMBAL_LEFT", true, -1, 0, _btnGimbalLeft);
                _btnGimbalLeft.MouseUp += (s, e) => TriggerGimbalMove("GIMBAL_LEFT", false, 0, 0, _btnGimbalLeft);

                _btnGimbalRight.MouseDown += (s, e) => TriggerGimbalMove("GIMBAL_RIGHT", true, 1, 0, _btnGimbalRight);
                _btnGimbalRight.MouseUp += (s, e) => TriggerGimbalMove("GIMBAL_RIGHT", false, 0, 0, _btnGimbalRight);

                _btnGimbalMode.Click += (s, e) =>
                {
                    _gimbalModeIndex = (_gimbalModeIndex + 1) % _gimbalModes.Length;
                    string mode = _gimbalModes[_gimbalModeIndex];
                    UpdateGimbalModeUI(mode);
                    FlashButtonClicked(_btnGimbalMode, BTN_PURPLE_COLOR);
                    SendButtonSyncEvent("GIMBAL_MODE", "CLICK", mode);
                    SendCameraHttpCommand($"gimbal/mode?m={mode}");
                    LabelAnnouncement.Text = $"Gimbal RS3 Mini Mode: {mode}";
                };

                UpdateGimbalModeUI("PF");
            }
            catch { }
        }

        private void UpdateGimbalModeUI(string mode)
        {
            if (InvokeRequired)
            {
                Invoke(new Action(() => UpdateGimbalModeUI(mode)));
                return;
            }
            if (_btnGimbalMode != null) _btnGimbalMode.Text = mode;
            bool isFpv = string.Equals(mode, "FPV", StringComparison.OrdinalIgnoreCase);
            // Khi ở chế độ FPV: ẩn 2 nút Trái và Phải; ở 2 chế độ kia (PF & PTF): hiện cả 4 nút
            if (_btnGimbalLeft != null) _btnGimbalLeft.Visible = !isFpv;
            if (_btnGimbalRight != null) _btnGimbalRight.Visible = !isFpv;
        }

        private void TriggerGimbalMove(string btnId, bool isDown, int pan, int tilt, Button btn)
        {
            SetButtonHeldState(btn, isDown);
            SendButtonSyncEvent(btnId, isDown ? "DOWN" : "UP");
            SendCameraHttpCommand($"gimbal/move?pan={pan}&tilt={tilt}");
            if (isDown)
            {
                LabelAnnouncement.Text = $"Gimbal RS3 Mini: Đang xoay ({btnId.Replace("GIMBAL_", "")})...";
            }
        }

        private void SetupPermissionModeUI()
        {
            try
            {
                // Tinh chỉnh vị trí các control trong TCPGroupBox để nhường chỗ cho nút Quyền điều khiển
                IPComboBox.Size = new System.Drawing.Size(215, 28);
                label4.Location = new System.Drawing.Point(250, 29);
                PortTextBox.Location = new System.Drawing.Point(285, 24);
                PortTextBox.Size = new System.Drawing.Size(80, 26);
                ScanIPButton.Location = new System.Drawing.Point(375, 16);
                ScanIPButton.Size = new System.Drawing.Size(88, 38);
                ConnectButton.Location = new System.Drawing.Point(470, 16);
                ConnectButton.Size = new System.Drawing.Size(88, 38);
                DisconnectButton.Location = new System.Drawing.Point(565, 16);
                DisconnectButton.Size = new System.Drawing.Size(100, 38);

                _btnPermissionMode = new Button
                {
                    Name = "btnPermissionMode",
                    Location = new System.Drawing.Point(672, 16),
                    Size = new System.Drawing.Size(184, 38),
                    Font = new System.Drawing.Font("Microsoft Sans Serif", 9.0F, System.Drawing.FontStyle.Bold),
                    FlatStyle = FlatStyle.Flat,
                    Cursor = Cursors.Hand
                };
                _btnPermissionMode.FlatAppearance.BorderSize = 1;
                _btnPermissionMode.Click += (s, e) => CyclePermissionMode();
                TCPGroupBox.Controls.Add(_btnPermissionMode);

                UpdatePermissionModeUI();
            }
            catch { }
        }

        private void CyclePermissionMode()
        {
            ControlPermissionMode prev = _permissionMode;
            ControlPermissionMode next = _permissionMode == ControlPermissionMode.Auto
                ? ControlPermissionMode.Master
                : (_permissionMode == ControlPermissionMode.Master ? ControlPermissionMode.Viewer : ControlPermissionMode.Auto);

            SetPermissionMode(next, true, prev);
        }

        private void SetPermissionMode(ControlPermissionMode mode, bool broadcastSync, ControlPermissionMode? prevMode = null)
        {
            _permissionMode = mode;
            if (mode == ControlPermissionMode.Viewer)
            {
                _isLocalDrivingActive = false;
                _burstPacketsRemaining = 0;
                isGamepadDriving = false;
                state_run = 0x01;
                set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
            }

            UpdatePermissionModeUI();

            if (broadcastSync)
            {
                if (mode == ControlPermissionMode.Master)
                {
                    SendButtonSyncEvent("PERMISSION", "MASTER", "PC");
                    LabelAnnouncement.ForeColor = System.Drawing.Color.DarkOrange;
                    LabelAnnouncement.Text = "Quyền điều khiển: ĐỘC QUYỀN (Đã khóa quyền tại PC, thiết bị khác chuyển sang Chỉ xem)";
                }
                else if (prevMode == ControlPermissionMode.Master)
                {
                    SendButtonSyncEvent("PERMISSION", "RELEASE", "PC");
                    LabelAnnouncement.ForeColor = System.Drawing.Color.Green;
                    LabelAnnouncement.Text = mode == ControlPermissionMode.Auto
                        ? "Quyền điều khiển: TỰ ĐỘNG (Ai nhấn nút người đó lái)"
                        : "Quyền điều khiển: CHỈ XEM (Ngắt phát lệnh điều khiển)";
                }
            }
        }

        private void UpdatePermissionModeUI()
        {
            if (_btnPermissionMode == null || _btnPermissionMode.IsDisposed) return;
            switch (_permissionMode)
            {
                case ControlPermissionMode.Auto:
                    _btnPermissionMode.Text = "Quyền: Tự động";
                    _btnPermissionMode.BackColor = System.Drawing.Color.FromArgb(0, 122, 255);
                    _btnPermissionMode.ForeColor = System.Drawing.Color.White;
                    _btnPermissionMode.FlatAppearance.BorderColor = System.Drawing.Color.FromArgb(0, 90, 200);
                    break;
                case ControlPermissionMode.Master:
                    _btnPermissionMode.Text = "Quyền: Độc quyền";
                    _btnPermissionMode.BackColor = System.Drawing.Color.FromArgb(255, 149, 0);
                    _btnPermissionMode.ForeColor = System.Drawing.Color.White;
                    _btnPermissionMode.FlatAppearance.BorderColor = System.Drawing.Color.FromArgb(200, 110, 0);
                    break;
                case ControlPermissionMode.Viewer:
                    _btnPermissionMode.Text = "Quyền: Chỉ xem";
                    _btnPermissionMode.BackColor = System.Drawing.Color.FromArgb(120, 128, 140);
                    _btnPermissionMode.ForeColor = System.Drawing.Color.White;
                    _btnPermissionMode.FlatAppearance.BorderColor = System.Drawing.Color.FromArgb(90, 98, 110);
                    break;
            }
        }

        private bool CheckLocalControlPermission()
        {
            if (_permissionMode == ControlPermissionMode.Viewer)
            {
                LabelAnnouncement.ForeColor = System.Drawing.Color.Red;
                LabelAnnouncement.Text = "Đang ở chế độ 'Chỉ xem'! Nhấn nút 'Quyền: Chỉ xem' trên khung UDP để mở khóa lái.";
                return false;
            }
            return true;
        }

        private void ActivateLocalDriving()
        {
            _isLocalDrivingActive = true;
            _burstPacketsRemaining = 0;
        }

        private void StopLocalDrivingWithBurst(int burstCount = 8)
        {
            _isLocalDrivingActive = false;
            _burstPacketsRemaining = burstCount;
        }

        private void YieldControlToRemote()
        {
            _isLocalDrivingActive = false;
            _burstPacketsRemaining = 0;
            isGamepadDriving = false;
        }

        private void SetupScottPlot()
        {
            // Đặt tên trục và tiêu đề
            TrajectoryPlot.Plot.Title("Robot Trajectory (Real-time)");
            TrajectoryPlot.Plot.XLabel("X Position (m)");
            TrajectoryPlot.Plot.YLabel("Y Position (m)");

            // Thêm đường Scatter (Quỹ đạo)
            // Ban đầu truyền list rỗng vào, nó sẽ tự động cập nhật khi List có thêm điểm
            scatterPath = TrajectoryPlot.Plot.Add.ScatterLine(trajX, trajY);
            scatterPath.Color = Colors.Red;
            scatterPath.LineWidth = 2;
            scatterPath.LegendText = "Actual Path";

            // 2. Thêm đường Quỹ đạo mong muốn (Desired Path) - Màu Xanh lá (hoặc Magenta), Nét đứt
            scatterPath_d = TrajectoryPlot.Plot.Add.ScatterLine(trajX_d, trajY_d);
            scatterPath_d.Color = Colors.Green; // Bạn có thể chọn Colors.Magenta tùy ý
            scatterPath_d.LineWidth = 2;
            scatterPath_d.LinePattern = LinePattern.Dashed; // ĐẶT NÉT ĐỨT Ở ĐÂY
            scatterPath_d.LegendText = "Desired Path";

            // Hiển thị bảng chú thích (Legend) ở góc đồ thị để dễ phân biệt
            TrajectoryPlot.Plot.ShowLegend(ScottPlot.Alignment.UpperRight);

            // Giữ tỷ lệ X-Y bằng nhau (Square Axis) để quỹ đạo robot đi hình tròn không bị méo thành hình elip
            TrajectoryPlot.Plot.Axes.SquareUnits();

            // Refresh lần đầu để hiển thị
            TrajectoryPlot.Refresh();
        }

        private void SetupAnalysisPlots()
        {
            // 1. CẤU HÌNH ĐỒ THỊ X RESPONSE
            scatterX = PlotXTime.Plot.Add.ScatterLine(timeData, listX);
            scatterX.Color = Colors.Red; scatterX.LineWidth = 2; scatterX.Label = "Actual X";

            scatterXd = PlotXTime.Plot.Add.ScatterLine(timeData, listXd);
            scatterXd.Color = Colors.Green; scatterXd.LineWidth = 2; scatterXd.Label = "Desired X";
            scatterXd.LinePattern = LinePattern.Dashed; // Nét đứt cho Desired

            PlotXTime.Plot.Title("X Position Response");
            PlotXTime.Plot.YLabel("Position (m)");
            PlotXTime.Plot.Legend.IsVisible = true;
            PlotXTime.Plot.Legend.Alignment = ScottPlot.Alignment.UpperRight;

            // 2. CẤU HÌNH ĐỒ THỊ Y RESPONSE
            scatterY = PlotYTime.Plot.Add.ScatterLine(timeData, listY);
            scatterY.Color = Colors.Red; scatterY.LineWidth = 2; scatterY.Label = "Actual Y";

            scatterYd = PlotYTime.Plot.Add.ScatterLine(timeData, listYd);
            scatterYd.Color = Colors.Green; scatterYd.LineWidth = 2; scatterYd.Label = "Desired Y";
            scatterYd.LinePattern = LinePattern.Dashed;

            PlotYTime.Plot.Title("Y Position Response");
            PlotYTime.Plot.YLabel("Position (m)");
            PlotYTime.Plot.Legend.IsVisible = true;
            PlotYTime.Plot.Legend.Alignment = ScottPlot.Alignment.UpperRight;

            // 3. CẤU HÌNH ĐỒ THỊ THETA RESPONSE
            scatterTh = PlotThetaTime.Plot.Add.ScatterLine(timeData, listTh);
            scatterTh.Color = Colors.Red; scatterTh.LineWidth = 2; scatterTh.Label = "Actual Theta";

            scatterThd = PlotThetaTime.Plot.Add.ScatterLine(timeData, listThd);
            scatterThd.Color = Colors.Green; scatterThd.LineWidth = 2; scatterThd.Label = "Desired Theta";
            scatterThd.LinePattern = LinePattern.Dashed;

            PlotThetaTime.Plot.Title("Heading Angle (Theta) Response");
            PlotThetaTime.Plot.YLabel("Angle (rad)");
            PlotThetaTime.Plot.XLabel("Packet Index"); // Trục X chung cho cả 3 đồ thị thời gian
            PlotThetaTime.Plot.Legend.IsVisible = true;
            PlotThetaTime.Plot.Legend.Alignment = ScottPlot.Alignment.UpperRight;
        }

        private void SetupVelocityRobotPlots()
        {
            // ================== TAB 3: ROBOT VELOCITY (MECANUM) ==================
            // 1. Đồ thị Vx (Longitudinal Velocity - Đi thẳng)
            scatterVx = PlotVxTime.Plot.Add.ScatterLine(timeData, listVx);
            scatterVx.Color = Colors.Red; scatterVx.LineWidth = 2; scatterVx.Label = "Actual vx";
            scatterVxd = PlotVxTime.Plot.Add.ScatterLine(timeData, listVxd);
            scatterVxd.Color = Colors.Green; scatterVxd.LineWidth = 2; scatterVxd.Label = "Desired vx";
            scatterVxd.LinePattern = LinePattern.Dashed;
            PlotVxTime.Plot.Title("Forward Velocity (Vx) Response");
            PlotVxTime.Plot.YLabel("Velocity (m/s)");
            PlotVxTime.Plot.Legend.IsVisible = true;
            PlotVxTime.Plot.Legend.Alignment = ScottPlot.Alignment.UpperRight;

            // 2. Đồ thị Vy (Lateral Velocity - Trượt ngang)
            scatterVy = PlotVyTime.Plot.Add.ScatterLine(timeData, listVy);
            scatterVy.Color = Colors.Red; scatterVy.LineWidth = 2; scatterVy.Label = "Actual vy";
            scatterVyd = PlotVyTime.Plot.Add.ScatterLine(timeData, listVyd);
            scatterVyd.Color = Colors.Green; scatterVyd.LineWidth = 2; scatterVyd.Label = "Desired vy";
            scatterVyd.LinePattern = LinePattern.Dashed;
            PlotVyTime.Plot.Title("Lateral Velocity (Vy) Response");
            PlotVyTime.Plot.YLabel("Velocity (m/s)");
            PlotVyTime.Plot.Legend.IsVisible = true;
            PlotVyTime.Plot.Legend.Alignment = ScottPlot.Alignment.UpperRight;

            // 3. Đồ thị W (Angular Velocity - Xoay)
            scatterW = PlotWTime.Plot.Add.ScatterLine(timeData, listW);
            scatterW.Color = Colors.Red; scatterW.LineWidth = 2; scatterW.Label = "Actual w";
            scatterWd = PlotWTime.Plot.Add.ScatterLine(timeData, listWd);
            scatterWd.Color = Colors.Green; scatterWd.LineWidth = 2; scatterWd.Label = "Desired w";
            scatterWd.LinePattern = LinePattern.Dashed;
            PlotWTime.Plot.Title("Angular Velocity (W) Response");
            PlotWTime.Plot.YLabel("Velocity (rad/s)");
            PlotWTime.Plot.XLabel("Time (seconds)");
            PlotWTime.Plot.Legend.IsVisible = true;
            PlotWTime.Plot.Legend.Alignment = ScottPlot.Alignment.UpperRight;
        }

        private void SetupVelocityMotorsPlots()
        {
            // ================== TAB 4: WHEEL VELOCITY ==================
            // Đồ thị W1 (Trái Sau - RL)
            scatterW1 = PlotW1Time.Plot.Add.ScatterLine(timeData, listW1);
            scatterW1.Color = Colors.Red; scatterW1.LineWidth = 2; scatterW1.Label = "Actual W1";
            scatterW1d = PlotW1Time.Plot.Add.ScatterLine(timeData, listW1d);
            scatterW1d.Color = Colors.Green; scatterW1d.LineWidth = 2; scatterW1d.Label = "Desired W1";
            scatterW1d.LinePattern = LinePattern.Dashed;
            PlotW1Time.Plot.Title("Motor 1 (Rear Left) Velocity");
            PlotW1Time.Plot.YLabel("Speed (rad/s)");
            PlotW1Time.Plot.Legend.IsVisible = true;
            PlotW1Time.Plot.Legend.Alignment = ScottPlot.Alignment.UpperRight;

            // Đồ thị W2 (Trái Trước - FL)
            scatterW2 = PlotW2Time.Plot.Add.ScatterLine(timeData, listW2);
            scatterW2.Color = Colors.Red; scatterW2.LineWidth = 2; scatterW2.Label = "Actual W2";
            scatterW2d = PlotW2Time.Plot.Add.ScatterLine(timeData, listW2d);
            scatterW2d.Color = Colors.Green; scatterW2d.LineWidth = 2; scatterW2d.Label = "Desired W2";
            scatterW2d.LinePattern = LinePattern.Dashed;
            PlotW2Time.Plot.Title("Motor 2 (Front Left) Velocity");
            PlotW2Time.Plot.YLabel("Speed (rad/s)");
            PlotW2Time.Plot.Legend.IsVisible = true;
            PlotW2Time.Plot.Legend.Alignment = ScottPlot.Alignment.UpperRight;

            // Đồ thị W3 (Phải Sau - RR)
            scatterW3 = PlotW3Time.Plot.Add.ScatterLine(timeData, listW3);
            scatterW3.Color = Colors.Red; scatterW3.LineWidth = 2; scatterW3.Label = "Actual W3";
            scatterW3d = PlotW3Time.Plot.Add.ScatterLine(timeData, listW3d);
            scatterW3d.Color = Colors.Green; scatterW3d.LineWidth = 2; scatterW3d.Label = "Desired W3";
            scatterW3d.LinePattern = LinePattern.Dashed;
            PlotW3Time.Plot.Title("Motor 3 (Rear Right) Velocity");
            PlotW3Time.Plot.YLabel("Speed (rad/s)");
            PlotW3Time.Plot.Legend.IsVisible = true;
            PlotW3Time.Plot.Legend.Alignment = ScottPlot.Alignment.UpperRight;

            // Đồ thị W4 (Phải Trước - FR)
            scatterW4 = PlotW4Time.Plot.Add.ScatterLine(timeData, listW4);
            scatterW4.Color = Colors.Red; scatterW4.LineWidth = 2; scatterW4.Label = "Actual W4";
            scatterW4d = PlotW4Time.Plot.Add.ScatterLine(timeData, listW4d);
            scatterW4d.Color = Colors.Green; scatterW4d.LineWidth = 2; scatterW4d.Label = "Desired W4";
            scatterW4d.LinePattern = LinePattern.Dashed;
            PlotW4Time.Plot.Title("Motor 4 (Front Right) Velocity");
            PlotW4Time.Plot.YLabel("Speed (rad/s)");
            PlotW4Time.Plot.XLabel("Time (seconds)");
            PlotW4Time.Plot.Legend.IsVisible = true;
            PlotW4Time.Plot.Legend.Alignment = ScottPlot.Alignment.UpperRight;
        }

        // =========================================================================
        // 1. CHỨC NĂNG QUÉT IP
        // =========================================================================

        // SỬA: Phải có chữ "async" ở đây vì bên trong dùng await Task.Run
        private async void ScanIPButton_Click(object sender, EventArgs e)
        {
            IPComboBox.Items.Clear();
            LabelAnnouncement.ForeColor = System.Drawing.Color.Green;
            LabelAnnouncement.Text = "Scanning LAN for Active Devices....";
            ScanIPButton.Enabled = false;

            var localIPs = GetAllLocalIPAddresses();
            if (localIPs.Count == 0)
            {
                LabelAnnouncement.Text = "Local IP not found!";
                ScanIPButton.Enabled = true;
                return;
            }

            string subnet = localIPs[0].ToString();
            var parts = subnet.Split('.');
            if (parts.Length != 4)
            {
                LabelAnnouncement.Text = "Invalid local IP!";
                ScanIPButton.Enabled = true;
                return;
            }
            string baseIP = $"{parts[0]}.{parts[1]}.{parts[2]}.";

            List<string> foundDevices = new List<string>();
            int timeout = 150;

            await Task.Run(() =>
            {
                Parallel.For(1, 255, (i) =>
                {
                    string targetIP = baseIP + i;
                    if (targetIP == localIPs[0].ToString()) return;

                    bool isAlive = false;
                    try
                    {
                        using (var ping = new Ping())
                        {
                            var reply = ping.Send(targetIP, timeout);
                            if (reply.Status == IPStatus.Success)
                            {
                                isAlive = true;
                            }
                        }
                    }
                    catch { }

                    if (isAlive)
                    {
                        string hostname = "";
                        try
                        {
                            var hostEntry = Dns.GetHostEntry(targetIP);
                            hostname = hostEntry.HostName;
                            if (hostname.Contains("."))
                                hostname = hostname.Substring(0, hostname.IndexOf('.'));
                        }
                        catch { hostname = "Unknown Device"; }

                        string display = $"{targetIP} ({hostname})";

                        lock (foundDevices)
                        {
                            foundDevices.Add(display);
                        }
                    }
                });
            });

            IPComboBox.Invoke(new Action(() =>
            {
                var sortedDevices = foundDevices.OrderByDescending(d => d.ToLower().Contains("rasp") || d.ToLower().Contains("ubuntu")).ToList();
                foreach (var item in sortedDevices)
                {
                    IPComboBox.Items.Add(item);
                }

                if (sortedDevices.Count > 0)
                {
                    IPComboBox.SelectedIndex = 0;
                    LabelAnnouncement.ForeColor = System.Drawing.Color.Green;
                    LabelAnnouncement.Text = $"Found {sortedDevices.Count} devices. Ready to connect!";
                }
                else
                {
                    LabelAnnouncement.ForeColor = System.Drawing.Color.Red;
                    LabelAnnouncement.Text = "No devices found!";
                }

                ScanIPButton.Enabled = true;
            }));
        }

        // HÀM BỔ SUNG: Lấy IP của máy tính đang chạy App (Kế thừa từ app cũ của bạn)
        private List<IPAddress> GetAllLocalIPAddresses()
        {
            List<IPAddress> ipList = new List<IPAddress>();
            foreach (var netInterface in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (netInterface.OperationalStatus == OperationalStatus.Up &&
                    netInterface.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                {
                    var props = netInterface.GetIPProperties();
                    foreach (var ip in props.UnicastAddresses)
                    {
                        if (ip.Address.AddressFamily == AddressFamily.InterNetwork &&
                            !IPAddress.IsLoopback(ip.Address))
                        {
                            ipList.Add(ip.Address);
                        }
                    }
                }
            }
            return ipList;
        }

        // =========================================================================
        // 2. CHỨC NĂNG KẾT NỐI VÀ NHẬN DỮ LIỆU UDP
        // =========================================================================

        private async void ConnectButton_Click_1(object sender, EventArgs e)
        {
            try
            {
                _robotTimer.Restart(); // Bắt đầu tính thời gian từ 0 giây
                string ipText = IPComboBox.Text.Trim();
                if (string.IsNullOrEmpty(ipText))
                {
                    MessageBox.Show("Vui lòng chọn hoặc nhập IP của Raspberry Pi.");
                    return;
                }

                // TRICK: Vì ComboBox hiển thị dạng "192.168.1.100 (raspberrypi)", 
                // ta cần Split để lấy phần IP thực sự ở đằng trước.
                string serverIPString = ipText.Split(' ')[0];
                int port = int.Parse(PortTextBox.Text.Trim());

                // Xóa sạch dữ liệu thừa trong hàng đợi cũ (nếu có)
                while (dataQueue.TryDequeue(out _)) { }

                // Khởi tạo UDP
                udpClient = new UdpClient(0); // 0 để Windows tự cấp Port local
                serverEndPoint = new IPEndPoint(IPAddress.Parse(serverIPString), port);

                isConnected = true;
                isFirstPacket = true;
                ConnectButton.Enabled = false;
                ScanIPButton.Enabled = false;
                DisconnectButton.Enabled = true;
                ControlGroupBox.Enabled = true; // Bật nhóm điều khiển khi đã kết nối
                LabelAnnouncement.ForeColor = System.Drawing.Color.Blue;
                LabelAnnouncement.Text = "Đang gửi tín hiệu Wake-up...";

                // Gửi gói Wake-up xuống C++ Node để Pi biết IP/Port của App
                // Node C++ nhận được sẽ lập tức nhả 14 số float ngược lên
                byte[] wakeUpPacket = { 0xAA, 0xFF, 0x00, 0x00, 0x55 };
                await udpClient.SendAsync(wakeUpPacket, wakeUpPacket.Length, serverEndPoint);

                LabelAnnouncement.ForeColor = System.Drawing.Color.Green;
                LabelAnnouncement.Text = "Đã kết nối UDP!";

                // Mở luồng lắng nghe (Chạy ngầm không đơ Form)
                cancellationTokenSource = new CancellationTokenSource();
                _ = ReceiveDataAsync(cancellationTokenSource.Token);

                txSeqCount = 0;      // Reset lại sequence gửi
                TimerSend.Start();   // Bắt đầu dội dữ liệu xuống Pi

                // Nếu thao tác bấm trực tiếp trên PC thì đồng bộ lệnh kết nối sang Tablet
                if (sender != null)
                {
                    SendButtonSyncEvent("CONNECT_UDP", "CLICK", $"{serverIPString},{port}");
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show("Lỗi kết nối: " + ex.Message);
                DisconnectButton_Click(null, null); // Tự động reset UI
            }
        }

        private void DisconnectButton_Click(object sender, EventArgs e)
        {
            isConnected = false;
            TimerSend.Stop();
            cancellationTokenSource?.Cancel();
            udpClient?.Close();

            ConnectButton.Enabled = true;
            ScanIPButton.Enabled = true;
            DisconnectButton.Enabled = false;
            ControlGroupBox.Enabled = true; // Luôn mở nhóm điều khiển để đồng bộ nút bấm!

            LabelAnnouncement.ForeColor = System.Drawing.Color.Red;
            LabelAnnouncement.Text = "Đã ngắt kết nối";

            // Nếu thao tác bấm trực tiếp trên PC thì đồng bộ lệnh ngắt sang Tablet
            if (sender != null)
            {
                SendButtonSyncEvent("DISCONNECT_UDP", "CLICK");
            }
        }

        private async Task ReceiveDataAsync(CancellationToken token)
        {
            try
            {
                while (!token.IsCancellationRequested && isConnected)
                {
                    UdpReceiveResult result = await udpClient.ReceiveAsync();
                    byte[] data = result.Buffer;

                    // KIỂM TRA FRAME GỬI TỪ RASPBERRY PI (62 bytes)
                    if (data.Length == 86 && data[0] == 0xAA && data[85] == 0x55)
                    {
                        // 1. Kiểm tra CRC16 (Tính trên 83 byte đầu tiên)
                        ushort receivedCrc = BitConverter.ToUInt16(data, 83);
                        ushort calculatedCrc = CalculateCRC16(data, 83);
                        if (receivedCrc != calculatedCrc) continue;

                        // 2. Lấy số Sequence
                        ushort seq = BitConverter.ToUInt16(data, 1);
                        if (isFirstPacket) { expectedSeq = seq; isFirstPacket = false; }

                        // 3. Xử lý Sequence chống mất gói... (GIỮ NGUYÊN NHƯ CŨ)
                        int diff = seq - expectedSeq;
                        if (diff < -32768) diff += 65536; else if (diff > 32768) diff -= 65536;

                        if (diff > 0)
                        {
                            int missingCount = Math.Min(diff, 256);
                            for (int i = 0; i < missingCount; i++)
                            {
                                ushort missingSeq = (ushort)((expectedSeq + i) % 65536);
                                _ = RequestMissingPacket(missingSeq);
                            }
                            expectedSeq = (ushort)((seq + 1) % 65536);
                        }
                        else if (diff == 0) { expectedSeq = (ushort)((seq + 1) % 65536); }

                        // 4. Bóc tách 20 số Float cho Mecanum
                        float[] robotData = new float[20];
                        for (int i = 0; i < 20; i++)
                        {
                            int offset = 3 + (i * 4);
                            float val = BitConverter.ToSingle(data, offset);

                            // BỨC TƯỜNG LỬA CHỐNG CRASH APP: 
                            // Nếu C++ gửi lên số lỗi (NaN) hoặc Vô cực, ép nó về 0 ngay lập tức
                            if (float.IsNaN(val) || float.IsInfinity(val))
                            {
                                val = 0.0f;
                            }

                            robotData[i] = val;
                        }

                        // 5. Nạp vào Row
                        lastSeq = seq;
                        var row = new RobotDataRow
                        {
                            Time = _robotTimer.Elapsed.TotalSeconds,
                            Xd = robotData[0],
                            X = robotData[1],
                            Yd = robotData[2],
                            Y = robotData[3],
                            Thetad = robotData[4],
                            Theta = robotData[5],
                            Vxd = robotData[6],
                            Vx = robotData[7],
                            Vyd = robotData[8],
                            Vy = robotData[9],
                            Wd = robotData[10],
                            W = robotData[11],
                            W1d = robotData[12],
                            W1 = robotData[13],
                            W2d = robotData[14],
                            W2 = robotData[15],
                            W3d = robotData[16],
                            W3 = robotData[17],
                            W4d = robotData[18],
                            W4 = robotData[19]
                        };
                        dataQueue.Enqueue(row);
                    }
                }
            }
            catch (ObjectDisposedException) { /* Lỗi vặt khi ấn Disconnect */ }
            catch (Exception ex)
            {
                // Kiểm tra xem Form có đang bị tắt không trước khi gọi Invoke
                if (isConnected && !this.IsDisposed && !this.Disposing)
                {
                    Invoke(new Action(() => LabelAnnouncement.Text = "Lỗi mạng: " + ex.Message));
                }
            }
        }

        private void label7_Click(object sender, EventArgs e)
        {

        }

        // Hàm gửi yêu cầu Pi gửi lại gói tin bị mất
        private async Task RequestMissingPacket(ushort missingSeq)
        {
            try
            {
                byte[] reqPacket = new byte[5];
                reqPacket[0] = 0xAA; // STX
                reqPacket[1] = 0xFF; // ID = 0xFF (Yêu cầu gửi lại gói)

                // Theo chuẩn Little Endian như code C++ (recv_buf[2] | recv_buf[3] << 8)
                reqPacket[2] = (byte)(missingSeq & 0xFF);        // SEQ LOW
                reqPacket[3] = (byte)((missingSeq >> 8) & 0xFF); // SEQ HIGH

                reqPacket[4] = 0x55; // ETX

                await udpClient.SendAsync(reqPacket, reqPacket.Length, serverEndPoint);
            }
            catch { /* Bỏ qua lỗi gửi nếu mạng rớt */ }
        }

        private async void TimerSend_Tick(object sender, EventArgs e)
        {
            if (!isConnected || udpClient == null) return;

            try
            {
                // =====================================================================
                // CƠ CHẾ ACTIVE-ONLY TX: Chỉ phát gói 47 bytes khi đang nhấn nút điều khiển
                // hoặc gửi nốt cụm gói Stop/One-shot (_burstPacketsRemaining > 0).
                // Khi nghỉ (Idle), tuyệt đối KHÔNG spam tốc độ = 0 để không chặn thiết bị khác!
                // =====================================================================
                if (_permissionMode == ControlPermissionMode.Viewer && _burstPacketsRemaining <= 0) return;
                if (!_isLocalDrivingActive && _burstPacketsRemaining <= 0) return;
                if (_burstPacketsRemaining > 0) _burstPacketsRemaining--;

                byte[] frame = new byte[47]; // KÍCH THƯỚC MỚI

                // 1. Header & SEQ & ID
                frame[0] = 0xAA;
                frame[1] = (byte)(txSeqCount & 0xFF);
                frame[2] = (byte)((txSeqCount >> 8) & 0xFF);
                frame[3] = state_run;

                // 2. Data: 10 biến float (tổng 40 byte)
                Buffer.BlockCopy(BitConverter.GetBytes(set_x), 0, frame, 4, 4);
                Buffer.BlockCopy(BitConverter.GetBytes(set_y), 0, frame, 8, 4);
                Buffer.BlockCopy(BitConverter.GetBytes(set_theta), 0, frame, 12, 4);
                Buffer.BlockCopy(BitConverter.GetBytes(set_vx), 0, frame, 16, 4);
                Buffer.BlockCopy(BitConverter.GetBytes(set_vy), 0, frame, 20, 4);
                Buffer.BlockCopy(BitConverter.GetBytes(set_w), 0, frame, 24, 4);
                Buffer.BlockCopy(BitConverter.GetBytes(set_w1), 0, frame, 28, 4);
                Buffer.BlockCopy(BitConverter.GetBytes(set_w2), 0, frame, 32, 4);
                Buffer.BlockCopy(BitConverter.GetBytes(set_w3), 0, frame, 36, 4);
                Buffer.BlockCopy(BitConverter.GetBytes(set_w4), 0, frame, 40, 4);

                // 3. Tính CRC16 trên 44 byte đầu (Từ index 0 đến 43)
                ushort crc = CalculateCRC16(frame, 44);
                frame[44] = (byte)(crc & 0xFF);
                frame[45] = (byte)((crc >> 8) & 0xFF);

                // 4. Footer
                frame[46] = 0x55;

                await udpClient.SendAsync(frame, frame.Length, serverEndPoint);
                txSeqCount++;

                if (!_isLocalDrivingActive && _burstPacketsRemaining == 0 && state_run >= 0x07)
                {
                    state_run = 0x01;
                }
            }
            catch { }
        }

        // --- ĐIỀU KHIỂN BẰNG NÚT NHẤN (MANUAL MODE) ---
        float manualSpeed = 10.0f;


        // =========================================================================
        // TAY CẦM ĐIỀU KHIỂN ROBOT (GAMEPAD / XINPUT CONTROLLER)
        // =========================================================================
        private void InitGamepadSupport()
        {
            DetectGamepad();

            gamepadTimer = new System.Windows.Forms.Timer();
            gamepadTimer.Interval = 25; // 40Hz tương đương chu kỳ truyền thông UDP
            gamepadTimer.Tick += GamepadTimer_Tick;
            gamepadTimer.Start();
        }

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

        public void UpdateGamepadUI()
        {
            if (this.InvokeRequired)
            {
                this.BeginInvoke(new Action(UpdateGamepadUI));
                return;
            }

            try
            {
                UpdateModernGamepadPill();
            }
            catch { }
        }

        private void GamepadTimer_Tick(object sender, EventArgs e)
        {
            try
            {
                // 1. Quét cắm nóng (Hot-plugging) định kỳ mỗi 1 giây hoặc khi chưa kết nối
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

                                // 2. Đọc trạng thái Gamepad
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
                float rightY = gamepad.RightThumbY; // Đổi số tốc độ (Gear Speed)

                if (Math.Abs(leftY) < DEADZONE) leftY = 0;
                if (Math.Abs(leftX) < DEADZONE) leftX = 0;
                if (Math.Abs(rightX) < DEADZONE) rightX = 0;

                // Cần phải Y (Lên/Xuống) điều chỉnh tốc độ tối đa mượt mà
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

                        // Mecanum kinematics:
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
                // C. ĐIỀU KHIỂN GIMBAL RS 3 MINI QUA D-PAD & RIGHT THUMB
                // -------------------------------------------------------------
                var currentButtons = gamepad.Buttons;

                bool dUp = currentButtons.HasFlag(GamepadButtonFlags.DPadUp);
                bool dDown = currentButtons.HasFlag(GamepadButtonFlags.DPadDown);
                bool dLeft = currentButtons.HasFlag(GamepadButtonFlags.DPadLeft);
                bool dRight = currentButtons.HasFlag(GamepadButtonFlags.DPadRight);

                bool prevUp = previousButtons.HasFlag(GamepadButtonFlags.DPadUp);
                bool prevDown = previousButtons.HasFlag(GamepadButtonFlags.DPadDown);
                bool prevLeft = previousButtons.HasFlag(GamepadButtonFlags.DPadLeft);
                bool prevRight = previousButtons.HasFlag(GamepadButtonFlags.DPadRight);

                if (dUp != prevUp) TriggerGimbalMove("GIMBAL_UP", dUp, 0, dUp ? 1 : 0, _btnGimbalUp);
                if (dDown != prevDown) TriggerGimbalMove("GIMBAL_DOWN", dDown, 0, dDown ? -1 : 0, _btnGimbalDown);
                if (dLeft != prevLeft) TriggerGimbalMove("GIMBAL_LEFT", dLeft, dLeft ? -1 : 0, 0, _btnGimbalLeft);
                if (dRight != prevRight) TriggerGimbalMove("GIMBAL_RIGHT", dRight, dRight ? 1 : 0, 0, _btnGimbalRight);

                // Nhấn cần phải (RightThumb Click) -> Căn giữa Gimbal (Center)
                if (currentButtons.HasFlag(GamepadButtonFlags.RightThumb) && !previousButtons.HasFlag(GamepadButtonFlags.RightThumb))
                {
                    if (_btnGimbalCenter != null) FlashButtonClicked(_btnGimbalCenter, BTN_BLUE_COLOR);
                    SendButtonSyncEvent("GIMBAL_RECENTER", "CLICK");
                    SendCameraHttpCommand("gimbal/recenter");
                    LabelAnnouncement.Text = "Gimbal RS3 Mini: Đã căn giữa (Center)...";
                }

                // Nhấn cần trái (LeftThumb Click) -> Chuyển đổi Turbo / Bình thường (10 <-> 20 rad/s)
                if (currentButtons.HasFlag(GamepadButtonFlags.LeftThumb) && !previousButtons.HasFlag(GamepadButtonFlags.LeftThumb))
                {
                    maxGamepadSpeed = (maxGamepadSpeed == 10.0f) ? 20.0f : 10.0f;
                    UpdateGamepadUI();
                }

                // -------------------------------------------------------------
                // D. CÒ TRIGGER (LT / RT)
                // -------------------------------------------------------------
                bool currentLT = gamepad.LeftTrigger > 128;
                bool currentRT = gamepad.RightTrigger > 128;

                if (currentLT && !previousLT) { TakePhotoButton_Click(TakePhotoButton, EventArgs.Empty); }
                if (currentRT && !previousRT) { SwitchCameraButton_Click(SwitchCameraButton, EventArgs.Empty); }

                previousLT = currentLT;
                previousRT = currentRT;

                // -------------------------------------------------------------
                // E. NÚT VAI (LB / RB) & CÁC NÚT BẤM (A, B, X, Y, START, BACK)
                // -------------------------------------------------------------
                if (currentButtons.HasFlag(GamepadButtonFlags.LeftShoulder) && !previousButtons.HasFlag(GamepadButtonFlags.LeftShoulder))
                { StartRecordVideoButton_Click(null, null); }

                if (currentButtons.HasFlag(GamepadButtonFlags.RightShoulder) && !previousButtons.HasFlag(GamepadButtonFlags.RightShoulder))
                { StopRecordVideoButton_Click(null, null); }

                if (currentButtons.HasFlag(GamepadButtonFlags.X) && !previousButtons.HasFlag(GamepadButtonFlags.X))
                { EmergencyStopButton_Click(null, null); } // X -> Dừng khẩn cấp

                if (currentButtons.HasFlag(GamepadButtonFlags.Y) && !previousButtons.HasFlag(GamepadButtonFlags.Y))
                { CircleLineButton_Click(null, null); } // Y -> Chạy quỹ đạo tròn

                if (currentButtons.HasFlag(GamepadButtonFlags.A) && !previousButtons.HasFlag(GamepadButtonFlags.A))
                { ReturnHomeButton_Click(null, null); } // A -> Về gốc tọa độ

                if (currentButtons.HasFlag(GamepadButtonFlags.B) && !previousButtons.HasFlag(GamepadButtonFlags.B))
                { ResetRobotButton_Click(null, null); } // B -> Dừng mềm & Reset

                if (currentButtons.HasFlag(GamepadButtonFlags.Start) && !previousButtons.HasFlag(GamepadButtonFlags.Start))
                {
                    if (!isConnected) ConnectButton.PerformClick();
                    else DisconnectButton.PerformClick();
                }

                if (currentButtons.HasFlag(GamepadButtonFlags.Back) && !previousButtons.HasFlag(GamepadButtonFlags.Back))
                {
                    CyclePermissionMode();
                }

                previousButtons = currentButtons;
            }
            catch { }
        }

        // ĐI THẲNG TỚI
        private void UpButton_MouseDown(object sender, MouseEventArgs e)
        {
            if (!CheckLocalControlPermission()) return;
            SetButtonHeldState(UpButton, true);
            state_run = 0x03; set_w1 = manualSpeed; set_w2 = manualSpeed; set_w3 = manualSpeed; set_w4 = manualSpeed;
            ActivateLocalDriving();
            if (sender != null) SendButtonSyncEvent("UP", "DOWN");
        }

        // ĐI LÙI
        private void DownButton_MouseDown(object sender, MouseEventArgs e)
        {
            if (!CheckLocalControlPermission()) return;
            SetButtonHeldState(DownButton, true);
            state_run = 0x03; set_w1 = -manualSpeed; set_w2 = -manualSpeed; set_w3 = -manualSpeed; set_w4 = -manualSpeed;
            ActivateLocalDriving();
            if (sender != null) SendButtonSyncEvent("DOWN", "DOWN");
        }

        // ĐI SANG TRÁI
        private void LeftButton_MouseDown(object sender, MouseEventArgs e)
        {
            if (!CheckLocalControlPermission()) return;
            SetButtonHeldState(LeftButton, true);
            state_run = 0x03; set_w1 = -manualSpeed; set_w2 = manualSpeed; set_w3 = manualSpeed; set_w4 = -manualSpeed;
            ActivateLocalDriving();
            if (sender != null) SendButtonSyncEvent("LEFT", "DOWN");
        }
        //ĐI SANG PHẢI  
        private void RightButton_MouseDown(object sender, MouseEventArgs e)
        {
            if (!CheckLocalControlPermission()) return;
            SetButtonHeldState(RightButton, true);
            state_run = 0x03; set_w1 = manualSpeed; set_w2 = -manualSpeed; set_w3 = -manualSpeed; set_w4 = manualSpeed;
            ActivateLocalDriving();
            if (sender != null) SendButtonSyncEvent("RIGHT", "DOWN");
        }
        // XOAY TRÁI TẠI CHỖ
        private void TurnLeftButton_MouseDown(object sender, MouseEventArgs e)
        {
            if (!CheckLocalControlPermission()) return;
            SetButtonHeldState(TurnLeftButton, true);
            state_run = 0x03; set_w1 = -manualSpeed; set_w2 = -manualSpeed; set_w3 = manualSpeed; set_w4 = manualSpeed;
            ActivateLocalDriving();
            if (sender != null) SendButtonSyncEvent("TURN_LEFT", "DOWN");
        }

        // XOAY PHẢI TẠI CHỖ
        private void TurnRightButton_MouseDown(object sender, MouseEventArgs e)
        {
            if (!CheckLocalControlPermission()) return;
            SetButtonHeldState(TurnRightButton, true);
            state_run = 0x03; set_w1 = manualSpeed; set_w2 = manualSpeed; set_w3 = -manualSpeed; set_w4 = -manualSpeed;
            ActivateLocalDriving();
            if (sender != null) SendButtonSyncEvent("TURN_RIGHT", "DOWN");
        }

        private void UpButton_MouseUp(object sender, MouseEventArgs e)
        {
            SetButtonHeldState(UpButton, false);
            if (_permissionMode == ControlPermissionMode.Viewer) return;
            state_run = 0x01; // Nhả ra thì dừng
            set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
            StopLocalDrivingWithBurst();
            if (sender != null) SendButtonSyncEvent("UP", "UP");
        }
        private void DownButton_MouseUp(object sender, MouseEventArgs e)
        {
            SetButtonHeldState(DownButton, false);
            if (_permissionMode == ControlPermissionMode.Viewer) return;
            state_run = 0x01;
            set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
            StopLocalDrivingWithBurst();
            if (sender != null) SendButtonSyncEvent("DOWN", "UP");
        }
        private void LeftButton_MouseUp(object sender, MouseEventArgs e)
        {
            SetButtonHeldState(LeftButton, false);
            if (_permissionMode == ControlPermissionMode.Viewer) return;
            state_run = 0x01;
            set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
            StopLocalDrivingWithBurst();
            if (sender != null) SendButtonSyncEvent("LEFT", "UP");
        }
        private void RightButton_MouseUp(object sender, MouseEventArgs e)
        {
            SetButtonHeldState(RightButton, false);
            if (_permissionMode == ControlPermissionMode.Viewer) return;
            state_run = 0x01;
            set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
            StopLocalDrivingWithBurst();
            if (sender != null) SendButtonSyncEvent("RIGHT", "UP");
        }

        private void TurnLeftButton_MouseUp(object sender, MouseEventArgs e)
        {
            SetButtonHeldState(TurnLeftButton, false);
            if (_permissionMode == ControlPermissionMode.Viewer) return;
            state_run = 0x01;
            set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
            StopLocalDrivingWithBurst();
            if (sender != null) SendButtonSyncEvent("TURN_LEFT", "UP");
        }
        private void TurnRightButton_MouseUp(object sender, MouseEventArgs e)
        {
            SetButtonHeldState(TurnRightButton, false);
            if (_permissionMode == ControlPermissionMode.Viewer) return;
            state_run = 0x01;
            set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
            StopLocalDrivingWithBurst();
            if (sender != null) SendButtonSyncEvent("TURN_RIGHT", "UP");
        }

        // --- SET SPEED TỪ TEXTBOX ---
        private void SetMotorButton_Click(object sender, EventArgs e)
        {
            if (!CheckLocalControlPermission()) return;
            FlashButtonClicked(SetMotorButton, BTN_HELD_COLOR);
            if (float.TryParse(SetW1TextBox.Text, out float w1_val) &&
                float.TryParse(SetW2TextBox.Text, out float w2_val) &&
                float.TryParse(SetW3TextBox.Text, out float w3_val) &&
                float.TryParse(SetW4TextBox.Text, out float w4_val))
            {
                state_run = 0x02; // Tùy theo logic Pi của bạn (0x02 hoặc 0x03)
                set_w1 = w1_val;
                set_w2 = w2_val;
                set_w3 = w3_val;
                set_w4 = w4_val;
                ActivateLocalDriving();
                if (sender != null) SendButtonSyncEvent("SET_WHEELS", "CLICK", $"{set_w1},{set_w2},{set_w3},{set_w4}");
                LabelAnnouncement.Text = $"Wheel velocity set: wRL={set_w1}, wFL={set_w2}, wRR={set_w3}, wFR={set_w4}";
            }
            else
            {
                LabelAnnouncement.Text = "Lỗi: Vui lòng nhập đúng định dạng số tốc độ bánh!";
            }
        }
        private void ResetMotorButton_Click(object sender, EventArgs e)
        {
            if (!CheckLocalControlPermission()) return;
            FlashButtonClicked(ResetButton, System.Drawing.Color.Gray);
            // Xóa chữ trên UI
            SetW1TextBox.Text = "0";
            SetW2TextBox.Text = "0";
            SetW3TextBox.Text = "0";
            SetW4TextBox.Text = "0";
            if (sender != null) SendButtonSyncEvent("RESET_WHEELS", "CLICK");
            LabelAnnouncement.Text = "Wheel velocities reset to 0";
        }

        private void StartGraphButton_Click(object sender, EventArgs e)
        {
            // Nếu bấm Start từ trạng thái Stop hẳn, ta xóa trắng đồ thị cũ để vẽ lại từ đầu
            if (isStopped)
            {
                // Xóa Data vẽ đồ thị
                timeData.Clear(); trajX.Clear(); trajY.Clear(); trajX_d.Clear(); trajY_d.Clear();
                listX.Clear(); listXd.Clear(); listY.Clear(); listYd.Clear();
                listTh.Clear(); listThd.Clear();
                listVx.Clear(); listVxd.Clear(); listVy.Clear(); listVyd.Clear(); listW.Clear(); listWd.Clear();
                listW1.Clear(); listW1d.Clear(); listW2.Clear(); listW2d.Clear();
                listW3.Clear(); listW3d.Clear(); listW4.Clear(); listW4d.Clear();

                // Xóa Data CSV
                csvExportList.Clear();

                // Reset đồng hồ
                _robotTimer.Restart();
                isStopped = false;
            }

            isPlotting = true;
            PauseGraphButton.Text = "Pause"; // Reset chữ nếu trước đó đang pause
        }

        private void tableLayoutPanel2_Paint(object sender, PaintEventArgs e)
        {

        }

        private void PauseGraphButton_Click(object sender, EventArgs e)
        {
            if (!isStopped)
            {
                isPlotting = !isPlotting; // Đảo trạng thái vẽ/dừng vẽ
                PauseGraphButton.Text = isPlotting ? "Pause" : "Resume";
            }
        }

        private void StopGraphButton_Click(object sender, EventArgs e)
        {
            isPlotting = false;
            isStopped = true;
            PauseGraphButton.Text = "Pause";
        }

        private void ExportCSVButton_Click(object sender, EventArgs e)
        {
            if (isPlotting)
            {
                MessageBox.Show("Vui lòng Pause hoặc Stop trước khi xuất dữ liệu!", "Cảnh báo", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            if (csvExportList.Count == 0)
            {
                MessageBox.Show("Không có dữ liệu nào để xuất!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            using (SaveFileDialog saveFileDialog = new SaveFileDialog())
            {
                saveFileDialog.Filter = "CSV files (*.csv)|*.csv";
                saveFileDialog.Title = "Save Robot Data";
                saveFileDialog.FileName = $"RobotData_{DateTime.Now:yyyyMMdd_HHmmss}.csv";

                if (saveFileDialog.ShowDialog() == DialogResult.OK)
                {
                    try
                    {
                        using (System.IO.StreamWriter writer = new System.IO.StreamWriter(saveFileDialog.FileName))
                        {
                            // 1. Ghi tiêu đề (Header)
                            writer.WriteLine("Time(s),X_d,X,Y_d,Y,Theta_d,Theta,Vx_d,Vx,Vy_d,Vy,W_d,W,W1_d,W1,W2_d,W2,W3_d,W3,W4_d,W4");

                            // 2. Ghi từng dòng dữ liệu
                            foreach (var row in csvExportList)
                            {
                                string line = string.Format(System.Globalization.CultureInfo.InvariantCulture,
                                    "{0:F3},{1:F3},{2:F3},{3:F3},{4:F3},{5:F3},{6:F3},{7:F3},{8:F3},{9:F3},{10:F3},{11:F3},{12:F3},{13:F3},{14:F3},{15:F3},{16:F3},{17:F3},{18:F3},{19:F3},{20:F3}",
                                    row.Time,
                                    row.Xd, row.X, row.Yd, row.Y, row.Thetad, row.Theta,
                                    row.Vxd, row.Vx, row.Vyd, row.Vy, row.Wd, row.W,
                                    row.W1d, row.W1, row.W2d, row.W2, row.W3d, row.W3, row.W4d, row.W4);

                                writer.WriteLine(line);
                            }
                        }
                        MessageBox.Show($"Xuất thành công {csvExportList.Count} dòng dữ liệu!", "Thành công", MessageBoxButtons.OK, MessageBoxIcon.Information);
                    }
                    catch (Exception ex)
                    {
                        MessageBox.Show($"Lỗi ghi file: {ex.Message}", "Lỗi", MessageBoxButtons.OK, MessageBoxIcon.Error);
                    }
                }
            }
        }

        private void CircleLineButton_Click(object sender, EventArgs e)
        {
            if (!CheckLocalControlPermission()) return;
            FlashButtonClicked(CircleLineButton, BTN_PURPLE_COLOR);
            state_run = 0x04; // Gửi mã ID chạy quỹ đạo tròn
            ActivateLocalDriving();
            if (sender != null) SendButtonSyncEvent("CIRCLE_LINE", "CLICK");
            LabelAnnouncement.Text = "Mode: CIRCLE LINE -> Sent to Robot & Tablet";
        }

        private void ReturnHomeButton_Click(object sender, EventArgs e)
        {
            if (!CheckLocalControlPermission()) return;
            FlashButtonClicked(ReturnHomeButton, BTN_GREEN_COLOR);
            state_run = 0x05; // Gửi mã ID quay về điểm Home (0,0,0)

            // Đặt tọa độ đích về 0 luôn cho chắc chắn (dự phòng Pi dùng các biến này)
            set_x = 0.0f;
            set_y = 0.0f;
            set_theta = 0.0f;
            ActivateLocalDriving();

            if (sender != null) SendButtonSyncEvent("RETURN_HOME", "CLICK");
            LabelAnnouncement.Text = "Mode: RETURN HOME -> Sent to Robot & Tablet";
        }

        private void SetRobotButton_Click(object sender, EventArgs e)
        {
            if (!CheckLocalControlPermission()) return;
            FlashButtonClicked(SetRobotButton, BTN_HELD_COLOR);
            // Sử dụng float.TryParse để chống lỗi crash app nếu người dùng nhập chữ hoặc để trống
            if (float.TryParse(Vx_dTextBox.Text, out float vx_val) &&
                float.TryParse(Vy_dTextBox.Text, out float vy_val) &&
                float.TryParse(W_dTextBox.Text, out float w_val) &&
                float.TryParse(X_dTextBox.Text, out float x_val) &&
                float.TryParse(Y_dTextBox.Text, out float y_val) &&
                float.TryParse(Theta_dTextBox.Text, out float theta_deg))
            {
                // Gán các giá trị tốc độ và tọa độ
                set_vx = vx_val;
                set_vy = vy_val;
                set_w = w_val;
                set_x = x_val;
                set_y = y_val;

                // Chuyển đổi Theta từ ĐỘ (Degree) sang RADIAN
                set_theta = (float)(theta_deg * Math.PI / 180.0);

                // Đổi trạng thái sang ID điều khiển bám quỹ đạo/điểm
                state_run = 0x06;
                ActivateLocalDriving();

                if (sender != null) SendButtonSyncEvent("SET_ROBOT", "CLICK", $"{set_vx},{set_vy},{set_w},{set_x},{set_y},{theta_deg}");
                LabelAnnouncement.Text = $"Setpoint: Vx={set_vx}, Vy={set_vy}, W={set_w}, X={set_x}, Y={set_y}";
            }
            else
            {
                LabelAnnouncement.Text = "Lỗi: Vui lòng nhập đúng định dạng số vào tất cả các ô Setpoint!";
            }
        }

        private void ResetRobotButton_Click(object sender, EventArgs e)
        {
            if (!CheckLocalControlPermission()) return;
            FlashButtonClicked(ResetRobotButton, System.Drawing.Color.Gray);
            // Xóa chữ trên UI
            Vx_dTextBox.Text = "0";
            Vy_dTextBox.Text = "0";
            W_dTextBox.Text = "0";
            X_dTextBox.Text = "0";
            Y_dTextBox.Text = "0";
            Theta_dTextBox.Text = "0";
            if (sender != null) SendButtonSyncEvent("RESET_ROBOT", "CLICK");
            LabelAnnouncement.Text = "Robot parameters reset to 0";
        }

        private void EmergencyStopButton_Click(object sender, EventArgs e)
        {
            // Nút Dừng khẩn cấp luôn được phép kích hoạt ngay cả ở chế độ Chỉ xem vì lý do an toàn
            FlashButtonClicked(EmergencyStopButton, BTN_EMERGENCY_COLOR);
            // Đưa xe về mode Stop (Code của bạn quy định 0x01 là Stop)
            state_run = 0x01;

            // Ép toàn bộ vận tốc về 0 ngay lập tức
            set_x = 0.0f; set_y = 0.0f; set_theta = 0.0f;
            set_vx = 0.0f; set_vy = 0.0f; set_w = 0.0f;
            set_w1 = 0.0f; set_w2 = 0.0f;
            set_w3 = 0.0f; set_w4 = 0.0f;
            StopLocalDrivingWithBurst();

            // Stop luôn phần vẽ đồ thị để tiện theo dõi khoảnh khắc xe bị ngắt
            isPlotting = false;
            PauseGraphButton.Text = "Resume";

            if (sender != null) SendButtonSyncEvent("EMERGENCY", "CLICK");
            LabelAnnouncement.Text = "EMERGENCY STOP ACTIVATED -> Robot stopped!";
        }

        private void TakePhotoButton_Click(object sender, EventArgs e)
        {
            FlashButtonClicked(TakePhotoButton, BTN_AMBER_COLOR);
            state_run = 0x07;
            if (!Equals(sender, "REMOTE"))
            {
                StopLocalDrivingWithBurst(6);
                SendButtonSyncEvent("TAKE_PHOTO", "CLICK");
                SendCameraHttpCommand("snap");
            }
            LabelAnnouncement.Text = "Command: TAKE PHOTO -> Sent to Tablet";
        }

        private void StartRecordVideoButton_Click(object sender, EventArgs e)
        {
            FlashButtonClicked(StartRecordVideoButton, BTN_EMERGENCY_COLOR);
            state_run = 0x08;
            if (!Equals(sender, "REMOTE"))
            {
                StopLocalDrivingWithBurst(6);
                SendButtonSyncEvent("START_RECORD", "CLICK");
                SendCameraHttpCommand("record/start");
            }
            LabelAnnouncement.Text = "Command: START RECORD -> Sent to Tablet";
        }

        private void StopRecordVideoButton_Click(object sender, EventArgs e)
        {
            FlashButtonClicked(StopRecordVideoButton, System.Drawing.Color.Gray);
            state_run = 0x09;
            if (!Equals(sender, "REMOTE"))
            {
                StopLocalDrivingWithBurst(6);
                SendButtonSyncEvent("STOP_RECORD", "CLICK");
                SendCameraHttpCommand("record/stop");
            }
            LabelAnnouncement.Text = "Command: STOP RECORD -> Sent to Tablet";
        }

        private void SwitchCameraButton_Click(object sender, EventArgs e)
        {
            FlashButtonClicked(SwitchCameraButton, BTN_PURPLE_COLOR);
            state_run = 0x0A;
            if (!Equals(sender, "REMOTE"))
            {
                StopLocalDrivingWithBurst(6);
                SendButtonSyncEvent("SWITCH_CAMERA", "CLICK");
                SendCameraHttpCommand("switch");
            }
            LabelAnnouncement.Text = "Command: SWITCH CAMERA -> Sent to Tablet";
        }
        private void StartStreamButton_Click(object sender, EventArgs e)
        {
            if (!Equals(sender, "REMOTE")) SendButtonSyncEvent("START_STREAM", "CLICK");

            if (streamForm == null || streamForm.IsDisposed)
            {
                streamForm = new CameraStreamForm();
            }

            // Nếu streamForm đã mở thì đưa lên phía trước, không mở thêm cửa sổ thứ 2
            if (!streamForm.Visible)
            {
                streamForm.Show();
            }
            else
            {
                streamForm.BringToFront();
            }
            streamForm.Text = "Live Camera Stream (Robot Camera)";

            // Lấy IP từ ô nhập txtCameraIp (mặc định 192.168.2.80)
            string cameraIp = txtCameraIp != null && !string.IsNullOrWhiteSpace(txtCameraIp.Text)
                ? txtCameraIp.Text.Trim()
                : "192.168.2.80";

            string streamUrl;
            if (cameraIp.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
                cameraIp.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                streamUrl = cameraIp;
            }
            else
            {
                if (cameraIp.Contains(":8081"))
                {
                    streamUrl = $"http://admin:admin@{cameraIp}/";
                }
                else if (!cameraIp.Contains(":"))
                {
                    streamUrl = $"http://{cameraIp}:8080/video.mjpeg";
                }
                else
                {
                    streamUrl = $"http://{cameraIp}/video.mjpeg";
                }
            }
            streamForm.PlayStream(streamUrl);
            LabelAnnouncement.Text = "Live Camera Stream started";
        }

        private void StopStreamButton_Click(object sender, EventArgs e)
        {
            // Gửi lệnh 0x0C xuống Pi (nếu cần)
            state_run = 0x0C;
            if (!Equals(sender, "REMOTE")) SendButtonSyncEvent("STOP_STREAM", "CLICK");

            // Dừng stream và ẩn form
            streamForm.StopStream();
            streamForm.Hide();
            LabelAnnouncement.Text = "Live Camera Stream stopped";
        }

        private void RenderTimer_Tick(object sender, EventArgs e)
        {
            try
            {
                bool hasNewData = false;
                RobotDataRow lastRow = null;

                // 1. Rút TOÀN BỘ dữ liệu đang xếp hàng ra xử lý siêu tốc
                while (dataQueue.TryDequeue(out RobotDataRow row))
                {
                    hasNewData = true;
                    lastRow = row;

                    if (isPlotting)
                    {
                        csvExportList.Add(row);

                        // Bộ lọc: Chỉ vẽ quỹ đạo nếu xe nhích hơn 5mm
                        bool shouldAddPoint = true;
                        if (trajX.Count > 0)
                        {
                            double dist = Math.Sqrt(Math.Pow(row.X - trajX.Last(), 2) + Math.Pow(row.Y - trajY.Last(), 2));
                            if (dist < 0.005) shouldAddPoint = false;
                        }
                        if (shouldAddPoint)
                        {
                            trajX.Add(row.X); trajY.Add(row.Y);
                            trajX_d.Add(row.Xd); trajY_d.Add(row.Yd);
                        }

                        // Nạp dữ liệu đồ thị thời gian
                        timeData.Add(row.Time);
                        listX.Add(row.X); listXd.Add(row.Xd);
                        listY.Add(row.Y); listYd.Add(row.Yd);
                        listTh.Add(row.Theta); listThd.Add(row.Thetad);

                        listVx.Add(row.Vx); listVxd.Add(row.Vxd);
                        listVy.Add(row.Vy); listVyd.Add(row.Vyd);
                        listW.Add(row.W); listWd.Add(row.Wd);

                        listW1.Add(row.W1); listW1d.Add(row.W1d);
                        listW2.Add(row.W2); listW2d.Add(row.W2d);
                        listW3.Add(row.W3); listW3d.Add(row.W3d);
                        listW4.Add(row.W4); listW4d.Add(row.W4d);
                    }
                }

                // 2. Chỉ vẽ lại UI NẾU CÓ DỮ LIỆU MỚI (Tiết kiệm CPU)
                if (hasNewData && lastRow != null)
                {
                    // Cập nhật Textbox 1 lần duy nhất bằng gói tin mới nhất
                    LabelAnnouncement.Text = $"Seq: {lastSeq} | Data Received";
                    CurrentXTextBox.Text = lastRow.X.ToString("F3");
                    CurrentYTextBox.Text = lastRow.Y.ToString("F3");
                    CurrentThetaTextBox.Text = (lastRow.Theta * 180.0 / Math.PI).ToString("F2");
                    CurrentVxTextBox.Text = lastRow.Vx.ToString("F3");
                    CurrentVyTextBox.Text = lastRow.Vy.ToString("F3");
                    CurrentWTextBox.Text = lastRow.W.ToString("F3");

                    CurrentW1TextBox.Text = lastRow.W1.ToString("F3");
                    CurrentW2TextBox.Text = lastRow.W2.ToString("F3");
                    CurrentW3TextBox.Text = lastRow.W3.ToString("F3");
                    CurrentW4TextBox.Text = lastRow.W4.ToString("F3");

                    if (isPlotting)
                    {
                        // Tối ưu xóa dữ liệu cũ bằng RemoveRange
                        int removeCount = 0;
                        while (removeCount < timeData.Count && (lastRow.Time - timeData[removeCount] > 30)) { removeCount++; }
                        if (removeCount > 0)
                        {
                            timeData.RemoveRange(0, removeCount);
                            listX.RemoveRange(0, removeCount); listXd.RemoveRange(0, removeCount);
                            listY.RemoveRange(0, removeCount); listYd.RemoveRange(0, removeCount);
                            listTh.RemoveRange(0, removeCount); listThd.RemoveRange(0, removeCount);
                            listVx.RemoveRange(0, removeCount); listVxd.RemoveRange(0, removeCount);
                            listVy.RemoveRange(0, removeCount); listVyd.RemoveRange(0, removeCount);
                            listW.RemoveRange(0, removeCount); listWd.RemoveRange(0, removeCount);
                            listW1.RemoveRange(0, removeCount); listW1d.RemoveRange(0, removeCount);
                            listW2.RemoveRange(0, removeCount); listW2d.RemoveRange(0, removeCount);
                            listW3.RemoveRange(0, removeCount); listW3d.RemoveRange(0, removeCount);
                            listW4.RemoveRange(0, removeCount); listW4d.RemoveRange(0, removeCount);
                        }

                        // Xóa Mũi tên cũ và vẽ Mũi tên Theta mới
                        if (directionArrow != null) TrajectoryPlot.Plot.Remove(directionArrow);
                        double arrowLength = 0.3;
                        double tipX = lastRow.X + arrowLength * Math.Cos(lastRow.Theta);
                        double tipY = lastRow.Y + arrowLength * Math.Sin(lastRow.Theta);
                        directionArrow = TrajectoryPlot.Plot.Add.Arrow(lastRow.X, lastRow.Y, tipX, tipY);
                        directionArrow.ArrowLineColor = Colors.Blue; directionArrow.ArrowFillColor = Colors.Blue;
                        directionArrow.ArrowheadLength = 10f; directionArrow.ArrowheadWidth = 10f;

                        // Xử lý đồ thị Tab hiện tại
                        switch (_activeTabIndex)
                        {
                            case 0:
                                if (checkTrajectoryAutoScale.Checked) TrajectoryPlot.Plot.Axes.AutoScale();
                                TrajectoryPlot.Refresh();
                                break;
                            case 1:
                                if (checkPositionRobotAutoScale.Checked)
                                {
                                    PlotXTime.Plot.Axes.AutoScale();
                                    PlotYTime.Plot.Axes.AutoScale();
                                    PlotThetaTime.Plot.Axes.AutoScale();
                                }
                                PlotXTime.Refresh(); PlotYTime.Refresh(); PlotThetaTime.Refresh();
                                break;
                            case 2:
                                if (checkVelocityRobotAutoScale.Checked)
                                {
                                    PlotVxTime.Plot.Axes.AutoScale();
                                    PlotVyTime.Plot.Axes.AutoScale();
                                    PlotWTime.Plot.Axes.AutoScale();
                                }
                                PlotVxTime.Refresh(); PlotVyTime.Refresh(); PlotWTime.Refresh();
                                break;
                            case 3:
                                if (checkVelocityMotorsAutoScale.Checked)
                                {
                                    PlotW1Time.Plot.Axes.AutoScale();
                                    PlotW2Time.Plot.Axes.AutoScale();
                                    PlotW3Time.Plot.Axes.AutoScale();
                                    PlotW4Time.Plot.Axes.AutoScale();
                                }
                                PlotW1Time.Refresh(); PlotW2Time.Refresh(); PlotW3Time.Refresh(); PlotW4Time.Refresh();
                                break;
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                // Nếu có lỗi (ví dụ đồ thị lag), âm thầm nuốt lỗi này, 
                // bỏ qua chu kỳ vẽ này và vẽ lại ở 50ms tiếp theo.
                // App sẽ TUYỆT ĐỐI KHÔNG BAO GIỜ BỊ VĂNG NỮA!
                Console.WriteLine("Bỏ qua 1 khung hình do lỗi: " + ex.Message);
            }
        }

        // =========================================================================
        // 3. CÁC HÀM TIỆN ÍCH HỖ TRỢ (CRC & ĐÓNG FORM)
        // =========================================================================

        private ushort CalculateCRC16(byte[] data, int length)
        {
            ushort crc = 0xFFFF;
            for (int i = 0; i < length; ++i)
            {
                crc ^= data[i];
                for (int j = 0; j < 8; ++j)
                {
                    if ((crc & 1) != 0)
                    {
                        crc >>= 1;
                        crc ^= 0xA001;
                    }
                    else
                    {
                        crc >>= 1;
                    }
                }
            }
            return crc;
        }

        private void Form1_FormClosing(object sender, FormClosingEventArgs e)
        {
            buttonSyncHeartbeatTimer?.Stop();
            buttonSyncHeartbeatTimer?.Dispose();
            buttonSyncCts?.Cancel();
            buttonSyncUdpListener?.Close();
            gamepadTimer?.Stop();
            gamepadTimer?.Dispose();
            isConnected = false;
            cancellationTokenSource?.Cancel();
            udpClient?.Close();
            try { streamForm?.StopStream(); } catch { }
        }

        // =========================================================================
        // 4. ĐỒNG BỘ NÚT BẤM REAL-TIME GIỮA TABLET VÀ PC (BUTTON SYNC)
        // =========================================================================

        private void SetButtonHeldState(Button btn, bool isHeld, System.Drawing.Color? customColor = null)
        {
            if (btn == null || btn.IsDisposed) return;
            try
            {
                if (isHeld)
                {
                    if (!originalButtonStyles.ContainsKey(btn))
                    {
                        originalButtonStyles[btn] = (btn.BackColor, btn.ForeColor, btn.FlatStyle);
                    }
                    System.Drawing.Color activeColor = customColor ?? (btn == EmergencyStopButton ? BTN_EMERGENCY_COLOR : BTN_HELD_COLOR);
                    btn.BackColor = activeColor;
                    btn.ForeColor = System.Drawing.Color.White;
                    btn.FlatStyle = FlatStyle.Flat;
                    btn.FlatAppearance.BorderColor = System.Drawing.Color.White;
                    btn.FlatAppearance.BorderSize = 2;
                    btn.Invalidate();
                }
                else
                {
                    if (originalButtonStyles.TryGetValue(btn, out var orig))
                    {
                        btn.FlatStyle = orig.style;
                        btn.BackColor = orig.back;
                        btn.ForeColor = orig.fore;
                        btn.FlatAppearance.BorderSize = 1;
                        btn.UseVisualStyleBackColor = true;
                        btn.Invalidate();
                    }
                    else
                    {
                        btn.FlatStyle = FlatStyle.Standard;
                        btn.UseVisualStyleBackColor = true;
                        btn.Invalidate();
                        btn.ForeColor = SystemColors.ControlText;
                        btn.Invalidate();
                    }
                }
            }
            catch { }
        }

        private void FlashButtonClicked(Button btn, System.Drawing.Color flashColor)
        {
            if (btn == null || btn.IsDisposed) return;
            try
            {
                SetButtonHeldState(btn, true, flashColor);

                var timer = new System.Windows.Forms.Timer { Interval = 280 };
                timer.Tick += (s, e) =>
                {
                    timer.Stop();
                    timer.Dispose();
                    if (!btn.IsDisposed)
                    {
                        SetButtonHeldState(btn, false);
                    }
                };
                timer.Start();
            }
            catch { }
        }

        private void StartButtonSyncListener()
        {
            try
            {
                buttonSyncCts = new CancellationTokenSource();
                buttonSyncUdpListener = new UdpClient();
                buttonSyncUdpListener.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                buttonSyncUdpListener.Client.Bind(new IPEndPoint(IPAddress.Any, 5005));
                buttonSyncUdpListener.EnableBroadcast = true;

                Task.Run(async () =>
                {
                    while (!buttonSyncCts.Token.IsCancellationRequested)
                    {
                        try
                        {
                            var result = await buttonSyncUdpListener.ReceiveAsync();
                            // Bỏ qua gói tin do chính PC phát broadcast tự vọng lại (echo)
                            if (IsLocalIpAddress(result.RemoteEndPoint.Address))
                            {
                                continue;
                            }

                            string msg = Encoding.UTF8.GetString(result.Buffer);
                            if (!string.IsNullOrEmpty(msg))
                            {
                                if (msg.StartsWith("BTN:"))
                                {
                                    if (this.IsHandleCreated && !this.IsDisposed)
                                    {
                                        this.BeginInvoke(new Action(() => ProcessRemoteButtonEvent(msg)));
                                    }
                                }
                            }
                        }
                        catch (ObjectDisposedException) { break; }
                        catch (SocketException) { break; }
                        catch (Exception) { }
                    }
                }, buttonSyncCts.Token);

                // Heartbeat gửi định kỳ để Tablet biết IP PC
                buttonSyncHeartbeatTimer = new System.Windows.Forms.Timer { Interval = 2500 };
                buttonSyncHeartbeatTimer.Tick += (s, e) => SendButtonSyncHeartbeat();
                buttonSyncHeartbeatTimer.Start();
                SendButtonSyncHeartbeat();
            }
            catch (Exception ex)
            {
                Debug.WriteLine("Lỗi khởi động button sync listener: " + ex.Message);
            }
        }

        private void SendButtonSyncHeartbeat()
        {
            try
            {
                string camIp = txtCameraIp != null && !string.IsNullOrWhiteSpace(txtCameraIp.Text)
                    ? txtCameraIp.Text.Trim()
                    : "192.168.2.80";

                byte[] helloBytes = Encoding.UTF8.GetBytes("HELLO_PC");

                if (!string.IsNullOrEmpty(camIp))
                {
                    buttonSyncUdpListener?.SendAsync(helloBytes, helloBytes.Length, camIp, 5005);
                }
                buttonSyncUdpListener?.SendAsync(helloBytes, helloBytes.Length, "192.168.2.255", 5005);
                buttonSyncUdpListener?.SendAsync(helloBytes, helloBytes.Length, "255.255.255.255", 5005);
            }
            catch { }
        }

        private void SendButtonSyncEvent(string btnId, string action, string extra = null)
        {
            try
            {
                string payload = extra != null ? $"BTN:{btnId}:{action}:{extra}" : $"BTN:{btnId}:{action}";
                byte[] data = Encoding.UTF8.GetBytes(payload);
                string camIp = txtCameraIp != null && !string.IsNullOrWhiteSpace(txtCameraIp.Text)
                    ? txtCameraIp.Text.Trim()
                    : "192.168.2.80";

                if (!string.IsNullOrEmpty(camIp))
                {
                    buttonSyncUdpListener?.SendAsync(data, data.Length, camIp, 5005);
                }
                buttonSyncUdpListener?.SendAsync(data, data.Length, "192.168.2.255", 5005);
                buttonSyncUdpListener?.SendAsync(data, data.Length, "255.255.255.255", 5005);
            }
            catch { }
        }

        private static HashSet<string> _localIpAddresses;
        private static bool IsLocalIpAddress(IPAddress address)
        {
            if (IPAddress.IsLoopback(address)) return true;
            if (_localIpAddresses == null)
            {
                try
                {
                    _localIpAddresses = new HashSet<string>(
                        NetworkInterface.GetAllNetworkInterfaces()
                            .SelectMany(n => n.GetIPProperties().UnicastAddresses)
                            .Select(a => a.Address.ToString())
                    );
                }
                catch
                {
                    _localIpAddresses = new HashSet<string>();
                }
            }
            return _localIpAddresses.Contains(address.ToString());
        }

        private void SendCameraHttpCommand(string endpoint)
        {
            Task.Run(() =>
            {
                try
                {
                    string cameraIp = txtCameraIp != null && !string.IsNullOrWhiteSpace(txtCameraIp.Text)
                        ? txtCameraIp.Text.Trim()
                        : "192.168.2.80";

                    if (cameraIp.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
                        cameraIp.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
                    {
                        var uri = new Uri(cameraIp);
                        cameraIp = uri.Host;
                    }
                    else if (cameraIp.Contains(":"))
                    {
                        cameraIp = cameraIp.Split(':')[0];
                    }

                    string url = $"http://{cameraIp}:8080/{endpoint}";
                    using (var handler = new HttpClientHandler { UseProxy = false })
                    using (var client = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(2) })
                    {
                        client.GetAsync(url).Wait();
                    }
                }
                catch { }
            });
        }

        private void ProcessRemoteButtonEvent(string msg)
        {
            try
            {
                if (!ControlGroupBox.Enabled)
                {
                    ControlGroupBox.Enabled = true;
                }

                // Format: BTN:<ID>:<ACTION>[:EXTRA]
                var parts = msg.Split(':');
                if (parts.Length < 3) return;

                string btnId = parts[1].Trim().ToUpperInvariant();
                string action = parts[2].Trim().ToUpperInvariant();
                string extra = parts.Length > 3 ? parts[3] : null;

                bool isDown = action == "DOWN";
                bool isClick = action == "CLICK";

                switch (btnId)
                {
                    case "PERMISSION":
                        if (action == "MASTER")
                        {
                            SetPermissionMode(ControlPermissionMode.Viewer, false);
                            LabelAnnouncement.ForeColor = System.Drawing.Color.DarkOrange;
                            LabelAnnouncement.Text = "Thiết bị khác đang giữ quyền ĐỘC QUYỀN -> PC chuyển sang chế độ Chỉ xem";
                        }
                        else if (action == "RELEASE" && _permissionMode == ControlPermissionMode.Viewer)
                        {
                            SetPermissionMode(ControlPermissionMode.Auto, false);
                            LabelAnnouncement.ForeColor = System.Drawing.Color.Green;
                            LabelAnnouncement.Text = "Thiết bị khác đã nhả quyền Độc quyền -> PC trở về chế độ Tự động";
                        }
                        break;

                    case "CLAIM_CONTROL":
                        if (isDown)
                        {
                            YieldControlToRemote();
                            LabelAnnouncement.ForeColor = System.Drawing.Color.DodgerBlue;
                            LabelAnnouncement.Text = "Thiết bị khác đang điều khiển xe...";
                        }
                        break;

                    case "CONNECT_UDP":
                        FlashButtonClicked(ConnectButton, BTN_GREEN_COLOR);
                        if (!string.IsNullOrEmpty(extra))
                        {
                            var partsUdp = extra.Split(',');
                            if (partsUdp.Length >= 2)
                            {
                                IPComboBox.Text = partsUdp[0];
                                PortTextBox.Text = partsUdp[1];
                            }
                        }
                        if (!isConnected)
                        {
                            ConnectButton_Click_1(null, null);
                        }
                        break;

                    case "DISCONNECT_UDP":
                        FlashButtonClicked(DisconnectButton, BTN_EMERGENCY_COLOR);
                        if (isConnected)
                        {
                            DisconnectButton_Click(null, null);
                        }
                        break;
                    // --- D-PAD ĐIỀU KHIỂN THỦ CÔNG (CHỈ CẬP NHẬT UI VÀ NHƯỜNG QUYỀN PHÁT UDP CHO THIẾT BỊ ĐANG NHẤN) ---
                    case "UP":
                        SetButtonHeldState(UpButton, isDown);
                        YieldControlToRemote();
                        if (isDown)
                        {
                            state_run = 0x03;
                            set_w1 = manualSpeed; set_w2 = manualSpeed; set_w3 = manualSpeed; set_w4 = manualSpeed;
                        }
                        else
                        {
                            state_run = 0x01;
                            set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
                        }
                        break;

                    case "DOWN":
                        SetButtonHeldState(DownButton, isDown);
                        YieldControlToRemote();
                        if (isDown)
                        {
                            state_run = 0x03;
                            set_w1 = -manualSpeed; set_w2 = -manualSpeed; set_w3 = -manualSpeed; set_w4 = -manualSpeed;
                        }
                        else
                        {
                            state_run = 0x01;
                            set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
                        }
                        break;

                    case "LEFT":
                        SetButtonHeldState(LeftButton, isDown);
                        YieldControlToRemote();
                        if (isDown)
                        {
                            state_run = 0x03;
                            set_w1 = -manualSpeed; set_w2 = manualSpeed; set_w3 = manualSpeed; set_w4 = -manualSpeed;
                        }
                        else
                        {
                            state_run = 0x01;
                            set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
                        }
                        break;

                    case "RIGHT":
                        SetButtonHeldState(RightButton, isDown);
                        YieldControlToRemote();
                        if (isDown)
                        {
                            state_run = 0x03;
                            set_w1 = manualSpeed; set_w2 = -manualSpeed; set_w3 = -manualSpeed; set_w4 = manualSpeed;
                        }
                        else
                        {
                            state_run = 0x01;
                            set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
                        }
                        break;

                    case "TURN_LEFT":
                        SetButtonHeldState(TurnLeftButton, isDown);
                        YieldControlToRemote();
                        if (isDown)
                        {
                            state_run = 0x03;
                            set_w1 = -manualSpeed; set_w2 = -manualSpeed; set_w3 = manualSpeed; set_w4 = manualSpeed;
                        }
                        else
                        {
                            state_run = 0x01;
                            set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
                        }
                        break;

                    case "TURN_RIGHT":
                        SetButtonHeldState(TurnRightButton, isDown);
                        YieldControlToRemote();
                        if (isDown)
                        {
                            state_run = 0x03;
                            set_w1 = manualSpeed; set_w2 = manualSpeed; set_w3 = -manualSpeed; set_w4 = -manualSpeed;
                        }
                        else
                        {
                            state_run = 0x01;
                            set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
                        }
                        break;

                    // --- CHẾ ĐỘ TỰ ĐỘNG & SETPOINT ---
                    case "EMERGENCY":
                        FlashButtonClicked(EmergencyStopButton, BTN_EMERGENCY_COLOR);
                        YieldControlToRemote();
                        state_run = 0x01;
                        set_x = 0.0f; set_y = 0.0f; set_theta = 0.0f;
                        set_vx = 0.0f; set_vy = 0.0f; set_w = 0.0f;
                        set_w1 = 0.0f; set_w2 = 0.0f; set_w3 = 0.0f; set_w4 = 0.0f;
                        isPlotting = false;
                        PauseGraphButton.Text = "Resume";
                        break;

                    case "CIRCLE_LINE":
                        FlashButtonClicked(CircleLineButton, BTN_PURPLE_COLOR);
                        YieldControlToRemote();
                        state_run = 0x04;
                        break;

                    case "RETURN_HOME":
                        FlashButtonClicked(ReturnHomeButton, BTN_GREEN_COLOR);
                        YieldControlToRemote();
                        state_run = 0x05;
                        set_x = 0.0f; set_y = 0.0f; set_theta = 0.0f;
                        break;

                    case "RESET_WHEELS":
                        FlashButtonClicked(ResetButton, System.Drawing.Color.Gray);
                        YieldControlToRemote();
                        SetW1TextBox.Text = "0";
                        SetW2TextBox.Text = "0";
                        SetW3TextBox.Text = "0";
                        SetW4TextBox.Text = "0";
                        break;

                    case "SET_WHEELS":
                        FlashButtonClicked(SetMotorButton, BTN_HELD_COLOR);
                        YieldControlToRemote();
                        if (!string.IsNullOrEmpty(extra))
                        {
                            var nums = extra.Split(',');
                            if (nums.Length >= 4)
                            {
                                SetW1TextBox.Text = nums[0];
                                SetW2TextBox.Text = nums[1];
                                SetW3TextBox.Text = nums[2];
                                SetW4TextBox.Text = nums[3];
                                if (float.TryParse(nums[0], out float w1)) set_w1 = w1;
                                if (float.TryParse(nums[1], out float w2)) set_w2 = w2;
                                if (float.TryParse(nums[2], out float w3)) set_w3 = w3;
                                if (float.TryParse(nums[3], out float w4)) set_w4 = w4;
                                state_run = 0x02;
                            }
                        }
                        break;

                    case "RESET_ROBOT":
                        FlashButtonClicked(ResetRobotButton, System.Drawing.Color.Gray);
                        YieldControlToRemote();
                        Vx_dTextBox.Text = "0";
                        Vy_dTextBox.Text = "0";
                        W_dTextBox.Text = "0";
                        X_dTextBox.Text = "0";
                        Y_dTextBox.Text = "0";
                        Theta_dTextBox.Text = "0";
                        break;

                    case "SET_ROBOT":
                        FlashButtonClicked(SetRobotButton, BTN_HELD_COLOR);
                        YieldControlToRemote();
                        if (!string.IsNullOrEmpty(extra))
                        {
                            var nums = extra.Split(',');
                            if (nums.Length >= 6)
                            {
                                Vx_dTextBox.Text = nums[0];
                                Vy_dTextBox.Text = nums[1];
                                W_dTextBox.Text = nums[2];
                                X_dTextBox.Text = nums[3];
                                Y_dTextBox.Text = nums[4];
                                Theta_dTextBox.Text = nums[5];
                                if (float.TryParse(nums[0], out float vx)) set_vx = vx;
                                if (float.TryParse(nums[1], out float vy)) set_vy = vy;
                                if (float.TryParse(nums[2], out float w)) set_w = w;
                                if (float.TryParse(nums[3], out float x)) set_x = x;
                                if (float.TryParse(nums[4], out float y)) set_y = y;
                                if (float.TryParse(nums[5], out float th)) set_theta = (float)(th * Math.PI / 180.0);
                                state_run = 0x06;
                            }
                        }
                        break;

                    // --- ĐIỀU KHIỂN CAMERA ---
                    case "TAKE_PHOTO":
                        FlashButtonClicked(TakePhotoButton, BTN_AMBER_COLOR);
                        state_run = 0x07;
                        break;

                    case "START_RECORD":
                        FlashButtonClicked(StartRecordVideoButton, BTN_EMERGENCY_COLOR);
                        state_run = 0x08;
                        break;

                    case "STOP_RECORD":
                        FlashButtonClicked(StopRecordVideoButton, System.Drawing.Color.Gray);
                        state_run = 0x09;
                        break;

                    case "SWITCH_CAMERA":
                        FlashButtonClicked(SwitchCameraButton, BTN_PURPLE_COLOR);
                        state_run = 0x0A;
                        break;

                    case "START_STREAM":
                        FlashButtonClicked(StartStreamButton, BTN_GREEN_COLOR);
                        if (streamForm == null || !streamForm.Visible)
                        {
                            StartStreamButton_Click("REMOTE", null);
                        }
                        break;

                    case "STOP_STREAM":
                        FlashButtonClicked(StopStreamButton, BTN_EMERGENCY_COLOR);
                        StopStreamButton_Click("REMOTE", null);
                        break;

                    // --- ĐỒNG BỘ NÚT ĐIỀU KHIỂN GIMBAL DJI RS 3 MINI ---
                    case "GIMBAL_UP":
                        SetButtonHeldState(_btnGimbalUp, isDown);
                        break;

                    case "GIMBAL_DOWN":
                        SetButtonHeldState(_btnGimbalDown, isDown);
                        break;

                    case "GIMBAL_LEFT":
                        SetButtonHeldState(_btnGimbalLeft, isDown);
                        break;

                    case "GIMBAL_RIGHT":
                        SetButtonHeldState(_btnGimbalRight, isDown);
                        break;
case "GIMBAL_RECENTER":
                        FlashButtonClicked(_btnGimbalCenter, BTN_BLUE_COLOR);
                        LabelAnnouncement.Text = "Gimbal RS3 Mini: Đã căn giữa (Center)...";
                        break;

                    case "GIMBAL_MODE":
                        FlashButtonClicked(_btnGimbalMode, BTN_PURPLE_COLOR);
                        if (!string.IsNullOrEmpty(extra))
                        {
                            UpdateGimbalModeUI(extra);
                        }
                        break;

                    // --- DONG BO CHE DO AUTO & HOLD TU HANH ---
                    case "AUTO_MODE":
                        UpdateAutoModePC(action != "MANUAL");
                        break;

                    case "AUTO_HOLD":
                        SetButtonHeldState(_btnAutoHold, isDown);
                        break;
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine("Lỗi xử lý remote button event: " + ex.Message);
            }
        }
    }
}
