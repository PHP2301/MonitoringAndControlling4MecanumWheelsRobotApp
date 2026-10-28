namespace MonitoringAndControllingRobotsApp
{
    partial class AppForm
    {
        /// <summary>
        /// Required designer variable.
        /// </summary>
        private System.ComponentModel.IContainer components = null;

        /// <summary>
        /// Clean up any resources being used.
        /// </summary>
        /// <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
            {
                components.Dispose();
            }
            base.Dispose(disposing);
        }

        #region Windows Form Designer generated code

        /// <summary>
        /// Required method for Designer support - do not modify
        /// the contents of this method with the code editor.
        /// </summary>
        private void InitializeComponent()
        {
            this.TCPGroupBox = new System.Windows.Forms.GroupBox();
            this.DisconnectButton = new System.Windows.Forms.Button();
            this.ScanIPButton = new System.Windows.Forms.Button();
            this.IPComboBox = new System.Windows.Forms.ComboBox();
            this.label4 = new System.Windows.Forms.Label();
            this.label5 = new System.Windows.Forms.Label();
            this.ConnectButton = new System.Windows.Forms.Button();
            this.PortTextBox = new System.Windows.Forms.TextBox();
            this.LabelAnnouncement = new System.Windows.Forms.Label();
            this.TrajectoryPlot = new ScottPlot.WinForms.FormsPlot();
            this.checkTrajectoryAutoScale = new System.Windows.Forms.CheckBox();
            this.tabControl1 = new System.Windows.Forms.TabControl();
            this.MainMonitorTab = new System.Windows.Forms.TabPage();
            this.tableLayoutPanel1 = new System.Windows.Forms.TableLayoutPanel();
            this.PositionRobotAnalysisTab = new System.Windows.Forms.TabPage();
            this.tableLayoutPanel6 = new System.Windows.Forms.TableLayoutPanel();
            this.PlotXTime = new ScottPlot.WinForms.FormsPlot();
            this.PlotYTime = new ScottPlot.WinForms.FormsPlot();
            this.PlotThetaTime = new ScottPlot.WinForms.FormsPlot();
            this.checkPositionRobotAutoScale = new System.Windows.Forms.CheckBox();
            this.VelocityRobotAnalysisTab = new System.Windows.Forms.TabPage();
            this.tableLayoutPanel4 = new System.Windows.Forms.TableLayoutPanel();
            this.PlotWTime = new ScottPlot.WinForms.FormsPlot();
            this.PlotVxTime = new ScottPlot.WinForms.FormsPlot();
            this.PlotVyTime = new ScottPlot.WinForms.FormsPlot();
            this.checkVelocityRobotAutoScale = new System.Windows.Forms.CheckBox();
            this.VelocityMotorsAnalysisTab = new System.Windows.Forms.TabPage();
            this.tableLayoutPanel2 = new System.Windows.Forms.TableLayoutPanel();
            this.tableLayoutPanel3 = new System.Windows.Forms.TableLayoutPanel();
            this.PlotW4Time = new ScottPlot.WinForms.FormsPlot();
            this.PlotW3Time = new ScottPlot.WinForms.FormsPlot();
            this.PlotW2Time = new ScottPlot.WinForms.FormsPlot();
            this.PlotW1Time = new ScottPlot.WinForms.FormsPlot();
            this.checkVelocityMotorsAutoScale = new System.Windows.Forms.CheckBox();
            this.ControlGroupBox = new System.Windows.Forms.GroupBox();
            this.MonitorGroupBox = new System.Windows.Forms.GroupBox();
            this.groupBox2 = new System.Windows.Forms.GroupBox();
            this.label17 = new System.Windows.Forms.Label();
            this.CurrentW4TextBox = new System.Windows.Forms.TextBox();
            this.label16 = new System.Windows.Forms.Label();
            this.CurrentW3TextBox = new System.Windows.Forms.TextBox();
            this.label15 = new System.Windows.Forms.Label();
            this.CurrentW2TextBox = new System.Windows.Forms.TextBox();
            this.label9 = new System.Windows.Forms.Label();
            this.CurrentVxTextBox = new System.Windows.Forms.TextBox();
            this.CurrentW1TextBox = new System.Windows.Forms.TextBox();
            this.CurrentXTextBox = new System.Windows.Forms.TextBox();
            this.label6 = new System.Windows.Forms.Label();
            this.label2 = new System.Windows.Forms.Label();
            this.CurrentYTextBox = new System.Windows.Forms.TextBox();
            this.label1 = new System.Windows.Forms.Label();
            this.CurrentThetaTextBox = new System.Windows.Forms.TextBox();
            this.label3 = new System.Windows.Forms.Label();
            this.CurrentWTextBox = new System.Windows.Forms.TextBox();
            this.label8 = new System.Windows.Forms.Label();
            this.label7 = new System.Windows.Forms.Label();
            this.CurrentVyTextBox = new System.Windows.Forms.TextBox();
            this.PauseGraphButton = new System.Windows.Forms.Button();
            this.ExportCSVButton = new System.Windows.Forms.Button();
            this.StartGraphButton = new System.Windows.Forms.Button();
            this.StopGraphButton = new System.Windows.Forms.Button();
            this.AutoGroupBox = new System.Windows.Forms.GroupBox();
            this.ReturnHomeButton = new System.Windows.Forms.Button();
            this.groupBox3 = new System.Windows.Forms.GroupBox();
            this.label20 = new System.Windows.Forms.Label();
            this.Vy_dTextBox = new System.Windows.Forms.TextBox();
            this.Theta_dTextBox = new System.Windows.Forms.TextBox();
            this.label14 = new System.Windows.Forms.Label();
            this.label12 = new System.Windows.Forms.Label();
            this.X_dTextBox = new System.Windows.Forms.TextBox();
            this.Y_dTextBox = new System.Windows.Forms.TextBox();
            this.label13 = new System.Windows.Forms.Label();
            this.ResetRobotButton = new System.Windows.Forms.Button();
            this.label10 = new System.Windows.Forms.Label();
            this.Vx_dTextBox = new System.Windows.Forms.TextBox();
            this.SetRobotButton = new System.Windows.Forms.Button();
            this.W_dTextBox = new System.Windows.Forms.TextBox();
            this.label11 = new System.Windows.Forms.Label();
            this.CircleLineButton = new System.Windows.Forms.Button();
            this.EmergencyStopButton = new System.Windows.Forms.Button();
            this.ManualGroupBox = new System.Windows.Forms.GroupBox();
            this.TurnRightButton = new System.Windows.Forms.Button();
            this.TurnLeftButton = new System.Windows.Forms.Button();
            this.groupBox1 = new System.Windows.Forms.GroupBox();
            this.SetW4TextBox = new System.Windows.Forms.TextBox();
            this.label19 = new System.Windows.Forms.Label();
            this.SetW3TextBox = new System.Windows.Forms.TextBox();
            this.label18 = new System.Windows.Forms.Label();
            this.ResetButton = new System.Windows.Forms.Button();
            this.LabelSPVel = new System.Windows.Forms.Label();
            this.SetW1TextBox = new System.Windows.Forms.TextBox();
            this.SetMotorButton = new System.Windows.Forms.Button();
            this.SetW2TextBox = new System.Windows.Forms.TextBox();
            this.LabelSPPos = new System.Windows.Forms.Label();
            this.DownButton = new System.Windows.Forms.Button();
            this.RightButton = new System.Windows.Forms.Button();
            this.LeftButton = new System.Windows.Forms.Button();
            this.UpButton = new System.Windows.Forms.Button();
            this.TakePhotoButton = new System.Windows.Forms.Button();
            this.StartRecordVideoButton = new System.Windows.Forms.Button();
            this.StopRecordVideoButton = new System.Windows.Forms.Button();
            this.SwitchCameraButton = new System.Windows.Forms.Button();
            this.StartStreamButton = new System.Windows.Forms.Button();
            this.StopStreamButton = new System.Windows.Forms.Button();
            this.cboCameraMode = new System.Windows.Forms.ComboBox();
            this.txtCameraIp = new System.Windows.Forms.TextBox();
            this.TCPGroupBox.SuspendLayout();
            this.tabControl1.SuspendLayout();
            this.MainMonitorTab.SuspendLayout();
            this.tableLayoutPanel1.SuspendLayout();
            this.PositionRobotAnalysisTab.SuspendLayout();
            this.tableLayoutPanel6.SuspendLayout();
            this.VelocityRobotAnalysisTab.SuspendLayout();
            this.tableLayoutPanel4.SuspendLayout();
            this.VelocityMotorsAnalysisTab.SuspendLayout();
            this.tableLayoutPanel2.SuspendLayout();
            this.tableLayoutPanel3.SuspendLayout();
            this.ControlGroupBox.SuspendLayout();
            this.MonitorGroupBox.SuspendLayout();
            this.groupBox2.SuspendLayout();
            this.AutoGroupBox.SuspendLayout();
            this.groupBox3.SuspendLayout();
            this.ManualGroupBox.SuspendLayout();
            this.groupBox1.SuspendLayout();
            this.SuspendLayout();
            // 
            // TCPGroupBox
            // 
            this.TCPGroupBox.Controls.Add(this.DisconnectButton);
            this.TCPGroupBox.Controls.Add(this.ScanIPButton);
            this.TCPGroupBox.Controls.Add(this.IPComboBox);
            this.TCPGroupBox.Controls.Add(this.label4);
            this.TCPGroupBox.Controls.Add(this.label5);
            this.TCPGroupBox.Controls.Add(this.ConnectButton);
            this.TCPGroupBox.Controls.Add(this.PortTextBox);
            this.TCPGroupBox.Font = new System.Drawing.Font("Microsoft Sans Serif", 7.8F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.TCPGroupBox.Location = new System.Drawing.Point(12, 5);
            this.TCPGroupBox.Name = "TCPGroupBox";
            this.TCPGroupBox.Size = new System.Drawing.Size(868, 60);
            this.TCPGroupBox.TabIndex = 77;
            this.TCPGroupBox.TabStop = false;
            this.TCPGroupBox.Text = "UDP";
            // 
            // DisconnectButton
            // 
            this.DisconnectButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.DisconnectButton.Location = new System.Drawing.Point(740, 16);
            this.DisconnectButton.Name = "DisconnectButton";
            this.DisconnectButton.Size = new System.Drawing.Size(114, 38);
            this.DisconnectButton.TabIndex = 15;
            this.DisconnectButton.Text = "Disconnect";
            this.DisconnectButton.UseVisualStyleBackColor = true;
            this.DisconnectButton.Click += new System.EventHandler(this.DisconnectButton_Click);
            // 
            // ScanIPButton
            // 
            this.ScanIPButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.ScanIPButton.Location = new System.Drawing.Point(522, 16);
            this.ScanIPButton.Name = "ScanIPButton";
            this.ScanIPButton.Size = new System.Drawing.Size(91, 38);
            this.ScanIPButton.TabIndex = 14;
            this.ScanIPButton.Text = "Scan IP";
            this.ScanIPButton.UseVisualStyleBackColor = true;
            this.ScanIPButton.Click += new System.EventHandler(this.ScanIPButton_Click);
            // 
            // IPComboBox
            // 
            this.IPComboBox.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.IPComboBox.FormattingEnabled = true;
            this.IPComboBox.Items.AddRange(new object[] {
            "0.0.0.0"});
            this.IPComboBox.Location = new System.Drawing.Point(26, 24);
            this.IPComboBox.Name = "IPComboBox";
            this.IPComboBox.Size = new System.Drawing.Size(300, 28);
            this.IPComboBox.TabIndex = 13;
            // 
            // label4
            // 
            this.label4.AutoSize = true;
            this.label4.Font = new System.Drawing.Font("Microsoft Sans Serif", 7.8F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label4.Location = new System.Drawing.Point(345, 29);
            this.label4.Name = "label4";
            this.label4.Size = new System.Drawing.Size(31, 16);
            this.label4.TabIndex = 11;
            this.label4.Text = "Port";
            // 
            // label5
            // 
            this.label5.AutoSize = true;
            this.label5.Font = new System.Drawing.Font("Microsoft Sans Serif", 7.8F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label5.Location = new System.Drawing.Point(6, 28);
            this.label5.Name = "label5";
            this.label5.Size = new System.Drawing.Size(19, 16);
            this.label5.TabIndex = 10;
            this.label5.Text = "IP";
            // 
            // ConnectButton
            // 
            this.ConnectButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.ConnectButton.Location = new System.Drawing.Point(631, 16);
            this.ConnectButton.Name = "ConnectButton";
            this.ConnectButton.Size = new System.Drawing.Size(91, 38);
            this.ConnectButton.TabIndex = 5;
            this.ConnectButton.Text = "Connect";
            this.ConnectButton.UseVisualStyleBackColor = true;
            this.ConnectButton.Click += new System.EventHandler(this.ConnectButton_Click_1);
            // 
            // PortTextBox
            // 
            this.PortTextBox.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.PortTextBox.Location = new System.Drawing.Point(382, 24);
            this.PortTextBox.Name = "PortTextBox";
            this.PortTextBox.Size = new System.Drawing.Size(118, 26);
            this.PortTextBox.TabIndex = 9;
            // 
            // LabelAnnouncement
            // 
            this.LabelAnnouncement.AutoSize = true;
            this.LabelAnnouncement.Font = new System.Drawing.Font("Microsoft Sans Serif", 7.8F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.LabelAnnouncement.ForeColor = System.Drawing.SystemColors.ControlText;
            this.LabelAnnouncement.Location = new System.Drawing.Point(887, 33);
            this.LabelAnnouncement.Margin = new System.Windows.Forms.Padding(4, 0, 4, 0);
            this.LabelAnnouncement.Name = "LabelAnnouncement";
            this.LabelAnnouncement.Size = new System.Drawing.Size(31, 16);
            this.LabelAnnouncement.TabIndex = 78;
            this.LabelAnnouncement.Text = "Abc";
            // 
            // TrajectoryPlot
            // 
            this.TrajectoryPlot.Dock = System.Windows.Forms.DockStyle.Fill;
            this.TrajectoryPlot.Location = new System.Drawing.Point(3, 3);
            this.TrajectoryPlot.Name = "TrajectoryPlot";
            this.TrajectoryPlot.Size = new System.Drawing.Size(997, 701);
            this.TrajectoryPlot.TabIndex = 79;
            // 
            // checkTrajectoryAutoScale
            // 
            this.checkTrajectoryAutoScale.AutoSize = true;
            this.checkTrajectoryAutoScale.Font = new System.Drawing.Font("Microsoft Sans Serif", 7.8F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.checkTrajectoryAutoScale.Location = new System.Drawing.Point(3, 710);
            this.checkTrajectoryAutoScale.Name = "checkTrajectoryAutoScale";
            this.checkTrajectoryAutoScale.Size = new System.Drawing.Size(94, 20);
            this.checkTrajectoryAutoScale.TabIndex = 80;
            this.checkTrajectoryAutoScale.Text = "Auto Scale";
            this.checkTrajectoryAutoScale.UseVisualStyleBackColor = true;
            // 
            // tabControl1
            // 
            this.tabControl1.Controls.Add(this.MainMonitorTab);
            this.tabControl1.Controls.Add(this.PositionRobotAnalysisTab);
            this.tabControl1.Controls.Add(this.VelocityRobotAnalysisTab);
            this.tabControl1.Controls.Add(this.VelocityMotorsAnalysisTab);
            this.tabControl1.Font = new System.Drawing.Font("Microsoft Sans Serif", 7.8F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.tabControl1.Location = new System.Drawing.Point(12, 91);
            this.tabControl1.Name = "tabControl1";
            this.tabControl1.SelectedIndex = 0;
            this.tabControl1.Size = new System.Drawing.Size(1017, 772);
            this.tabControl1.TabIndex = 81;
            // 
            // MainMonitorTab
            // 
            this.MainMonitorTab.Controls.Add(this.tableLayoutPanel1);
            this.MainMonitorTab.Font = new System.Drawing.Font("Microsoft Sans Serif", 7.8F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.MainMonitorTab.Location = new System.Drawing.Point(4, 25);
            this.MainMonitorTab.Name = "MainMonitorTab";
            this.MainMonitorTab.Padding = new System.Windows.Forms.Padding(3);
            this.MainMonitorTab.Size = new System.Drawing.Size(1009, 743);
            this.MainMonitorTab.TabIndex = 0;
            this.MainMonitorTab.Text = "Main Monitor ";
            this.MainMonitorTab.UseVisualStyleBackColor = true;
            // 
            // tableLayoutPanel1
            // 
            this.tableLayoutPanel1.ColumnCount = 1;
            this.tableLayoutPanel1.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel1.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel1.Controls.Add(this.TrajectoryPlot, 0, 0);
            this.tableLayoutPanel1.Controls.Add(this.checkTrajectoryAutoScale, 0, 1);
            this.tableLayoutPanel1.Dock = System.Windows.Forms.DockStyle.Fill;
            this.tableLayoutPanel1.Location = new System.Drawing.Point(3, 3);
            this.tableLayoutPanel1.Name = "tableLayoutPanel1";
            this.tableLayoutPanel1.RowCount = 2;
            this.tableLayoutPanel1.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 95.99428F));
            this.tableLayoutPanel1.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 4.005723F));
            this.tableLayoutPanel1.Size = new System.Drawing.Size(1003, 737);
            this.tableLayoutPanel1.TabIndex = 0;
            // 
            // PositionRobotAnalysisTab
            // 
            this.PositionRobotAnalysisTab.Controls.Add(this.tableLayoutPanel6);
            this.PositionRobotAnalysisTab.Font = new System.Drawing.Font("Microsoft Sans Serif", 7.8F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.PositionRobotAnalysisTab.Location = new System.Drawing.Point(4, 25);
            this.PositionRobotAnalysisTab.Name = "PositionRobotAnalysisTab";
            this.PositionRobotAnalysisTab.Padding = new System.Windows.Forms.Padding(3);
            this.PositionRobotAnalysisTab.Size = new System.Drawing.Size(1009, 721);
            this.PositionRobotAnalysisTab.TabIndex = 1;
            this.PositionRobotAnalysisTab.Text = "Position Robot Analysis";
            this.PositionRobotAnalysisTab.UseVisualStyleBackColor = true;
            // 
            // tableLayoutPanel6
            // 
            this.tableLayoutPanel6.ColumnCount = 1;
            this.tableLayoutPanel6.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel6.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel6.Controls.Add(this.PlotXTime, 0, 0);
            this.tableLayoutPanel6.Controls.Add(this.PlotYTime, 0, 1);
            this.tableLayoutPanel6.Controls.Add(this.PlotThetaTime, 0, 2);
            this.tableLayoutPanel6.Controls.Add(this.checkPositionRobotAutoScale, 0, 3);
            this.tableLayoutPanel6.Dock = System.Windows.Forms.DockStyle.Fill;
            this.tableLayoutPanel6.Location = new System.Drawing.Point(3, 3);
            this.tableLayoutPanel6.Name = "tableLayoutPanel6";
            this.tableLayoutPanel6.RowCount = 4;
            this.tableLayoutPanel6.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel6.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel6.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 229F));
            this.tableLayoutPanel6.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 27F));
            this.tableLayoutPanel6.Size = new System.Drawing.Size(1003, 715);
            this.tableLayoutPanel6.TabIndex = 0;
            // 
            // PlotXTime
            // 
            this.PlotXTime.Dock = System.Windows.Forms.DockStyle.Fill;
            this.PlotXTime.Location = new System.Drawing.Point(3, 3);
            this.PlotXTime.Name = "PlotXTime";
            this.PlotXTime.Size = new System.Drawing.Size(997, 223);
            this.PlotXTime.TabIndex = 0;
            // 
            // PlotYTime
            // 
            this.PlotYTime.Dock = System.Windows.Forms.DockStyle.Fill;
            this.PlotYTime.Location = new System.Drawing.Point(3, 232);
            this.PlotYTime.Name = "PlotYTime";
            this.PlotYTime.Size = new System.Drawing.Size(997, 223);
            this.PlotYTime.TabIndex = 1;
            // 
            // PlotThetaTime
            // 
            this.PlotThetaTime.Dock = System.Windows.Forms.DockStyle.Fill;
            this.PlotThetaTime.Location = new System.Drawing.Point(3, 461);
            this.PlotThetaTime.Name = "PlotThetaTime";
            this.PlotThetaTime.Size = new System.Drawing.Size(997, 223);
            this.PlotThetaTime.TabIndex = 2;
            // 
            // checkPositionRobotAutoScale
            // 
            this.checkPositionRobotAutoScale.AutoSize = true;
            this.checkPositionRobotAutoScale.Font = new System.Drawing.Font("Microsoft Sans Serif", 7.8F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.checkPositionRobotAutoScale.Location = new System.Drawing.Point(3, 690);
            this.checkPositionRobotAutoScale.Name = "checkPositionRobotAutoScale";
            this.checkPositionRobotAutoScale.Size = new System.Drawing.Size(94, 20);
            this.checkPositionRobotAutoScale.TabIndex = 81;
            this.checkPositionRobotAutoScale.Text = "Auto Scale";
            this.checkPositionRobotAutoScale.UseVisualStyleBackColor = true;
            // 
            // VelocityRobotAnalysisTab
            // 
            this.VelocityRobotAnalysisTab.Controls.Add(this.tableLayoutPanel4);
            this.VelocityRobotAnalysisTab.Location = new System.Drawing.Point(4, 25);
            this.VelocityRobotAnalysisTab.Name = "VelocityRobotAnalysisTab";
            this.VelocityRobotAnalysisTab.Padding = new System.Windows.Forms.Padding(3);
            this.VelocityRobotAnalysisTab.Size = new System.Drawing.Size(1009, 721);
            this.VelocityRobotAnalysisTab.TabIndex = 2;
            this.VelocityRobotAnalysisTab.Text = "Velocity Robot Analysis";
            this.VelocityRobotAnalysisTab.UseVisualStyleBackColor = true;
            // 
            // tableLayoutPanel4
            // 
            this.tableLayoutPanel4.ColumnCount = 1;
            this.tableLayoutPanel4.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel4.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel4.Controls.Add(this.PlotWTime, 0, 2);
            this.tableLayoutPanel4.Controls.Add(this.PlotVxTime, 0, 0);
            this.tableLayoutPanel4.Controls.Add(this.PlotVyTime, 0, 1);
            this.tableLayoutPanel4.Controls.Add(this.checkVelocityRobotAutoScale, 0, 3);
            this.tableLayoutPanel4.Dock = System.Windows.Forms.DockStyle.Fill;
            this.tableLayoutPanel4.Location = new System.Drawing.Point(3, 3);
            this.tableLayoutPanel4.Name = "tableLayoutPanel4";
            this.tableLayoutPanel4.RowCount = 4;
            this.tableLayoutPanel4.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 49.66443F));
            this.tableLayoutPanel4.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 50.33557F));
            this.tableLayoutPanel4.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 224F));
            this.tableLayoutPanel4.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 23F));
            this.tableLayoutPanel4.Size = new System.Drawing.Size(1003, 715);
            this.tableLayoutPanel4.TabIndex = 0;
            // 
            // PlotWTime
            // 
            this.PlotWTime.Dock = System.Windows.Forms.DockStyle.Fill;
            this.PlotWTime.Location = new System.Drawing.Point(3, 470);
            this.PlotWTime.Name = "PlotWTime";
            this.PlotWTime.Size = new System.Drawing.Size(997, 218);
            this.PlotWTime.TabIndex = 83;
            // 
            // PlotVxTime
            // 
            this.PlotVxTime.Dock = System.Windows.Forms.DockStyle.Fill;
            this.PlotVxTime.Location = new System.Drawing.Point(3, 3);
            this.PlotVxTime.Name = "PlotVxTime";
            this.PlotVxTime.Size = new System.Drawing.Size(997, 226);
            this.PlotVxTime.TabIndex = 0;
            // 
            // PlotVyTime
            // 
            this.PlotVyTime.Dock = System.Windows.Forms.DockStyle.Fill;
            this.PlotVyTime.Location = new System.Drawing.Point(3, 235);
            this.PlotVyTime.Name = "PlotVyTime";
            this.PlotVyTime.Size = new System.Drawing.Size(997, 229);
            this.PlotVyTime.TabIndex = 1;
            // 
            // checkVelocityRobotAutoScale
            // 
            this.checkVelocityRobotAutoScale.AutoSize = true;
            this.checkVelocityRobotAutoScale.Font = new System.Drawing.Font("Microsoft Sans Serif", 7.8F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.checkVelocityRobotAutoScale.Location = new System.Drawing.Point(3, 694);
            this.checkVelocityRobotAutoScale.Name = "checkVelocityRobotAutoScale";
            this.checkVelocityRobotAutoScale.Size = new System.Drawing.Size(94, 18);
            this.checkVelocityRobotAutoScale.TabIndex = 82;
            this.checkVelocityRobotAutoScale.Text = "Auto Scale";
            this.checkVelocityRobotAutoScale.UseVisualStyleBackColor = true;
            // 
            // VelocityMotorsAnalysisTab
            // 
            this.VelocityMotorsAnalysisTab.Controls.Add(this.tableLayoutPanel2);
            this.VelocityMotorsAnalysisTab.Location = new System.Drawing.Point(4, 25);
            this.VelocityMotorsAnalysisTab.Name = "VelocityMotorsAnalysisTab";
            this.VelocityMotorsAnalysisTab.Padding = new System.Windows.Forms.Padding(3);
            this.VelocityMotorsAnalysisTab.Size = new System.Drawing.Size(1009, 721);
            this.VelocityMotorsAnalysisTab.TabIndex = 3;
            this.VelocityMotorsAnalysisTab.Text = "Velocity Motors Analysis";
            this.VelocityMotorsAnalysisTab.UseVisualStyleBackColor = true;
            // 
            // tableLayoutPanel2
            // 
            this.tableLayoutPanel2.ColumnCount = 1;
            this.tableLayoutPanel2.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel2.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel2.Controls.Add(this.tableLayoutPanel3, 0, 0);
            this.tableLayoutPanel2.Controls.Add(this.checkVelocityMotorsAutoScale, 0, 1);
            this.tableLayoutPanel2.Dock = System.Windows.Forms.DockStyle.Fill;
            this.tableLayoutPanel2.Location = new System.Drawing.Point(3, 3);
            this.tableLayoutPanel2.Name = "tableLayoutPanel2";
            this.tableLayoutPanel2.RowCount = 2;
            this.tableLayoutPanel2.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 96.42346F));
            this.tableLayoutPanel2.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 3.576538F));
            this.tableLayoutPanel2.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 118F));
            this.tableLayoutPanel2.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 135F));
            this.tableLayoutPanel2.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 24F));
            this.tableLayoutPanel2.Size = new System.Drawing.Size(1003, 715);
            this.tableLayoutPanel2.TabIndex = 0;
            this.tableLayoutPanel2.Paint += new System.Windows.Forms.PaintEventHandler(this.tableLayoutPanel2_Paint);
            // 
            // tableLayoutPanel3
            // 
            this.tableLayoutPanel3.ColumnCount = 2;
            this.tableLayoutPanel3.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel3.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel3.Controls.Add(this.PlotW4Time, 1, 1);
            this.tableLayoutPanel3.Controls.Add(this.PlotW3Time, 0, 1);
            this.tableLayoutPanel3.Controls.Add(this.PlotW2Time, 1, 0);
            this.tableLayoutPanel3.Controls.Add(this.PlotW1Time, 0, 0);
            this.tableLayoutPanel3.Dock = System.Windows.Forms.DockStyle.Fill;
            this.tableLayoutPanel3.Location = new System.Drawing.Point(3, 3);
            this.tableLayoutPanel3.Name = "tableLayoutPanel3";
            this.tableLayoutPanel3.RowCount = 2;
            this.tableLayoutPanel3.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel3.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 50F));
            this.tableLayoutPanel3.Size = new System.Drawing.Size(997, 683);
            this.tableLayoutPanel3.TabIndex = 0;
            // 
            // PlotW4Time
            // 
            this.PlotW4Time.Dock = System.Windows.Forms.DockStyle.Fill;
            this.PlotW4Time.Location = new System.Drawing.Point(501, 344);
            this.PlotW4Time.Name = "PlotW4Time";
            this.PlotW4Time.Size = new System.Drawing.Size(493, 336);
            this.PlotW4Time.TabIndex = 4;
            // 
            // PlotW3Time
            // 
            this.PlotW3Time.Dock = System.Windows.Forms.DockStyle.Fill;
            this.PlotW3Time.Location = new System.Drawing.Point(3, 344);
            this.PlotW3Time.Name = "PlotW3Time";
            this.PlotW3Time.Size = new System.Drawing.Size(492, 336);
            this.PlotW3Time.TabIndex = 3;
            // 
            // PlotW2Time
            // 
            this.PlotW2Time.Dock = System.Windows.Forms.DockStyle.Fill;
            this.PlotW2Time.Location = new System.Drawing.Point(501, 3);
            this.PlotW2Time.Name = "PlotW2Time";
            this.PlotW2Time.Size = new System.Drawing.Size(493, 335);
            this.PlotW2Time.TabIndex = 2;
            // 
            // PlotW1Time
            // 
            this.PlotW1Time.Dock = System.Windows.Forms.DockStyle.Fill;
            this.PlotW1Time.Location = new System.Drawing.Point(3, 3);
            this.PlotW1Time.Name = "PlotW1Time";
            this.PlotW1Time.Size = new System.Drawing.Size(492, 335);
            this.PlotW1Time.TabIndex = 1;
            // 
            // checkVelocityMotorsAutoScale
            // 
            this.checkVelocityMotorsAutoScale.AutoSize = true;
            this.checkVelocityMotorsAutoScale.Font = new System.Drawing.Font("Microsoft Sans Serif", 7.8F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.checkVelocityMotorsAutoScale.Location = new System.Drawing.Point(3, 692);
            this.checkVelocityMotorsAutoScale.Name = "checkVelocityMotorsAutoScale";
            this.checkVelocityMotorsAutoScale.Size = new System.Drawing.Size(94, 20);
            this.checkVelocityMotorsAutoScale.TabIndex = 83;
            this.checkVelocityMotorsAutoScale.Text = "Auto Scale";
            this.checkVelocityMotorsAutoScale.UseVisualStyleBackColor = true;
            // 
            // ControlGroupBox
            // 
            this.ControlGroupBox.Controls.Add(this.MonitorGroupBox);
            this.ControlGroupBox.Controls.Add(this.AutoGroupBox);
            this.ControlGroupBox.Controls.Add(this.ManualGroupBox);
            this.ControlGroupBox.Enabled = false;
            this.ControlGroupBox.Font = new System.Drawing.Font("Microsoft Sans Serif", 14F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.ControlGroupBox.Location = new System.Drawing.Point(1035, 12);
            this.ControlGroupBox.Name = "ControlGroupBox";
            this.ControlGroupBox.Size = new System.Drawing.Size(874, 851);
            this.ControlGroupBox.TabIndex = 82;
            this.ControlGroupBox.TabStop = false;
            this.ControlGroupBox.Text = "Control";
            // 
            // MonitorGroupBox
            // 
            this.MonitorGroupBox.Controls.Add(this.groupBox2);
            this.MonitorGroupBox.Controls.Add(this.PauseGraphButton);
            this.MonitorGroupBox.Controls.Add(this.ExportCSVButton);
            this.MonitorGroupBox.Controls.Add(this.StartGraphButton);
            this.MonitorGroupBox.Controls.Add(this.StopGraphButton);
            this.MonitorGroupBox.Font = new System.Drawing.Font("Microsoft Sans Serif", 12F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.MonitorGroupBox.Location = new System.Drawing.Point(18, 29);
            this.MonitorGroupBox.Name = "MonitorGroupBox";
            this.MonitorGroupBox.Size = new System.Drawing.Size(850, 277);
            this.MonitorGroupBox.TabIndex = 59;
            this.MonitorGroupBox.TabStop = false;
            this.MonitorGroupBox.Text = "Monitor";
            // 
            // groupBox2
            // 
            this.groupBox2.Controls.Add(this.label17);
            this.groupBox2.Controls.Add(this.CurrentW4TextBox);
            this.groupBox2.Controls.Add(this.label16);
            this.groupBox2.Controls.Add(this.CurrentW3TextBox);
            this.groupBox2.Controls.Add(this.label15);
            this.groupBox2.Controls.Add(this.CurrentW2TextBox);
            this.groupBox2.Controls.Add(this.label9);
            this.groupBox2.Controls.Add(this.CurrentVxTextBox);
            this.groupBox2.Controls.Add(this.CurrentW1TextBox);
            this.groupBox2.Controls.Add(this.CurrentXTextBox);
            this.groupBox2.Controls.Add(this.label6);
            this.groupBox2.Controls.Add(this.label2);
            this.groupBox2.Controls.Add(this.CurrentYTextBox);
            this.groupBox2.Controls.Add(this.label1);
            this.groupBox2.Controls.Add(this.CurrentThetaTextBox);
            this.groupBox2.Controls.Add(this.label3);
            this.groupBox2.Controls.Add(this.CurrentWTextBox);
            this.groupBox2.Controls.Add(this.label8);
            this.groupBox2.Controls.Add(this.label7);
            this.groupBox2.Controls.Add(this.CurrentVyTextBox);
            this.groupBox2.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.groupBox2.Location = new System.Drawing.Point(16, 29);
            this.groupBox2.Name = "groupBox2";
            this.groupBox2.Size = new System.Drawing.Size(828, 178);
            this.groupBox2.TabIndex = 59;
            this.groupBox2.TabStop = false;
            this.groupBox2.Text = "Robot Current Values:";
            // 
            // label17
            // 
            this.label17.AutoSize = true;
            this.label17.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label17.Location = new System.Drawing.Point(551, 138);
            this.label17.Name = "label17";
            this.label17.Size = new System.Drawing.Size(116, 20);
            this.label17.TabIndex = 81;
            this.label17.Text = "W_FR (rad/s):";
            // 
            // CurrentW4TextBox
            // 
            this.CurrentW4TextBox.Location = new System.Drawing.Point(685, 137);
            this.CurrentW4TextBox.Name = "CurrentW4TextBox";
            this.CurrentW4TextBox.Size = new System.Drawing.Size(124, 26);
            this.CurrentW4TextBox.TabIndex = 80;
            // 
            // label16
            // 
            this.label16.AutoSize = true;
            this.label16.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label16.Location = new System.Drawing.Point(551, 106);
            this.label16.Name = "label16";
            this.label16.Size = new System.Drawing.Size(118, 20);
            this.label16.TabIndex = 79;
            this.label16.Text = "W_RR (rad/s):";
            // 
            // CurrentW3TextBox
            // 
            this.CurrentW3TextBox.Location = new System.Drawing.Point(685, 105);
            this.CurrentW3TextBox.Name = "CurrentW3TextBox";
            this.CurrentW3TextBox.Size = new System.Drawing.Size(124, 26);
            this.CurrentW3TextBox.TabIndex = 78;
            // 
            // label15
            // 
            this.label15.AutoSize = true;
            this.label15.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label15.Location = new System.Drawing.Point(551, 70);
            this.label15.Name = "label15";
            this.label15.Size = new System.Drawing.Size(114, 20);
            this.label15.TabIndex = 77;
            this.label15.Text = "W_FL (rad/s):";
            // 
            // CurrentW2TextBox
            // 
            this.CurrentW2TextBox.Location = new System.Drawing.Point(685, 69);
            this.CurrentW2TextBox.Name = "CurrentW2TextBox";
            this.CurrentW2TextBox.Size = new System.Drawing.Size(124, 26);
            this.CurrentW2TextBox.TabIndex = 76;
            // 
            // label9
            // 
            this.label9.AutoSize = true;
            this.label9.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label9.Location = new System.Drawing.Point(551, 32);
            this.label9.Name = "label9";
            this.label9.Size = new System.Drawing.Size(116, 20);
            this.label9.TabIndex = 75;
            this.label9.Text = "W_RL (rad/s):";
            // 
            // CurrentVxTextBox
            // 
            this.CurrentVxTextBox.Location = new System.Drawing.Point(403, 28);
            this.CurrentVxTextBox.Name = "CurrentVxTextBox";
            this.CurrentVxTextBox.Size = new System.Drawing.Size(124, 26);
            this.CurrentVxTextBox.TabIndex = 68;
            // 
            // CurrentW1TextBox
            // 
            this.CurrentW1TextBox.Location = new System.Drawing.Point(685, 31);
            this.CurrentW1TextBox.Name = "CurrentW1TextBox";
            this.CurrentW1TextBox.Size = new System.Drawing.Size(124, 26);
            this.CurrentW1TextBox.TabIndex = 74;
            // 
            // CurrentXTextBox
            // 
            this.CurrentXTextBox.Location = new System.Drawing.Point(151, 25);
            this.CurrentXTextBox.Name = "CurrentXTextBox";
            this.CurrentXTextBox.Size = new System.Drawing.Size(124, 26);
            this.CurrentXTextBox.TabIndex = 62;
            // 
            // label6
            // 
            this.label6.AutoSize = true;
            this.label6.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label6.Location = new System.Drawing.Point(304, 106);
            this.label6.Name = "label6";
            this.label6.Size = new System.Drawing.Size(85, 20);
            this.label6.TabIndex = 73;
            this.label6.Text = "W (rad/s):";
            // 
            // label2
            // 
            this.label2.AutoSize = true;
            this.label2.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label2.Location = new System.Drawing.Point(75, 31);
            this.label2.Name = "label2";
            this.label2.Size = new System.Drawing.Size(56, 20);
            this.label2.TabIndex = 63;
            this.label2.Text = "X (m):";
            // 
            // CurrentYTextBox
            // 
            this.CurrentYTextBox.Location = new System.Drawing.Point(150, 61);
            this.CurrentYTextBox.Name = "CurrentYTextBox";
            this.CurrentYTextBox.Size = new System.Drawing.Size(124, 26);
            this.CurrentYTextBox.TabIndex = 64;
            // 
            // label1
            // 
            this.label1.AutoSize = true;
            this.label1.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label1.Location = new System.Drawing.Point(75, 67);
            this.label1.Name = "label1";
            this.label1.Size = new System.Drawing.Size(55, 20);
            this.label1.TabIndex = 65;
            this.label1.Text = "Y (m):";
            // 
            // CurrentThetaTextBox
            // 
            this.CurrentThetaTextBox.Location = new System.Drawing.Point(151, 97);
            this.CurrentThetaTextBox.Name = "CurrentThetaTextBox";
            this.CurrentThetaTextBox.Size = new System.Drawing.Size(124, 26);
            this.CurrentThetaTextBox.TabIndex = 66;
            // 
            // label3
            // 
            this.label3.AutoSize = true;
            this.label3.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label3.Location = new System.Drawing.Point(8, 103);
            this.label3.Name = "label3";
            this.label3.Size = new System.Drawing.Size(124, 20);
            this.label3.TabIndex = 67;
            this.label3.Text = "Theta (degree):";
            // 
            // CurrentWTextBox
            // 
            this.CurrentWTextBox.Location = new System.Drawing.Point(403, 100);
            this.CurrentWTextBox.Name = "CurrentWTextBox";
            this.CurrentWTextBox.Size = new System.Drawing.Size(124, 26);
            this.CurrentWTextBox.TabIndex = 72;
            // 
            // label8
            // 
            this.label8.AutoSize = true;
            this.label8.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label8.Location = new System.Drawing.Point(311, 35);
            this.label8.Name = "label8";
            this.label8.Size = new System.Drawing.Size(78, 20);
            this.label8.TabIndex = 69;
            this.label8.Text = "Vx (m/s):";
            // 
            // label7
            // 
            this.label7.AutoSize = true;
            this.label7.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label7.Location = new System.Drawing.Point(311, 70);
            this.label7.Name = "label7";
            this.label7.Size = new System.Drawing.Size(78, 20);
            this.label7.TabIndex = 71;
            this.label7.Text = "Vy (m/s):";
            this.label7.Click += new System.EventHandler(this.label7_Click);
            // 
            // CurrentVyTextBox
            // 
            this.CurrentVyTextBox.Location = new System.Drawing.Point(403, 64);
            this.CurrentVyTextBox.Name = "CurrentVyTextBox";
            this.CurrentVyTextBox.Size = new System.Drawing.Size(124, 26);
            this.CurrentVyTextBox.TabIndex = 70;
            // 
            // PauseGraphButton
            // 
            this.PauseGraphButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.PauseGraphButton.Location = new System.Drawing.Point(248, 223);
            this.PauseGraphButton.Margin = new System.Windows.Forms.Padding(4);
            this.PauseGraphButton.Name = "PauseGraphButton";
            this.PauseGraphButton.Size = new System.Drawing.Size(113, 43);
            this.PauseGraphButton.TabIndex = 54;
            this.PauseGraphButton.Text = "Pause Graph";
            this.PauseGraphButton.UseVisualStyleBackColor = true;
            this.PauseGraphButton.Click += new System.EventHandler(this.PauseGraphButton_Click);
            // 
            // ExportCSVButton
            // 
            this.ExportCSVButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.ExportCSVButton.Location = new System.Drawing.Point(689, 223);
            this.ExportCSVButton.Margin = new System.Windows.Forms.Padding(4);
            this.ExportCSVButton.Name = "ExportCSVButton";
            this.ExportCSVButton.Size = new System.Drawing.Size(113, 43);
            this.ExportCSVButton.TabIndex = 55;
            this.ExportCSVButton.Text = "Export to CSV";
            this.ExportCSVButton.UseVisualStyleBackColor = true;
            this.ExportCSVButton.Click += new System.EventHandler(this.ExportCSVButton_Click);
            // 
            // StartGraphButton
            // 
            this.StartGraphButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.StartGraphButton.Location = new System.Drawing.Point(50, 223);
            this.StartGraphButton.Margin = new System.Windows.Forms.Padding(4);
            this.StartGraphButton.Name = "StartGraphButton";
            this.StartGraphButton.Size = new System.Drawing.Size(107, 43);
            this.StartGraphButton.TabIndex = 52;
            this.StartGraphButton.Text = "Start Graph";
            this.StartGraphButton.UseVisualStyleBackColor = true;
            this.StartGraphButton.Click += new System.EventHandler(this.StartGraphButton_Click);
            // 
            // StopGraphButton
            // 
            this.StopGraphButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.StopGraphButton.Location = new System.Drawing.Point(464, 223);
            this.StopGraphButton.Margin = new System.Windows.Forms.Padding(4);
            this.StopGraphButton.Name = "StopGraphButton";
            this.StopGraphButton.Size = new System.Drawing.Size(107, 43);
            this.StopGraphButton.TabIndex = 53;
            this.StopGraphButton.Text = "Stop Graph";
            this.StopGraphButton.UseVisualStyleBackColor = true;
            this.StopGraphButton.Click += new System.EventHandler(this.StopGraphButton_Click);
            // 
            // AutoGroupBox
            // 
            this.AutoGroupBox.Controls.Add(this.cboCameraMode);
            this.AutoGroupBox.Controls.Add(this.txtCameraIp);
            this.AutoGroupBox.Controls.Add(this.StopStreamButton);
            this.AutoGroupBox.Controls.Add(this.StartStreamButton);
            this.AutoGroupBox.Controls.Add(this.SwitchCameraButton);
            this.AutoGroupBox.Controls.Add(this.StopRecordVideoButton);
            this.AutoGroupBox.Controls.Add(this.StartRecordVideoButton);
            this.AutoGroupBox.Controls.Add(this.TakePhotoButton);
            this.AutoGroupBox.Controls.Add(this.ReturnHomeButton);
            this.AutoGroupBox.Controls.Add(this.groupBox3);
            this.AutoGroupBox.Controls.Add(this.CircleLineButton);
            this.AutoGroupBox.Controls.Add(this.EmergencyStopButton);
            this.AutoGroupBox.Font = new System.Drawing.Font("Microsoft Sans Serif", 12F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.AutoGroupBox.Location = new System.Drawing.Point(18, 551);
            this.AutoGroupBox.Name = "AutoGroupBox";
            this.AutoGroupBox.Size = new System.Drawing.Size(850, 293);
            this.AutoGroupBox.TabIndex = 57;
            this.AutoGroupBox.TabStop = false;
            this.AutoGroupBox.Text = "Auto";
            // 
            // ReturnHomeButton
            // 
            this.ReturnHomeButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.ReturnHomeButton.Location = new System.Drawing.Point(16, 88);
            this.ReturnHomeButton.Margin = new System.Windows.Forms.Padding(4);
            this.ReturnHomeButton.Name = "ReturnHomeButton";
            this.ReturnHomeButton.Size = new System.Drawing.Size(141, 43);
            this.ReturnHomeButton.TabIndex = 65;
            this.ReturnHomeButton.Text = "Return Home";
            this.ReturnHomeButton.UseVisualStyleBackColor = true;
            this.ReturnHomeButton.Click += new System.EventHandler(this.ReturnHomeButton_Click);
            // 
            // groupBox3
            // 
            this.groupBox3.Controls.Add(this.label20);
            this.groupBox3.Controls.Add(this.Vy_dTextBox);
            this.groupBox3.Controls.Add(this.Theta_dTextBox);
            this.groupBox3.Controls.Add(this.label14);
            this.groupBox3.Controls.Add(this.label12);
            this.groupBox3.Controls.Add(this.X_dTextBox);
            this.groupBox3.Controls.Add(this.Y_dTextBox);
            this.groupBox3.Controls.Add(this.label13);
            this.groupBox3.Controls.Add(this.ResetRobotButton);
            this.groupBox3.Controls.Add(this.label10);
            this.groupBox3.Controls.Add(this.Vx_dTextBox);
            this.groupBox3.Controls.Add(this.SetRobotButton);
            this.groupBox3.Controls.Add(this.W_dTextBox);
            this.groupBox3.Controls.Add(this.label11);
            this.groupBox3.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.groupBox3.Location = new System.Drawing.Point(331, 29);
            this.groupBox3.Name = "groupBox3";
            this.groupBox3.Size = new System.Drawing.Size(503, 234);
            this.groupBox3.TabIndex = 64;
            this.groupBox3.TabStop = false;
            this.groupBox3.Text = "Set Robot Value:";
            // 
            // label20
            // 
            this.label20.AutoSize = true;
            this.label20.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label20.Location = new System.Drawing.Point(32, 65);
            this.label20.Name = "label20";
            this.label20.Size = new System.Drawing.Size(181, 20);
            this.label20.TabIndex = 71;
            this.label20.Text = "Strafing Velocity (m/s):";
            // 
            // Vy_dTextBox
            // 
            this.Vy_dTextBox.Location = new System.Drawing.Point(241, 59);
            this.Vy_dTextBox.Name = "Vy_dTextBox";
            this.Vy_dTextBox.Size = new System.Drawing.Size(124, 26);
            this.Vy_dTextBox.TabIndex = 70;
            // 
            // Theta_dTextBox
            // 
            this.Theta_dTextBox.Location = new System.Drawing.Point(241, 195);
            this.Theta_dTextBox.Name = "Theta_dTextBox";
            this.Theta_dTextBox.Size = new System.Drawing.Size(124, 26);
            this.Theta_dTextBox.TabIndex = 68;
            // 
            // label14
            // 
            this.label14.AutoSize = true;
            this.label14.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label14.Location = new System.Drawing.Point(29, 198);
            this.label14.Name = "label14";
            this.label14.Size = new System.Drawing.Size(184, 20);
            this.label14.TabIndex = 69;
            this.label14.Text = "Theta desired (degree):";
            // 
            // label12
            // 
            this.label12.AutoSize = true;
            this.label12.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label12.Location = new System.Drawing.Point(92, 133);
            this.label12.Name = "label12";
            this.label12.Size = new System.Drawing.Size(121, 20);
            this.label12.TabIndex = 65;
            this.label12.Text = "Position X (m):";
            // 
            // X_dTextBox
            // 
            this.X_dTextBox.Location = new System.Drawing.Point(241, 127);
            this.X_dTextBox.Name = "X_dTextBox";
            this.X_dTextBox.Size = new System.Drawing.Size(124, 26);
            this.X_dTextBox.TabIndex = 64;
            // 
            // Y_dTextBox
            // 
            this.Y_dTextBox.Location = new System.Drawing.Point(241, 163);
            this.Y_dTextBox.Name = "Y_dTextBox";
            this.Y_dTextBox.Size = new System.Drawing.Size(124, 26);
            this.Y_dTextBox.TabIndex = 66;
            // 
            // label13
            // 
            this.label13.AutoSize = true;
            this.label13.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label13.Location = new System.Drawing.Point(93, 169);
            this.label13.Name = "label13";
            this.label13.Size = new System.Drawing.Size(120, 20);
            this.label13.TabIndex = 67;
            this.label13.Text = "Position Y (m):";
            // 
            // ResetRobotButton
            // 
            this.ResetRobotButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.ResetRobotButton.Location = new System.Drawing.Point(383, 78);
            this.ResetRobotButton.Margin = new System.Windows.Forms.Padding(4);
            this.ResetRobotButton.Name = "ResetRobotButton";
            this.ResetRobotButton.Size = new System.Drawing.Size(99, 43);
            this.ResetRobotButton.TabIndex = 63;
            this.ResetRobotButton.Text = "Reset";
            this.ResetRobotButton.UseVisualStyleBackColor = true;
            // 
            // label10
            // 
            this.label10.AutoSize = true;
            this.label10.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label10.Location = new System.Drawing.Point(29, 30);
            this.label10.Name = "label10";
            this.label10.Size = new System.Drawing.Size(184, 20);
            this.label10.TabIndex = 59;
            this.label10.Text = "Forward Velocity (m/s):";
            // 
            // Vx_dTextBox
            // 
            this.Vx_dTextBox.Location = new System.Drawing.Point(241, 27);
            this.Vx_dTextBox.Name = "Vx_dTextBox";
            this.Vx_dTextBox.Size = new System.Drawing.Size(124, 26);
            this.Vx_dTextBox.TabIndex = 58;
            // 
            // SetRobotButton
            // 
            this.SetRobotButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.SetRobotButton.Location = new System.Drawing.Point(383, 26);
            this.SetRobotButton.Margin = new System.Windows.Forms.Padding(4);
            this.SetRobotButton.Name = "SetRobotButton";
            this.SetRobotButton.Size = new System.Drawing.Size(99, 43);
            this.SetRobotButton.TabIndex = 62;
            this.SetRobotButton.Text = "Set";
            this.SetRobotButton.UseVisualStyleBackColor = true;
            this.SetRobotButton.Click += new System.EventHandler(this.SetRobotButton_Click);
            // 
            // W_dTextBox
            // 
            this.W_dTextBox.Location = new System.Drawing.Point(241, 95);
            this.W_dTextBox.Name = "W_dTextBox";
            this.W_dTextBox.Size = new System.Drawing.Size(124, 26);
            this.W_dTextBox.TabIndex = 60;
            // 
            // label11
            // 
            this.label11.AutoSize = true;
            this.label11.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label11.Location = new System.Drawing.Point(23, 101);
            this.label11.Name = "label11";
            this.label11.Size = new System.Drawing.Size(190, 20);
            this.label11.TabIndex = 61;
            this.label11.Text = "Angular Velocity (rad/s):";
            // 
            // CircleLineButton
            // 
            this.CircleLineButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.CircleLineButton.Location = new System.Drawing.Point(16, 39);
            this.CircleLineButton.Margin = new System.Windows.Forms.Padding(4);
            this.CircleLineButton.Name = "CircleLineButton";
            this.CircleLineButton.Size = new System.Drawing.Size(141, 43);
            this.CircleLineButton.TabIndex = 57;
            this.CircleLineButton.Text = "Circle Line";
            this.CircleLineButton.UseVisualStyleBackColor = true;
            this.CircleLineButton.Click += new System.EventHandler(this.CircleLineButton_Click);
            // 
            // EmergencyStopButton
            // 
            this.EmergencyStopButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.EmergencyStopButton.Location = new System.Drawing.Point(16, 139);
            this.EmergencyStopButton.Margin = new System.Windows.Forms.Padding(4);
            this.EmergencyStopButton.Name = "EmergencyStopButton";
            this.EmergencyStopButton.Size = new System.Drawing.Size(141, 43);
            this.EmergencyStopButton.TabIndex = 58;
            this.EmergencyStopButton.Text = "Emergency Stop";
            this.EmergencyStopButton.UseVisualStyleBackColor = true;
            this.EmergencyStopButton.Click += new System.EventHandler(this.EmergencyStopButton_Click);
            // 
            // ManualGroupBox
            // 
            this.ManualGroupBox.Controls.Add(this.TurnRightButton);
            this.ManualGroupBox.Controls.Add(this.TurnLeftButton);
            this.ManualGroupBox.Controls.Add(this.groupBox1);
            this.ManualGroupBox.Controls.Add(this.DownButton);
            this.ManualGroupBox.Controls.Add(this.RightButton);
            this.ManualGroupBox.Controls.Add(this.LeftButton);
            this.ManualGroupBox.Controls.Add(this.UpButton);
            this.ManualGroupBox.Font = new System.Drawing.Font("Microsoft Sans Serif", 12F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.ManualGroupBox.Location = new System.Drawing.Point(18, 327);
            this.ManualGroupBox.Name = "ManualGroupBox";
            this.ManualGroupBox.Size = new System.Drawing.Size(850, 218);
            this.ManualGroupBox.TabIndex = 56;
            this.ManualGroupBox.TabStop = false;
            this.ManualGroupBox.Text = "Manual";
            // 
            // TurnRightButton
            // 
            this.TurnRightButton.Font = new System.Drawing.Font("Segoe UI Symbol", 14F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.TurnRightButton.Location = new System.Drawing.Point(116, 95);
            this.TurnRightButton.Name = "TurnRightButton";
            this.TurnRightButton.Size = new System.Drawing.Size(48, 41);
            this.TurnRightButton.TabIndex = 61;
            this.TurnRightButton.Text = "↻";
            this.TurnRightButton.UseVisualStyleBackColor = true;
            this.TurnRightButton.MouseDown += new System.Windows.Forms.MouseEventHandler(this.TurnRightButton_MouseDown);
            this.TurnRightButton.MouseUp += new System.Windows.Forms.MouseEventHandler(this.TurnRightButton_MouseUp);
            // 
            // TurnLeftButton
            // 
            this.TurnLeftButton.Font = new System.Drawing.Font("Segoe UI Symbol", 14F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.TurnLeftButton.Location = new System.Drawing.Point(62, 95);
            this.TurnLeftButton.Name = "TurnLeftButton";
            this.TurnLeftButton.Size = new System.Drawing.Size(48, 41);
            this.TurnLeftButton.TabIndex = 60;
            this.TurnLeftButton.Text = "↺";
            this.TurnLeftButton.UseVisualStyleBackColor = true;
            this.TurnLeftButton.MouseDown += new System.Windows.Forms.MouseEventHandler(this.TurnLeftButton_MouseDown);
            this.TurnLeftButton.MouseUp += new System.Windows.Forms.MouseEventHandler(this.TurnLeftButton_MouseUp);
            // 
            // groupBox1
            // 
            this.groupBox1.Controls.Add(this.SetW4TextBox);
            this.groupBox1.Controls.Add(this.label19);
            this.groupBox1.Controls.Add(this.SetW3TextBox);
            this.groupBox1.Controls.Add(this.label18);
            this.groupBox1.Controls.Add(this.ResetButton);
            this.groupBox1.Controls.Add(this.LabelSPVel);
            this.groupBox1.Controls.Add(this.SetW1TextBox);
            this.groupBox1.Controls.Add(this.SetMotorButton);
            this.groupBox1.Controls.Add(this.SetW2TextBox);
            this.groupBox1.Controls.Add(this.LabelSPPos);
            this.groupBox1.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.groupBox1.Location = new System.Drawing.Point(228, 29);
            this.groupBox1.Name = "groupBox1";
            this.groupBox1.Size = new System.Drawing.Size(606, 177);
            this.groupBox1.TabIndex = 59;
            this.groupBox1.TabStop = false;
            this.groupBox1.Text = "Set Wheel Velocity:";
            // 
            // SetW4TextBox
            // 
            this.SetW4TextBox.Location = new System.Drawing.Point(329, 130);
            this.SetW4TextBox.Name = "SetW4TextBox";
            this.SetW4TextBox.Size = new System.Drawing.Size(124, 26);
            this.SetW4TextBox.TabIndex = 66;
            // 
            // label19
            // 
            this.label19.AutoSize = true;
            this.label19.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label19.Location = new System.Drawing.Point(15, 135);
            this.label19.Name = "label19";
            this.label19.Size = new System.Drawing.Size(274, 20);
            this.label19.TabIndex = 67;
            this.label19.Text = "Velocity Front Right Wheel (Rad/s):";
            // 
            // SetW3TextBox
            // 
            this.SetW3TextBox.Location = new System.Drawing.Point(329, 98);
            this.SetW3TextBox.Name = "SetW3TextBox";
            this.SetW3TextBox.Size = new System.Drawing.Size(124, 26);
            this.SetW3TextBox.TabIndex = 64;
            // 
            // label18
            // 
            this.label18.AutoSize = true;
            this.label18.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.label18.Location = new System.Drawing.Point(18, 102);
            this.label18.Name = "label18";
            this.label18.Size = new System.Drawing.Size(271, 20);
            this.label18.TabIndex = 65;
            this.label18.Text = "Velocity Rear Right Wheel (Rad/s):";
            // 
            // ResetButton
            // 
            this.ResetButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.ResetButton.Location = new System.Drawing.Point(489, 102);
            this.ResetButton.Margin = new System.Windows.Forms.Padding(4);
            this.ResetButton.Name = "ResetButton";
            this.ResetButton.Size = new System.Drawing.Size(99, 43);
            this.ResetButton.TabIndex = 63;
            this.ResetButton.Text = "Reset";
            this.ResetButton.UseVisualStyleBackColor = true;
            // 
            // LabelSPVel
            // 
            this.LabelSPVel.AutoSize = true;
            this.LabelSPVel.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.LabelSPVel.Location = new System.Drawing.Point(28, 39);
            this.LabelSPVel.Name = "LabelSPVel";
            this.LabelSPVel.Size = new System.Drawing.Size(261, 20);
            this.LabelSPVel.TabIndex = 59;
            this.LabelSPVel.Text = "Velocity Rear Left Wheel (Rad/s):";
            // 
            // SetW1TextBox
            // 
            this.SetW1TextBox.Location = new System.Drawing.Point(329, 34);
            this.SetW1TextBox.Name = "SetW1TextBox";
            this.SetW1TextBox.Size = new System.Drawing.Size(124, 26);
            this.SetW1TextBox.TabIndex = 58;
            // 
            // SetMotorButton
            // 
            this.SetMotorButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.SetMotorButton.Location = new System.Drawing.Point(489, 26);
            this.SetMotorButton.Margin = new System.Windows.Forms.Padding(4);
            this.SetMotorButton.Name = "SetMotorButton";
            this.SetMotorButton.Size = new System.Drawing.Size(99, 43);
            this.SetMotorButton.TabIndex = 62;
            this.SetMotorButton.Text = "Set";
            this.SetMotorButton.UseVisualStyleBackColor = true;
            this.SetMotorButton.Click += new System.EventHandler(this.SetMotorButton_Click);
            // 
            // SetW2TextBox
            // 
            this.SetW2TextBox.Location = new System.Drawing.Point(329, 66);
            this.SetW2TextBox.Name = "SetW2TextBox";
            this.SetW2TextBox.Size = new System.Drawing.Size(124, 26);
            this.SetW2TextBox.TabIndex = 60;
            // 
            // LabelSPPos
            // 
            this.LabelSPPos.AutoSize = true;
            this.LabelSPPos.Font = new System.Drawing.Font("Microsoft Sans Serif", 10F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.LabelSPPos.Location = new System.Drawing.Point(25, 71);
            this.LabelSPPos.Name = "LabelSPPos";
            this.LabelSPPos.Size = new System.Drawing.Size(264, 20);
            this.LabelSPPos.TabIndex = 61;
            this.LabelSPPos.Text = "Velocity Front Left Wheel (Rad/s):";
            // 
            // DownButton
            // 
            this.DownButton.Location = new System.Drawing.Point(85, 150);
            this.DownButton.Name = "DownButton";
            this.DownButton.Size = new System.Drawing.Size(48, 41);
            this.DownButton.TabIndex = 3;
            this.DownButton.Text = "↓";
            this.DownButton.UseVisualStyleBackColor = true;
            this.DownButton.MouseDown += new System.Windows.Forms.MouseEventHandler(this.DownButton_MouseDown);
            this.DownButton.MouseUp += new System.Windows.Forms.MouseEventHandler(this.DownButton_MouseUp);
            // 
            // RightButton
            // 
            this.RightButton.Location = new System.Drawing.Point(176, 95);
            this.RightButton.Name = "RightButton";
            this.RightButton.Size = new System.Drawing.Size(46, 41);
            this.RightButton.TabIndex = 2;
            this.RightButton.Text = "→";
            this.RightButton.UseVisualStyleBackColor = true;
            this.RightButton.MouseDown += new System.Windows.Forms.MouseEventHandler(this.RightButton_MouseDown);
            this.RightButton.MouseUp += new System.Windows.Forms.MouseEventHandler(this.RightButton_MouseUp);
            // 
            // LeftButton
            // 
            this.LeftButton.Location = new System.Drawing.Point(6, 94);
            this.LeftButton.Name = "LeftButton";
            this.LeftButton.Size = new System.Drawing.Size(38, 41);
            this.LeftButton.TabIndex = 1;
            this.LeftButton.Text = "←";
            this.LeftButton.UseVisualStyleBackColor = true;
            this.LeftButton.MouseDown += new System.Windows.Forms.MouseEventHandler(this.LeftButton_MouseDown);
            this.LeftButton.MouseUp += new System.Windows.Forms.MouseEventHandler(this.LeftButton_MouseUp);
            // 
            // UpButton
            // 
            this.UpButton.Location = new System.Drawing.Point(85, 29);
            this.UpButton.Name = "UpButton";
            this.UpButton.Size = new System.Drawing.Size(48, 41);
            this.UpButton.TabIndex = 0;
            this.UpButton.Text = "↑";
            this.UpButton.UseVisualStyleBackColor = true;
            this.UpButton.MouseDown += new System.Windows.Forms.MouseEventHandler(this.UpButton_MouseDown);
            this.UpButton.MouseUp += new System.Windows.Forms.MouseEventHandler(this.UpButton_MouseUp);
            // 
            // TakePhotoButton
            // 
            this.TakePhotoButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.TakePhotoButton.Location = new System.Drawing.Point(176, 20);
            this.TakePhotoButton.Margin = new System.Windows.Forms.Padding(4);
            this.TakePhotoButton.Name = "TakePhotoButton";
            this.TakePhotoButton.Size = new System.Drawing.Size(141, 43);
            this.TakePhotoButton.TabIndex = 66;
            this.TakePhotoButton.Text = "Take Photo";
            this.TakePhotoButton.UseVisualStyleBackColor = true;
            this.TakePhotoButton.Click += new System.EventHandler(this.TakePhotoButton_Click);
            // 
            // StartRecordVideoButton
            // 
            this.StartRecordVideoButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.StartRecordVideoButton.Location = new System.Drawing.Point(176, 71);
            this.StartRecordVideoButton.Margin = new System.Windows.Forms.Padding(4);
            this.StartRecordVideoButton.Name = "StartRecordVideoButton";
            this.StartRecordVideoButton.Size = new System.Drawing.Size(141, 43);
            this.StartRecordVideoButton.TabIndex = 67;
            this.StartRecordVideoButton.Text = "Start Record Video";
            this.StartRecordVideoButton.UseVisualStyleBackColor = true;
            this.StartRecordVideoButton.Click += new System.EventHandler(this.StartRecordVideoButton_Click);
            // 
            // StopRecordVideoButton
            // 
            this.StopRecordVideoButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.StopRecordVideoButton.Location = new System.Drawing.Point(176, 118);
            this.StopRecordVideoButton.Margin = new System.Windows.Forms.Padding(4);
            this.StopRecordVideoButton.Name = "StopRecordVideoButton";
            this.StopRecordVideoButton.Size = new System.Drawing.Size(141, 43);
            this.StopRecordVideoButton.TabIndex = 68;
            this.StopRecordVideoButton.Text = "Stop Record Video";
            this.StopRecordVideoButton.UseVisualStyleBackColor = true;
            this.StopRecordVideoButton.Click += new System.EventHandler(this.StopRecordVideoButton_Click);
            // 
            // SwitchCameraButton
            // 
            this.SwitchCameraButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.SwitchCameraButton.Location = new System.Drawing.Point(176, 169);
            this.SwitchCameraButton.Margin = new System.Windows.Forms.Padding(4);
            this.SwitchCameraButton.Name = "SwitchCameraButton";
            this.SwitchCameraButton.Size = new System.Drawing.Size(141, 43);
            this.SwitchCameraButton.TabIndex = 69;
            this.SwitchCameraButton.Text = "Switch Camera";
            this.SwitchCameraButton.UseVisualStyleBackColor = true;
            this.SwitchCameraButton.Click += new System.EventHandler(this.SwitchCameraButton_Click);
            // 
            // StartStreamButton
            // 
            this.StartStreamButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.StartStreamButton.Location = new System.Drawing.Point(16, 243);
            this.StartStreamButton.Margin = new System.Windows.Forms.Padding(4);
            this.StartStreamButton.Name = "StartStreamButton";
            this.StartStreamButton.Size = new System.Drawing.Size(141, 43);
            this.StartStreamButton.TabIndex = 70;
            this.StartStreamButton.Text = "Start Stream";
            this.StartStreamButton.UseVisualStyleBackColor = true;
            this.StartStreamButton.Click += new System.EventHandler(this.StartStreamButton_Click);
            // 
            // StopStreamButton
            // 
            this.StopStreamButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.StopStreamButton.Location = new System.Drawing.Point(176, 243);
            this.StopStreamButton.Margin = new System.Windows.Forms.Padding(4);
            this.StopStreamButton.Name = "StopStreamButton";
            this.StopStreamButton.Size = new System.Drawing.Size(141, 43);
            this.StopStreamButton.TabIndex = 71;
            this.StopStreamButton.Text = "Stop Stream";
            this.StopStreamButton.UseVisualStyleBackColor = true;
            this.StopStreamButton.Click += new System.EventHandler(this.StopStreamButton_Click);
            // 
            // cboCameraMode
            // 
            this.cboCameraMode.DropDownStyle = System.Windows.Forms.ComboBoxStyle.DropDownList;
            this.cboCameraMode.Font = new System.Drawing.Font("Microsoft Sans Serif", 8.25F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.cboCameraMode.FormattingEnabled = true;
            this.cboCameraMode.Items.AddRange(new object[] {
            "Robot Camera"});
            this.cboCameraMode.Location = new System.Drawing.Point(16, 188);
            this.cboCameraMode.Name = "cboCameraMode";
            this.cboCameraMode.Size = new System.Drawing.Size(141, 25);
            this.cboCameraMode.TabIndex = 72;
            // 
            // txtCameraIp
            // 
            this.txtCameraIp.Font = new System.Drawing.Font("Microsoft Sans Serif", 9F, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.txtCameraIp.Location = new System.Drawing.Point(16, 216);
            this.txtCameraIp.Name = "txtCameraIp";
            this.txtCameraIp.Size = new System.Drawing.Size(141, 24);
            this.txtCameraIp.TabIndex = 73;
            this.txtCameraIp.Text = "192.168.2.80";
            // 
            // AppForm
            // 
            this.AutoScaleDimensions = new System.Drawing.SizeF(8F, 16F);
            this.AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
            this.ClientSize = new System.Drawing.Size(1913, 875);
            this.Controls.Add(this.ControlGroupBox);
            this.Controls.Add(this.tabControl1);
            this.Controls.Add(this.LabelAnnouncement);
            this.Controls.Add(this.TCPGroupBox);
            this.Name = "AppForm";
            this.Text = "Monitoring and Controlling 4 Mecanum Wheels Robot App";
            this.TCPGroupBox.ResumeLayout(false);
            this.TCPGroupBox.PerformLayout();
            this.tabControl1.ResumeLayout(false);
            this.MainMonitorTab.ResumeLayout(false);
            this.tableLayoutPanel1.ResumeLayout(false);
            this.tableLayoutPanel1.PerformLayout();
            this.PositionRobotAnalysisTab.ResumeLayout(false);
            this.tableLayoutPanel6.ResumeLayout(false);
            this.tableLayoutPanel6.PerformLayout();
            this.VelocityRobotAnalysisTab.ResumeLayout(false);
            this.tableLayoutPanel4.ResumeLayout(false);
            this.tableLayoutPanel4.PerformLayout();
            this.VelocityMotorsAnalysisTab.ResumeLayout(false);
            this.tableLayoutPanel2.ResumeLayout(false);
            this.tableLayoutPanel2.PerformLayout();
            this.tableLayoutPanel3.ResumeLayout(false);
            this.ControlGroupBox.ResumeLayout(false);
            this.MonitorGroupBox.ResumeLayout(false);
            this.groupBox2.ResumeLayout(false);
            this.groupBox2.PerformLayout();
            this.AutoGroupBox.ResumeLayout(false);
            this.groupBox3.ResumeLayout(false);
            this.groupBox3.PerformLayout();
            this.ManualGroupBox.ResumeLayout(false);
            this.groupBox1.ResumeLayout(false);
            this.groupBox1.PerformLayout();
            this.ResumeLayout(false);
            this.PerformLayout();

        }

        #endregion

        private System.Windows.Forms.GroupBox TCPGroupBox;
        private System.Windows.Forms.Button DisconnectButton;
        private System.Windows.Forms.Button ScanIPButton;
        private System.Windows.Forms.ComboBox IPComboBox;
        private System.Windows.Forms.Label label4;
        private System.Windows.Forms.Label label5;
        private System.Windows.Forms.Button ConnectButton;
        private System.Windows.Forms.TextBox PortTextBox;
        private System.Windows.Forms.Label LabelAnnouncement;
        private ScottPlot.WinForms.FormsPlot TrajectoryPlot;
        private System.Windows.Forms.CheckBox checkTrajectoryAutoScale;
        private System.Windows.Forms.TabControl tabControl1;
        private System.Windows.Forms.TabPage MainMonitorTab;
        private System.Windows.Forms.TabPage PositionRobotAnalysisTab;
        private System.Windows.Forms.GroupBox ControlGroupBox;
        private System.Windows.Forms.Button CircleLineButton;
        private System.Windows.Forms.GroupBox ManualGroupBox;
        private System.Windows.Forms.Button DownButton;
        private System.Windows.Forms.Button RightButton;
        private System.Windows.Forms.Button LeftButton;
        private System.Windows.Forms.Button UpButton;
        private System.Windows.Forms.Button ExportCSVButton;
        private System.Windows.Forms.Button PauseGraphButton;
        private System.Windows.Forms.Button StopGraphButton;
        private System.Windows.Forms.Button StartGraphButton;
        private System.Windows.Forms.TableLayoutPanel tableLayoutPanel1;
        private System.Windows.Forms.TabPage VelocityRobotAnalysisTab;
        private System.Windows.Forms.TableLayoutPanel tableLayoutPanel4;
        private System.Windows.Forms.TabPage VelocityMotorsAnalysisTab;
        private System.Windows.Forms.TableLayoutPanel tableLayoutPanel6;
        private ScottPlot.WinForms.FormsPlot PlotXTime;
        private ScottPlot.WinForms.FormsPlot PlotYTime;
        private ScottPlot.WinForms.FormsPlot PlotThetaTime;
        private System.Windows.Forms.CheckBox checkPositionRobotAutoScale;
        private ScottPlot.WinForms.FormsPlot PlotVxTime;
        private ScottPlot.WinForms.FormsPlot PlotVyTime;
        private System.Windows.Forms.CheckBox checkVelocityRobotAutoScale;
        private System.Windows.Forms.GroupBox AutoGroupBox;
        private System.Windows.Forms.Button ResetButton;
        private System.Windows.Forms.Button SetMotorButton;
        private System.Windows.Forms.Label LabelSPPos;
        private System.Windows.Forms.TextBox SetW2TextBox;
        private System.Windows.Forms.Label LabelSPVel;
        private System.Windows.Forms.TextBox SetW1TextBox;
        private System.Windows.Forms.Button EmergencyStopButton;
        private System.Windows.Forms.GroupBox MonitorGroupBox;
        private System.Windows.Forms.Label label1;
        private System.Windows.Forms.TextBox CurrentYTextBox;
        private System.Windows.Forms.Label label2;
        private System.Windows.Forms.TextBox CurrentXTextBox;
        private System.Windows.Forms.Label label3;
        private System.Windows.Forms.TextBox CurrentThetaTextBox;
        private System.Windows.Forms.GroupBox groupBox1;
        private System.Windows.Forms.Label label6;
        private System.Windows.Forms.TextBox CurrentWTextBox;
        private System.Windows.Forms.Label label7;
        private System.Windows.Forms.TextBox CurrentVyTextBox;
        private System.Windows.Forms.Label label8;
        private System.Windows.Forms.TextBox CurrentVxTextBox;
        private System.Windows.Forms.Label label9;
        private System.Windows.Forms.TextBox CurrentW1TextBox;
        private System.Windows.Forms.GroupBox groupBox2;
        private System.Windows.Forms.GroupBox groupBox3;
        private System.Windows.Forms.Button ResetRobotButton;
        private System.Windows.Forms.Label label10;
        private System.Windows.Forms.TextBox Vx_dTextBox;
        private System.Windows.Forms.Button SetRobotButton;
        private System.Windows.Forms.TextBox W_dTextBox;
        private System.Windows.Forms.Label label11;
        private System.Windows.Forms.TextBox Theta_dTextBox;
        private System.Windows.Forms.Label label14;
        private System.Windows.Forms.Label label12;
        private System.Windows.Forms.TextBox X_dTextBox;
        private System.Windows.Forms.TextBox Y_dTextBox;
        private System.Windows.Forms.Label label13;
        private System.Windows.Forms.Button ReturnHomeButton;
        private ScottPlot.WinForms.FormsPlot PlotWTime;
        private System.Windows.Forms.TableLayoutPanel tableLayoutPanel2;
        private System.Windows.Forms.TableLayoutPanel tableLayoutPanel3;
        private ScottPlot.WinForms.FormsPlot PlotW4Time;
        private ScottPlot.WinForms.FormsPlot PlotW3Time;
        private ScottPlot.WinForms.FormsPlot PlotW2Time;
        private ScottPlot.WinForms.FormsPlot PlotW1Time;
        private System.Windows.Forms.CheckBox checkVelocityMotorsAutoScale;
        private System.Windows.Forms.Label label17;
        private System.Windows.Forms.TextBox CurrentW4TextBox;
        private System.Windows.Forms.Label label16;
        private System.Windows.Forms.TextBox CurrentW3TextBox;
        private System.Windows.Forms.Label label15;
        private System.Windows.Forms.TextBox CurrentW2TextBox;
        private System.Windows.Forms.TextBox SetW4TextBox;
        private System.Windows.Forms.Label label19;
        private System.Windows.Forms.TextBox SetW3TextBox;
        private System.Windows.Forms.Label label18;
        private System.Windows.Forms.Label label20;
        private System.Windows.Forms.TextBox Vy_dTextBox;
        private System.Windows.Forms.Button TurnRightButton;
        private System.Windows.Forms.Button TurnLeftButton;
        private System.Windows.Forms.Button SwitchCameraButton;
        private System.Windows.Forms.Button StopRecordVideoButton;
        private System.Windows.Forms.Button StartRecordVideoButton;
        private System.Windows.Forms.Button TakePhotoButton;
        private System.Windows.Forms.Button StopStreamButton;
        private System.Windows.Forms.Button StartStreamButton;
        private System.Windows.Forms.ComboBox cboCameraMode;
        private System.Windows.Forms.TextBox txtCameraIp;
    }
}

