# SmartFuel Raspberry Pi Telemetry Service

This service is the future Raspberry Pi edge process. For the current portfolio MVP it runs in mock mode and uploads generated telemetry to the Express backend.

## Run Locally

```bash
python -m smartfuel_pi.telemetry_service
```

Default backend:

```text
http://localhost:4000
```

On the Raspberry Pi, set environment variables in a systemd service or `.env` loader.

## Behavior

- Generates mock speed, RPM, engine load, coolant temperature, and distance increments.
- Sends telemetry to `POST /api/telemetry`.
- Authenticates with `X-Device-Token`.
- Caches failed uploads to `cache/telemetry-cache.jsonl`.
- Flushes cached telemetry when the backend is reachable again.
- Logs failures to `logs/telemetry.log`.

## Future OBD Provider

The mock provider is isolated in `smartfuel_pi/providers.py`. A real provider can later read from a USB OBD-II adapter using `python-OBD` and return the same telemetry payload shape.
