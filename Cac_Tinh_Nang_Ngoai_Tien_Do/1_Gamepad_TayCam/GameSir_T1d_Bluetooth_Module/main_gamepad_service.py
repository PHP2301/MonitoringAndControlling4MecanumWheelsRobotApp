#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
========================================================================================
CHƯƠNG TRÌNH ĐIỀU KHIỂN CHÍNH (GAMESIR T1D ➔ MECANUM ROBOT)
TÍCH HỢP GIAO DIỆN TRỰC QUAN (VISUAL ASCII HUD) NGAY TRÊN TERMINAL
========================================================================================
Cách chạy:
    python3 main_gamepad_service.py [--ip 127.0.0.1] [--port 8888]
========================================================================================
"""

import sys
import os
import asyncio
import argparse
import time

from gamesir_ble_driver import GameSirT1dDriver
from robot_udp_bridge import RobotUdpBridge

# Biến toàn cục hiển thị HUD
status_info = {
    "connection": "Đang tìm tay cầm...",
    "device_name": "Chưa kết nối",
    "motion_text": "Sẵn sàng (Đang dừng)",
    "lx": 0.0, "ly": 0.0, "rx": 0.0, "ry": 0.0,
    "w1": 0.0, "w2": 0.0, "w3": 0.0, "w4": 0.0,
    "last_btn": "None",
    "pkt_count": 0
}

def render_visual_hud():
    """Vẽ giao diện buồng lái trực quan ngay trên cửa sổ dòng lệnh Linux"""
    # Xóa màn hình
    sys.stdout.write("\033[H\033[J")

    def bar(val: float, max_val: float = 12.0) -> str:
        pct = max(0.0, min(1.0, abs(val) / max_val))
        filled = int(pct * 12)
        sign = "+" if val >= 0 else "-"
        color = "\033[92m" if val >= 0 else "\033[93m"
        reset = "\033[0m"
        return f"{sign}[{color}{'█' * filled}{'░' * (12 - filled)}{reset}]"

    # Tạo biểu đồ 2D cho Cần gạt Analog Trái (5x5)
    gx = int(status_info["lx"] * 2.0) + 2
    gy = -int(status_info["ly"] * 2.0) + 2
    gx = max(0, min(4, gx))
    gy = max(0, min(4, gy))
    grid = [["·" for _ in range(5)] for _ in range(5)]
    grid[gy][gx] = "\033[96m●\033[0m"

    out = []
    out.append("\033[94m====================================================================\033[0m")
    out.append("         🎮 \033[1mBUỒNG LÁI ROBOT MECANUM - GAMESIR T1D BLUETOOTH\033[0m")
    out.append("\033[94m====================================================================\033[0m")
    conn_color = "\033[92m" if "THÀNH CÔNG" in status_info["connection"] else "\033[93m"
    out.append(f" Trạng thái Bluetooth: {conn_color}{status_info['connection']}\033[0m ({status_info['device_name']})")
    out.append(f" Thao tác hiện tại   : \033[1m{status_info['motion_text']}\033[0m | Nút vừa bấm: \033[95m{status_info['last_btn']}\033[0m")
    out.append("\033[90m--------------------------------------------------------------------\033[0m")
    out.append("  \033[4mCẦN ANALOG TRÁI (Lái)\033[0m               \033[4mTỐC ĐỘ 4 BÁNH XE MECANUM (rad/s)\033[0m")
    out.append(f"     {' '.join(grid[0])}          Bánh 1 (Trước-T): {bar(status_info['w1'])} {status_info['w1']:+5.1f}")
    out.append(f"     {' '.join(grid[1])}          Bánh 2 (Trước-P): {bar(status_info['w2'])} {status_info['w2']:+5.1f}")
    out.append(f"     {' '.join(grid[2])}          Bánh 3 (Sau-T)  : {bar(status_info['w3'])} {status_info['w3']:+5.1f}")
    out.append(f"     {' '.join(grid[3])}          Bánh 4 (Sau-P)  : {bar(status_info['w4'])} {status_info['w4']:+5.1f}")
    out.append(f"     {' '.join(grid[4])}          Gói UDP đã gửi : \033[96m{status_info['pkt_count']}\033[0m frames")
    out.append("\033[94m====================================================================\033[0m")
    out.append("  \033[90m[Cần Trái]: Tiến/Lùi/Trượt Ngang | [Cần Phải]: Xoay góc | [Ctrl+C]: Dừng\033[0m")

    sys.stdout.write("\n".join(out) + "\n")
    sys.stdout.flush()

async def main():
    parser = argparse.ArgumentParser(description="GameSir T1d Robot Controller")
    parser.add_argument("--ip", default="127.0.0.1", help="Địa chỉ IP của xe Robot (mặc định: 127.0.0.1)")
    parser.add_argument("--port", type=int, default=8888, help="Cổng UDP nhận lệnh của xe (mặc định: 8888)")
    parser.add_argument("--speed", type=float, default=12.0, help="Tốc độ bánh xe tối đa (rad/s)")
    args = parser.parse_args()

    robot = RobotUdpBridge(target_ip=args.ip, target_port=args.port, max_wheel_speed=args.speed)
    driver = GameSirT1dDriver(target_name_filter="Gamesir-T1d")

    def on_motion(lx, ly, rx, ry):
        w1, w2, w3, w4, vx, vy, w = robot.drive_mecanum(lx, ly, rx)
        status_info["lx"] = lx
        status_info["ly"] = ly
        status_info["rx"] = rx
        status_info["ry"] = ry
        status_info["w1"] = w1
        status_info["w2"] = w2
        status_info["w3"] = w3
        status_info["w4"] = w4
        status_info["pkt_count"] = robot.seq_count

        if abs(rx) > 0.2 and abs(ly) < 0.3 and abs(lx) < 0.3:
            status_info["motion_text"] = "Xoay Phải ↻" if rx > 0 else "Xoay Trái ↺"
        elif ly > 0.3 and abs(lx) < 0.3:
            status_info["motion_text"] = "Tiến ▲"
        elif ly < -0.3 and abs(lx) < 0.3:
            status_info["motion_text"] = "Lùi ▼"
        elif lx < -0.3 and abs(ly) < 0.3:
            status_info["motion_text"] = "Trượt Trái ◀"
        elif lx > 0.3 and abs(ly) < 0.3:
            status_info["motion_text"] = "Trượt Phải ▶"
        elif abs(lx) > 0 or abs(ly) > 0 or abs(rx) > 0:
            status_info["motion_text"] = "Đang lái xe..."
        else:
            status_info["motion_text"] = "Sẵn sàng (Đang dừng)"

    def on_button(btns):
        pressed = [k for k, v in btns.items() if v]
        if pressed:
            status_info["last_btn"] = "+".join(pressed)
            if "B" in pressed or "X" in pressed:
                robot.stop()

    def on_status(msg):
        status_info["connection"] = msg
        if driver.device_name:
            status_info["device_name"] = driver.device_name

    driver.set_on_motion(on_motion)
    driver.set_on_button(on_button)
    driver.set_on_status(on_status)

    # Chạy vòng lặp render HUD ở tần số 10Hz
    async def hud_loop():
        while True:
            render_visual_hud()
            await asyncio.sleep(0.1)

    # Chạy đồng thời vòng lặp Bluetooth và vòng lặp vẽ HUD
    await asyncio.gather(
        driver.run_loop(),
        hud_loop()
    )

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n>> Đã tắt chương trình an toàn.")
