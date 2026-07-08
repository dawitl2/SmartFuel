import math
import random
from datetime import UTC, datetime


class MockTelemetryProvider:
    def __init__(self, sample_seconds: int):
        self.sample_seconds = sample_seconds
        self.tick = 0
        self.odometer_km = 128447.1

    def read(self) -> dict:
        phase = self.tick / 4
        base_speed = 42 + math.sin(phase) * 28
        stop_cycle = self.tick % 18
        speed_kph = 0 if stop_cycle in (0, 1, 2) else max(8, round(base_speed + random.uniform(-6, 6), 1))
        rpm = round(760 + speed_kph * 31 + random.uniform(-80, 120))
        distance_increment_km = round((speed_kph * self.sample_seconds) / 3600, 4)
        self.odometer_km = round(self.odometer_km + distance_increment_km, 3)
        self.tick += 1

        return {
            "timestamp": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
            "speedKph": speed_kph,
            "rpm": max(720, rpm),
            "sampleSeconds": self.sample_seconds,
            "distanceIncrementKm": distance_increment_km,
            "estimatedOdometerKm": self.odometer_km,
            "engineLoadPercent": 18 if speed_kph == 0 else min(88, round(25 + speed_kph * 0.72, 1)),
            "coolantTempC": round(87 + math.sin(phase / 2) * 4, 1),
            "batteryVoltage": round(13.8 + random.uniform(-0.2, 0.2), 2),
        }


def create_provider(provider_name: str, sample_seconds: int):
    if provider_name != "mock":
        raise ValueError(f"Unsupported provider '{provider_name}'. Current MVP supports 'mock'.")
    return MockTelemetryProvider(sample_seconds)
