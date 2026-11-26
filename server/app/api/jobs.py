from __future__ import annotations

import re
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import Blob, Model, Printer, PrintJob, utcnow
from ..schemas import JobRead, PrintRequest
from ..services.moonraker import (
    MoonrakerClient,
    MoonrakerError,
    map_job_status,
    parse_status,
)
from ..services.security import decrypt_secret, require_auth
from ..services.storage import storage

router = APIRouter(
    prefix="/api",
    tags=["jobs"],
    dependencies=[Depends(require_auth)],
)

ACTIVE_STATUSES = {"queued", "uploading", "printing", "paused"}


def _get_job(db: Session, job_id: str) -> PrintJob:
    job = db.get(PrintJob, job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="Print job not found")
    return job


async def _refresh_job(db: Session, job: PrintJob) -> None:
    if job.status not in ACTIVE_STATUSES or not job.printer_id:
        return
    printer = db.get(Printer, job.printer_id)
    if printer is None or not printer.enabled:
        return

    try:
        client = MoonrakerClient(printer.host, decrypt_secret(printer.api_key_enc))
        parsed = parse_status(await client.status())
    except MoonrakerError:
        return

    if (parsed["filename"] and job.filename
            and Path(parsed["filename"]).name != Path(job.filename).name):
        return

    mapped = map_job_status(parsed["state"], parsed["progress"])
    changed = False
    if mapped and mapped != job.status:
        job.status = mapped
        changed = True
    if parsed["progress"] is not None and parsed["progress"] != job.progress:
        job.progress = parsed["progress"]
        changed = True
    if parsed["message"] and parsed["message"] != job.message:
        job.message = parsed["message"][:2000]
        changed = True
    if changed and mapped in {"completed", "failed", "cancelled"}:
        job.finished_at = utcnow()
    if changed:
        db.commit()


def _safe_filename(name: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "_", Path(name).name)
    cleaned = cleaned.strip("._") or "model.gcode"
    if not cleaned.lower().endswith((".gcode", ".bgcode")):
        cleaned += ".gcode"
    return cleaned[:200]


@router.post(
    "/models/{model_id}/print",
    response_model=JobRead,
    status_code=status.HTTP_201_CREATED,
)
async def start_print(
    model_id: str,
    payload: PrintRequest,
    db: Session = Depends(get_db),
) -> JobRead:
    model = db.get(Model, model_id)
    if model is None:
        raise HTTPException(status_code=404, detail="Model not found")

    version = None
    if model.versions:
        version = model.versions[0]

    printer = db.get(Printer, payload.printer_id)
    if printer is None:
        raise HTTPException(status_code=404, detail="Printer not found")
    if not printer.enabled:
        raise HTTPException(status_code=400, detail="Printer is disabled")

    blob_id = payload.gcode_blob_id or (version.gcode_blob_id if version else None)
    if not blob_id:
        raise HTTPException(
            status_code=400,
            detail="No G-code attached to this model. Upload a .gcode file first.",
        )
    blob = db.get(Blob, blob_id)
    if blob is None:
        raise HTTPException(status_code=404, detail="G-code blob is missing")

    path = storage.path_for(blob)
    if not path.is_file():
        raise HTTPException(status_code=404, detail="G-code file is missing on disk")

    filename = _safe_filename(
        payload.filename
        or (version.gcode_filename if version else None)
        or f"{Path(model.name).name}-v{version.version if version else 1}.gcode"
    )

    job = PrintJob(
        model_id=model.id,
        version_id=version.id if version else None,
        printer_id=printer.id,
        model_name=model.name,
        printer_name=printer.name,
        status="uploading",
        filename=filename,
        gcode_blob_id=blob.id,
        progress=0.0,
        started_at=utcnow(),
    )
    db.add(job)
    db.commit()

    try:
        client = MoonrakerClient(printer.host, decrypt_secret(printer.api_key_enc))
        await client.upload_gcode(filename, path.read_bytes())
        await client.start_print(filename)
        job.status = "printing"
        job.message = "Print started"
    except MoonrakerError as exc:
        job.status = "failed"
        job.message = str(exc)[:2000]
        job.finished_at = utcnow()
        db.commit()
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    db.commit()
    return JobRead.model_validate(job)


@router.get("/jobs", response_model=list[JobRead])
async def list_jobs(
    active_only: bool = False,
    limit: int = 50,
    db: Session = Depends(get_db),
) -> list[JobRead]:
    stmt = select(PrintJob).order_by(PrintJob.created_at.desc()).limit(min(limit, 200))
    if active_only:
        stmt = stmt.where(PrintJob.status.in_(ACTIVE_STATUSES))
    jobs = db.scalars(stmt).all()
    for job in jobs:
        await _refresh_job(db, job)
    return [JobRead.model_validate(job) for job in jobs]


@router.get("/jobs/{job_id}", response_model=JobRead)
async def get_job(job_id: str, db: Session = Depends(get_db)) -> JobRead:
    job = _get_job(db, job_id)
    await _refresh_job(db, job)
    return JobRead.model_validate(job)


async def _job_action(job_id: str, action: str, db: Session) -> JobRead:
    job = _get_job(db, job_id)
    if not job.printer_id:
        raise HTTPException(status_code=400, detail="Printer is no longer available")
    printer = db.get(Printer, job.printer_id)
    if printer is None:
        raise HTTPException(status_code=400, detail="Printer is no longer available")

    try:
        client = MoonrakerClient(printer.host, decrypt_secret(printer.api_key_enc))
        if action == "pause":
            await client.pause()
            job.status = "paused"
        elif action == "resume":
            await client.resume()
            job.status = "printing"
        else:
            await client.cancel()
            job.status = "cancelled"
            job.finished_at = utcnow()
    except MoonrakerError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    db.commit()
    return JobRead.model_validate(job)


@router.post("/jobs/{job_id}/pause", response_model=JobRead)
async def pause_job(job_id: str, db: Session = Depends(get_db)) -> JobRead:
    return await _job_action(job_id, "pause", db)


@router.post("/jobs/{job_id}/resume", response_model=JobRead)
async def resume_job(job_id: str, db: Session = Depends(get_db)) -> JobRead:
    return await _job_action(job_id, "resume", db)


@router.post("/jobs/{job_id}/cancel", response_model=JobRead)
async def cancel_job(job_id: str, db: Session = Depends(get_db)) -> JobRead:
    return await _job_action(job_id, "cancel", db)
