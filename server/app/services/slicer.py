from __future__ import annotations

import glob
import json
import logging
import shutil
import subprocess
from functools import lru_cache
from pathlib import Path

from sqlalchemy.orm import Session

from ..config import get_settings
from ..db import SessionLocal
from ..models import AppSetting, Blob, ModelVersion
from .storage import storage

logger = logging.getLogger("meshvault.slicer")

CUSTOM_SLICERS_KEY = "slicer_custom_paths"


class SlicerError(Exception):
    pass


def _load_custom_slicers() -> list[dict]:
    try:
        with SessionLocal() as db:
            row = db.get(AppSetting, CUSTOM_SLICERS_KEY)
            if row is None:
                return []
            data = json.loads(row.value or "[]")
            return [item for item in data if isinstance(item, dict) and item.get("path")]
    except Exception:
        logger.exception("Could not read custom slicers")
        return []


def _save_custom_slicers(items: list[dict]) -> None:
    with SessionLocal() as db:
        row = db.get(AppSetting, CUSTOM_SLICERS_KEY)
        value = json.dumps(items, ensure_ascii=False)
        if row is None:
            db.add(AppSetting(key=CUSTOM_SLICERS_KEY, value=value))
        else:
            row.value = value
        db.commit()
    find_slicers.cache_clear()


def add_custom_slicer(name: str, path: str) -> list[dict]:
    executable = Path(path).expanduser()
    if not executable.is_file():
        raise SlicerError("Указанный путь не является файлом")
    items = _load_custom_slicers()
    key = str(executable.resolve()).lower()
    items = [item for item in items if str(Path(item["path"]).resolve()).lower() != key]
    items.append({"name": (name or executable.stem).strip()[:128], "path": str(executable)})
    _save_custom_slicers(items)
    return find_slicers()


def remove_custom_slicer(path: str) -> list[dict]:
    key = str(Path(path).resolve()).lower()
    items = [
        item
        for item in _load_custom_slicers()
        if str(Path(item["path"]).resolve()).lower() != key
    ]
    _save_custom_slicers(items)
    return find_slicers()


# id, display name, PATH command names, install-location glob patterns
SLICER_SPECS: list[tuple[str, str, list[str], list[str]]] = [
    (
        "prusaslicer",
        "PrusaSlicer",
        ["prusa-slicer", "prusa-slicer-console"],
        [
            r"C:\Program Files\PrusaSlicer*\prusa-slicer.exe",
            r"C:\Program Files\PrusaSlicer\prusa-slicer.exe",
            "/usr/bin/prusa-slicer",
            "/usr/local/bin/prusa-slicer",
            "/Applications/PrusaSlicer.app/Contents/MacOS/PrusaSlicer",
        ],
    ),
    (
        "orcaslicer",
        "OrcaSlicer",
        ["orca-slicer", "OrcaSlicer"],
        [
            r"C:\Program Files\OrcaSlicer*\orca-slicer.exe",
            r"C:\Program Files\OrcaSlicer*\OrcaSlicer.exe",
            r"C:\Program Files\OrcaSlicer\orca-slicer.exe",
            "/usr/bin/orca-slicer",
            "/Applications/OrcaSlicer.app/Contents/MacOS/OrcaSlicer",
        ],
    ),
    (
        "superslicer",
        "SuperSlicer",
        ["superslicer", "superslicer-console"],
        [
            r"C:\Program Files\SuperSlicer*\superslicer.exe",
            "/usr/bin/superslicer",
        ],
    ),
    (
        "bambustudio",
        "Bambu Studio",
        ["bambu-studio", "BambuStudio"],
        [
            r"C:\Program Files\BambuStudio*\bambu-studio.exe",
            r"C:\Program Files\BambuStudio*\BambuStudio.exe",
            "/Applications/BambuStudio.app/Contents/MacOS/BambuStudio",
        ],
    ),
    (
        "cura",
        "UltiMaker Cura",
        ["Cura", "UltiMaker-Cura"],
        [
            r"C:\Program Files\UltiMaker Cura *\UltiMaker-Cura.exe",
            r"C:\Program Files\UltiMaker Cura *\Cura.exe",
            r"C:\Program Files (x86)\Cura *\Cura.exe",
            "/usr/bin/cura",
            "/Applications/UltiMaker Cura.app/Contents/MacOS/UltiMaker-Cura",
        ],
    ),
    (
        "flashforge",
        "Flashforge Flash Studio",
        [],
        [
            r"C:\Program Files\Flashforge\Flash Studio Desktop\flash studio.exe",
            r"C:\Program Files\Flashforge*\Flash Studio*\flash studio.exe",
        ],
    ),
]


@lru_cache(maxsize=1)
def find_slicers() -> list[dict]:
    results: list[dict] = []
    seen: set[str] = set()

    configured = (get_settings().slicer_path or "").strip()
    if configured:
        path = Path(configured).expanduser()
        if path.is_file():
            results.append({"id": "custom", "name": path.stem, "path": str(path)})
            seen.add(str(path).lower())
        else:
            logger.warning("Configured slicer path is not a file: %s", configured)

    for index, item in enumerate(_load_custom_slicers(), start=1):
        path = Path(str(item["path"])).expanduser()
        if path.is_file() and str(path).lower() not in seen:
            seen.add(str(path).lower())
            results.append(
                {
                    "id": f"custom-{index}",
                    "name": str(item.get("name") or path.stem),
                    "path": str(path),
                }
            )

    for slicer_id, name, commands, patterns in SLICER_SPECS:
        found: str | None = None
        for command in commands:
            on_path = shutil.which(command)
            if on_path:
                found = on_path
                break
        if not found:
            candidates: list[str] = []
            for pattern in patterns:
                candidates.extend(glob.glob(pattern))
            if candidates:
                found = max(candidates)
        if found and found.lower() not in seen:
            seen.add(found.lower())
            results.append({"id": slicer_id, "name": name, "path": found})

    return results


def materialize_original(db: Session, version: ModelVersion) -> Path:
    """Copy the original blob to a file with its real name/extension for the slicer."""
    blob = db.get(Blob, version.original_blob_id)
    if blob is None:
        raise SlicerError("Original file is missing from storage")
    source = storage.path_for(blob)
    if not source.is_file():
        raise SlicerError("Original file is missing on disk")

    dest_dir = storage.root / "slicer" / version.sha256
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = dest_dir / Path(version.source_filename).name
    if not dest.is_file() or dest.stat().st_size != blob.size:
        shutil.copyfile(source, dest)
    return dest


def open_in_slicer(db: Session, version: ModelVersion, slicer_id: str | None = None) -> dict:
    slicers = find_slicers()
    if not slicers:
        raise SlicerError(
            "No slicer found. Install PrusaSlicer/OrcaSlicer/Cura/Flashforge "
            "or set MESHVault_SLICER_PATH."
        )

    if slicer_id:
        chosen = next((item for item in slicers if item["id"] == slicer_id), None)
        if chosen is None:
            raise SlicerError(f"Unknown slicer id: {slicer_id}")
    else:
        chosen = slicers[0]

    file_path = materialize_original(db, version)
    try:
        subprocess.Popen([chosen["path"], str(file_path)], close_fds=True)
    except OSError as exc:
        raise SlicerError(f"Could not launch slicer: {exc}") from exc

    logger.info("Opened %s in %s", file_path.name, chosen["name"])
    return {"slicer": chosen["name"], "file": str(file_path)}
