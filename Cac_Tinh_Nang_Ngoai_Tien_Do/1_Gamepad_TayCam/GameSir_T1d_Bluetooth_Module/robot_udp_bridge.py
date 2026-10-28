#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
========================================================================================
MODULE: ROBOT UDP SENDER & MECANUM KINEMATICS
DỰ ÁN: AUTONOMOUS CINEMATIC ROBOT (XE QUAY PHIM MECANUM)
========================================================================================
Độc lập phụ trách:
1. Nhận lệnh vận tốc (vx, vy, w) từ tay cầm.
2. Tính toán động học phân phối tốc độ 4 bánh xe Mecanum (w1, w2, w3, w4).
3. Đóng gói frame chuẩn 47 bytes (STX 0xAA, CRC16 Modbus, ETX 0x55).
4. Bắn gói tin UDP vào backend xe robot (mặc định 127.0.0.1:8888 trên Linux/Pi).
========================================================================================
"""

import socket
import struct
import math

class RobotUdpBridge:
    def __init__(self, target_ip: str = "127.0.0.1", target_port: int = 8888, max_wheel_speed: float = 12.0):
        self.target_ip = target_ip
        self.target_port = target_port
        self.max_wheel_speed = max_wheel_speed
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.seq_count = 0
        self.is_driving = False

    @staticmethod
    def crc16_modbus(data: bytes) -> int:
        crc = 0xFFFF
        for byte in data:
            crc ^= byte
            for _ in range(8):
                if crc & 1:
                    crc = (crc >> 1) ^ 0xA001
                else:
                    crc >>= 1
        return crc

    def drive_mecanum(self, lx: float, ly: float, rx: float) -> tuple:
        """
        lx: Trượt ngang (Trái: âm, Phải: dương)
        ly: Tiến / Lùi (Tiến: dương, Lùi: âm)
        rx: Xoay tại chỗ (Trái: âm, Phải: dương)
        Trả về: (w1, w2, w3, w4, vx, vy, w)
        """
        # Động học xe Mecanum:
        # w1 (Trước-Trái) = ly + lx + rx
        # w2 (Trước-Phải) = ly - lx + rx
        # w3 (Sau-Trái)   = ly - lx - rx
        # w4 (Sau-Phải)   = ly + lx - rx
        raw_w1 = ly + lx + rx
        raw_w2 = ly - lx + rx
        raw_w3 = ly - lx - rx
        raw_w4 = ly + lx - rx

        max_val = max(1.0, abs(raw_w1), abs(raw_w2), abs(raw_w3), abs(raw_w4))
        w1 = (raw_w1 / max_val) * self.max_wheel_speed
        w2 = (raw_w2 / max_val) * self.max_wheel_speed
        w3 = (raw_w3 / max_val) * self.max_wheel_speed
        w4 = (raw_w4 / max_val) * self.max_wheel_speed

        vx = ly * (self.max_wheel_speed * 0.05)
        vy = lx * (self.max_wheel_speed * 0.05)
        w  = rx * (self.max_wheel_speed * 0.20)

        is_moving = (abs(lx) > 0 or abs(ly) > 0 or abs(rx) > 0)
        state_run = 0x03 if is_moving else 0x01

        self.send_packet(vx, vy, w, w1, w2, w3, w4, state_run=state_run)
        self.is_driving = is_moving

        return (w1, w2, w3, w4, vx, vy, w)

    def stop(self):
        """Dừng xe an toàn"""
        self.send_packet(0, 0, 0, 0, 0, 0, 0, state_run=0x01)
        self.is_driving = False

    def send_packet(self, vx, vy, w, w1, w2, w3, w4, state_run=0x03, camera_cmd=0x00):
        self.seq_count = (self.seq_count + 1) % 65536

        # Cấu trúc 47 bytes chuẩn:
        # [0] STX 0xAA
        # [1] state_run (0x03: chạy, 0x01: dừng)
        # [2..41] 10 float32: x, y, theta, vx, vy, w, w1, w2, w3, w4
        # [42] camera_cmd
        # [43..44] uint16 seq
        # [45..46] uint16 session
        payload = bytearray([0xAA, state_run])
        payload += struct.pack('<10f', 0.0, 0.0, 0.0, float(vx), float(vy), float(w), float(w1), float(w2), float(w3), float(w4))
        payload.append(camera_cmd)
        payload += struct.pack('<2H', self.seq_count, 1)

        # CRC16 Modbus (tính từ byte 1 đến 44)
        crc = self.crc16_modbus(payload[1:])
        payload += struct.pack('<H', crc)
        payload.append(0x55)  # ETX 0x55

        try:
            self.sock.sendto(payload, (self.target_ip, self.target_port))
        except Exception:
            pass
