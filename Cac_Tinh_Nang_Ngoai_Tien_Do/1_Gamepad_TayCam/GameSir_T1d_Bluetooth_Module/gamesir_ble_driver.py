#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
========================================================================================
MODULE: GAMESIR T1D BLUETOOTH LOW ENERGY (BLE) DRIVER
DỰ ÁN: AUTONOMOUS CINEMATIC ROBOT (XE QUAY PHIM MECANUM)
========================================================================================
Driver độc lập chuyên trách:
1. Tự động quét và bắt sóng tay cầm GameSir T1d (vd: Gamesir-T1d-51FC).
2. Kết nối BLE trực tiếp không cần mã PIN / Pairing.
3. Tự động khôi phục kết nối (Auto-Reconnect) khi bị mất sóng hoặc tắt bật lại tay cầm.
4. Phân tích gói tin 20 bytes từ Characteristic: 00008651-0000-1000-8000-00805f9b34fb
   - Giải mã 4 trục 10-bit Analog: Cần trái (LX, LY), Cần phải (RX, RY) [0..1023, Tâm 512].
   - Giải mã nút bấm: A, B, X, Y, L1, R1, L2, R2.
========================================================================================
"""

import asyncio
from typing import Callable, Optional
from bleak import BleakScanner, BleakClient

CHARACTERISTIC_UUID = "00008651-0000-1000-8000-00805f9b34fb"

class GameSirT1dDriver:
    def __init__(self, target_name_filter: str = "Gamesir-T1d"):
        self.target_name_filter = target_name_filter
        self.client: Optional[BleakClient] = None
        self.is_connected = False
        self.device_address = None
        self.device_name = None

        # Callbacks
        self.on_motion_callback: Optional[Callable[[float, float, float, float], None]] = None
        self.on_button_callback: Optional[Callable[[dict], None]] = None
        self.on_status_callback: Optional[Callable[[str], None]] = None

    def set_on_motion(self, callback: Callable[[float, float, float, float], None]):
        """Callback nhận (lx, ly, rx, ry) trong khoảng [-1.0, 1.0]"""
        self.on_motion_callback = callback

    def set_on_button(self, callback: Callable[[dict], None]):
        """Callback nhận dictionary trạng thái các nút bấm"""
        self.on_button_callback = callback

    def set_on_status(self, callback: Callable[[str], None]):
        """Callback thông báo trạng thái kết nối"""
        self.on_status_callback = callback

    def _log(self, msg: str):
        if self.on_status_callback:
            self.on_status_callback(msg)
        else:
            print(f"[GameSir-BLE] {msg}")

    def _notification_handler(self, sender, data: bytes):
        """Xử lý gói tin nhận về từ GameSir T1d"""
        if len(data) < 10:
            return

        # 0xA1, 0xC5: Gói tin input tay cầm
        if len(data) >= 12 and data[0] == 0xA1 and data[1] == 0xC5:
            # 1. Giải mã 4 trục Analog 10-bit (MSB-first chuẩn GameSir T1d: 0..1023, tâm 512)
            lx_raw = ((data[2]) << 2) | (data[3] >> 6)
            ly_raw = ((data[3] & 0x3F) << 4) | (data[4] >> 4)
            rx_raw = ((data[4] & 0x0F) << 6) | (data[5] >> 2)
            ry_raw = ((data[5] & 0x03) << 8) | (data[6])

            # Chuẩn hóa về [-1.0, 1.0] kèm Deadzone lọc trôi cần
            def normalize_axis(raw_val: int) -> float:
                val = (raw_val - 512.0) / 512.0
                return 0.0 if abs(val) < 0.12 else max(-1.0, min(1.0, val))

            lx = normalize_axis(lx_raw)
            ly = -normalize_axis(ly_raw)  # Đẩy lên là tiến (+)
            rx = normalize_axis(rx_raw)   # Đẩy sang phải là (+)
            ry = -normalize_axis(ry_raw)

            if self.on_motion_callback:
                self.on_motion_callback(lx, ly, rx, ry)

            # 2. Giải mã nút bấm (Byte 7=L2, Byte 8=R2, Byte 9=A/B/X/Y/L1/R1, Byte 10=C1/C2, Byte 11=DPad)
            if self.on_button_callback:
                l2_val = data[7]
                r2_val = data[8]
                btn9 = data[9]
                btn10 = data[10]
                dpad = data[11]
                btn_state = {
                    "A": bool(btn9 & 0x01),
                    "B": bool(btn9 & 0x02),
                    "MENU": bool(btn9 & 0x04),
                    "X": bool(btn9 & 0x08),
                    "Y": bool(btn9 & 0x10),
                    "L1": bool(btn9 & 0x40),
                    "R1": bool(btn9 & 0x80),
                    "L2": (l2_val > 128) or bool(btn10 & 0x10),
                    "R2": (r2_val > 128) or bool(btn10 & 0x20),
                    "C1": bool(btn10 & 0x04),
                    "C2": bool(btn10 & 0x08),
                    "DPAD": dpad,
                }
                self.on_button_callback(btn_state)

    async def scan_and_connect(self) -> bool:
        """Quét tìm và kết nối tới tay cầm GameSir T1d"""
        self._log(f"Đang quét tìm thiết bị Bluetooth chứa '{self.target_name_filter}'...")
        devices = await BleakScanner.discover(timeout=5.0)
        target_device = None

        for d in devices:
            if d.name and self.target_name_filter.lower() in d.name.lower():
                target_device = d
                break

        if not target_device:
            self._log("Không tìm thấy tay cầm. Hãy nhấn [A + Nút Nguồn] cho 4 đèn nháy!")
            return False

        self.device_address = target_device.address
        self.device_name = target_device.name
        self._log(f"Đã phát hiện: {self.device_name} [{self.device_address}]. Đang bắt tay BLE...")

        def on_disconnected(client):
            self.is_connected = False
            self._log("Mất kết nối Bluetooth với tay cầm!")

        try:
            self.client = BleakClient(self.device_address, disconnected_callback=on_disconnected)
            await self.client.connect()
            self.is_connected = self.client.is_connected

            if self.is_connected:
                self._log(f"KẾT NỐI THÀNH CÔNG với {self.device_name}! (4 đèn trên tay cầm sẽ dừng nháy)")
                await self.client.start_notify(CHARACTERISTIC_UUID, self._notification_handler)
                return True
        except Exception as e:
            self._log(f"Lỗi kết nối BLE: {e}")
            self.is_connected = False

        return False

    async def run_loop(self):
        """Vòng lặp tự duy trì kết nối và tự Reconnect khi mất sóng"""
        while True:
            if not self.is_connected:
                connected = await self.scan_and_connect()
                if not connected:
                    await asyncio.sleep(2.0)
                    continue

            while self.is_connected and self.client and self.client.is_connected:
                await asyncio.sleep(0.5)

            self._log("Đang chờ để kết nối lại...")
            await asyncio.sleep(2.0)
