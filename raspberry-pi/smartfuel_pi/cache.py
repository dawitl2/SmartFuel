import json
from pathlib import Path
from typing import Iterable


class TelemetryCache:
    def __init__(self, path: Path):
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def append(self, payload: dict) -> None:
        with self.path.open("a", encoding="utf-8") as file:
            file.write(json.dumps(payload, separators=(",", ":")) + "\n")

    def read_all(self) -> list[dict]:
        if not self.path.exists():
            return []

        items: list[dict] = []
        with self.path.open("r", encoding="utf-8") as file:
            for line in file:
                line = line.strip()
                if not line:
                    continue
                items.append(json.loads(line))
        return items

    def replace(self, items: Iterable[dict]) -> None:
        remaining = list(items)
        if not remaining:
            self.clear()
            return

        with self.path.open("w", encoding="utf-8") as file:
            for item in remaining:
                file.write(json.dumps(item, separators=(",", ":")) + "\n")

    def clear(self) -> None:
        if self.path.exists():
            self.path.unlink()
