from __future__ import annotations

import logging
import tempfile
from pathlib import Path

from sqlalchemy.orm import Session

from ..db import SessionLocal
from ..models import Blob, ModelVersion, utcnow
from .geometry import GCODE_EXTENSIONS, GeometryError, analyze_file
from .storage import storage
from .thumbnails import generate_thumbnail

logger = logging.getLogger("meshvault.processor")


def process_version(version_id: str) -> None:
    db: Session = SessionLocal()
    try:
        version = db.get(ModelVersion, version_id)
        if version is None:
            return

        model = version.model
        original = db.get(Blob, version.original_blob_id)
        if original is None:
            raise GeometryError("Original file is missing from storage")

        version.status = "processing"
        version.error = None
        db.commit()

        extension = Path(version.source_filename).suffix.lower() or f".{version.source_format}"
        source_path = storage.path_for(original)

        result = analyze_file(source_path, extension)

        glb_blob_id = version.glb_blob_id
        glb_bytes: bytes | None = None
        if result.glb:
            glb_bytes = result.glb
            if extension == ".glb":
                glb_blob_id = original.id
            else:
                glb_blob = storage.save_bytes(db, result.glb, "model/gltf-binary")
                glb_blob_id = glb_blob.id

        thumb_blob_id = version.thumb_blob_id
        with tempfile.TemporaryDirectory() as tmp_dir:
            thumb_path = Path(tmp_dir) / "thumb.png"
            generate_thumbnail(glb_bytes or b"", model.name, thumb_path)
            if thumb_path.is_file() and thumb_path.stat().st_size > 0:
                thumb_blob = storage.save_bytes(db, thumb_path.read_bytes(), "image/png")
                thumb_blob_id = thumb_blob.id

        if extension in GCODE_EXTENSIONS:
            version.gcode_blob_id = original.id
            version.gcode_filename = version.source_filename

        version.glb_blob_id = glb_blob_id
        version.thumb_blob_id = thumb_blob_id
        version.bbox_x = result.bbox_x
        version.bbox_y = result.bbox_y
        version.bbox_z = result.bbox_z
        version.volume = result.volume
        version.triangles = result.triangles
        version.watertight = result.watertight
        version.units = result.units
        version.status = "ready"
        version.processed_at = utcnow()
        db.commit()
        logger.info("Processed model version %s", version_id)
    except Exception as exc:
        logger.exception("Failed to process model version %s", version_id)
        try:
            db.rollback()
            version = db.get(ModelVersion, version_id)
            if version is not None:
                version.status = "failed"
                version.error = str(exc)[:4000]
                version.processed_at = utcnow()
                db.commit()
        except Exception:
            logger.exception("Could not store failure state for %s", version_id)
    finally:
        db.close()
