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
    upload_target: str
    firebase_project_id: str
    firebase_web_api_key: str
    firestore_vehicle_id: str


def load_config() -> Config:
    sample_seconds = int(os.getenv("SMARTFUEL_SAMPLE_SECONDS", "5"))

    return Config(
        api_url=os.getenv("SMARTFUEL_API_URL", "http://localhost:4000").rstrip("/"),
        device_token=os.getenv("SMARTFUEL_DEVICE_TOKEN", "smartfuel-demo-device-token"),
        sample_seconds=max(1, sample_seconds),
        cache_file=BASE_DIR / os.getenv("SMARTFUEL_CACHE_FILE", "cache/telemetry-cache.jsonl"),
        log_file=BASE_DIR / os.getenv("SMARTFUEL_LOG_FILE", "logs/telemetry.log"),
        provider=os.getenv("SMARTFUEL_PROVIDER", "mock"),
        upload_target=os.getenv("SMARTFUEL_UPLOAD_TARGET", "backend"),
        firebase_project_id=os.getenv("FIREBASE_PROJECT_ID", ""),
        firebase_web_api_key=os.getenv("FIREBASE_WEB_API_KEY", ""),
        firestore_vehicle_id=os.getenv("SMARTFUEL_FIRESTORE_VEHICLE_ID", "demo-vehicle"),
    )
