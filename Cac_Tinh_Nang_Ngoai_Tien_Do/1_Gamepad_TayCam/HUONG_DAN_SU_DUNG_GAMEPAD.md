# MODULE 1: ĐIỀU KHIỂN BẰNG TAY CẦM (GAMEPAD / XBOX 360)

## 1. Lý do tách riêng module
* Trong tài liệu **Nhiệm vụ 3 (Control Application & Real-time Media System)** và file **Báo cáo tiến độ**, hệ thống chỉ yêu cầu điều khiển xe thủ công (Manual Control) qua:
  * Trên Android Tablet: Bàn phím ảo, Joystick ảo cảm ứng.
  * Trên Windows PC: Phím mũi tên bàn phím máy tính hoặc các nút bấm trên giao diện.
* Tính năng điều khiển bằng **Tay cầm vật lý (Xbox 360 / Gamepad)** là tính năng bổ sung mở rộng do người dùng kết nối thêm phần cứng điều khiển ngoại vi, nên được đóng gói riêng biệt để độc lập với tài liệu nghiệm thu chính thức.

---

## 2. Các file mã nguồn
1. **Phía Android Java (`RobotCameraApp`):**
   * [`MainActivityGamepad.java`](file:///C:/Users/ACER/Downloads/MonitoringAndControlling4MecanumWheelsRobotApp/Cac_Tinh_Nang_Ngoai_Tien_Do/1_Gamepad_TayCam/Android/MainActivityGamepad.java)
   * Tích hợp: Bắt sự kiện `dispatchGenericMotionEvent` và `dispatchKeyEvent`. Hỗ trợ cả tay cầm USB-OTG và Bluetooth không dây.
   * Tính năng Real-time: Núm joystick ảo lệch tâm theo cần analog trái, 2 nút xoay sáng đèn khi xoay cần analog phải.
2. **Phía Windows C# (`MonitoringAndControllingRobotsApp`):**
   * [`Form1_Gamepad_Module.cs`](file:///C:/Users/ACER/Downloads/MonitoringAndControlling4MecanumWheelsRobotApp/Cac_Tinh_Nang_Ngoai_Tien_Do/1_Gamepad_TayCam/Windows_CSharp/Form1_Gamepad_Module.cs)
   * Sử dụng thư viện `SharpDX.XInput` (DirectX).
   * Vòng lặp quét 40Hz (25ms), tự động phát hiện cắm nóng (Hot-plugging).
   * Hiển thị Badge trạng thái động trên Top Bar và nhảy số thời gian thực trên các ô vận tốc bánh xe ($w_1, w_2, w_3, w_4$).

---

## 3. Sơ đồ ánh xạ nút (Button Mapping)
| Phím trên Tay cầm | Chức năng trên Xe Mecanum & Camera |
| :--- | :--- |
| **Cần Analog Trái (LS)** | Tiến ▲, Lùi ▼, Trượt ngang Trái ◀, Trượt ngang Phải ▶ (Mecanum Kinematics) |
| **Cần Analog Phải (RS) X** | Xoay góc tại chỗ: Xoay Trái ↺ / Xoay Phải ↻ |
| **Cần Analog Phải (RS) Y** | Tăng / Giảm trần tốc độ xe mượt mà [2.0 – 30.0 rad/s] |
| **Nút bấm vai LB / RB** | LB: Bắt đầu quay Video MP4 / RB: Dừng quay Video |
| **Cò Analog LT / RT** | LT: Chụp ảnh độ phân giải cao / RT: Chuyển Camera Trước ⟷ Sau |
| **D-Pad (▲ ▼ ◀ ▶)** | Điều khiển Gimbal RS 3 Mini (Tilt Lên/Xuống, Pan Trái/Phải) |
| **Click Cần Phải (RS Click)** | Căn giữa Gimbal (Recenter `⊙`) |
| **Nút Start** | Kết nối / Ngắt kết nối mạng UDP (Connect / Disconnect) |
| **Nút Back** | Chuyển đổi chế độ phân quyền (Master ⟷ Viewer) |
| **Nút X** | Phanh dừng khẩn cấp (Emergency Stop) |
