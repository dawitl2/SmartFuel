import json
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


class SmartFuelClient:
    def __init__(self, api_url: str, device_token: str, timeout_seconds: int = 8):
        self.api_url = api_url
        self.device_token = device_token
        self.timeout_seconds = timeout_seconds

    def send_telemetry(self, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        request = Request(
            f"{self.api_url}/api/telemetry",
            data=body,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "X-Device-Token": self.device_token,
            },
        )

        try:
            with urlopen(request, timeout=self.timeout_seconds) as response:
                if response.status >= 400:
                    raise RuntimeError(f"Backend returned HTTP {response.status}")
        except HTTPError as exc:
            raise RuntimeError(f"Backend returned HTTP {exc.code}: {exc.reason}") from exc
        except URLError as exc:
            raise RuntimeError(f"Backend unavailable: {exc.reason}") from exc
