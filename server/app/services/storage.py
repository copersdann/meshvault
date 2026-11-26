from __future__ import annotations

import hashlib
import os
import tempfile
from pathlib import Path

from fastapi import UploadFile
from sqlalchemy.orm import Session

from ..config import get_settings
from ..models import Blob

settings = get_settings()


class StorageError(Exception):
    pass


class UploadTooLarge(StorageError):
    pass


class Storage:
    def __init__(self, root: Path) -> None:
        self.root = root.resolve()
        self.blob_root = self.root / "blobs"
        self.tmp_root = self.root / "tmp"
        self.blob_root.mkdir(parents=True, exist_ok=True)
        self.tmp_root.mkdir(parents=True, exist_ok=True)

    def relative_path(self, sha256: str) -> str:
        return f"blobs/{sha256[:2]}/{sha256[2:4]}/{sha256}"

    def path_for(self, blob: Blob) -> Path:
        path = Path(blob.path)
        return path if path.is_absolute() else self.root / path

    def _finalize(self, db: Session, tmp_path: Path, sha256: str, mime: str, size: int) -> Blob:
        blob = db.get(Blob, sha256)
        final_path = self.root / self.relative_path(sha256)
        final_path.parent.mkdir(parents=True, exist_ok=True)

        if blob is not None and final_path.exists():
            tmp_path.unlink(missing_ok=True)
            return blob

        os.replace(tmp_path, final_path)

        if blob is None:
            blob = Blob(
                id=sha256,
                path=self.relative_path(sha256),
                mime=mime or "application/octet-stream",
                size=size,
            )
            db.add(blob)
        else:
            blob.path = self.relative_path(sha256)
            blob.mime = mime or blob.mime
            blob.size = size
        db.flush()
        return blob

    def save_bytes(self, db: Session, data: bytes, mime: str) -> Blob:
        sha256 = hashlib.sha256(data).hexdigest()
        existing = db.get(Blob, sha256)
        final_path = self.root / self.relative_path(sha256)
        if existing is not None and final_path.exists():
            return existing

        final_path.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp_name = tempfile.mkstemp(dir=self.tmp_root, suffix=".part")
        try:
            with os.fdopen(fd, "wb") as handle:
                handle.write(data)
            return self._finalize(db, Path(tmp_name), sha256, mime, len(data))
        except Exception:
            Path(tmp_name).unlink(missing_ok=True)
            raise

    async def save_upload(
        self,
        db: Session,
        upload: UploadFile,
        max_bytes: int,
        chunk_size: int = 1024 * 1024,
    ) -> Blob:
        digest = hashlib.sha256()
        size = 0
        fd, tmp_name = tempfile.mkstemp(dir=self.tmp_root, suffix=".part")
        try:
            with os.fdopen(fd, "wb") as handle:
                while True:
                    chunk = await upload.read(chunk_size)
                    if not chunk:
                        break
                    size += len(chunk)
                    if size > max_bytes:
                        raise UploadTooLarge(
                            f"File exceeds the {max_bytes // (1024 * 1024)} MB limit"
                        )
                    digest.update(chunk)
                    handle.write(chunk)

            sha256 = digest.hexdigest()
            return self._finalize(
                db,
                Path(tmp_name),
                sha256,
                upload.content_type or "application/octet-stream",
                size,
            )
        except Exception:
            Path(tmp_name).unlink(missing_ok=True)
            raise


storage = Storage(settings.data_dir)
