import logging
import signal
import time

from .cache import TelemetryCache
from .client import SmartFuelClient
from .config import load_config
from .providers import create_provider


running = True


def configure_logging(log_file):
    log_file.parent.mkdir(parents=True, exist_ok=True)
    logging.basicConfig(
        filename=log_file,
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )
    console = logging.StreamHandler()
    console.setLevel(logging.INFO)
    console.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    logging.getLogger().addHandler(console)


def stop(_signum, _frame):
    global running
    running = False


def flush_cache(client: SmartFuelClient, cache: TelemetryCache) -> None:
    cached = cache.read_all()
    if not cached:
        return

    remaining = []
    for payload in cached:
        try:
            client.send_telemetry(payload)
        except RuntimeError:
            remaining.append(payload)

    cache.replace(remaining)
    sent = len(cached) - len(remaining)
    if sent:
        logging.info("Flushed %s cached telemetry sample(s)", sent)


def main() -> None:
    config = load_config()
    configure_logging(config.log_file)

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)

    client = SmartFuelClient(config.api_url, config.device_token)
    cache = TelemetryCache(config.cache_file)
    provider = create_provider(config.provider, config.sample_seconds)

    logging.info("SmartFuel telemetry service started with provider=%s api=%s", config.provider, config.api_url)

    while running:
        payload = provider.read()

        try:
            flush_cache(client, cache)
            client.send_telemetry(payload)
            logging.info("Uploaded telemetry speed=%s rpm=%s distance=%s", payload["speedKph"], payload["rpm"], payload["distanceIncrementKm"])
        except RuntimeError as exc:
            cache.append(payload)
            logging.warning("Upload failed and telemetry was cached: %s", exc)

        time.sleep(config.sample_seconds)

    logging.info("SmartFuel telemetry service stopped")


if __name__ == "__main__":
    main()
