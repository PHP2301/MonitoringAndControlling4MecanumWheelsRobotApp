# TỔNG QUAN CÁC ĐOẠN CODE ĐƯỢC TÁCH RIÊNG KHỎI BÁO CÁO TIẾN ĐỘ
### DỰ ÁN: AUTONOMOUS CINEMATIC ROBOT (XE QUAY PHIM TỰ HÀNH MECANUM)
### NHIỆM VỤ: NHIỆM VỤ 3 — CONTROL APPLICATION & REAL-TIME MEDIA SYSTEM

---

## I. CĂN CỨ ĐỐI CHIẾU
Việc rà soát và bóc tách mã nguồn dựa trên hai tài liệu tiến độ chính:
1. **File Word Tiến độ thực tế của sinh viên (`BÁO CÁO TIẾN ĐỘ TUẦN 1.docx`):**
   * Báo cáo tuần 1 tập trung vào giai đoạn khởi tạo (06/10 – 09/10/2026):
     * *Ngày 06/10:* Nghiên cứu kiến trúc truyền thông công nghiệp (MQTT, Mô hình OSI 7 tầng, Chuẩn VDA 5050 & OPC-UA).
     * *Ngày 07/10:* Xây dựng ứng dụng camera Java CameraX (`RobotCameraApp`), Foreground Service `CameraStreamService`, máy chủ `MjpegServer:8080`, nạp APK `RobotCamera.apk`.
     * *Ngày 09/10:* Xử lý Zero-Latency Drop-Old-Frame, LibVLC caching = 0ms, giao diện Samsung One UI Bottom Sheet, thẻ HUD IP WiFi.
2. **Tài liệu Đặc tả Kỹ thuật & Báo cáo Nghiệm thu Nhiệm vụ 3 (`BAO_CAO_TIEN_DO_NHIEM_VU_3.md` / PDF 19 trang):**
   * Quy định 35 mục yêu cầu và 21 bài test (APP-T01 đến APP-T21) bao gồm: Giao thức UDP 47B/86B, Ứng dụng điều khiển xe Mecanum (Manual), Streaming video trực tiếp, Chụp/Quay từ xa & lưu MediaStore, Phân quyền Master/Viewer, Heartbeat Fail-safe và ePTZ kỹ thuật số không dùng motor.

---

## II. BẢNG PHÂN LOẠI MÃ NGUỒN THEO TIẾN ĐỘ

| Thành phần mã nguồn | Nằm trong File tiến độ? | Trạng thái phân loại | Thư mục lưu trữ |
| :--- | :---: | :---: | :--- |
| **Giao thức UDP 47B / 86B & CRC16** (`RobotUdpManager.java`) | ✅ **CÓ** (Phần A, NET01–NET06) | Mã nguồn cốt lõi Nhiệm vụ 3 | Giữ nguyên trong `RobotCameraApp` |
| **Dịch vụ CameraX & MJPEG Streaming** (`CameraStreamService.java`, `MjpegServer.java`) | ✅ **CÓ** (Phần C, STR01–STR05) | Mã nguồn cốt lõi Nhiệm vụ 3 | Giữ nguyên trong `RobotCameraApp` |
| **Giao diện Samsung One UI & FPV Player** (`MainActivitySettings.java`, `RemoteMjpegView.java`) | ✅ **CÓ** (Phần B, APP02, STR02) | Mã nguồn cốt lõi Nhiệm vụ 3 | Giữ nguyên trong `RobotCameraApp` |
| **Phân vai Streamer / Controller** (`RoleSelectionView.java`, `MainActivityStreamer.java`) | ✅ **CÓ** (Phần B, APP03) | Mã nguồn cốt lõi Nhiệm vụ 3 | Giữ nguyên trong `RobotCameraApp` |
| **Đồng bộ 2 chiều & Phân quyền Master/Viewer** (`ButtonSyncManager.java`) | ✅ **CÓ** (Phần E, AUTH01–AUTH03) | Mã nguồn cốt lõi Nhiệm vụ 3 | Giữ nguyên trong `RobotCameraApp` |
| **Chụp ảnh & Quay video MP4 MediaStore** (`MainActivityCamera.java`) | ✅ **CÓ** (Phần D, CAM01–CAM04) | Mã nguồn cốt lõi Nhiệm vụ 3 | Giữ nguyên trong `RobotCameraApp` |
| **Ứng dụng PC Giám sát & Điều khiển WinForms** (`Form1.cs`, `Form1.ModernTheme.cs`) | ✅ **CÓ** (Phần A, B, C) | Mã nguồn cốt lõi Nhiệm vụ 3 | Giữ nguyên trong `MonitoringAndControllingRobotsApp` |
| **1. Điều khiển xe bằng Tay cầm (Gamepad / Xbox 360)** (`MainActivityGamepad.java`, XInput C#) | ❌ **KHÔNG** | **TÁCH RIÊNG THÀNH FOLDER** | `Cac_Tinh_Nang_Ngoai_Tien_Do/1_Gamepad_TayCam` |
| **2. Tích hợp Gimbal vật lý DJI RS 3 Mini qua BLE** (`GimbalBleManager.java`, Gimbal C# UI) | ❌ **KHÔNG** *(Đặc tả chỉ yêu cầu ePTZ không motor)* | **TÁCH RIÊNG THÀNH FOLDER** | `Cac_Tinh_Nang_Ngoai_Tien_Do/2_Gimbal_DJI_RS3_Mini` |
| **3. AI Đo khoảng cách 360° & Tự hành bám người (Auto-Follow)** (`VisualDistanceDetector.java`, `AutoFollowManager.java`, `FpvHudOverlayView.java`) | ❌ **KHÔNG** *(Thuộc Nhiệm vụ 4 / Tự hành)* | **TÁCH RIÊNG THÀNH FOLDER** | `Cac_Tinh_Nang_Ngoai_Tien_Do/3_AI_Vision_AutoFollow` |

---

## III. CẤU TRÚC THƯ MỤC ĐÃ TÁCH
```
MonitoringAndControlling4MecanumWheelsRobotApp/
└── Cac_Tinh_Nang_Ngoai_Tien_Do/
    ├── TONG_QUAN_CAC_TINH_NANG_TACH_RIENG.md
    │
    ├── 1_Gamepad_TayCam/
    │   ├── HUONG_DAN_SU_DUNG_GAMEPAD.md
    │   ├── Android/
    │   │   └── MainActivityGamepad.java
    │   └── Windows_CSharp/
    │       └── Form1_Gamepad_Module.cs
    │
    ├── 2_Gimbal_DJI_RS3_Mini/
    │   ├── HUONG_DAN_GIMBAL_RS3.md
    │   ├── Android/
    │   │   └── GimbalBleManager.java
    │   └── Windows_CSharp/
    │       └── Form1_Gimbal_Module.cs
    │
    └── 3_AI_Vision_AutoFollow/
        ├── HUONG_DAN_AI_AUTO_FOLLOW.md
        └── Android/
            ├── VisualDistanceDetector.java
            ├── AutoFollowManager.java
            └── FpvHudOverlayView.java
```

---

## IV. NGUYÊN TẮC BẢO TOÀN DỰ ÁN
1. **Dự án chính vẫn hoạt động 100%:** Các file gốc trong `RobotCameraApp` và `MonitoringAndControllingRobotsApp` vẫn được giữ nguyên vẹn để đảm bảo ứng dụng Android và ứng dụng Windows không bị lỗi biên dịch (Compilation Error).
2. **Tính độc lập & Dễ tra cứu:** Toàn bộ mã nguồn, tài liệu hướng dẫn và sơ đồ nguyên lý của 3 module mở rộng ngoài tiến độ đã được sao chép và đóng gói độc lập trong thư mục `Cac_Tinh_Nang_Ngoai_Tien_Do` để bạn dễ dàng quản lý, trình chiếu hoặc đưa vào các phụ lục báo cáo sau này.
