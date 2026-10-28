#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
========================================================================================
  GAMESIR T1D (51FC) BLUETOOTH CONTROLLER CHO XE ROBOT MECANUM (CHUẨN FORM1 APP WINDOWS)
  - Cần Trái (LS) / D-Pad : Tiến / Lùi / Dạt ngang Trái - Phải
  - Cần Phải (RS) X       : Xoay tròn thân xe tại chỗ Trái / Phải
  - Cần Phải (RS) Y       : Tăng / Giảm tốc độ xe mượt mà (2.0 - 30.0 rad/s)
  - Nút C1 / C2           : Đổi nhanh chế độ tốc độ (10.0 <-> 20.0 rad/s)
  - Nút X                 : DỪNG KHẨN CẤP (EmergencyStopButton -> Khóa/Mở phanh)
  - Nút Y                 : CHẠY QUỸ ĐẠO TRÒN (CircleLineButton -> Mode 0x04)
  - Nút A                 : QUAY VỀ GỐC TỌA ĐỘ (ReturnHomeButton -> Mode 0x05)
  - Nút B                 : DỪNG MỀM & CÂN BẰNG LẠI TÂM CẦN GẠT (Mode 0x01)
  - Cò LT (L2)            : Chụp ảnh độ phân giải cao (TakePhotoButton -> 0x07)
  - Cò RT (R2)            : Đổi Camera Trước <-> Sau (SwitchCameraButton -> 0x0A)
  - Nút LB (L1)           : Bắt đầu quay Video MP4 (StartRecordButton -> 0x08)
  - Nút RB (R1)           : Dừng quay Video MP4 (StopRecordButton -> 0x09)
========================================================================================
"""

import sys
import os
import struct
import socket
import asyncio
from bleak import BleakScanner, BleakClient

UDP_IP = "127.0.0.1"
if len(sys.argv) > 1 and not sys.argv[1].startswith("-"):
    UDP_IP = sys.argv[1]

UDP_PORT = 5001
CHARACTERISTIC_UUID = "00008651-0000-1000-8000-00805f9b34fb"

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.setblocking(False)
seq_counter = 0

state = {
    "connected": False,
    "device_name": "Dang tim kiem...",
    "motion_text": "Cho ket noi Bluetooth",
    "lx": 0.0, "ly": 0.0, "rx": 0.0, "ry": 0.0,
    "raw_lx": 512, "raw_ly": 512, "raw_rx": 512, "raw_ry": 512,
    "center_lx": 512, "center_ly": 512, "center_rx": 512, "center_ry": 512,
    "calibrated": False,
    "bit_mode": "MSB",
    "raw_hex": "",
    "l2": 0, "r2": 0, "dpad": 0, "center_dpad": None,
    "w1": 0.0, "w2": 0.0, "w3": 0.0, "w4": 0.0,
    "vx": 0.0, "vy": 0.0, "w": 0.0,
    "max_speed": 10.0,
    "emergency": False,
    "is_driving": False,
    "state_run": 0x01,
    "stop_burst": 0,
    "last_btn": "None",
    "pkts": 0
}

prev_btn9 = 0
prev_btn10 = 0
prev_lt = False
prev_rt = False


def crc16_modbus(data: bytes) -> int:
    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ 0xA001
            else:
                crc >>= 1
    return crc & 0xFFFF


def drain_udp_rx():
    while True:
        try:
            sock.recvfrom(256)
        except (BlockingIOError, socket.error):
            break


def send_udp_frame(state_run: int, w1: float, w2: float, w3: float, w4: float, vx: float, vy: float, w: float):
    global seq_counter
    drain_udp_rx()
    seq_counter = (seq_counter + 1) & 0xFFFF

    header = struct.pack('<BHB', 0xAA, seq_counter, state_run)
    floats_data = struct.pack(
        '<10f',
        0.0, 0.0, 0.0,
        float(vx), float(vy), float(w),
        float(w1), float(w2), float(w3), float(w4)
    )
    raw_44 = header + floats_data
    crc = crc16_modbus(raw_44)
    packet = raw_44 + struct.pack('<HB', crc, 0x55)

    try:
        sock.sendto(packet, (UDP_IP, UDP_PORT))
    except Exception:
        pass

    state["w1"] = w1
    state["w2"] = w2
    state["w3"] = w3
    state["w4"] = w4
    state["vx"] = vx
    state["vy"] = vy
    state["w"] = w
    state["pkts"] = seq_counter


def compute_and_update_motion():
    """Tính toán vận tốc 4 bánh Mecanum chuẩn xác 100% theo Form1.cs"""
    lx = state["lx"]
    ly = state["ly"]
    rx = state["rx"]
    dpad = state["dpad"]
    c_dpad = state["center_dpad"]

    # Chỉ nhận D-Pad khi khác trạng thái nghỉ ban đầu (tránh tự trôi nếu byte 11 khác 0)
    if lx == 0.0 and ly == 0.0 and c_dpad is not None and dpad != c_dpad and dpad != 0:
        if dpad == 0x01:   # Up
            ly = 1.0
        elif dpad == 0x05: # Down
            ly = -1.0
        elif dpad == 0x07: # Left
            lx = -1.0
        elif dpad == 0x03: # Right
            lx = 1.0
        elif dpad == 0x02: # Up-Right
            ly = 1.0; lx = 1.0
        elif dpad == 0x08: # Up-Left
            ly = 1.0; lx = -1.0
        elif dpad == 0x04: # Down-Right
            ly = -1.0; lx = 1.0
        elif dpad == 0x06: # Down-Left
            ly = -1.0; lx = -1.0

    if state["emergency"]:
        state["is_driving"] = False
        state["state_run"] = 0x01
        state["motion_text"] = "!!! DUNG KHAN CAP (EMERGENCY STOP) !!!"
        state["w1"] = state["w2"] = state["w3"] = state["w4"] = 0.0
        state["vx"] = state["vy"] = state["w"] = 0.0
        return

    is_moving = (ly != 0.0 or lx != 0.0 or rx != 0.0)

    if is_moving:
        state["is_driving"] = True
        state["state_run"] = 0x03  # Mode Manual

        cur_max = state["max_speed"]
        # Công thức động học chuẩn Form1.cs & robot_controller.cpp:
        # w1 (M1 - Trái sau)  : ly + lx + rx
        # w2 (M2 - Trái trước): ly - lx + rx
        # w3 (M3 - Phải sau)  : ly - lx - rx
        # w4 (M4 - Phải trước): ly + lx - rx
        raw_w1 = ly + lx + rx
        raw_w2 = ly - lx + rx
        raw_w3 = ly - lx - rx
        raw_w4 = ly + lx - rx

        max_val = max(1.0, abs(raw_w1), abs(raw_w2), abs(raw_w3), abs(raw_w4))
        w1 = (raw_w1 / max_val) * cur_max
        w2 = (raw_w2 / max_val) * cur_max
        w3 = (raw_w3 / max_val) * cur_max
        w4 = (raw_w4 / max_val) * cur_max

        vx = ly * (cur_max * 0.05)
        vy = lx * (cur_max * 0.05)
        w  = rx * (cur_max * 0.20)

        state["w1"] = w1; state["w2"] = w2; state["w3"] = w3; state["w4"] = w4
        state["vx"] = vx; state["vy"] = vy; state["w"] = w

        if abs(rx) > 0.22 and abs(ly) < 0.35 and abs(lx) < 0.35:
            state["motion_text"] = f"XOAY PHAI (+{abs(w1):.1f})" if rx > 0 else f"XOAY TRAI (-{abs(w1):.1f})"
        elif ly > 0.35 and abs(lx) < 0.35:
            state["motion_text"] = f"TIEN THANG (+{w1:.1f})"
        elif ly < -0.35 and abs(lx) < 0.35:
            state["motion_text"] = f"LUI THANG ({w1:.1f})"
        elif lx < -0.35 and abs(ly) < 0.35:
            state["motion_text"] = f"TRUOT TRAI ({abs(w2):.1f})"
        elif lx > 0.35 and abs(ly) < 0.35:
            state["motion_text"] = f"TRUOT PHAI ({abs(w1):.1f})"
        else:
            state["motion_text"] = f"DI CHEO MECANUM ({cur_max:.1f} rad/s)"

    elif state["is_driving"]:
        state["is_driving"] = False
        state["state_run"] = 0x01
        state["stop_burst"] = 4
        state["w1"] = state["w2"] = state["w3"] = state["w4"] = 0.0
        state["vx"] = state["vy"] = state["w"] = 0.0
        state["motion_text"] = "DUNG YEN (STOP)"
        send_udp_frame(0x01, 0, 0, 0, 0, 0, 0, 0)


def unpack_axes(data: bytes, mode: str):
    if mode == "MSB":
        lx = ((data[2]) << 2) | (data[3] >> 6)
        ly = ((data[3] & 0x3F) << 4) | (data[4] >> 4)
        rx = ((data[4] & 0x0F) << 6) | (data[5] >> 2)
        ry = ((data[5] & 0x03) << 8) | (data[6])
    else:
        lx = data[2] | ((data[3] & 0x03) << 8)
        ly = ((data[3] & 0xFC) >> 2) | ((data[4] & 0x0F) << 6)
        rx = ((data[4] & 0xF0) >> 4) | ((data[5] & 0x3F) << 4)
        ry = ((data[5] & 0xC0) >> 6) | (data[6] << 2)
    return lx, ly, rx, ry


def on_ble_notification(sender, data: bytes):
    global prev_btn9, prev_btn10, prev_lt, prev_rt

    state["raw_hex"] = data.hex()
    try:
        with open("/tmp/gamesir_raw.log", "a") as f:
            f.write(f"{data.hex()} | {list(data)}\n")
    except Exception:
        pass

    # Chỉ xử lý gói Input Report 0xA1 0xC5 (bỏ qua gói Keep-Alive 0xC9 0xC6)
    if len(data) < 12 or data[0] != 0xA1 or data[1] != 0xC5:
        return

    # Tự động nhận diện chuẩn bit (MSB hay LSB) & khóa tâm 0.0 ngay ở gói đầu tiên
    if not state["calibrated"]:
        msb_axes = unpack_axes(data, "MSB")
        lsb_axes = unpack_axes(data, "LSB")
        msb_err = sum(abs(v - 512) for v in msb_axes)
        lsb_err = sum(abs(v - 512) for v in lsb_axes)
        if lsb_err < msb_err:
            state["bit_mode"] = "LSB"
            c_lx, c_ly, c_rx, c_ry = lsb_axes
        else:
            state["bit_mode"] = "MSB"
            c_lx, c_ly, c_rx, c_ry = msb_axes

        state["center_lx"] = c_lx
        state["center_ly"] = c_ly
        state["center_rx"] = c_rx
        state["center_ry"] = c_ry
        state["center_dpad"] = data[11]
        state["calibrated"] = True

    lx_raw, ly_raw, rx_raw, ry_raw = unpack_axes(data, state["bit_mode"])

    state["raw_lx"] = lx_raw
    state["raw_ly"] = ly_raw
    state["raw_rx"] = rx_raw
    state["raw_ry"] = ry_raw

    # Chuẩn hóa theo tâm thực tế của tay cầm (chống tự trôi 100%)
    norm_lx = (lx_raw - state["center_lx"]) / 512.0
    norm_ly = (state["center_ly"] - ly_raw) / 512.0
    norm_rx = (rx_raw - state["center_rx"]) / 512.0
    norm_ry = (state["center_ry"] - ry_raw) / 512.0

    # Deadzone lọc nhiễu tâm cần gạt (0.15)
    deadzone = 0.15
    lx = max(-1.0, min(1.0, norm_lx)) if abs(norm_lx) > deadzone else 0.0
    ly = max(-1.0, min(1.0, norm_ly)) if abs(norm_ly) > deadzone else 0.0
    rx = max(-1.0, min(1.0, norm_rx)) if abs(norm_rx) > deadzone else 0.0
    ry = max(-1.0, min(1.0, norm_ry)) if abs(norm_ry) > deadzone else 0.0

    state["lx"] = lx
    state["ly"] = ly
    state["rx"] = rx
    state["ry"] = ry

    # 2. CÒ ANALOG L2 / R2 (BYTE 7 & BYTE 8) & PHÍM BẤM (BYTE 9, 10, 11)
    l2_val = data[7]
    r2_val = data[8]
    btn9 = data[9]
    btn10 = data[10]
    dpad = data[11]

    state["l2"] = l2_val
    state["r2"] = r2_val
    state["dpad"] = dpad

    just_btn9 = btn9 & (~prev_btn9)
    just_btn10 = btn10 & (~prev_btn10)
    prev_btn9 = btn9
    prev_btn10 = btn10

    cur_lt = (l2_val > 180)
    cur_rt = (r2_val > 180)
    just_lt = cur_lt and (not prev_lt)
    just_rt = cur_rt and (not prev_rt)
    prev_lt = cur_lt
    prev_rt = cur_rt

    btn_labels = []

    if just_btn10 & (0x04 | 0x08):
        state["max_speed"] = 20.0 if state["max_speed"] == 10.0 else 10.0
        btn_labels.append(f"Turbo ({state['max_speed']:.0f} rad/s)")

    # Nút X (Byte 9 bit 0x08): DỪNG KHẨN CẤP
    if just_btn9 & 0x08:
        state["emergency"] = not state["emergency"]
        if state["emergency"]:
            state["state_run"] = 0x01
            state["is_driving"] = False
            state["stop_burst"] = 5
            send_udp_frame(0x01, 0, 0, 0, 0, 0, 0, 0)
            btn_labels.append("X [PHANH KHAN CAP]")
        else:
            btn_labels.append("X [Mo Khoa Phanh]")

    # Nút Y (Byte 9 bit 0x10): CHẠY QUỸ ĐẠO TRÒN (Mode 0x04)
    if just_btn9 & 0x10:
        state["emergency"] = False
        state["is_driving"] = False
        state["state_run"] = 0x04
        state["motion_text"] = ">>> DANG CHAY QUY DAO TRON (MODE 0x04) <<<"
        send_udp_frame(0x04, 0, 0, 0, 0, 0, 0, 0)
        btn_labels.append("Y (Quy Dao Tron 0x04)")

    # Nút A (Byte 9 bit 0x01): QUAY VỀ GỐC TỌA ĐỘ (Mode 0x05)
    if just_btn9 & 0x01:
        state["emergency"] = False
        state["is_driving"] = False
        state["state_run"] = 0x05
        state["motion_text"] = ">>> DANG QUAY VE GOC TOA DO (MODE 0x05) <<<"
        send_udp_frame(0x05, 0, 0, 0, 0, 0, 0, 0)
        btn_labels.append("A (Ve Goc Home 0x05)")

    # Nút B (Byte 9 bit 0x02): DỪNG MỀM & CÂN BẰNG LẠI TÂM CẦN GẠT (Mode 0x01)
    if just_btn9 & 0x02:
        state["is_driving"] = False
        state["state_run"] = 0x01
        state["stop_burst"] = 4
        state["center_lx"] = lx_raw
        state["center_ly"] = ly_raw
        state["center_rx"] = rx_raw
        state["center_ry"] = ry_raw
        state["center_dpad"] = dpad
        state["lx"] = state["ly"] = state["rx"] = state["ry"] = 0.0
        state["motion_text"] = "DUNG MEM & RESET TAM = 0 (MODE 0x01)"
        send_udp_frame(0x01, 0, 0, 0, 0, 0, 0, 0)
        btn_labels.append("B (Reset & Can Tam)")

    # Cò LT / L2: Chụp ảnh (0x07)
    if just_lt:
        send_udp_frame(0x07, 0, 0, 0, 0, 0, 0, 0)
        state["stop_burst"] = 1
        btn_labels.append("LT (Chup Anh 0x07)")

    # Cò RT / R2: Đổi Camera Trước/Sau (0x0A)
    if just_rt:
        send_udp_frame(0x0A, 0, 0, 0, 0, 0, 0, 0)
        state["stop_burst"] = 1
        btn_labels.append("RT (Doi Camera 0x0A)")

    # Nút LB / L1 (Byte 9 bit 0x40): Bắt đầu quay Video (0x08)
    if just_btn9 & 0x40:
        send_udp_frame(0x08, 0, 0, 0, 0, 0, 0, 0)
        state["stop_burst"] = 1
        btn_labels.append("LB (Quay Video 0x08)")

    # Nút RB / R1 (Byte 9 bit 0x80): Dừng quay Video (0x09)
    if just_btn9 & 0x80:
        send_udp_frame(0x09, 0, 0, 0, 0, 0, 0, 0)
        state["stop_burst"] = 1
        btn_labels.append("RB (Dung Video 0x09)")

    if btn_labels:
        state["last_btn"] = " + ".join(btn_labels)

    compute_and_update_motion()


async def control_tx_loop():
    while True:
        try:
            if state["connected"] and state["calibrated"]:
                ry = state["ry"]
                if ry > 0.65:
                    state["max_speed"] = min(30.0, state["max_speed"] + 0.15)
                elif ry < -0.65:
                    state["max_speed"] = max(2.0, state["max_speed"] - 0.15)

                compute_and_update_motion()

                if state["is_driving"]:
                    send_udp_frame(
                        0x03,
                        state["w1"], state["w2"], state["w3"], state["w4"],
                        state["vx"], state["vy"], state["w"]
                    )
                elif state["state_run"] in (0x04, 0x05):
                    send_udp_frame(state["state_run"], 0, 0, 0, 0, 0, 0, 0)
                elif state["stop_burst"] > 0:
                    state["stop_burst"] -= 1
                    send_udp_frame(0x01, 0, 0, 0, 0, 0, 0, 0)
                else:
                    drain_udp_rx()
        except Exception:
            pass
        await asyncio.sleep(0.025)


async def hud_loop():
    while True:
        try:
            status_str = "DA KET NOI (ONLINE)" if state["connected"] else "CHUA KET NOI (OFFLINE)"
            bar_len = 12

            def make_bar(val):
                pos = int((val + 1.0) / 2.0 * bar_len)
                pos = max(0, min(bar_len, pos))
                return "[" + "-" * pos + "|" + "-" * (bar_len - pos) + "]"

            mode_str = f"0x{state['state_run']:02X}"
            if state["emergency"]:
                mode_str = "EMERGENCY STOP"
            elif state["state_run"] == 0x04:
                mode_str = "QUY DAO TRON (0x04)"
            elif state["state_run"] == 0x05:
                mode_str = "VE GOC HOME (0x05)"
            elif state["is_driving"]:
                mode_str = "MANUAL DRIVE (0x03)"
            else:
                mode_str = "STANDBY STOP (0x01)"

            lines = [
                "\033[H\033[J" + "=" * 72,
                f"   GAMESIR T1d -> ROBOT MECANUM (BitMode: {state['bit_mode']} | Auto-Zero: ON)",
                "=" * 72,
                f" BLE: {status_str} | {state['device_name']}",
                f" UDP: {UDP_IP}:{UDP_PORT} (Pkts: {state['pkts']}) | Che do: {mode_str} | Toc do: {state['max_speed']:4.1f} rad/s",
                f" Trang thai: {state['motion_text']}",
                "-" * 72,
                f" [CAN GAT ANALOG (Tam={state['center_lx']},{state['center_ly']})]        [VAN TOC 4 BANH (rad/s)]",
                f"  LX ({state['raw_lx']:4d}): {make_bar(state['lx'])} {state['lx']:+5.2f}     M1 (Trai-Sau)  : {state['w1']:+6.2f}",
                f"  LY ({state['raw_ly']:4d}): {make_bar(state['ly'])} {state['ly']:+5.2f}     M2 (Trai-Truoc): {state['w2']:+6.2f}",
                f"  RX ({state['raw_rx']:4d}): {make_bar(state['rx'])} {state['rx']:+5.2f}     M3 (Phai-Sau)  : {state['w3']:+6.2f}",
                f"  RY ({state['raw_ry']:4d}): {make_bar(state['ry'])} {state['ry']:+5.2f}     M4 (Phai-Truoc): {state['w4']:+6.2f}",
                f"  D-Pad: {state['dpad']} | L2: {state['l2']:3d} | R2: {state['r2']:3d} | Phim: {state['last_btn']}",
                f"  RAW: {state['raw_hex'][:40]}",
                "=" * 72,
                "  * Can Trai / D-Pad: Tien / Lui / Truot Ngang | Can Phai X: Xoay Tai Cho",
                "  * X: DUNG KHAN CAP | Y: QUY DAO TRON (0x04)  | A: VE HOME | B: RESET TAM",
                "=" * 72
            ]
            sys.stdout.write("\n".join(lines) + "\n")
            sys.stdout.flush()
        except Exception:
            pass
        await asyncio.sleep(0.25)


async def main():
    try:
        if os.path.exists("/tmp/gamesir_raw.log"):
            os.remove("/tmp/gamesir_raw.log")
    except Exception:
        pass

    asyncio.create_task(control_tx_loop())
    asyncio.create_task(hud_loop())
    target_addr = "C6:86:A1:05:51:FC"

    while True:
        target_device = None
        try:
            devices = await BleakScanner.discover(timeout=3.0)
            for d in devices:
                d_name = (d.name or "").strip()
                if target_addr.lower() == d.address.lower() or "51fc" in d_name.lower() or "gamesir" in d_name.lower():
                    target_device = d
                    break
        except Exception as e:
            state["device_name"] = f"Loi quet BLE: {e}"
            await asyncio.sleep(1.5)
            continue

        if not target_device:
            state["connected"] = False
            state["device_name"] = "Dang cho tay cam (Hay bat nguon GameSir T1d)..."
            await asyncio.sleep(1.0)
            continue

        state["device_name"] = f"{target_device.name} ({target_device.address})"
        try:
            async with BleakClient(target_device.address, timeout=8.0) as client:
                state["connected"] = True
                state["calibrated"] = False
                state["is_driving"] = False
                state["lx"] = state["ly"] = state["rx"] = state["ry"] = 0.0
                state["motion_text"] = "DA KET NOI - DUNG YEN (STOP)"
                send_udp_frame(0x01, 0, 0, 0, 0, 0, 0, 0)
                await client.start_notify(CHARACTERISTIC_UUID, on_ble_notification)

                while client.is_connected:
                    await asyncio.sleep(0.2)

        except Exception as e:
            state["connected"] = False
            state["calibrated"] = False
            state["is_driving"] = False
            state["lx"] = state["ly"] = state["rx"] = state["ry"] = 0.0
            state["device_name"] = f"Mat ket noi ({e}). Dang ket noi lai..."
            send_udp_frame(0x01, 0, 0, 0, 0, 0, 0, 0)
            await asyncio.sleep(1.0)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        send_udp_frame(0x01, 0, 0, 0, 0, 0, 0, 0)
        print("\nDa dung chuong trinh & phanh xe an toan.")
