# MODULE 2: TÍCH HỢP GIMBAL CHỐNG RUNG 3 TRỤC DJI RS 3 MINI

## 1. Lý do tách riêng module
* Trong đặc tả chính thức của **Nhiệm vụ 3 — Phần H (Nghiên cứu ePTZ)**:
  * Yêu cầu kỹ thuật cốt lõi là **Digital Pan/Tilt/Zoom (ePTZ thuần phần mềm)**, phóng to/thu nhỏ và crop khung hình trực tiếp từ cảm biến camera điện thoại mà **không sử dụng động cơ vật lý (không cần motor)**.
  * Việc tích hợp Gimbal chống rung 3 trục cơ điện tử **DJI RS 3 Mini** là một giải pháp mở rộng phần cứng cao cấp được đưa vào thực tế để tăng cường chất lượng ổn định hình ảnh khi xe Mecanum lăn bánh trên địa hình gồ ghề.
  * Vì vậy, module này được đóng gói độc lập để không làm sai lệch yêu cầu nghiệm thu ePTZ ban đầu.

---

## 2. Các file mã nguồn
1. **Phía Android Java (`RobotCameraApp`):**
   * [`GimbalBleManager.java`](file:///C:/Users/ACER/Downloads/MonitoringAndControlling4MecanumWheelsRobotApp/Cac_Tinh_Nang_Ngoai_Tien_Do/2_Gimbal_DJI_RS3_Mini/Android/GimbalBleManager.java)
   * Tích hợp giao thức Bluetooth Low Energy (BLE) với Gimbal DJI RS 3 Mini.
   * Xử lý handshake, đọc dung lượng pin, thiết lập chế độ xoay (PF, PTF, FPV), điều khiển góc Pan / Tilt và căn giữa Center.
2. **Phía Windows C# (`MonitoringAndControllingRobotsApp`):**
   * [`Form1_Gimbal_Module.cs`](file:///C:/Users/ACER/Downloads/MonitoringAndControlling4MecanumWheelsRobotApp/Cac_Tinh_Nang_Ngoai_Tien_Do/2_Gimbal_DJI_RS3_Mini/Windows_CSharp/Form1_Gimbal_Module.cs)
   * Cụm nút điều khiển 6 phím (`◀ ▲ ▶ ⊙ ▼ PF`) gửi lệnh điều khiển Gimbal qua cầu nối HTTP / UDP.

---

## 3. Cấu hình tối ưu trên app DJI Ronin (Mobile)
Khi kết nối Gimbal DJI RS 3 Mini lên xe robot, cần cài đặt Profile M2 như sau để Gimbal xoay bám kịp tốc độ đổi hướng của xe Mecanum:
* **Pan Speed:** Đặt `100` (Tốc độ xoay tối đa)
* **Deadband (Vùng chết):** Đặt `Low` hoặc `Off` (Khử hoàn toàn độ trễ khi xe bắt đầu xoay)
* **Pan Smoothness:** Đặt `8` (Vừa đủ mượt, không bị trượt góc nhìn)
