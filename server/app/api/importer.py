from __future__ import annotations

import json
from pathlib import Path

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session

from ..config import get_settings
from ..db import get_db
from ..services import url_import
from ..services.importer import get_setting, scan_directory, set_setting
from ..services.processor import process_version
from ..services.security import require_auth

router = APIRouter(
    prefix="/api/import",
    tags=["import"],
    dependencies=[Depends(require_auth)],
)


class ImportConfigRead(BaseModel):
    directory: str
    interval_min: int
    last_scan: str = ""
    last_result: dict = {}


class ImportConfigUpdate(BaseModel):
    directory: str
    interval_min: int = 5


class ScanResult(BaseModel):
    found: int
    added: int
    skipped: int
    duplicates: int = 0
    errors: int
    messages: list[str] = []


class UrlImportRequest(BaseModel):
    url: str


class UrlImportResult(BaseModel):
    added: int = 0
    duplicates: int = 0
    errors: list[str] = []


def _directory(db: Session) -> str:
    return get_setting(db, "import_dir", get_settings().import_dir or "")


@router.get("/config", response_model=ImportConfigRead)
def get_import_config(db: Session = Depends(get_db)) -> ImportConfigRead:
    raw_result = get_setting(db, "import_last_result", "{}")
    try:
        result = json.loads(raw_result)
    except json.JSONDecodeError:
        result = {}
    try:
        interval = int(get_setting(db, "import_interval_min", "5"))
    except ValueError:
        interval = 5
    return ImportConfigRead(
        directory=_directory(db),
        interval_min=interval,
        last_scan=get_setting(db, "import_last_scan", ""),
        last_result=result,
    )


@router.put("/config", response_model=ImportConfigRead)
def update_import_config(
    payload: ImportConfigUpdate,
    db: Session = Depends(get_db),
) -> ImportConfigRead:
    directory = payload.directory.strip()
    if directory and not Path(directory).expanduser().is_dir():
        raise HTTPException(status_code=400, detail="Directory does not exist on the server")
    set_setting(db, "import_dir", directory)
    set_setting(db, "import_interval_min", str(max(1, payload.interval_min)))
    return get_import_config(db)


@router.post("/scan", response_model=ScanResult)
def run_scan(
    background_tasks: BackgroundTasks,
    dry_run: bool = False,
    db: Session = Depends(get_db),
) -> ScanResult:
    directory = _directory(db)
    if not directory:
        raise HTTPException(status_code=400, detail="Import directory is not configured")
    if not Path(directory).expanduser().is_dir():
        raise HTTPException(status_code=400, detail="Directory does not exist on the server")

    stats = scan_directory(
        db,
        directory,
        on_version=lambda version_id: background_tasks.add_task(process_version, version_id),
        dry_run=dry_run,
    )
    return ScanResult.model_validate(stats)


@router.post("/url", response_model=UrlImportResult)
def import_url(
    payload: UrlImportRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
) -> UrlImportResult:
    result = UrlImportResult()
    try:
        downloads = url_import.fetch_url(payload.url)
    except url_import.ImportUrlError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    for data, filename in downloads:
        item = url_import.import_bytes(db, data, filename, source_url=payload.url)
        if item.status == "added" and item.version_id:
            result.added += 1
            background_tasks.add_task(process_version, item.version_id)
        elif item.status == "duplicate":
            result.duplicates += 1
        elif item.status == "unsupported":
            result.errors.append(item.message or f"{filename}: неподдерживаемый формат")
    return result
