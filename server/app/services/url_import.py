"""Импорт модели по прямой ссылке (локальная разработка, без внешних каталогов)."""

from __future__ import annotations

import hashlib
import io
import mimetypes
import zipfile
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse

import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..config import get_settings
from ..models import Blob, Model, ModelVersion, Tag
from .geometry import SUPPORTED_EXTENSIONS
from .storage import storage

MAX_ZIP_FILES = 10


class ImportUrlError(Exception):
    """Ошибка загрузки модели по ссылке."""


@dataclass
class ImportItem:
    model_id: str | None = None
    version_id: str | None = None
    status: str = "error"  # added | duplicate | unsupported | error
    message: str = ""


def _extract_zip(data: bytes) -> list[tuple[bytes, str]]:
    result: list[tuple[bytes, str]] = []
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for info in archive.infolist():
                if info.is_dir() or Path(info.filename).suffix.lower() not in SUPPORTED_EXTENSIONS:
                    continue
                if len(result) >= MAX_ZIP_FILES:
                    break
                result.append((archive.read(info), Path(info.filename).name))
    except zipfile.BadZipFile as exc:
        raise ImportUrlError("Некорректный ZIP-архив") from exc
    if not result:
        raise ImportUrlError("В ZIP нет поддерживаемых моделей")
    return result


def fetch_url(url: str) -> list[tuple[bytes, str]]:
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        raise ImportUrlError("Нужна ссылка http:// или https://")
    limit = get_settings().max_upload_mb * 1024 * 1024
    try:
        with httpx.stream("GET", url, follow_redirects=True, timeout=60) as response:
            response.raise_for_status()
            declared = response.headers.get("content-length")
            if declared and declared.isdigit() and int(declared) > limit:
                raise ImportUrlError("Файл превышает лимит загрузки")
            chunks: list[bytes] = []
            total = 0
            for chunk in response.iter_bytes():
                total += len(chunk)
                if total > limit:
                    raise ImportUrlError("Файл превышает лимит загрузки")
                chunks.append(chunk)
    except httpx.HTTPError as exc:
        raise ImportUrlError(f"Не удалось скачать файл: {exc}") from exc

    data = b"".join(chunks)
    name = Path(parsed.path).name or "model.stl"
    if Path(name).suffix.lower() == ".zip":
        return _extract_zip(data)
    return [(data, name)]


def import_bytes(
    db: Session,
    data: bytes,
    filename: str,
    *,
    name: str | None = None,
    description: str = "",
    source_url: str = "",
    tags: list[str] | None = None,
) -> ImportItem:
    """Сохраняет файл как новую модель. Дубликаты по sha256 не создаются."""
    extension = Path(filename).suffix.lower()
    if extension not in SUPPORTED_EXTENSIONS:
        return ImportItem(status="unsupported", message=f"Формат {extension} не поддерживается")

    sha256 = hashlib.sha256(data).hexdigest()
    if db.get(Blob, sha256) is not None:
        return ImportItem(status="duplicate")

    mime = mimetypes.guess_type(filename)[0] or "application/octet-stream"
    blob = storage.save_bytes(db, data, mime)
    model = Model(
        name=(name or Path(filename).stem)[:255],
        description=description[:2000],
        source_url=source_url or None,
        source_author=None,
        source_license=None,
    )
    model.refresh_search_text()
    db.add(model)
    db.flush()

    for raw_tag in dict.fromkeys([*(tags or []), "url-import", extension.lstrip(".")]):
        tag_name = str(raw_tag).strip()[:64]
        if not tag_name:
            continue
        tag = db.scalar(select(Tag).where(Tag.name == tag_name))
        if tag is None:
            tag = Tag(name=tag_name)
            db.add(tag)
            db.flush()
        model.tags.append(tag)

    version = ModelVersion(
        model_id=model.id,
        version=1,
        original_blob_id=blob.id,
        source_format=extension.lstrip("."),
        source_filename=Path(filename).name,
        size=blob.size,
        sha256=sha256,
        status="processing",
    )
    db.add(version)
    db.commit()
    return ImportItem(model_id=model.id, version_id=version.id, status="added")
