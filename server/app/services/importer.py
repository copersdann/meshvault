from __future__ import annotations

import hashlib
import json
import logging
import mimetypes
from collections.abc import Callable
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import (
    AppSetting,
    Blob,
    Collection,
    ImportFile,
    Model,
    ModelVersion,
    Tag,
    utcnow,
)
from .geometry import SUPPORTED_EXTENSIONS
from .storage import storage

logger = logging.getLogger("meshvault.importer")


def get_setting(db: Session, key: str, default: str = "") -> str:
    row = db.get(AppSetting, key)
    return row.value if row else default


def set_setting(db: Session, key: str, value: str) -> None:
    row = db.get(AppSetting, key)
    if row is None:
        db.add(AppSetting(key=key, value=value))
    else:
        row.value = value
    db.commit()


def _get_or_create_tag(db: Session, name: str) -> Tag:
    name = name.strip()[:64]
    if not name:
        raise ValueError("empty tag")
    tag = db.scalar(select(Tag).where(Tag.name == name))
    if tag is None:
        tag = Tag(name=name)
        db.add(tag)
        db.flush()
    return tag


def _get_or_create_collection(db: Session, name: str) -> Collection:
    name = name.strip()[:128]
    collection = db.scalar(select(Collection).where(Collection.name == name))
    if collection is None:
        collection = Collection(name=name, description="Импортировано из каталога")
        db.add(collection)
        db.flush()
    return collection


def scan_directory(
    db: Session,
    directory: str,
    on_version: Callable[[str], None] | None = None,
    dry_run: bool = False,
) -> dict:
    stats: dict = {
        "found": 0,
        "added": 0,
        "skipped": 0,
        "duplicates": 0,
        "errors": 0,
        "messages": [],
    }
    seen_hashes: set[str] = set()
    root = Path(directory).expanduser()

    if not root.is_dir():
        raise FileNotFoundError(f"Directory does not exist: {directory}")

    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        extension = path.suffix.lower()
        if extension not in SUPPORTED_EXTENSIONS:
            continue

        stats["found"] += 1
        source_key = str(path.resolve())

        known = db.get(ImportFile, source_key)
        if known is not None:
            stats["skipped"] += 1
            if known.duplicate_of:
                stats["duplicates"] += 1
            continue

        try:
            data = path.read_bytes()
            sha256 = hashlib.sha256(data).hexdigest()

            if db.get(Blob, sha256) is not None:
                original_id = db.scalar(
                    select(ModelVersion.model_id)
                    .where(ModelVersion.sha256 == sha256)
                    .limit(1)
                )
                if not dry_run:
                    db.add(
                        ImportFile(
                            source_path=source_key,
                            sha256=sha256,
                            model_id="",
                            duplicate_of=original_id or "",
                        )
                    )
                stats["skipped"] += 1
                stats["duplicates"] += 1
                continue

            if dry_run:
                if sha256 in seen_hashes:
                    stats["skipped"] += 1
                    stats["duplicates"] += 1
                else:
                    stats["added"] += 1
                    seen_hashes.add(sha256)
                continue

            mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
            blob = storage.save_bytes(db, data, mime)

            relative = path.relative_to(root)
            parts = relative.parts
            name = path.stem[:255] or "Untitled"

            model = Model(
                name=name,
                description=f"Импорт: {relative.as_posix()}",
            )
            model.refresh_search_text()
            db.add(model)
            db.flush()

            tags = [part[:64] for part in parts[:-1] if part.strip()]
            tags.append(extension.lstrip("."))
            for tag_name in dict.fromkeys(tags):
                try:
                    model.tags.append(_get_or_create_tag(db, tag_name))
                except ValueError:
                    continue

            if len(parts) > 1:
                model.collections.append(_get_or_create_collection(db, parts[0]))

            version = ModelVersion(
                model_id=model.id,
                version=1,
                original_blob_id=blob.id,
                source_format=extension.lstrip("."),
                source_filename=path.name,
                size=blob.size,
                sha256=sha256,
                status="processing",
            )
            db.add(version)
            db.flush()

            db.add(
                ImportFile(
                    source_path=source_key,
                    sha256=sha256,
                    model_id=model.id,
                )
            )
            db.commit()

            if on_version:
                on_version(version.id)
            stats["added"] += 1
        except Exception as exc:
            db.rollback()
            stats["errors"] += 1
            stats["messages"].append(f"{path.name}: {exc}"[:500])
            logger.exception("Failed to import %s", path)

    if not dry_run:
        set_setting(db, "import_last_scan", utcnow().isoformat())
        set_setting(db, "import_last_result", json.dumps(stats, ensure_ascii=False))
    return stats
