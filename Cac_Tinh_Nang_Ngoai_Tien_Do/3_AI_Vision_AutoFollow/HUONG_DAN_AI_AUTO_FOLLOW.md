# MODULE 3: AI THỊ GIÁC MÁY TÍNH ĐO CỰ LY 360° & TỰ HÀNH BÁM MỤC TIÊU (AUTO-FOLLOW)

## 1. Lý do tách riêng module
* Trong đặc tả kỹ thuật **Nhiệm vụ 3 (Control Application & Real-time Media System)**:
  * Trọng tâm của nhiệm vụ là xây dựng **Ứng dụng Điều khiển Thủ công (Manual Control)**, hệ thống **Truyền dẫn Video Thời gian thực (Zero-Latency Streaming)** và **Quản lý Media từ xa**.
  * Chức năng tự hành bám người (Autonomous Target Tracking & Auto-Follow) thuộc về **Nhiệm vụ 4 / Giai đoạn Tự hành nâng cao**, vượt ngoài phạm vi yêu cầu của Nhiệm vụ 3.
  * Vì vậy, toàn bộ phần AI Pose Detection, đo cự ly 360° và giải thuật tự động điều tiết vận tốc $V_x, \omega_z$ được bóc tách sang thư mục này.

---

## 2. Các file mã nguồn
1. [`VisualDistanceDetector.java`](file:///C:/Users/ACER/Downloads/MonitoringAndControlling4MecanumWheelsRobotApp/Cac_Tinh_Nang_Ngoai_Tien_Do/3_AI_Vision_AutoFollow/Android/VisualDistanceDetector.java)
   * Sử dụng Google MediaPipe Pose Landmark Detection để nhận diện 33 điểm khớp cơ thể.
   * **Đột phá đo cự ly 360° bất biến:** Đo bằng khoảng cách trục dọc từ Mũi ➔ Trung điểm 2 Vai ($0.24\text{ m}$). Giúp khoảng cách đo được luôn chuẩn xác ở mốc $50\text{ cm} \pm 10\text{ cm}$, không bị sai số nhảy mét từ 3m lên 5m khi người đứng nghiêng hoặc quay người xoay $360^\circ$.
2. [`AutoFollowManager.java`](file:///C:/Users/ACER/Downloads/MonitoringAndControlling4MecanumWheelsRobotApp/Cac_Tinh_Nang_Ngoai_Tien_Do/3_AI_Vision_AutoFollow/Android/AutoFollowManager.java)
   * Giải thuật tự động bám mục tiêu:
     * Tính toán sai số cự ly $\Delta d = d - 0.50\text{ m}$ để điều tiết vận tốc tiến/lùi $V_x$.
     * Tính toán độ lệch tâm $x_{offset}$ của người trong khung hình để điều tiết vận tốc góc $\omega_z$ của xe hoặc góc quay Pan của Gimbal.
3. [`FpvHudOverlayView.java`](file:///C:/Users/ACER/Downloads/MonitoringAndControlling4MecanumWheelsRobotApp/Cac_Tinh_Nang_Ngoai_Tien_Do/3_AI_Vision_AutoFollow/Android/FpvHudOverlayView.java)
   * Lớp phủ đồ họa FPV HUD trực quan:
     * Khung nhận diện đổi màu theo cự ly an toàn: 🟩 Xanh lá ($40 - 60\text{ cm}$), 🟨 Vàng ($>60\text{ cm}$), 🟥 Đỏ ($<38\text{ cm}$).
     * Thẻ lơ lửng hiển thị số mét thực tế: `👤 0.52 m (52 cm) [✓ ĐÚNG 50CM]`.
     * Vẽ điểm mốc phát sáng và chỉ vàng đo đạc trực tiếp trên cơ thể người.
