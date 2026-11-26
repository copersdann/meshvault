from __future__ import annotations

from typing import Any

import httpx


class MoonrakerError(Exception):
    pass


def normalize_host(host: str) -> str:
    value = host.strip().rstrip("/")
    if not value:
        raise MoonrakerError("Printer host is empty")
    if not value.startswith(("http://", "https://")):
        value = f"http://{value}"
    return value


class MoonrakerClient:
    def __init__(self, host: str, api_key: str | None = None) -> None:
        self.base_url = normalize_host(host)
        self.api_key = api_key

    async def _request(self, method: str, path: str, **kwargs: Any) -> dict:
        headers = dict(kwargs.pop("headers", {}) or {})
        if self.api_key:
            headers["X-Api-Key"] = self.api_key

        try:
            async with httpx.AsyncClient(
                base_url=self.base_url,
                timeout=20.0,
                headers=headers,
                follow_redirects=True,
            ) as client:
                response = await client.request(method, path, **kwargs)
        except httpx.HTTPError as exc:
            raise MoonrakerError(f"Cannot reach printer: {exc}") from exc

        if response.status_code >= 400:
            detail = ""
            try:
                payload = response.json()
                detail = str(payload.get("error") or payload.get("detail") or "")
            except ValueError:
                detail = response.text[:200]
            raise MoonrakerError(
                f"Moonraker returned HTTP {response.status_code}: {detail or response.reason_phrase}"
            )

        try:
            return response.json()
        except ValueError as exc:
            raise MoonrakerError("Moonraker returned invalid JSON") from exc

    async def server_info(self) -> dict:
        return await self._request("GET", "/server/info")

    async def printer_info(self) -> dict:
        return await self._request("GET", "/printer/info")

    async def status(self) -> dict:
        return await self._request(
            "GET",
            "/printer/objects/query",
            params={
                "print_stats": "",
                "virtual_sdcard": "",
                "heater_bed": "",
                "extruder": "",
                "display_status": "",
            },
        )

    async def upload_gcode(self, filename: str, content: bytes) -> dict:
        return await self._request(
            "POST",
            "/server/files/upload",
            files={"file": (filename, content, "application/octet-stream")},
            data={"root": "gcodes"},
        )

    async def start_print(self, filename: str) -> dict:
        return await self._request(
            "POST",
            "/printer/print/start",
            params={"filename": filename},
        )

    async def pause(self) -> dict:
        return await self._request("POST", "/printer/print/pause")

    async def resume(self) -> dict:
        return await self._request("POST", "/printer/print/resume")

    async def cancel(self) -> dict:
        return await self._request("POST", "/printer/print/cancel")


def parse_status(payload: dict) -> dict:
    status = payload.get("result", {}).get("status", {})
    print_stats = status.get("print_stats", {}) or {}
    sdcard = status.get("virtual_sdcard", {}) or {}
    bed = status.get("heater_bed", {}) or {}
    extruder = status.get("extruder", {}) or {}

    def to_float(value: Any) -> float | None:
        try:
            return round(float(value), 2)
        except (TypeError, ValueError):
            return None

    progress = to_float(sdcard.get("progress"))
    if progress is not None:
        progress = round(progress * 100, 1)

    return {
        "state": str(print_stats.get("state", "unknown")),
        "filename": print_stats.get("filename"),
        "progress": progress or 0.0,
        "bed_temp": to_float(bed.get("temperature")),
        "bed_target": to_float(bed.get("target")),
        "nozzle_temp": to_float(extruder.get("temperature")),
        "nozzle_target": to_float(extruder.get("target")),
        "message": str(print_stats.get("message") or print_stats.get("state_message") or ""),
    }


def map_job_status(state: str, progress: float) -> str | None:
    mapping = {
        "printing": "printing",
        "paused": "paused",
        "complete": "completed",
        "error": "failed",
        "cancelled": "cancelled",
        "standby": None,
    }
    if state in mapping:
        status = mapping[state]
        if status is None and progress >= 99.0:
            return "completed"
        return status
    return None
