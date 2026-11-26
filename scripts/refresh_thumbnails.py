"""Перегенерировать миниатюры из уже готовых GLB-блобов (без повторного слайсинга)."""

from __future__ import annotations

import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

from sqlalchemy import select

from app.db import SessionLocal
from app.models import Blob, ModelVersion
from app.services.storage import storage
from app.services.thumbnails import generate_thumbnail


def main() -> None:
    db = SessionLocal()
    try:
        versions = list(
            db.scalars(select(ModelVersion).where(ModelVersion.glb_blob_id.is_not(None))).all()
        )
        print(f"refreshing {len(versions)} thumbnails", flush=True)
        updated = 0
        for index, version in enumerate(versions, 1):
            try:
                glb = db.get(Blob, version.glb_blob_id)
                if glb is None:
                    continue
                glb_bytes = storage.path_for(glb).read_bytes()
                with tempfile.TemporaryDirectory() as tmp_dir:
                    target = Path(tmp_dir) / "thumb.png"
                    name = version.model.name if version.model else "3D model"
                    if generate_thumbnail(glb_bytes, name, target):
                        thumb = storage.save_bytes(db, target.read_bytes(), "image/png")
                        version.thumb_blob_id = thumb.id
                        db.commit()
                        updated += 1
            except Exception as exc:
                db.rollback()
                print(f"{index}: error {exc}", flush=True)
            if index % 50 == 0:
                print(f"processed {index}/{len(versions)}", flush=True)
        print(f"DONE updated={updated}", flush=True)
    finally:
        db.close()


if __name__ == "__main__":
    main()
