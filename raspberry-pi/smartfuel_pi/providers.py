import math
import random
from datetime import UTC, datetime


class MockTelemetryProvider:
    def __init__(self, sample_seconds: int):
        self.sample_seconds = sample_seconds
        self.tick = 0
        self.odometer_km = 128447.1
        self.fuel_sensor_percent = 100.0

    def read(self) -> dict:
        phase = self.tick / 4
        base_speed = 42 + math.sin(phase) * 28
        stop_cycle = self.tick % 18
        speed_kph = 0 if stop_cycle in (0, 1, 2) else max(8, round(base_speed + random.uniform(-6, 6), 1))
        rpm = round(760 + speed_kph * 31 + random.uniform(-80, 120))
        engine_state = "idle" if speed_kph == 0 and rpm > 500 else "driving"
        distance_increment_km = round((speed_kph * self.sample_seconds) / 3600, 4)
        self.odometer_km = round(self.odometer_km + distance_increment_km, 3)
        if self.tick > 3:
            self.fuel_sensor_percent = max(0, round(self.fuel_sensor_percent - distance_increment_km * 0.24, 2))
        fuel_sensor_state = "full" if self.fuel_sensor_percent >= 98 and self.tick < 5 else "descending"
        self.tick += 1

        return {
            "timestamp": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
            "speedKph": speed_kph,
            "rpm": max(720, rpm),
            "engineState": engine_state,
            "sampleSeconds": self.sample_seconds,
            "distanceIncrementKm": distance_increment_km,
            "estimatedOdometerKm": self.odometer_km,
            "engineLoadPercent": 18 if speed_kph == 0 else min(88, round(25 + speed_kph * 0.72, 1)),
            "coolantTempC": round(87 + math.sin(phase / 2) * 4, 1),
            "batteryVoltage": round(13.8 + random.uniform(-0.2, 0.2), 2),
            "fuelSensorPercent": self.fuel_sensor_percent,
            "fuelSensorState": fuel_sensor_state,
            "obd": {
                "adapter": "ELM327 USB",
                "protocol": "ISO 15765-4 CAN",
                "pids": {
                    "010C": "engine_rpm",
                    "010D": "vehicle_speed",
                    "0104": "engine_load",
                    "0105": "coolant_temperature",
                    "ATRV": "battery_voltage",
                },
                "mode": "mock-realistic",
            },
        }


def create_provider(provider_name: str, sample_seconds: int):
    if provider_name != "mock":
        raise ValueError(f"Unsupported provider '{provider_name}'. Current MVP supports 'mock'.")
    return MockTelemetryProvider(sample_seconds)
