from __future__ import annotations

from pathlib import Path

import trimesh
from sqlalchemy.orm import Session

from ..models import Blob, ModelVersion
from .storage import storage

EXPORT_FORMATS: dict[str, tuple[str, str]] = {
    "stl": ("model/stl", ".stl"),
    "obj": ("text/plain", ".obj"),
    "glb": ("model/gltf-binary", ".glb"),
    "ply": ("application/octet-stream", ".ply"),
    "3mf": ("model/3mf", ".3mf"),
}


class ExportError(Exception):
    pass


def export_version(db: Session, version: ModelVersion, fmt: str) -> tuple[bytes, str, str]:
    """Return (bytes, media_type, extension) for a converted model version."""
    fmt = fmt.lower().lstrip(".")
    if fmt not in EXPORT_FORMATS:
        supported = ", ".join(sorted(EXPORT_FORMATS))
        raise ExportError(f"Unsupported export format '{fmt}'. Supported: {supported}")

    blob = db.get(Blob, version.glb_blob_id or version.original_blob_id)
    if blob is None:
        raise ExportError("Blob is missing")
    path = storage.path_for(blob)
    if not path.is_file():
        raise ExportError("File is missing on disk")

    media_type, extension = EXPORT_FORMATS[fmt]
    if fmt == "glb":
        if version.glb_blob_id:
            return path.read_bytes(), media_type, extension
        raise ExportError("GLB preview is not available for this version")

    try:
        if version.glb_blob_id:
            scene = trimesh.load(path, file_type="glb", force="scene")
        else:
            original_ext = f".{version.source_format.lower()}"
            scene = trimesh.load(path, file_type=original_ext, force="scene")
        mesh = scene.to_geometry()
        data = mesh.export(file_type=fmt)
    except Exception as exc:
        raise ExportError(f"Could not export {fmt}: {exc}") from exc

    if isinstance(data, str):
        data = data.encode("utf-8")
    return bytes(data), media_type, extension


def export_filename(version: ModelVersion, extension: str) -> str:
    stem = Path(version.source_filename).stem or f"model-v{version.version}"
    return f"{stem}-v{version.version}{extension}"
