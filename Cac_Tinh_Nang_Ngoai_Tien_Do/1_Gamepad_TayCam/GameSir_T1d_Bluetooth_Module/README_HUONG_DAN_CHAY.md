# MODULE ĐỘC LẬP: KẾT NỐI TAY CẦM BLUETOOTH GAMESIR T1D ĐIỀU KHIỂN XE MECANUM

Module này được thiết kế **hoàn toàn độc lập** theo mô hình 3 lớp (Driver BLE ➔ Bộ chuyển đổi UDP Động học ➔ Giao diện Trực quan Visual HUD), chạy mượt mà ngay trên môi trường Linux chỉ có dòng lệnh (CMD / Terminal / SSH) hoặc chạy từ Visual Studio.

---

## 1. Cấu trúc các file trong Module
* [`gamesir_ble_driver.py`](file:///C:/Users/ACER/Downloads/MonitoringAndControlling4MecanumWheelsRobotApp/Cac_Tinh_Nang_Ngoai_Tien_Do/1_Gamepad_TayCam/GameSir_T1d_Bluetooth_Module/gamesir_ble_driver.py):
  * Tự động quét Bluetooth tìm `Gamesir-T1d-51FC` (hoặc các phiên bản T1d khác).
  * Kết nối trực tiếp qua Bluetooth Low Energy (BLE) mà **không cần mã PIN / không bị lỗi Pairing của Linux**.
  * Tự động khôi phục kết nối (Auto-Reconnect) nếu bị mất sóng.
  * Phân tích và giải mã 4 trục Analog 10-bit và 8 nút bấm.
* [`robot_udp_bridge.py`](file:///C:/Users/ACER/Downloads/MonitoringAndControlling4MecanumWheelsRobotApp/Cac_Tinh_Nang_Ngoai_Tien_Do/1_Gamepad_TayCam/GameSir_T1d_Bluetooth_Module/robot_udp_bridge.py):
  * Tính toán phân phối động học 4 bánh xe Mecanum ($w_1, w_2, w_3, w_4$).
  * Đóng gói frame chuẩn 47 bytes (STX `0xAA`, CRC16 Modbus, ETX `0x55`).
  * Gửi UDP trực tiếp tới xe (`127.0.0.1:8888` hoặc IP mạng LAN).
* [`main_gamepad_service.py`](file:///C:/Users/ACER/Downloads/MonitoringAndControlling4MecanumWheelsRobotApp/Cac_Tinh_Nang_Ngoai_Tien_Do/1_Gamepad_TayCam/GameSir_T1d_Bluetooth_Module/main_gamepad_service.py):
  * Chương trình chính kết nối 2 phần trên.
  * **Tích hợp Buồng lái Trực quan (Visual ASCII HUD)** vẽ trực tiếp trên màn hình đen của Linux: Vị trí cần gạt Analog 2D, thanh đo tốc độ 4 bánh xe và số frame UDP đã truyền.

---

## 2. Hướng dẫn chạy trên Linux Terminal

### Bước 1: Cài thư viện `bleak`
```bash
sudo apt update
sudo apt install -y python3-pip python3-bleak
# Hoặc: pip3 install bleak --break-system-packages
```

### Bước 2: Bật tay cầm GameSir T1d
* Giữ tổ hợp: **`A + Nút Nguồn tròn ở giữa`** khoảng 2–3 giây cho 4 đèn LED nháy chạy qua chạy lại.

### Bước 3: Khởi chạy chương trình
```bash
python3 main_gamepad_service.py
```
*(Nếu muốn bắn UDP sang IP khác, ví dụ IP máy khác trong mạng: `python3 main_gamepad_service.py --ip 192.168.2.50`)*.

---

## 3. Giao diện Buồng lái Trực quan trên Terminal
Khi kết nối thành công, 4 đèn trên tay cầm sẽ dừng nháy và màn hình terminal Linux của bạn sẽ hiện buồng lái:

```text
====================================================================
         🎮 BUỒNG LÁI ROBOT MECANUM - GAMESIR T1D BLUETOOTH
====================================================================
 Trạng thái Bluetooth: KẾT NỐI THÀNH CÔNG! (Gamesir-T1d-51FC)
 Thao tác hiện tại   : Tiến ▲ | Nút vừa bấm: None
--------------------------------------------------------------------
  CẦN ANALOG TRÁI (Lái)               TỐC ĐỘ 4 BÁNH XE MECANUM (rad/s)
     · · ● · ·          Bánh 1 (Trước-T): +[████████░░░░]  +8.5
     · · · · ·          Bánh 2 (Trước-P): +[████████░░░░]  +8.5
     · · · · ·          Bánh 3 (Sau-T)  : +[████████░░░░]  +8.5
     · · · · ·          Bánh 4 (Sau-P)  : +[████████░░░░]  +8.5
     · · · · ·          Gói UDP đã gửi : 124 frames
====================================================================
  [Cần Trái]: Tiến/Lùi/Trượt Ngang | [Cần Phải]: Xoay góc | [Ctrl+C]: Dừng
```
