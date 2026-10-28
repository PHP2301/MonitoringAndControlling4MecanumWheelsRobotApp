using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;
using LibVLCSharp.Shared;
using LibVLCSharp.WinForms;

namespace MonitoringAndControlling4MecanumWheelsRobotsApp
{
    public partial class CameraStreamForm : Form
    {
        private LibVLC _libVLC;
        private MediaPlayer _mediaPlayer;
        private VideoView _videoView;
        private bool _isStretchMode = false; // Mặc định FALSE: Chuẩn tỷ lệ gốc (9:16 hoặc 16:9), tuyệt đối không bị bè hình
        private string _lastStreamUrl = null;
        private readonly object _mediaLock = new object();
        private System.Threading.CancellationTokenSource _reconnectCts;
        private volatile bool _isConnecting = false;
        private volatile bool _isUserStopped = false;

        public CameraStreamForm()
        {
            InitializeComponent();
            SetupUI();
            this.TopMost = true;
        }

        private void SetupUI()
        {
            this.Text = "Live Camera Stream (Robot Camera) - [Chuẩn tỷ lệ]";
            this.Width = 800;
            this.Height = 500;
            this.KeyPreview = true;

            // Khởi tạo thư viện VLC với cấu hình siêu tốc không giật / không chớp nháy
            Core.Initialize();
            _libVLC = new LibVLC(
                "--demux=mjpeg",
                "--network-caching=150",
                "--http-caching=150",
                "--live-caching=150",
                "--file-caching=150",
                "--sout-mux-caching=150",
                "--clock-jitter=300",
                "--no-audio",
                "--no-video-title-show",
                "--avcodec-fast",
                "--avcodec-threads=2",
                "--avcodec-skiploopfilter=4"
            );
            _mediaPlayer = new MediaPlayer(_libVLC)
            {
                EnableMouseInput = false, // Cho phép WinForms bắt sự kiện click chuột
                EnableKeyInput = false
            };

            _videoView = new VideoView
            {
                MediaPlayer = _mediaPlayer,
                Dock = DockStyle.Fill
            };

            // Sự kiện đúp chuột bật/tắt toàn màn hình
            _videoView.MouseDoubleClick += (s, e) => ToggleFullScreen();
            this.MouseDoubleClick += (s, e) => ToggleFullScreen();

            // Phím tắt điều khiển:
            // - F11 / Enter / Escape: Bật/Tắt Toàn màn hình
            // - A: Bật/Tắt chế độ Chuẩn tỷ lệ vs Kéo tràn viền
            this.KeyDown += (s, e) =>
            {
                if (e.KeyCode == Keys.F11 || e.KeyCode == Keys.Enter)
                {
                    ToggleFullScreen();
                    e.Handled = true;
                }
                else if (e.KeyCode == Keys.Escape && this.WindowState == FormWindowState.Maximized)
                {
                    ToggleFullScreen();
                    e.Handled = true;
                }
                else if (e.KeyCode == Keys.A)
                {
                    _isStretchMode = !_isStretchMode;
                    UpdateAspectRatio();
                    e.Handled = true;
                }
            };

            // Khi thay đổi kích thước cửa sổ
            this.Resize += (s, e) => UpdateAspectRatio();

            // Khi VLC bắt đầu phát video (chỉ gọi 1 lần khi Playing, KHÔNG gọi trong Vout để tránh loop giật chớp màn hình)
            _mediaPlayer.Playing += (s, e) =>
            {
                this.BeginInvoke((Action)(() => UpdateAspectRatio()));
            };

            _mediaPlayer.EndReached += (s, e) => ScheduleReconnect(1200);
            _mediaPlayer.EncounteredError += (s, e) => ScheduleReconnect(2000);

            this.Controls.Add(_videoView);
            this.FormClosing += CameraStreamForm_FormClosing;
        }

        private void ToggleFullScreen()
        {
            if (this.WindowState == FormWindowState.Maximized && this.FormBorderStyle == FormBorderStyle.None)
            {
                this.FormBorderStyle = FormBorderStyle.Sizable;
                this.WindowState = FormWindowState.Normal;
            }
            else
            {
                this.FormBorderStyle = FormBorderStyle.None;
                this.WindowState = FormWindowState.Maximized;
            }
            UpdateAspectRatio();
        }

        public void UpdateAspectRatio()
        {
            try
            {
                if (_mediaPlayer != null)
                {
                    string targetRatio = (_isStretchMode && this.ClientSize.Width > 0 && this.ClientSize.Height > 0)
                        ? $"{this.ClientSize.Width}:{this.ClientSize.Height}"
                        : null;

                    // Chỉ gán khi giá trị thực sự thay đổi để tránh VLC re-init video filter liên tục gây chớp nháy
                    if (_mediaPlayer.AspectRatio != targetRatio)
                    {
                        _mediaPlayer.AspectRatio = targetRatio;
                    }
                    UpdateTitle();
                }
            }
            catch { }
        }

        private void UpdateTitle()
        {
            string modeDesc = _isStretchMode 
                ? "Kéo dãn tràn viền (Bấm A để về Chuẩn tỷ lệ)" 
                : "Chuẩn tỷ lệ (Không méo hình - Bấm A để Tràn viền)";
            this.Text = $"Live Camera Stream (Robot Camera) - [{modeDesc}]";
        }

        private void ScheduleReconnect(int delayMs)
        {
            if (_isUserStopped || !this.Visible || string.IsNullOrEmpty(_lastStreamUrl))
                return;

            lock (_mediaLock)
            {
                try { _reconnectCts?.Cancel(); } catch { }
                _reconnectCts = new System.Threading.CancellationTokenSource();
                var token = _reconnectCts.Token;

                Task.Delay(delayMs, token).ContinueWith(t =>
                {
                    if (!t.IsCanceled && !token.IsCancellationRequested && this.Visible && !_isUserStopped)
                    {
                        PlayStreamInternal(_lastStreamUrl);
                    }
                }, token);
            }
        }

        public void PlayStream(string udpUrl)
        {
            _isUserStopped = false;
            _lastStreamUrl = udpUrl;

            lock (_mediaLock)
            {
                try { _reconnectCts?.Cancel(); } catch { }
                _reconnectCts = null;
            }

            PlayStreamInternal(udpUrl);
        }

        private void PlayStreamInternal(string streamUrl)
        {
            if (_isUserStopped || _isConnecting) return;

            Task.Run(() =>
            {
                lock (_mediaLock)
                {
                    if (_isConnecting || _isUserStopped) return;
                    _isConnecting = true;
                }

                try
                {
                    try 
                    { 
                        if (_mediaPlayer != null && _mediaPlayer.IsPlaying)
                        {
                            _mediaPlayer.Stop(); 
                        }
                    } 
                    catch { }

                    var media = new Media(_libVLC, streamUrl, FromType.FromLocation);
                    // Cấu hình tối ưu truyền tải mượt mà không delay và không chớp nháy
                    media.AddOption(":demux=mjpeg");
                    media.AddOption(":network-caching=150");
                    media.AddOption(":http-caching=150");
                    media.AddOption(":live-caching=150");
                    media.AddOption(":file-caching=150");
                    media.AddOption(":sout-mux-caching=150");
                    media.AddOption(":clock-jitter=300");
                    media.AddOption(":no-audio");
                    media.AddOption(":no-video-title-show");
                    media.AddOption(":avcodec-fast");
                    media.AddOption(":avcodec-threads=2");
                    media.AddOption(":avcodec-skiploopfilter=4");
                    media.AddOption(":network-timeout=2000"); // 2s timeout chống treo unmanaged VLC
                    media.AddOption(":http-timeout=2000");
                    _mediaPlayer.Play(media);

                    this.BeginInvoke((Action)(() =>
                    {
                        UpdateAspectRatio();
                    }));
                }
                catch { }
                finally
                {
                    lock (_mediaLock)
                    {
                        _isConnecting = false;
                    }
                }
            });
        }

        public void StopStream()
        {
            _isUserStopped = true;
            lock (_mediaLock)
            {
                try { _reconnectCts?.Cancel(); } catch { }
                _reconnectCts = null;
            }

            Task.Run(() =>
            {
                try
                {
                    if (_mediaPlayer != null && _mediaPlayer.IsPlaying)
                    {
                        _mediaPlayer.Stop();
                    }
                }
                catch { }
            });
        }

        private void CameraStreamForm_FormClosing(object sender, FormClosingEventArgs e)
        {
            // Tạm dừng phát video khi ẩn cửa sổ
            StopStream();
            e.Cancel = true;
            this.Hide();
        }

        protected override void OnFormClosed(FormClosedEventArgs e)
        {
            base.OnFormClosed(e);
            _mediaPlayer?.Dispose();
            _libVLC?.Dispose();
        }
    }
}