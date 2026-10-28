using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Windows.Forms;

namespace MonitoringAndControllingRobotsApp
{
    public partial class AppForm
    {
        // =========================================================================
        // MODERN DASHBOARD THEME & RESPONSIVE LAYOUT (MATCHING MOCKUP)
        // =========================================================================

        private static readonly Color BgIceBlue = Color.FromArgb(220, 233, 250);       // #DCE9FA
        private static readonly Color CardWhite = Color.FromArgb(255, 255, 255);       // #FFFFFF
        private static readonly Color CardBorder = Color.FromArgb(216, 230, 250);      // #D8E6FA
        private static readonly Color PrimaryBlue = Color.FromArgb(29, 92, 229);       // #1D5CE5
        private static readonly Color PrimaryBlueHover = Color.FromArgb(23, 76, 196);  // #174CC4
        private static readonly Color DangerRed = Color.FromArgb(220, 38, 38);         // #DC2626
        private static readonly Color DangerRedHover = Color.FromArgb(185, 28, 28);    // #B91C1C
        private static readonly Color PillBgSoft = Color.FromArgb(238, 243, 251);      // #EEF3FB
        private static readonly Color PillBorder = Color.FromArgb(203, 213, 225);      // #CBD5E1
        private static readonly Color TextDark = Color.FromArgb(15, 23, 42);           // #0F172A
        private static readonly Color TextMuted = Color.FromArgb(71, 85, 105);         // #475569
        private static readonly Color ReadyBg = Color.FromArgb(220, 252, 231);         // #DCFCE7
        private static readonly Color ReadyText = Color.FromArgb(21, 128, 61);         // #15803D

        private enum ModernBtnRole
        {
            PrimaryBlue,
            DangerRed,
            OutlineWhite,
            TabActive,
            TabInactive,
            DpadUp,
            DpadDown,
            DpadLeft,
            DpadRight,
            DpadTurnLeft,
            DpadTurnRight
        }

        private readonly Dictionary<Button, ModernBtnRole> _btnRoles = new Dictionary<Button, ModernBtnRole>();
        private readonly HashSet<Button> _hoveredButtons = new HashSet<Button>();
        private readonly HashSet<Button> _heldButtons = new HashSet<Button>();

        // Modern Containers
        private ModernCardPanel _topHeaderCard;
        private ModernCardPanel _leftGraphCard;
        private ModernCardPanel _monitorCard;
        private ModernCardPanel _manualCard;
        private ModernCardPanel _autoCard;

        // Auto Follow & Orbit Controls
        private Button _btnAutoToggle;
        private Button _btnAutoHold;
        private Label _lblAutoStatus;
        private bool _isAutoModeActive = false;

        // Top Header Elements
        private Label _lblAppTitle;
        private Label _lblAppSubtitle;
        private Label _lblUdpTitle;
        private Label _lblIpCaption;
        private ModernPillInputContainer _ipInputPill;
        private Label _lblPortCaption;
        private ModernPillInputContainer _portInputPill;
        private ModernStatusPill _statusPillBadge;
        private ModernStatusPill _gamepadPillBadge;

        // Left Card Elements (Tab Pills + Plot Views)
        private Button _tabBtnMain;
        private Button _tabBtnPos;
        private Button _tabBtnVelRobot;
        private Button _tabBtnVelMotors;
        private Panel _plotHostPanel;
        private int _activeTabIndex = 0;

        // Monitor Card Elements (10 Telemetry Pill Boxes)
        private readonly List<ModernTelemetryBox> _telemetryBoxes = new List<ModernTelemetryBox>();

        // Manual Card Elements (4 Labeled Wheel Inputs)
        private Label _lblW1Caption, _lblW2Caption, _lblW3Caption, _lblW4Caption;
        private ModernPillInputContainer _w1InputPill, _w2InputPill, _w3InputPill, _w4InputPill;

        // Auto Card Elements (Camera Pills + 6 Labeled Setpoint Inputs)
        private ModernPillInputContainer _camModePill, _camIpPill;
        private Label _lblVxCaption, _lblVyCaption, _lblWCaption, _lblXCaption, _lblYCaption, _lblThetaCaption;
        private ModernPillInputContainer _vxInputPill, _vyInputPill, _wInputPill, _xInputPill, _yInputPill, _thetaInputPill;

        private bool _modernThemeInitialized = false;

        private void ApplyModernDashboardTheme()
        {
            this.SuspendLayout();
            this.DoubleBuffered = true;
            this.BackColor = BgIceBlue;
            this.Font = new Font("Segoe UI", 9F, FontStyle.Regular);
            this.MinimumSize = new Size(1280, 740);

            // Hide legacy containers from view while keeping them alive for any internal references
            TCPGroupBox.Visible = false;
            ControlGroupBox.Visible = false;
            tabControl1.Visible = false;
            LabelAnnouncement.Visible = false;

            // Default initial values matching mockup
            SetW1TextBox.Text = "0";
            SetW2TextBox.Text = "0";
            SetW3TextBox.Text = "0";
            SetW4TextBox.Text = "0";

            Vx_dTextBox.Text = "0";
            Vy_dTextBox.Text = "0";
            W_dTextBox.Text = "0";
            X_dTextBox.Text = "0";
            Y_dTextBox.Text = "0";
            Theta_dTextBox.Text = "0";

            CurrentXTextBox.Text = "—";
            CurrentYTextBox.Text = "—";
            CurrentThetaTextBox.Text = "—";
            CurrentVxTextBox.Text = "—";
            CurrentVyTextBox.Text = "—";
            CurrentWTextBox.Text = "—";
            CurrentW1TextBox.Text = "—";
            CurrentW2TextBox.Text = "—";
            CurrentW3TextBox.Text = "—";
            CurrentW4TextBox.Text = "—";

            // Ensure Reset buttons have their click handlers wired
            ResetButton.Click -= ResetMotorButton_Click;
            ResetButton.Click += ResetMotorButton_Click;
            ResetRobotButton.Click -= ResetRobotButton_Click;
            ResetRobotButton.Click += ResetRobotButton_Click;

            // Build the 5 main cards
            BuildTopHeaderCard();
            BuildLeftGraphCard();
            BuildMonitorCard();
            BuildManualCard();
            BuildAutoCard();

            // Style ScottPlot charts to match the clean white/blue mockup
            StyleScottPlotCharts();

            // Sync LabelAnnouncement changes to the modern Status Pill Badge
            LabelAnnouncement.TextChanged += (s, e) => UpdateModernStatusPill();
            LabelAnnouncement.ForeColorChanged += (s, e) => UpdateModernStatusPill();
            UpdateModernStatusPill();

            _modernThemeInitialized = true;
            this.Resize += (s, e) => PerformModernLayout();
            this.Load += (s, e) =>
            {
                this.ActiveControl = null;
                IPComboBox.SelectionLength = 0;
                PerformModernLayout();
                if (Array.Exists(Environment.GetCommandLineArgs(), a => a == "--dump-ui"))
                {
                    var t = new System.Windows.Forms.Timer { Interval = 600 };
                    t.Tick += (ts, te) =>
                    {
                        t.Stop();
                        try
                        {
                            this.Size = new Size(1600, 900);
                            this.ActiveControl = null;
                            IPComboBox.SelectionLength = 0;
                            PerformModernLayout();
                            this.Refresh();
                            using (var bmp = new Bitmap(this.Width, this.Height))
                            {
                                this.DrawToBitmap(bmp, new Rectangle(0, 0, this.Width, this.Height));
                                bmp.Save(@"C:\Users\ACER\.gemini\antigravity\brain\ad4fef25-4ef4-4624-8c6d-7c08581f374a\pc_ui_preview.png", System.Drawing.Imaging.ImageFormat.Png);
                            }
                        }
                        catch { }
                        this.Close();
                    };
                    t.Start();
                }
            };
            this.ResumeLayout(false);
            PerformModernLayout();
        }

        // =========================================================================
        // 1. TOP HEADER BAR
        // =========================================================================
        private void BuildTopHeaderCard()
        {
            _topHeaderCard = new ModernCardPanel { CornerRadius = 18 };
            this.Controls.Add(_topHeaderCard);

            _lblAppTitle = new Label
            {
                Text = "Monitoring & Controlling",
                UseMnemonic = false,
                Font = new Font("Segoe UI", 10.5F, FontStyle.Bold),
                ForeColor = TextDark,
                BackColor = CardWhite,
                AutoSize = true
            };
            _topHeaderCard.Controls.Add(_lblAppTitle);

            _lblAppSubtitle = new Label
            {
                Text = "4 Mecanum Wheels Robot App",
                Font = new Font("Segoe UI", 7.8F, FontStyle.Regular),
                ForeColor = TextMuted,
                BackColor = CardWhite,
                AutoSize = true
            };
            _topHeaderCard.Controls.Add(_lblAppSubtitle);

            _lblUdpTitle = new Label
            {
                Text = "UDP",
                Font = new Font("Segoe UI", 9.5F, FontStyle.Bold),
                ForeColor = PrimaryBlue,
                BackColor = CardWhite,
                AutoSize = true
            };
            _topHeaderCard.Controls.Add(_lblUdpTitle);

            _lblIpCaption = new Label
            {
                Text = "IP",
                Font = new Font("Segoe UI", 8.5F, FontStyle.Regular),
                ForeColor = TextMuted,
                BackColor = CardWhite,
                AutoSize = true
            };
            _topHeaderCard.Controls.Add(_lblIpCaption);

            // Reparent IPComboBox inside a rounded pill container
            _ipInputPill = new ModernPillInputContainer { CornerRadius = 15 };
            IPComboBox.Parent = _ipInputPill;
            IPComboBox.FlatStyle = FlatStyle.Flat;
            IPComboBox.BackColor = PillBgSoft;
            IPComboBox.ForeColor = TextDark;
            IPComboBox.Font = new Font("Consolas", 9.5F, FontStyle.Bold);
            _topHeaderCard.Controls.Add(_ipInputPill);

            _lblPortCaption = new Label
            {
                Text = "Port",
                Font = new Font("Segoe UI", 8.5F, FontStyle.Regular),
                ForeColor = TextMuted,
                BackColor = CardWhite,
                AutoSize = true
            };
            _topHeaderCard.Controls.Add(_lblPortCaption);

            // Reparent PortTextBox inside a rounded pill container
            _portInputPill = new ModernPillInputContainer { CornerRadius = 15 };
            PortTextBox.Parent = _portInputPill;
            PortTextBox.BorderStyle = BorderStyle.None;
            PortTextBox.BackColor = PillBgSoft;
            PortTextBox.ForeColor = TextDark;
            PortTextBox.Font = new Font("Consolas", 9.5F, FontStyle.Bold);
            _topHeaderCard.Controls.Add(_portInputPill);

            // Reparent ScanIPButton, ConnectButton, DisconnectButton
            ScanIPButton.Parent = _topHeaderCard;
            ScanIPButton.Text = "Scan IP";
            RegisterModernButton(ScanIPButton, ModernBtnRole.OutlineWhite);

            ConnectButton.Parent = _topHeaderCard;
            ConnectButton.Text = "Connect";
            RegisterModernButton(ConnectButton, ModernBtnRole.PrimaryBlue);

            DisconnectButton.Parent = _topHeaderCard;
            DisconnectButton.Text = "Disconnect";
            RegisterModernButton(DisconnectButton, ModernBtnRole.OutlineWhite);

            // Status Pill Badge (• Ready)
            _statusPillBadge = new ModernStatusPill
            {
                StatusText = "• Ready",
                PillBackColor = ReadyBg,
                PillTextColor = ReadyText,
                Cursor = Cursors.Hand
            };
            // Clicking the status pill cycles permission mode (Auto / Master / Viewer)
            _statusPillBadge.Click += (s, e) => CyclePermissionMode();
            _topHeaderCard.Controls.Add(_statusPillBadge);

            // Gamepad Status Pill Badge
            _gamepadPillBadge = new ModernStatusPill
            {
                StatusText = "● Tay cầm: Đang quét...",
                PillBackColor = PillBgSoft,
                PillTextColor = TextMuted,
                Cursor = Cursors.Hand
            };
            _gamepadPillBadge.Click += (s, e) =>
            {
                maxGamepadSpeed = (maxGamepadSpeed >= 25.0f) ? 5.0f : (maxGamepadSpeed + 5.0f);
                UpdateGamepadUI();
            };
            _topHeaderCard.Controls.Add(_gamepadPillBadge);
        }

        public void UpdateModernGamepadPill()
        {
            if (_gamepadPillBadge == null || _gamepadPillBadge.IsDisposed) return;

            if (_isControllerConnected)
            {
                string motionDesc = string.IsNullOrEmpty(_gamepadMotionText) ? (isGamepadDriving ? "Đang lái" : "Sẵn sàng") : _gamepadMotionText;
                _gamepadPillBadge.StatusText = $"● Xbox 360: {motionDesc} ({maxGamepadSpeed:F1} rad/s)";
                _gamepadPillBadge.PillBackColor = isGamepadDriving
                    ? Color.FromArgb(239, 246, 255)  // #EFF6FF Blue
                    : Color.FromArgb(236, 253, 245); // #ECFDF5 Mint
                _gamepadPillBadge.PillTextColor = isGamepadDriving
                    ? PrimaryBlue
                    : Color.FromArgb(5, 150, 105);  // #059669 Emerald
            }
            else
            {
                _gamepadPillBadge.StatusText = "● Tay cầm: Chưa cắm";
                _gamepadPillBadge.PillBackColor = Color.FromArgb(241, 245, 249); // #F1F5F9 Slate
                _gamepadPillBadge.PillTextColor = Color.FromArgb(100, 116, 139); // #64748B Muted
            }

            using (Graphics g = _gamepadPillBadge.CreateGraphics())
            {
                SizeF sz = g.MeasureString(_gamepadPillBadge.StatusText, _gamepadPillBadge.Font);
                int bw = Math.Max(120, (int)Math.Ceiling(sz.Width) + 24);
                _gamepadPillBadge.Width = Math.Min(bw, 360);
            }
            _gamepadPillBadge.Invalidate();
        }

        private void UpdateModernStatusPill()
        {
            if (_statusPillBadge == null || _statusPillBadge.IsDisposed) return;

            string raw = (LabelAnnouncement.Text ?? "Ready").Trim();
            if (string.IsNullOrEmpty(raw) || raw == "Abc") raw = "Ready";

            // Keep badge concise so it fits cleanly in the top bar
            string display = raw;
            if (display.Length > 34) display = display.Substring(0, 31) + "...";

            Color fg = ReadyText;
            Color bg = ReadyBg;

            if (LabelAnnouncement.ForeColor == Color.Red || raw.IndexOf("ngắt", StringComparison.OrdinalIgnoreCase) >= 0 || raw.IndexOf("lỗi", StringComparison.OrdinalIgnoreCase) >= 0 || raw.IndexOf("STOP", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                fg = DangerRed;
                bg = Color.FromArgb(254, 226, 226); // #FEE2E2
            }
            else if (LabelAnnouncement.ForeColor == Color.DarkOrange)
            {
                fg = Color.FromArgb(180, 83, 9);
                bg = Color.FromArgb(254, 243, 199); // #FEF3C7
            }
            else if (LabelAnnouncement.ForeColor == Color.Blue || LabelAnnouncement.ForeColor == Color.DodgerBlue)
            {
                fg = PrimaryBlue;
                bg = Color.FromArgb(219, 234, 254); // #DBEAFE
            }

            _statusPillBadge.StatusText = "• " + display;
            _statusPillBadge.PillBackColor = bg;
            _statusPillBadge.PillTextColor = fg;

            using (Graphics g = _statusPillBadge.CreateGraphics())
            {
                SizeF sz = g.MeasureString(_statusPillBadge.StatusText, _statusPillBadge.Font);
                int w = Math.Max(74, (int)Math.Ceiling(sz.Width) + 24);
                _statusPillBadge.Width = Math.Min(w, 280);
            }
            _statusPillBadge.Invalidate();
        }

        // =========================================================================
        // 2. LEFT GRAPH & ANALYSIS CARD
        // =========================================================================
        private void BuildLeftGraphCard()
        {
            _leftGraphCard = new ModernCardPanel { CornerRadius = 18 };
            this.Controls.Add(_leftGraphCard);

            _tabBtnMain = CreateTabPillButton("Main Monitor", 0);
            _tabBtnPos = CreateTabPillButton("Position Robot Analysis", 1);
            _tabBtnVelRobot = CreateTabPillButton("Velocity Robot Analysis", 2);
            _tabBtnVelMotors = CreateTabPillButton("Velocity Motors Analysis", 3);

            _leftGraphCard.Controls.Add(_tabBtnMain);
            _leftGraphCard.Controls.Add(_tabBtnPos);
            _leftGraphCard.Controls.Add(_tabBtnVelRobot);
            _leftGraphCard.Controls.Add(_tabBtnVelMotors);

            _plotHostPanel = new Panel
            {
                BackColor = CardWhite,
                BorderStyle = BorderStyle.None
            };
            _leftGraphCard.Controls.Add(_plotHostPanel);

            // Move plot controls into _plotHostPanel
            TrajectoryPlot.Parent = _plotHostPanel;
            TrajectoryPlot.Dock = DockStyle.Fill;

            tableLayoutPanel6.Parent = _plotHostPanel;
            tableLayoutPanel6.Dock = DockStyle.Fill;
            tableLayoutPanel6.Visible = false;
            checkPositionRobotAutoScale.Visible = false;

            tableLayoutPanel4.Parent = _plotHostPanel;
            tableLayoutPanel4.Dock = DockStyle.Fill;
            tableLayoutPanel4.Visible = false;
            checkVelocityRobotAutoScale.Visible = false;

            tableLayoutPanel3.Parent = _plotHostPanel;
            tableLayoutPanel3.Dock = DockStyle.Fill;
            tableLayoutPanel3.Visible = false;

            // Unified Auto Scale checkbox at bottom-left of Left Card
            checkTrajectoryAutoScale.Parent = _leftGraphCard;
            checkTrajectoryAutoScale.BackColor = CardWhite;
            checkTrajectoryAutoScale.ForeColor = TextMuted;
            checkTrajectoryAutoScale.Font = new Font("Segoe UI", 8.5F, FontStyle.Bold);
            checkTrajectoryAutoScale.Text = "Auto Scale";
            checkTrajectoryAutoScale.CheckedChanged += (s, e) =>
            {
                bool c = checkTrajectoryAutoScale.Checked;
                checkPositionRobotAutoScale.Checked = c;
                checkVelocityRobotAutoScale.Checked = c;
                checkVelocityMotorsAutoScale.Checked = c;
            };

            SwitchModernTab(0);
        }

        private Button CreateTabPillButton(string title, int tabIdx)
        {
            var btn = new Button
            {
                Text = title,
                Font = new Font("Segoe UI", 8.5F, FontStyle.Bold),
                Cursor = Cursors.Hand,
                FlatStyle = FlatStyle.Flat
            };
            RegisterModernButton(btn, tabIdx == 0 ? ModernBtnRole.TabActive : ModernBtnRole.TabInactive);
            btn.Click += (s, e) => SwitchModernTab(tabIdx);
            return btn;
        }

        private void SwitchModernTab(int idx)
        {
            _activeTabIndex = idx;
            if (tabControl1.TabCount > idx)
            {
                tabControl1.SelectedIndex = idx;
            }

            _btnRoles[_tabBtnMain] = idx == 0 ? ModernBtnRole.TabActive : ModernBtnRole.TabInactive;
            _btnRoles[_tabBtnPos] = idx == 1 ? ModernBtnRole.TabActive : ModernBtnRole.TabInactive;
            _btnRoles[_tabBtnVelRobot] = idx == 2 ? ModernBtnRole.TabActive : ModernBtnRole.TabInactive;
            _btnRoles[_tabBtnVelMotors] = idx == 3 ? ModernBtnRole.TabActive : ModernBtnRole.TabInactive;

            _tabBtnMain.Invalidate();
            _tabBtnPos.Invalidate();
            _tabBtnVelRobot.Invalidate();
            _tabBtnVelMotors.Invalidate();

            TrajectoryPlot.Visible = (idx == 0);
            tableLayoutPanel6.Visible = (idx == 1);
            tableLayoutPanel4.Visible = (idx == 2);
            tableLayoutPanel3.Visible = (idx == 3);
        }

        private void StyleScottPlotCharts()
        {
            try
            {
                ScottPlot.WinForms.FormsPlot[] plots = {
                    TrajectoryPlot,
                    PlotXTime, PlotYTime, PlotThetaTime,
                    PlotVxTime, PlotVyTime, PlotWTime,
                    PlotW1Time, PlotW2Time, PlotW3Time, PlotW4Time
                };

                foreach (var fp in plots)
                {
                    if (fp == null) continue;
                    fp.Plot.FigureBackground.Color = ScottPlot.Color.FromHex("#FFFFFF");
                    fp.Plot.DataBackground.Color = ScottPlot.Color.FromHex("#FFFFFF");
                    fp.Plot.Grid.MajorLineColor = ScottPlot.Color.FromHex("#E8EEF7");
                    fp.Plot.Axes.Color(ScottPlot.Color.FromHex("#475569"));
                    fp.Plot.Axes.FrameColor(ScottPlot.Color.FromHex("#CBD5E1"));
                    fp.Refresh();
                }
                TrajectoryPlot.Plot.Axes.SetLimits(-11, 11, -7, 7);
                TrajectoryPlot.Refresh();
            }
            catch { }
        }

        // =========================================================================
        // 3. RIGHT CARD 1: MONITOR (ROBOT CURRENT VALUES)
        // =========================================================================
        private void BuildMonitorCard()
        {
            _monitorCard = new ModernCardPanel
            {
                CornerRadius = 18,
                HeaderTitle = "MONITOR",
                HeaderSubtitle = "Robot Current Values"
            };
            this.Controls.Add(_monitorCard);

            _telemetryBoxes.Clear();
            // Row 1 (5 boxes)
            _telemetryBoxes.Add(CreateTelemetryBox("X (m)", CurrentXTextBox));
            _telemetryBoxes.Add(CreateTelemetryBox("Y (m)", CurrentYTextBox));
            _telemetryBoxes.Add(CreateTelemetryBox("Theta (degree)", CurrentThetaTextBox));
            _telemetryBoxes.Add(CreateTelemetryBox("Vx (m/s)", CurrentVxTextBox));
            _telemetryBoxes.Add(CreateTelemetryBox("Vy (m/s)", CurrentVyTextBox));

            // Row 2 (5 boxes)
            _telemetryBoxes.Add(CreateTelemetryBox("W (rad/s)", CurrentWTextBox));
            _telemetryBoxes.Add(CreateTelemetryBox("W_RL (rad/s)", CurrentW1TextBox));
            _telemetryBoxes.Add(CreateTelemetryBox("W_FL (rad/s)", CurrentW2TextBox));
            _telemetryBoxes.Add(CreateTelemetryBox("W_RR (rad/s)", CurrentW3TextBox));
            _telemetryBoxes.Add(CreateTelemetryBox("W_FR (rad/s)", CurrentW4TextBox));

            foreach (var box in _telemetryBoxes)
            {
                _monitorCard.Controls.Add(box);
            }

            // 4 Bottom Graph Action Pill Buttons
            StartGraphButton.Parent = _monitorCard;
            StartGraphButton.Text = "Start Graph";
            RegisterModernButton(StartGraphButton, ModernBtnRole.PrimaryBlue);

            PauseGraphButton.Parent = _monitorCard;
            PauseGraphButton.Text = "Pause";
            RegisterModernButton(PauseGraphButton, ModernBtnRole.OutlineWhite);

            StopGraphButton.Parent = _monitorCard;
            StopGraphButton.Text = "Stop Graph";
            RegisterModernButton(StopGraphButton, ModernBtnRole.OutlineWhite);

            ExportCSVButton.Parent = _monitorCard;
            ExportCSVButton.Text = "Export to";
            RegisterModernButton(ExportCSVButton, ModernBtnRole.OutlineWhite);
        }

        private ModernTelemetryBox CreateTelemetryBox(string caption, TextBox valueTextBox)
        {
            var box = new ModernTelemetryBox(caption, valueTextBox);
            return box;
        }

        // =========================================================================
        // 4. RIGHT CARD 2: MANUAL (SET WHEEL VELOCITY)
        // =========================================================================
        private void BuildManualCard()
        {
            _manualCard = new ModernCardPanel
            {
                CornerRadius = 18,
                HeaderTitle = "MANUAL",
                HeaderSubtitle = "Set Wheel Velocity"
            };
            this.Controls.Add(_manualCard);

            // Reparent D-Pad & Turn Buttons
            UpButton.Parent = _manualCard;
            RegisterModernButton(UpButton, ModernBtnRole.DpadUp);

            DownButton.Parent = _manualCard;
            RegisterModernButton(DownButton, ModernBtnRole.DpadDown);

            LeftButton.Parent = _manualCard;
            RegisterModernButton(LeftButton, ModernBtnRole.DpadLeft);

            TurnLeftButton.Parent = _manualCard;
            RegisterModernButton(TurnLeftButton, ModernBtnRole.DpadTurnLeft);

            TurnRightButton.Parent = _manualCard;
            RegisterModernButton(TurnRightButton, ModernBtnRole.DpadTurnRight);

            RightButton.Parent = _manualCard;
            RegisterModernButton(RightButton, ModernBtnRole.DpadRight);

            // 4 Wheel Velocity Inputs (2x2 Grid)
            _lblW1Caption = CreateInputCaption("Velocity Rear Left Wheel (Rad/s)", _manualCard);
            _w1InputPill = WrapTextBoxInPill(SetW1TextBox, _manualCard);

            _lblW2Caption = CreateInputCaption("Velocity Front Left Wheel (Rad/s)", _manualCard);
            _w2InputPill = WrapTextBoxInPill(SetW2TextBox, _manualCard);

            _lblW3Caption = CreateInputCaption("Velocity Rear Right Wheel (Rad/s)", _manualCard);
            _w3InputPill = WrapTextBoxInPill(SetW3TextBox, _manualCard);

            _lblW4Caption = CreateInputCaption("Velocity Front Right Wheel (Rad/s)", _manualCard);
            _w4InputPill = WrapTextBoxInPill(SetW4TextBox, _manualCard);

            // Reset & Set Buttons
            ResetButton.Parent = _manualCard;
            ResetButton.Text = "Reset";
            RegisterModernButton(ResetButton, ModernBtnRole.OutlineWhite);

            SetMotorButton.Parent = _manualCard;
            SetMotorButton.Text = "Set";
            RegisterModernButton(SetMotorButton, ModernBtnRole.PrimaryBlue);
        }

        // =========================================================================
        // 5. RIGHT CARD 3: AUTO (SET ROBOT VALUE)
        // =========================================================================
        private void BuildAutoCard()
        {
            _autoCard = new ModernCardPanel
            {
                CornerRadius = 18,
                HeaderTitle = "AUTO",
                HeaderSubtitle = "Set Robot Value"
            };
            this.Controls.Add(_autoCard);

            // Left Side Row 1: Circle Line | Return Home | Emergency Stop
            CircleLineButton.Parent = _autoCard;
            CircleLineButton.Text = "Circle Line";
            RegisterModernButton(CircleLineButton, ModernBtnRole.OutlineWhite);

            ReturnHomeButton.Parent = _autoCard;
            ReturnHomeButton.Text = "Return Home";
            RegisterModernButton(ReturnHomeButton, ModernBtnRole.OutlineWhite);

            EmergencyStopButton.Parent = _autoCard;
            EmergencyStopButton.Text = "Emergency Stop";
            RegisterModernButton(EmergencyStopButton, ModernBtnRole.DangerRed);

            // Left Side Row 2: [Robot Camera] | [192.168.2.80]
            _camModePill = new ModernPillInputContainer { CornerRadius = 15 };
            cboCameraMode.Parent = _camModePill;
            cboCameraMode.FlatStyle = FlatStyle.Flat;
            cboCameraMode.BackColor = PillBgSoft;
            cboCameraMode.ForeColor = TextDark;
            cboCameraMode.Font = new Font("Consolas", 8.8F, FontStyle.Bold);
            _autoCard.Controls.Add(_camModePill);

            _camIpPill = WrapTextBoxInPill(txtCameraIp, _autoCard, 15);

            // Left Side Row 3: Start Stream | Stop Stream | Switch Camera
            StartStreamButton.Parent = _autoCard;
            StartStreamButton.Text = "Start Stream";
            RegisterModernButton(StartStreamButton, ModernBtnRole.PrimaryBlue);

            StopStreamButton.Parent = _autoCard;
            StopStreamButton.Text = "Stop Stream";
            RegisterModernButton(StopStreamButton, ModernBtnRole.OutlineWhite);

            SwitchCameraButton.Parent = _autoCard;
            SwitchCameraButton.Text = "Switch Camera";
            RegisterModernButton(SwitchCameraButton, ModernBtnRole.OutlineWhite);

            // Left Side Row 4: Take Photo | Start Record | Stop Record
            TakePhotoButton.Parent = _autoCard;
            TakePhotoButton.Text = "Take Photo";
            RegisterModernButton(TakePhotoButton, ModernBtnRole.OutlineWhite);

            StartRecordVideoButton.Parent = _autoCard;
            StartRecordVideoButton.Text = "Start Record";
            RegisterModernButton(StartRecordVideoButton, ModernBtnRole.OutlineWhite);

            StopRecordVideoButton.Parent = _autoCard;
            StopRecordVideoButton.Text = "Stop Record";
            RegisterModernButton(StopRecordVideoButton, ModernBtnRole.OutlineWhite);

            // Left Side Row 5: Auto Controls (AUTO & HOLD)
            _btnAutoToggle = new Button
            {
                Parent = _autoCard,
                Text = "🤖 AUTO"
            };
            _btnAutoToggle.Click += (s, e) =>
            {
                _isAutoModeActive = !_isAutoModeActive;
                SendButtonSyncEvent("AUTO_MODE", _isAutoModeActive ? "AUTO" : "MANUAL");
                UpdateAutoModePC(_isAutoModeActive);
            };
            RegisterModernButton(_btnAutoToggle, ModernBtnRole.OutlineWhite);

            _btnAutoHold = new Button
            {
                Parent = _autoCard,
                Text = "🛡️ HOLD (Space)"
            };
            _btnAutoHold.MouseDown += (s, e) =>
            {
                SendButtonSyncEvent("AUTO_HOLD", "DOWN");
                _heldButtons.Add(_btnAutoHold);
                _btnAutoHold.Invalidate();
            };
            _btnAutoHold.MouseUp += (s, e) =>
            {
                SendButtonSyncEvent("AUTO_HOLD", "UP");
                _heldButtons.Remove(_btnAutoHold);
                _btnAutoHold.Invalidate();
            };
            RegisterModernButton(_btnAutoHold, ModernBtnRole.PrimaryBlue);

            _lblAutoStatus = new Label
            {
                Parent = _autoCard,
                Text = "Chế độ: Thủ công",
                Font = new Font("Segoe UI", 8.2F, FontStyle.Bold),
                ForeColor = TextMuted,
                TextAlign = ContentAlignment.MiddleLeft
            };

            // Right Side 3x2 Grid of Setpoint Inputs
            _lblVxCaption = CreateInputCaption("Forward Velocity (m/s)", _autoCard);
            _vxInputPill = WrapTextBoxInPill(Vx_dTextBox, _autoCard);

            _lblVyCaption = CreateInputCaption("Strafing Velocity (m/s)", _autoCard);
            _vyInputPill = WrapTextBoxInPill(Vy_dTextBox, _autoCard);

            _lblWCaption = CreateInputCaption("Angular Velocity (rad/s)", _autoCard);
            _wInputPill = WrapTextBoxInPill(W_dTextBox, _autoCard);

            _lblXCaption = CreateInputCaption("Position X (m)", _autoCard);
            _xInputPill = WrapTextBoxInPill(X_dTextBox, _autoCard);

            _lblYCaption = CreateInputCaption("Position Y (m)", _autoCard);
            _yInputPill = WrapTextBoxInPill(Y_dTextBox, _autoCard);

            _lblThetaCaption = CreateInputCaption("Theta desired (degree)", _autoCard);
            _thetaInputPill = WrapTextBoxInPill(Theta_dTextBox, _autoCard);

            // Keep ResetRobotButton and SetRobotButton accessible in top-right of AUTO header
            ResetRobotButton.Parent = _autoCard;
            ResetRobotButton.Text = "Reset";
            RegisterModernButton(ResetRobotButton, ModernBtnRole.OutlineWhite);

            SetRobotButton.Parent = _autoCard;
            SetRobotButton.Text = "Set";
            RegisterModernButton(SetRobotButton, ModernBtnRole.PrimaryBlue);
        }

        private Label CreateInputCaption(string text, Control parent)
        {
            var lbl = new Label
            {
                Text = text,
                Font = new Font("Segoe UI", 8.0F, FontStyle.Regular),
                ForeColor = TextDark,
                BackColor = CardWhite,
                AutoSize = true
            };
            parent.Controls.Add(lbl);
            return lbl;
        }

        private ModernPillInputContainer WrapTextBoxInPill(TextBox tb, Control parent, int radius = 10)
        {
            var pill = new ModernPillInputContainer { CornerRadius = radius };
            tb.Parent = pill;
            tb.BorderStyle = BorderStyle.None;
            tb.BackColor = PillBgSoft;
            tb.ForeColor = TextDark;
            tb.Font = new Font("Consolas", 9.5F, FontStyle.Bold);
            parent.Controls.Add(pill);
            return pill;
        }

        // =========================================================================
        // 6. RESPONSIVE PROPORTIONAL LAYOUT ENGINE
        // =========================================================================
        private void PerformModernLayout()
        {
            if (!_modernThemeInitialized || this.ClientSize.Width <= 100 || this.ClientSize.Height <= 100)
                return;

            int pad = 14;
            int gap = 12;
            int totalW = this.ClientSize.Width;
            int totalH = this.ClientSize.Height;

            // 1. Top Header Bar
            int topBarH = 56;
            _topHeaderCard.SetBounds(pad, pad, totalW - pad * 2, topBarH);
            LayoutTopHeaderBar(_topHeaderCard.Width, topBarH);

            // 2. Main Content Area below Top Bar
            int contentY = pad + topBarH + gap;
            int contentH = Math.Max(400, totalH - contentY - pad);
            int availW = totalW - pad * 2 - gap;

            int leftW = (int)(availW * 0.535f);
            int rightW = availW - leftW;
            int rightX = pad + leftW + gap;

            // Left Graph Card
            _leftGraphCard.SetBounds(pad, contentY, leftW, contentH);
            LayoutLeftGraphCard(leftW, contentH);

            // 3 Right Stacked Cards: Monitor (~30%), Manual (~33%), Auto (~37%)
            int availRightH = contentH - gap * 2;
            int monitorH = (int)(availRightH * 0.30f);
            int manualH = (int)(availRightH * 0.33f);
            int autoH = availRightH - monitorH - manualH;

            int monitorY = contentY;
            int manualY = monitorY + monitorH + gap;
            int autoY = manualY + manualH + gap;

            _monitorCard.SetBounds(rightX, monitorY, rightW, monitorH);
            LayoutMonitorCard(rightW, monitorH);

            _manualCard.SetBounds(rightX, manualY, rightW, manualH);
            LayoutManualCard(rightW, manualH);

            _autoCard.SetBounds(rightX, autoY, rightW, autoH);
            LayoutAutoCard(rightW, autoH);
        }

        private void LayoutTopHeaderBar(int w, int h)
        {
            _lblAppTitle.Location = new Point(18, 9);
            _lblAppSubtitle.Location = new Point(18, 30);

            int cy = h / 2;
            int x = Math.Max(215, _lblAppSubtitle.Right + 24);

            _lblUdpTitle.Location = new Point(x, cy - _lblUdpTitle.Height / 2);
            x += _lblUdpTitle.Width + 10;

            _lblIpCaption.Location = new Point(x, cy - _lblIpCaption.Height / 2);
            x += _lblIpCaption.Width + 6;

            int pillH = 34;
            _ipInputPill.SetBounds(x, cy - pillH / 2, 162, pillH);
            IPComboBox.SetBounds(10, (pillH - IPComboBox.Height) / 2, _ipInputPill.Width - 20, IPComboBox.Height);
            x += _ipInputPill.Width + 14;

            _lblPortCaption.Location = new Point(x, cy - _lblPortCaption.Height / 2);
            x += _lblPortCaption.Width + 6;

            _portInputPill.SetBounds(x, cy - pillH / 2, 78, pillH);
            PortTextBox.SetBounds(12, (pillH - PortTextBox.Height) / 2 + 1, _portInputPill.Width - 24, PortTextBox.Height);
            x += _portInputPill.Width + 12;

            ScanIPButton.SetBounds(x, cy - pillH / 2, 74, pillH);
            x += ScanIPButton.Width + 8;

            ConnectButton.SetBounds(x, cy - pillH / 2, 82, pillH);
            x += ConnectButton.Width + 8;

            DisconnectButton.SetBounds(x, cy - pillH / 2, 92, pillH);
            x += DisconnectButton.Width + 10;

            _statusPillBadge.Location = new Point(x, cy - 15);
            _statusPillBadge.Height = 30;
            UpdateModernStatusPill();

            x += _statusPillBadge.Width + 8;
            if (_gamepadPillBadge != null)
            {
                _gamepadPillBadge.Location = new Point(x, cy - 15);
                _gamepadPillBadge.Height = 30;
                UpdateModernGamepadPill();
            }
        }

        private void LayoutLeftGraphCard(int w, int h)
        {
            int tabY = 14;
            int tabH = 30;
            int tx = 16;

            int[] tabWidths = { 104, 152, 148, 156 };
            Button[] tabs = { _tabBtnMain, _tabBtnPos, _tabBtnVelRobot, _tabBtnVelMotors };
            for (int i = 0; i < tabs.Length; i++)
            {
                tabs[i].SetBounds(tx, tabY, tabWidths[i], tabH);
                tx += tabWidths[i] + 8;
            }

            int autoScaleH = 24;
            checkTrajectoryAutoScale.Location = new Point(26, h - autoScaleH - 10);

            int plotTop = tabY + tabH + 8;
            int plotH = Math.Max(100, checkTrajectoryAutoScale.Top - plotTop - 6);
            _plotHostPanel.SetBounds(12, plotTop, w - 24, plotH);
        }

        private void LayoutMonitorCard(int w, int h)
        {
            int padX = 16;
            int topY = 40;
            int btnH = 34;
            int btnY = h - btnH - 12;

            int gridAvailH = Math.Max(76, btnY - topY - 10);
            int rowGap = 8;
            int boxH = (gridAvailH - rowGap) / 2;

            int colGap = 8;
            int boxW = (w - padX * 2 - colGap * 4) / 5;

            for (int r = 0; r < 2; r++)
            {
                for (int c = 0; c < 5; c++)
                {
                    int idx = r * 5 + c;
                    if (idx < _telemetryBoxes.Count)
                    {
                        int bx = padX + c * (boxW + colGap);
                        int by = topY + r * (boxH + rowGap);
                        _telemetryBoxes[idx].SetBounds(bx, by, boxW, boxH);
                        _telemetryBoxes[idx].PerformInternalLayout();
                    }
                }
            }

            // 4 Bottom Buttons
            int btnGap = 10;
            int btnW = (w - padX * 2 - btnGap * 3) / 4;
            Button[] btns = { StartGraphButton, PauseGraphButton, StopGraphButton, ExportCSVButton };
            for (int i = 0; i < btns.Length; i++)
            {
                btns[i].SetBounds(padX + i * (btnW + btnGap), btnY, btnW, btnH);
            }
        }

        private void LayoutManualCard(int w, int h)
        {
            int padX = 16;
            int topY = 40;

            // Left D-Pad Region (~37% width)
            int dpadRegionW = (int)((w - padX * 2) * 0.37f);
            int dpadCenterX = padX + dpadRegionW / 2;

            int sqW = Math.Min(44, (dpadRegionW - 18) / 4);
            int sqH = Math.Min(38, Math.Max(30, (h - topY - 18) / 3));
            int wideW = sqW * 2 + 6;
            int rowGap = 6;

            int totalDpadH = sqH * 3 + rowGap * 2;
            int dpadStartY = topY + Math.Max(2, (h - topY - 10 - totalDpadH) / 2);

            UpButton.SetBounds(dpadCenterX - wideW / 2, dpadStartY, wideW, sqH);

            int midY = dpadStartY + sqH + rowGap;
            int totalMidW = sqW * 4 + 6 * 3;
            int midStartX = dpadCenterX - totalMidW / 2;
            LeftButton.SetBounds(midStartX, midY, sqW, sqH);
            TurnLeftButton.SetBounds(midStartX + (sqW + 6), midY, sqW, sqH);
            TurnRightButton.SetBounds(midStartX + (sqW + 6) * 2, midY, sqW, sqH);
            RightButton.SetBounds(midStartX + (sqW + 6) * 3, midY, sqW, sqH);

            DownButton.SetBounds(dpadCenterX - wideW / 2, midY + sqH + rowGap, wideW, sqH);

            // Right 2x2 Wheel Velocity Inputs Region
            int rightX = padX + dpadRegionW + 14;
            int rightW = w - rightX - padX;
            int colGap = 10;
            int colW = (rightW - colGap) / 2;

            int btnH = 30;
            int inputH = Math.Min(34, Math.Max(26, (h - topY - btnH - 54) / 2));

            int r1LblY = topY - 2;
            int r1BoxY = r1LblY + 17;
            _lblW1Caption.Location = new Point(rightX, r1LblY);
            _w1InputPill.SetBounds(rightX, r1BoxY, colW, inputH);
            PositionInnerTextBox(SetW1TextBox, _w1InputPill);

            _lblW2Caption.Location = new Point(rightX + colW + colGap, r1LblY);
            _w2InputPill.SetBounds(rightX + colW + colGap, r1BoxY, colW, inputH);
            PositionInnerTextBox(SetW2TextBox, _w2InputPill);

            int r2LblY = r1BoxY + inputH + 8;
            int r2BoxY = r2LblY + 17;
            _lblW3Caption.Location = new Point(rightX, r2LblY);
            _w3InputPill.SetBounds(rightX, r2BoxY, colW, inputH);
            PositionInnerTextBox(SetW3TextBox, _w3InputPill);

            _lblW4Caption.Location = new Point(rightX + colW + colGap, r2LblY);
            _w4InputPill.SetBounds(rightX + colW + colGap, r2BoxY, colW, inputH);
            PositionInnerTextBox(SetW4TextBox, _w4InputPill);

            // Bottom-Right Reset & Set Pills right below Row 2
            int btnY = Math.Min(h - btnH - 10, r2BoxY + inputH + 10);
            int setW = 50;
            int resetW = 60;
            SetMotorButton.SetBounds(w - padX - setW, btnY, setW, btnH);
            ResetButton.SetBounds(SetMotorButton.Left - 8 - resetW, btnY, resetW, btnH);
        }

        private void LayoutAutoCard(int w, int h)
        {
            int padX = 16;
            int topY = 42;
            int bottomPad = 14;
            int availH = Math.Max(120, h - topY - bottomPad);

            // Top-right compact Reset / Set buttons for Robot Setpoints
            int hdrBtnH = 26;
            int hdrSetW = 48;
            int hdrResetW = 58;
            SetRobotButton.SetBounds(w - padX - hdrSetW, 10, hdrSetW, hdrBtnH);
            ResetRobotButton.SetBounds(SetRobotButton.Left - 6 - hdrResetW, 10, hdrResetW, hdrBtnH);

            // Split width: Left 53% (4 rows of buttons), Right 47% (3x2 grid of inputs)
            int totalInnerW = w - padX * 2;
            int leftW = (int)(totalInnerW * 0.53f);
            int rightX = padX + leftW + 14;
            int rightW = w - rightX - padX;

            // Left 5 Rows with sleek pill height
            int leftRowH = Math.Min(34, Math.Max(26, (availH - 28) / 5));
            int leftRowGap = Math.Max(5, (availH - leftRowH * 5) / 4);
            int btnGap = 7;
            int btn3W = (leftW - btnGap * 2) / 3;
            int btn2W = (leftW - btnGap) / 2;

            // Row 1: Circle Line | Return Home | Emergency Stop
            int ry0 = topY;
            CircleLineButton.SetBounds(padX, ry0, btn3W, leftRowH);
            ReturnHomeButton.SetBounds(padX + btn3W + btnGap, ry0, btn3W, leftRowH);
            EmergencyStopButton.SetBounds(padX + (btn3W + btnGap) * 2, ry0, leftW - (btn3W + btnGap) * 2, leftRowH);

            // Row 2: [Robot Camera] | [192.168.2.80]
            int ry1 = ry0 + leftRowH + leftRowGap;
            _camModePill.SetBounds(padX, ry1, btn2W, leftRowH);
            cboCameraMode.SetBounds(10, (leftRowH - cboCameraMode.Height) / 2, _camModePill.Width - 20, cboCameraMode.Height);

            _camIpPill.SetBounds(padX + btn2W + btnGap, ry1, leftW - btn2W - btnGap, leftRowH);
            PositionInnerTextBox(txtCameraIp, _camIpPill);

            // Row 3: Start Stream | Stop Stream | Switch Camera
            int ry2 = ry1 + leftRowH + leftRowGap;
            StartStreamButton.SetBounds(padX, ry2, btn3W, leftRowH);
            StopStreamButton.SetBounds(padX + btn3W + btnGap, ry2, btn3W, leftRowH);
            SwitchCameraButton.SetBounds(padX + (btn3W + btnGap) * 2, ry2, leftW - (btn3W + btnGap) * 2, leftRowH);

            // Row 4: Take Photo | Start Record | Stop Record
            int ry3 = ry2 + leftRowH + leftRowGap;
            TakePhotoButton.SetBounds(padX, ry3, btn3W, leftRowH);
            StartRecordVideoButton.SetBounds(padX + btn3W + btnGap, ry3, btn3W, leftRowH);
            StopRecordVideoButton.SetBounds(padX + (btn3W + btnGap) * 2, ry3, leftW - (btn3W + btnGap) * 2, leftRowH);

            // Row 5: Auto Controls (AUTO & HOLD)
            int ry4 = ry3 + leftRowH + leftRowGap;
            _btnAutoToggle.SetBounds(padX, ry4, btn2W, leftRowH);
            _btnAutoHold.SetBounds(padX + btn2W + btnGap, ry4, leftW - btn2W - btnGap, leftRowH);

            // Right 3x2 Grid of Setpoint Inputs aligned cleanly with the left rows
            int colGap = 10;
            int colW = (rightW - colGap) / 2;
            int rightBottom = ry4 + leftRowH;
            int rightSpanH = rightBottom - topY;
            int inputH = Math.Min(34, Math.Max(26, (rightSpanH - 64) / 3));
            int rowStep = (rightSpanH - (inputH + 17) - 24) / 2;

            // Row 1: Forward Velocity | Strafing Velocity
            int gy0 = topY - 2;
            _lblVxCaption.Location = new Point(rightX, gy0);
            _vxInputPill.SetBounds(rightX, gy0 + 17, colW, inputH);
            PositionInnerTextBox(Vx_dTextBox, _vxInputPill);

            _lblVyCaption.Location = new Point(rightX + colW + colGap, gy0);
            _vyInputPill.SetBounds(rightX + colW + colGap, gy0 + 17, colW, inputH);
            PositionInnerTextBox(Vy_dTextBox, _vyInputPill);

            // Row 2: Angular Velocity | Position X
            int gy1 = gy0 + rowStep;
            _lblWCaption.Location = new Point(rightX, gy1);
            _wInputPill.SetBounds(rightX, gy1 + 17, colW, inputH);
            PositionInnerTextBox(W_dTextBox, _wInputPill);

            _lblXCaption.Location = new Point(rightX + colW + colGap, gy1);
            _xInputPill.SetBounds(rightX + colW + colGap, gy1 + 17, colW, inputH);
            PositionInnerTextBox(X_dTextBox, _xInputPill);

            // Row 3: Position Y | Theta desired
            int gy2 = gy0 + rowStep * 2;
            _lblYCaption.Location = new Point(rightX, gy2);
            _yInputPill.SetBounds(rightX, gy2 + 17, colW, inputH);
            PositionInnerTextBox(Y_dTextBox, _yInputPill);

            _lblThetaCaption.Location = new Point(rightX + colW + colGap, gy2);
            _thetaInputPill.SetBounds(rightX + colW + colGap, gy2 + 17, colW, inputH);
            PositionInnerTextBox(Theta_dTextBox, _thetaInputPill);

            _lblAutoStatus.SetBounds(rightX, gy2 + 17 + inputH + 6, rightW, 20);
        }

        public void UpdateAutoModePC(bool isAuto)
        {
            if (_btnAutoToggle == null) return;
            _isAutoModeActive = isAuto;
            if (isAuto)
            {
                _btnRoles[_btnAutoToggle] = ModernBtnRole.PrimaryBlue;
                _btnAutoToggle.Text = "🤖 AUTO: ON";
                if (_lblAutoStatus != null) _lblAutoStatus.Text = "Chế độ: Tự động (Giữ HOLD để chạy)";
            }
            else
            {
                _btnRoles[_btnAutoToggle] = ModernBtnRole.OutlineWhite;
                _btnAutoToggle.Text = "🤖 AUTO";
                if (_lblAutoStatus != null) _lblAutoStatus.Text = "Chế độ: Thủ công";
            }
            _btnAutoToggle.Invalidate();
        }

        public void UpdateAutoSubModePC(string mode)
        {
            UpdateAutoModePC(mode != "MANUAL");
        }

        private void PositionInnerTextBox(TextBox tb, Control container)
        {
            int y = Math.Max(2, (container.Height - tb.PreferredHeight) / 2);
            tb.SetBounds(10, y, Math.Max(20, container.Width - 20), tb.PreferredHeight);
        }

        // =========================================================================
        // 7. CUSTOM VECTOR PILL BUTTON RENDERING
        // =========================================================================
        private void RegisterModernButton(Button btn, ModernBtnRole role)
        {
            _btnRoles[btn] = role;
            btn.FlatStyle = FlatStyle.Flat;
            btn.FlatAppearance.BorderSize = 0;
            btn.BackColor = CardWhite;
            btn.UseVisualStyleBackColor = false;
            btn.Cursor = Cursors.Hand;
            btn.Font = new Font("Segoe UI", 8.5F, FontStyle.Bold);

            btn.MouseEnter += (s, e) => { _hoveredButtons.Add(btn); btn.Invalidate(); };
            btn.MouseLeave += (s, e) => { _hoveredButtons.Remove(btn); btn.Invalidate(); };
            btn.Paint -= ModernButton_Paint;
            btn.Paint += ModernButton_Paint;
        }

        private void ModernButton_Paint(object sender, PaintEventArgs e)
        {
            var btn = sender as Button;
            if (btn == null) return;

            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;

            // Clear background with parent card color
            Color parentBg = btn.Parent != null ? btn.Parent.BackColor : CardWhite;
            using (var clearBrush = new SolidBrush(parentBg))
            {
                g.FillRectangle(clearBrush, btn.ClientRectangle);
            }

            ModernBtnRole role = _btnRoles.ContainsKey(btn) ? _btnRoles[btn] : ModernBtnRole.OutlineWhite;
            bool isHovered = _hoveredButtons.Contains(btn);
            bool isHeldOrFlashed = btn.BackColor == BTN_HELD_COLOR ||
                                   btn.BackColor == BTN_EMERGENCY_COLOR ||
                                   btn.BackColor == BTN_PURPLE_COLOR ||
                                   btn.BackColor == BTN_GREEN_COLOR ||
                                   btn.BackColor == BTN_AMBER_COLOR;

            bool isDpad = role >= ModernBtnRole.DpadUp && role <= ModernBtnRole.DpadTurnRight;
            int radius = isDpad ? 12 : Math.Max(10, (btn.Height - 2) / 2);

            Rectangle rect = new Rectangle(1, 1, Math.Max(4, btn.Width - 3), Math.Max(4, btn.Height - 3));

            Color fillColor = CardWhite;
            Color borderColor = Color.Empty;
            Color textColor = TextDark;

            if (!btn.Enabled)
            {
                if (role == ModernBtnRole.PrimaryBlue)
                {
                    fillColor = Color.FromArgb(147, 197, 253);
                    textColor = Color.White;
                }
                else
                {
                    fillColor = Color.FromArgb(248, 250, 252);
                    borderColor = Color.FromArgb(226, 232, 240);
                    textColor = Color.FromArgb(148, 163, 184);
                }
            }
            else if (isHeldOrFlashed)
            {
                fillColor = btn.BackColor;
                textColor = Color.White;
            }
            else
            {
                switch (role)
                {
                    case ModernBtnRole.PrimaryBlue:
                    case ModernBtnRole.TabActive:
                        fillColor = isHovered ? PrimaryBlueHover : PrimaryBlue;
                        textColor = Color.White;
                        break;

                    case ModernBtnRole.DangerRed:
                        fillColor = isHovered ? DangerRedHover : DangerRed;
                        textColor = Color.White;
                        break;

                    case ModernBtnRole.TabInactive:
                        fillColor = isHovered ? Color.FromArgb(224, 234, 248) : PillBgSoft;
                        textColor = TextDark;
                        break;

                    case ModernBtnRole.OutlineWhite:
                    default:
                        fillColor = isHovered ? Color.FromArgb(241, 245, 249) : CardWhite;
                        borderColor = PillBorder;
                        textColor = TextDark;
                        break;
                }
            }

            using (GraphicsPath path = CreateRoundedRectPath(rect, radius))
            {
                using (var fillBrush = new SolidBrush(fillColor))
                {
                    g.FillPath(fillBrush, path);
                }
                if (borderColor != Color.Empty)
                {
                    using (var pen = new Pen(borderColor, 1.2f))
                    {
                        g.DrawPath(pen, path);
                    }
                }
            }

            if (isDpad)
            {
                Color iconColor = isHeldOrFlashed ? Color.White : PrimaryBlue;
                DrawDpadVectorIcon(g, btn.ClientRectangle, role, iconColor);
            }
            else
            {
                StringFormat sf = new StringFormat
                {
                    Alignment = StringAlignment.Center,
                    LineAlignment = StringAlignment.Center,
                    FormatFlags = StringFormatFlags.NoWrap
                };
                using (var textBrush = new SolidBrush(textColor))
                {
                    g.DrawString(btn.Text, btn.Font, textBrush, btn.ClientRectangle, sf);
                }
            }
        }

        private void DrawDpadVectorIcon(Graphics g, Rectangle bounds, ModernBtnRole role, Color color)
        {
            float cx = bounds.Width / 2f;
            float cy = bounds.Height / 2f;

            using (var pen = new Pen(color, 2.2f))
            {
                pen.StartCap = LineCap.Round;
                pen.EndCap = LineCap.Round;
                pen.LineJoin = LineJoin.Round;

                float s = 4.5f;
                switch (role)
                {
                    case ModernBtnRole.DpadUp:
                        g.DrawLines(pen, new PointF[] {
                            new PointF(cx - s, cy + s * 0.45f),
                            new PointF(cx, cy - s * 0.55f),
                            new PointF(cx + s, cy + s * 0.45f)
                        });
                        break;

                    case ModernBtnRole.DpadDown:
                        g.DrawLines(pen, new PointF[] {
                            new PointF(cx - s, cy - s * 0.45f),
                            new PointF(cx, cy + s * 0.55f),
                            new PointF(cx + s, cy - s * 0.45f)
                        });
                        break;

                    case ModernBtnRole.DpadLeft:
                        g.DrawLines(pen, new PointF[] {
                            new PointF(cx + s * 0.45f, cy - s),
                            new PointF(cx - s * 0.55f, cy),
                            new PointF(cx + s * 0.45f, cy + s)
                        });
                        break;

                    case ModernBtnRole.DpadRight:
                        g.DrawLines(pen, new PointF[] {
                            new PointF(cx - s * 0.45f, cy - s),
                            new PointF(cx + s * 0.55f, cy),
                            new PointF(cx - s * 0.45f, cy + s)
                        });
                        break;

                    case ModernBtnRole.DpadTurnLeft:
                        {
                            float r = 7.5f;
                            RectangleF arcRect = new RectangleF(cx - r, cy - r, r * 2, r * 2);
                            g.DrawArc(pen, arcRect, -45f, 280f);
                            double rad = -45.0 * Math.PI / 180.0;
                            float ax = cx + (float)(r * Math.Cos(rad));
                            float ay = cy + (float)(r * Math.Sin(rad));
                            g.DrawLines(pen, new PointF[] {
                                new PointF(ax - 4.2f, ay - 0.8f),
                                new PointF(ax, ay),
                                new PointF(ax - 0.8f, ay + 4.2f)
                            });
                        }
                        break;

                    case ModernBtnRole.DpadTurnRight:
                        {
                            float r = 7.5f;
                            RectangleF arcRect = new RectangleF(cx - r, cy - r, r * 2, r * 2);
                            g.DrawArc(pen, arcRect, 225f, -280f);
                            double rad = 225.0 * Math.PI / 180.0;
                            float ax = cx + (float)(r * Math.Cos(rad));
                            float ay = cy + (float)(r * Math.Sin(rad));
                            g.DrawLines(pen, new PointF[] {
                                new PointF(ax + 4.2f, ay - 0.8f),
                                new PointF(ax, ay),
                                new PointF(ax + 0.8f, ay + 4.2f)
                            });
                        }
                        break;
                }
            }
        }

        internal static GraphicsPath CreateRoundedRectPath(Rectangle rect, int radius)
        {
            GraphicsPath path = new GraphicsPath();
            int d = Math.Min(radius * 2, Math.Min(rect.Width, rect.Height));
            if (d <= 0)
            {
                path.AddRectangle(rect);
                return path;
            }
            path.AddArc(rect.X, rect.Y, d, d, 180, 90);
            path.AddArc(rect.Right - d, rect.Y, d, d, 270, 90);
            path.AddArc(rect.Right - d, rect.Bottom - d, d, d, 0, 90);
            path.AddArc(rect.X, rect.Bottom - d, d, d, 90, 90);
            path.CloseFigure();
            return path;
        }

        // =========================================================================
        // 8. CUSTOM UI CONTROLS (CARDS, PILL INPUTS, TELEMETRY BOXES, STATUS BADGE)
        // =========================================================================

        private class ModernCardPanel : Panel
        {
            public int CornerRadius { get; set; } = 18;
            public string HeaderTitle { get; set; }
            public string HeaderSubtitle { get; set; }

            private static readonly Font TitleFont = new Font("Segoe UI", 10.5F, FontStyle.Bold);
            private static readonly Font SubtitleFont = new Font("Segoe UI", 8.5F, FontStyle.Regular);

            public ModernCardPanel()
            {
                this.DoubleBuffered = true;
                this.BackColor = CardWhite;
                this.SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            }

            protected override void OnPaint(PaintEventArgs e)
            {
                Graphics g = e.Graphics;
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;

                using (var parentBrush = new SolidBrush(BgIceBlue))
                {
                    g.FillRectangle(parentBrush, this.ClientRectangle);
                }

                Rectangle rect = new Rectangle(1, 1, Math.Max(4, this.Width - 3), Math.Max(4, this.Height - 3));
                using (GraphicsPath path = CreateRoundedRectPath(rect, CornerRadius))
                {
                    using (var fillBrush = new SolidBrush(CardWhite))
                    {
                        g.FillPath(fillBrush, path);
                    }
                    using (var borderPen = new Pen(CardBorder, 1.2f))
                    {
                        g.DrawPath(borderPen, path);
                    }
                }

                if (!string.IsNullOrEmpty(HeaderTitle))
                {
                    using (var titleBrush = new SolidBrush(PrimaryBlue))
                    {
                        g.DrawString(HeaderTitle, TitleFont, titleBrush, new PointF(16, 12));
                    }
                    if (!string.IsNullOrEmpty(HeaderSubtitle))
                    {
                        SizeF titleSz = g.MeasureString(HeaderTitle, TitleFont);
                        using (var subBrush = new SolidBrush(TextMuted))
                        {
                            g.DrawString(HeaderSubtitle, SubtitleFont, subBrush, new PointF(16 + titleSz.Width + 4, 15));
                        }
                    }
                }
            }
        }

        private class ModernPillInputContainer : Panel
        {
            public int CornerRadius { get; set; } = 10;

            public ModernPillInputContainer()
            {
                this.DoubleBuffered = true;
                this.BackColor = PillBgSoft;
                this.SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            }

            protected override void OnPaint(PaintEventArgs e)
            {
                Graphics g = e.Graphics;
                g.SmoothingMode = SmoothingMode.AntiAlias;

                Color parentBg = this.Parent != null ? this.Parent.BackColor : CardWhite;
                using (var clearBrush = new SolidBrush(parentBg))
                {
                    g.FillRectangle(clearBrush, this.ClientRectangle);
                }

                Rectangle rect = new Rectangle(1, 1, Math.Max(4, this.Width - 3), Math.Max(4, this.Height - 3));
                using (GraphicsPath path = CreateRoundedRectPath(rect, CornerRadius))
                {
                    using (var fillBrush = new SolidBrush(PillBgSoft))
                    {
                        g.FillPath(fillBrush, path);
                    }
                    using (var borderPen = new Pen(PillBorder, 1.0f))
                    {
                        g.DrawPath(borderPen, path);
                    }
                }
            }
        }

        private class ModernTelemetryBox : Panel
        {
            private readonly string _caption;
            private readonly TextBox _valueBox;
            private static readonly Font CaptionFont = new Font("Segoe UI", 8.0F, FontStyle.Regular);

            public ModernTelemetryBox(string caption, TextBox valueBox)
            {
                _caption = caption;
                _valueBox = valueBox;
                this.DoubleBuffered = true;
                this.BackColor = PillBgSoft;
                this.SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);

                _valueBox.Parent = this;
                _valueBox.BorderStyle = BorderStyle.None;
                _valueBox.ReadOnly = true;
                _valueBox.BackColor = PillBgSoft;
                _valueBox.ForeColor = PrimaryBlue;
                _valueBox.Font = new Font("Consolas", 9.5F, FontStyle.Bold);
                _valueBox.TabStop = false;
            }

            public void PerformInternalLayout()
            {
                int y = Math.Max(20, this.Height - _valueBox.PreferredHeight - 6);
                _valueBox.SetBounds(10, y, Math.Max(20, this.Width - 16), _valueBox.PreferredHeight);
            }

            protected override void OnPaint(PaintEventArgs e)
            {
                Graphics g = e.Graphics;
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;

                using (var clearBrush = new SolidBrush(CardWhite))
                {
                    g.FillRectangle(clearBrush, this.ClientRectangle);
                }

                Rectangle rect = new Rectangle(0, 0, Math.Max(4, this.Width - 1), Math.Max(4, this.Height - 1));
                using (GraphicsPath path = CreateRoundedRectPath(rect, 10))
                {
                    using (var fillBrush = new SolidBrush(PillBgSoft))
                    {
                        g.FillPath(fillBrush, path);
                    }
                }

                using (var capBrush = new SolidBrush(TextMuted))
                {
                    g.DrawString(_caption, CaptionFont, capBrush, new PointF(8, 5));
                }
            }
        }

        private class ModernStatusPill : Control
        {
            public string StatusText { get; set; } = "• Ready";
            public Color PillBackColor { get; set; } = ReadyBg;
            public Color PillTextColor { get; set; } = ReadyText;

            public ModernStatusPill()
            {
                this.DoubleBuffered = true;
                this.Font = new Font("Segoe UI", 8.5F, FontStyle.Bold);
                this.Size = new Size(80, 30);
                this.SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            }

            protected override void OnPaint(PaintEventArgs e)
            {
                Graphics g = e.Graphics;
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;

                using (var clearBrush = new SolidBrush(CardWhite))
                {
                    g.FillRectangle(clearBrush, this.ClientRectangle);
                }

                Rectangle rect = new Rectangle(1, 1, Math.Max(4, this.Width - 3), Math.Max(4, this.Height - 3));
                int radius = Math.Max(8, (this.Height - 2) / 2);
                using (GraphicsPath path = CreateRoundedRectPath(rect, radius))
                {
                    using (var fillBrush = new SolidBrush(PillBackColor))
                    {
                        g.FillPath(fillBrush, path);
                    }
                }

                StringFormat sf = new StringFormat
                {
                    Alignment = StringAlignment.Center,
                    LineAlignment = StringAlignment.Center,
                    FormatFlags = StringFormatFlags.NoWrap
                };
                using (var textBrush = new SolidBrush(PillTextColor))
                {
                    g.DrawString(StatusText, this.Font, textBrush, this.ClientRectangle, sf);
                }
            }
        }
    }
}
