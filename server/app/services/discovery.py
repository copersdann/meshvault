from __future__ import annotations

import ipaddress
import logging
import socket
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

import httpx

logger = logging.getLogger("meshvault.discovery")

MOONRAKER_PORT = 7125
SCAN_TIMEOUT = 0.6
MAX_WORKERS = 48
CACHE_TTL = 60

_cache_lock = threading.Lock()
_cache: tuple[float, list[dict]] = (0.0, [])


def local_ip() -> str:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        sock.close()


def _probe(host: str) -> dict | None:
    try:
        with socket.create_connection((host, MOONRAKER_PORT), timeout=SCAN_TIMEOUT):
            pass
    except OSError:
        return None

    try:
        response = httpx.get(
            f"http://{host}:{MOONRAKER_PORT}/server/info", timeout=SCAN_TIMEOUT
        )
        response.raise_for_status()
        result = response.json().get("result", {})
        name = result.get("hostname") or result.get("machine") or host
        return {
            "host": f"{host}:{MOONRAKER_PORT}",
            "name": str(name),
            "version": str(result.get("version", "")),
            "kind": "moonraker",
        }
    except Exception as exc:
        logger.debug("Printer probe failed for %s: %s", host, exc)
        return None


def discover_printers() -> list[dict]:
    """Find Moonraker printers on the local /24 subnet."""
    global _cache
    now = time.monotonic()
    with _cache_lock:
        if _cache[1] and now - _cache[0] < CACHE_TTL:
            return _cache[1]

    ip = local_ip()
    try:
        network = ipaddress.ip_network(f"{ip}/24", strict=False)
        hosts = [str(host) for host in network.hosts()]
    except ValueError:
        hosts = []

    found: list[dict] = []
    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as pool:
        futures = [pool.submit(_probe, host) for host in hosts]
        for future in as_completed(futures):
            result = future.result()
            if result:
                found.append(result)

    found.sort(key=lambda item: item["host"])
    with _cache_lock:
        _cache = (now, found)
    return found
