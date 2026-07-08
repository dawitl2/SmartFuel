import json
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen


def _value(value):
    if value is None:
        return {"nullValue": None}
    if isinstance(value, bool):
        return {"booleanValue": value}
    if isinstance(value, int):
        return {"integerValue": value}
    if isinstance(value, float):
        return {"doubleValue": value}
    if isinstance(value, list):
        return {"arrayValue": {"values": [_value(item) for item in value]}}
    if isinstance(value, dict):
        return {"mapValue": {"fields": {key: _value(item) for key, item in value.items()}}}
    return {"stringValue": str(value)}


def _fields(payload):
    return {"fields": {key: _value(value) for key, value in payload.items()}}


class FirestoreTelemetryClient:
    def __init__(self, project_id: str, api_key: str, vehicle_id: str, timeout_seconds: int = 8):
        if not project_id or not api_key or not vehicle_id:
            raise RuntimeError("Firestore target requires FIREBASE_PROJECT_ID, FIREBASE_WEB_API_KEY, and SMARTFUEL_FIRESTORE_VEHICLE_ID.")

        self.project_id = project_id
        self.api_key = api_key
        self.vehicle_id = vehicle_id
        self.timeout_seconds = timeout_seconds
        self.base_url = f"https://firestore.googleapis.com/v1/projects/{project_id}/databases/(default)/documents"

    def send_telemetry(self, payload: dict) -> None:
        self._post(f"vehicles/{self.vehicle_id}/telemetry_logs", payload)
        self._patch(
            f"vehicles/{self.vehicle_id}/runtime/current_status",
            {
                "speedKph": payload.get("speedKph", 0),
                "rpm": payload.get("rpm", 0),
                "engineState": payload.get("engineState", "unknown"),
                "engineLoadPercent": payload.get("engineLoadPercent", 0),
                "coolantTempC": payload.get("coolantTempC", 0),
                "batteryVoltage": payload.get("batteryVoltage"),
                "estimatedOdometerKm": payload.get("estimatedOdometerKm"),
                "timestamp": payload.get("timestamp"),
            },
        )

    def _post(self, path: str, payload: dict) -> None:
        self._request(f"{self.base_url}/{path}?key={self.api_key}", "POST", payload)

    def _patch(self, path: str, payload: dict) -> None:
        masks = "&".join(f"updateMask.fieldPaths={quote(key)}" for key in payload)
        self._request(f"{self.base_url}/{path}?key={self.api_key}&{masks}", "PATCH", payload)

    def _request(self, url: str, method: str, payload: dict) -> None:
        body = json.dumps(_fields(payload)).encode("utf-8")
        request = Request(url, data=body, method=method, headers={"Content-Type": "application/json"})
        try:
            with urlopen(request, timeout=self.timeout_seconds) as response:
                if response.status >= 400:
                    raise RuntimeError(f"Firestore returned HTTP {response.status}")
        except HTTPError as exc:
            raise RuntimeError(f"Firestore returned HTTP {exc.code}: {exc.reason}") from exc
        except URLError as exc:
            raise RuntimeError(f"Firestore unavailable: {exc.reason}") from exc
