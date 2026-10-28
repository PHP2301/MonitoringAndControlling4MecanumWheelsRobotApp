// =========================================================================================
// MODULE: ĐIỀU KHIỂN GIMBAL VẬT LÝ 3 TRỤC DJI RS 3 MINI
// DỰ ÁN: MONITORING AND CONTROLLING 4 MECANUM WHEELS ROBOT APP (C# WINFORMS)
// =========================================================================================
// Tính năng này được tách riêng do không nằm trong phạm vi đặc tả yêu cầu Nhiệm vụ 3 ban đầu.
// Đặc tả Nhiệm vụ 3 yêu cầu nghiên cứu ePTZ (Digital Pan/Tilt/Zoom) thuần phần mềm không dùng motor.
// Module này bổ sung giao tiếp phần cứng với Gimbal chống rung DJI RS 3 Mini qua BLE và HTTP bridge.
// =========================================================================================

using System;
using System.Drawing;
using System.Net.Http;
using System.Windows.Forms;

namespace MonitoringAndControllingRobotsApp
{
    public partial class Form1
    {
        // ---------------------------------------------------------------------------------
        // KHAI BÁO CÁC NÚT VÀ BIẾN ĐIỀU KHIỂN GIMBAL RS 3 MINI
        // ---------------------------------------------------------------------------------
        private Button _btnGimbalLeft;
        private Button _btnGimbalRight;
        private Button _btnGimbalUp;
        private Button _btnGimbalDown;
        private Button _btnGimbalCenter;
        private Button _btnGimbalMode;

        private readonly string[] _gimbalModes = { "PF", "PTF", "FPV" };
        private int _gimbalModeIndex = 0; // Mặc định là PF (Pan Follow)

        /// <summary>
        /// Khởi tạo cụm nút bấm điều khiển Gimbal trên giao diện Windows Form
        /// </summary>
        private void SetupGimbalControlsUI()
        {
            try
            {
                Label lblGimbal = new Label
                {
                    Text = "Gimbal RS 3 Mini:",
                    Location = new System.Drawing.Point(373, 93),
                    AutoSize = true,
                    Font = new System.Drawing.Font("Segoe UI", 8.5F, System.Drawing.FontStyle.Bold),
                    ForeColor = System.Drawing.Color.FromArgb(70, 80, 95)
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

                // Hàng 1: [◀] [▲] [▶] (Pan Trái, Tilt Lên, Pan Phải)
                _btnGimbalLeft = MakeGimbalBtn("◀", 373, 113, 38, 34);
                _btnGimbalUp = MakeGimbalBtn("▲", 414, 113, 38, 34);
                _btnGimbalRight = MakeGimbalBtn("▶", 455, 113, 38, 34);

                // Hàng 2: [⊙] [▼] [PF] (Căn giữa Center, Tilt Xuống, Chế độ Mode)
                _btnGimbalCenter = MakeGimbalBtn("⊙", 373, 151, 38, 34);
                _btnGimbalDown = MakeGimbalBtn("▼", 414, 151, 38, 34);
                _btnGimbalMode = MakeGimbalBtn("PF", 455, 151, 38, 34);

                // Sự kiện Căn giữa Gimbal (Center)
                _btnGimbalCenter.Click += (s, e) =>
                {
                    FlashButtonClicked(_btnGimbalCenter, BTN_BLUE_COLOR);
                    SendButtonSyncEvent("GIMBAL_RECENTER", "CLICK");
                    SendCameraHttpCommand("gimbal/recenter");
                    LabelAnnouncement.Text = "Gimbal RS3 Mini: Đã căn giữa (Center)...";
                };

                // Sự kiện giữ/nhả các nút điều hướng Gimbal
                _btnGimbalUp.MouseDown += (s, e) => TriggerGimbalMove("GIMBAL_UP", true, 0, 1, _btnGimbalUp);
                _btnGimbalUp.MouseUp += (s, e) => TriggerGimbalMove("GIMBAL_UP", false, 0, 0, _btnGimbalUp);

                _btnGimbalDown.MouseDown += (s, e) => TriggerGimbalMove("GIMBAL_DOWN", true, 0, -1, _btnGimbalDown);
                _btnGimbalDown.MouseUp += (s, e) => TriggerGimbalMove("GIMBAL_DOWN", false, 0, 0, _btnGimbalDown);

                _btnGimbalLeft.MouseDown += (s, e) => TriggerGimbalMove("GIMBAL_LEFT", true, -1, 0, _btnGimbalLeft);
                _btnGimbalLeft.MouseUp += (s, e) => TriggerGimbalMove("GIMBAL_LEFT", false, 0, 0, _btnGimbalLeft);

                _btnGimbalRight.MouseDown += (s, e) => TriggerGimbalMove("GIMBAL_RIGHT", true, 1, 0, _btnGimbalRight);
                _btnGimbalRight.MouseUp += (s, e) => TriggerGimbalMove("GIMBAL_RIGHT", false, 0, 0, _btnGimbalRight);

                // Đổi chế độ Gimbal (PF -> PTF -> FPV)
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

        /// <summary>
        /// Cập nhật hiển thị chế độ Gimbal trên UI
        /// </summary>
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

        /// <summary>
        /// Gửi lệnh điều khiển chuyển động Gimbal
        /// </summary>
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
    }
}
