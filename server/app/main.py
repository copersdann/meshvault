from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from sqlalchemy import select, update

from .api import categories, health, importer, jobs, meta, models, printers
from .config import get_settings
from .db import SessionLocal, ensure_schema
from .models import ModelVersion
from .services.importer import get_setting, scan_directory
from .services.processor import process_version

settings = get_settings()
logger = logging.getLogger("meshvault")


@asynccontextmanager
async def lifespan(_app: FastAPI):
    ensure_schema()
    with SessionLocal() as db:
        interrupted = list(
            db.scalars(
                select(ModelVersion.id).where(ModelVersion.status == "processing")
            ).all()
        )
        db.execute(
            update(ModelVersion)
            .where(ModelVersion.status == "processing")
            .values(status="failed", error="Server restarted during processing")
        )
        db.commit()

    async def recover_interrupted() -> None:
        for version_id in interrupted:
            await asyncio.to_thread(process_version, version_id)

    recovery_task = asyncio.create_task(recover_interrupted())

    async def periodic_import() -> None:
        while True:
            await asyncio.sleep(max(1, settings.import_interval_min) * 60)
            with SessionLocal() as db:
                directory = get_setting(db, "import_dir", settings.import_dir or "")
            if not directory:
                continue
            path = Path(directory).expanduser()
            if not path.is_dir():
                continue

            def run(target: str = directory) -> None:
                with SessionLocal() as scan_db:
                    scan_directory(scan_db, target, process_version)

            try:
                await asyncio.to_thread(run)
            except Exception:
                logger.exception("Periodic import failed")

    task = asyncio.create_task(periodic_import())
    try:
        yield
    finally:
        task.cancel()
        recovery_task.cancel()


app = FastAPI(
    title="MeshVault API",
    version=settings.version,
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(categories.router)
app.include_router(models.router)
app.include_router(printers.router)
app.include_router(jobs.router)
app.include_router(meta.router)
app.include_router(importer.router)


@app.get("/{full_path:path}", include_in_schema=False)
def spa_fallback(full_path: str) -> FileResponse:
    dist = settings.web_dist
    if not dist.is_dir() or full_path.startswith("api/"):
        raise HTTPException(status_code=404, detail="Not found")

    candidate: Path = dist / full_path
    if full_path and candidate.is_file():
        headers = {}
        if full_path.startswith("assets/"):
            headers["Cache-Control"] = "public, max-age=31536000, immutable"
        return FileResponse(candidate, headers=headers)

    index = dist / "index.html"
    if index.is_file():
        return FileResponse(index)
    raise HTTPException(status_code=404, detail="Not found")
