import os
from dataclasses import dataclass
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Config:
    api_url: str
    device_token: str
    sample_seconds: int
    cache_file: Path
    log_file: Path
    provider: str


def load_config() -> Config:
    sample_seconds = int(os.getenv("SMARTFUEL_SAMPLE_SECONDS", "5"))

    return Config(
        api_url=os.getenv("SMARTFUEL_API_URL", "http://localhost:4000").rstrip("/"),
        device_token=os.getenv("SMARTFUEL_DEVICE_TOKEN", "smartfuel-demo-device-token"),
        sample_seconds=max(1, sample_seconds),
        cache_file=BASE_DIR / os.getenv("SMARTFUEL_CACHE_FILE", "cache/telemetry-cache.jsonl"),
        log_file=BASE_DIR / os.getenv("SMARTFUEL_LOG_FILE", "logs/telemetry.log"),
        provider=os.getenv("SMARTFUEL_PROVIDER", "mock"),
    )
