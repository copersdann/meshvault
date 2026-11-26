from __future__ import annotations

from pathlib import Path

from fastapi import (
    APIRouter,
    BackgroundTasks,
    Depends,
    File,
    Form,
    HTTPException,
    Query,
    Response,
    UploadFile,
    status,
)
from fastapi.responses import FileResponse
from sqlalchemy import func, select
from sqlalchemy.orm import Session, selectinload

from ..config import get_settings
from ..db import get_db
from ..models import Blob, Collection, Model, ModelVersion, Printer, Tag, utcnow
from ..schemas import (
    ModelDetail,
    ModelList,
    ModelUpdate,
    OpenInSlicerRead,
    OpenInSlicerRequest,
    PrintabilityRead,
    SlicerCreate,
    SlicerRead,
    VersionRead,
)
from ..serializers import model_detail, model_summary, version_read
from ..services.export import ExportError, export_filename, export_version
from ..services.geometry import SUPPORTED_EXTENSIONS
from ..services.printability import analyze_printability
from ..services.processor import process_version
from ..services.security import require_auth
from ..services.slicer import (
    SlicerError,
    add_custom_slicer,
    find_slicers,
    open_in_slicer,
    remove_custom_slicer,
)
from ..services.storage import UploadTooLarge, storage

router = APIRouter(prefix="/api", tags=["models"], dependencies=[Depends(require_auth)])
settings = get_settings()

GCODE_EXTENSIONS = {".gcode", ".bgcode"}


def _load_model(db: Session, model_id: str, include_deleted: bool = False) -> Model:
    stmt = (
        select(Model)
        .options(selectinload(Model.versions))
        .where(Model.id == model_id)
    )
    if not include_deleted:
        stmt = stmt.where(Model.deleted_at.is_(None))
    model = db.scalars(stmt).unique().first()
    if model is None:
        raise HTTPException(status_code=404, detail="Model not found")
    return model


def _load_version(db: Session, model_id: str, version: int | None) -> ModelVersion:
    model = _load_model(db, model_id)
    if version is None:
        if not model.versions:
            raise HTTPException(status_code=404, detail="Model has no versions")
        return model.versions[0]
    for item in model.versions:
        if item.version == version:
            return item
    raise HTTPException(status_code=404, detail="Version not found")


def _get_or_create_tag(db: Session, name: str) -> Tag:
    name = name.strip()[:64]
    tag = db.scalar(select(Tag).where(Tag.name == name))
    if tag is None:
        tag = Tag(name=name)
        db.add(tag)
        db.flush()
    return tag


def _apply_tags(db: Session, model: Model, names: list[str]) -> None:
    cleaned: list[str] = []
    for name in names:
        value = name.strip()[:64]
        if value and value not in cleaned and len(cleaned) < 24:
            cleaned.append(value)
    model.tags = [_get_or_create_tag(db, name) for name in cleaned]


def _parse_tags(raw: str | None) -> list[str]:
    if not raw:
        return []
    return [item for item in (part.strip() for part in raw.split(",")) if item]


def _apply_collection(db: Session, model: Model, collection_id: str | None) -> None:
    if not collection_id:
        model.collections = []
        return
    collection = db.get(Collection, collection_id)
    if collection is None:
        raise HTTPException(status_code=404, detail="Collection not found")
    model.collections = [collection]


def _validate_upload(filename: str | None) -> tuple[str, str]:
    safe_name = Path(filename or "").name
    extension = Path(safe_name).suffix.lower()
    if extension not in SUPPORTED_EXTENSIONS:
        supported = ", ".join(sorted(SUPPORTED_EXTENSIONS))
        raise HTTPException(
            status_code=415,
            detail=f"Unsupported file format '{extension or '(none)'}'. Supported: {supported}",
        )
    return safe_name, extension


async def _store_upload(db: Session, upload: UploadFile) -> Blob:
    try:
        return await storage.save_upload(
            db, upload, settings.max_upload_mb * 1024 * 1024
        )
    except UploadTooLarge as exc:
        raise HTTPException(status_code=413, detail=str(exc)) from exc


@router.get("/models", response_model=ModelList)
def list_models(
    q: str = Query(default=""),
    tag: str | None = Query(default=None),
    collection_id: str | None = Query(default=None),
    sort: str = Query(default="created_desc"),
    trash: bool = Query(default=False),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> ModelList:
    conditions = [
        Model.deleted_at.is_not(None) if trash else Model.deleted_at.is_(None)
    ]
    if q.strip():
        conditions.append(Model.search_text.contains(q.strip().lower()))
    if tag:
        conditions.append(Model.tags.any(Tag.name == tag))
    if collection_id:
        conditions.append(Model.collections.any(Collection.id == collection_id))

    total = db.scalar(select(func.count(Model.id)).where(*conditions)) or 0

    stmt = select(Model).options(selectinload(Model.versions)).where(*conditions)
    order = (
        Model.name.asc()
        if sort == "name_asc"
        else Model.updated_at.desc()
        if sort == "updated_desc"
        else Model.created_at.desc()
    )
    models = db.scalars(stmt.order_by(order).limit(limit).offset(offset)).unique().all()
    return ModelList(items=[model_summary(model) for model in models], total=total)


@router.post("/models", response_model=ModelDetail, status_code=status.HTTP_201_CREATED)
async def create_model(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    name: str | None = Form(default=None),
    description: str | None = Form(default=None),
    tags: str | None = Form(default=None),
    collection_id: str | None = Form(default=None),
    db: Session = Depends(get_db),
) -> ModelDetail:
    filename, extension = _validate_upload(file.filename)
    blob = await _store_upload(db, file)

    model_name = (name or Path(filename).stem).strip()[:255] or "Untitled"
    model = Model(
        name=model_name,
        description=(description or "").strip(),
    )
    model.refresh_search_text()
    db.add(model)
    db.flush()

    version = ModelVersion(
        model_id=model.id,
        version=1,
        original_blob_id=blob.id,
        source_format=extension.lstrip("."),
        source_filename=filename,
        size=blob.size,
        sha256=blob.id,
        status="processing",
    )
    db.add(version)
    db.flush()

    _apply_tags(db, model, _parse_tags(tags))
    _apply_collection(db, model, collection_id)
    db.commit()

    background_tasks.add_task(process_version, version.id)
    return model_detail(_load_model(db, model.id))


@router.post("/models/{model_id}/versions", response_model=ModelDetail)
async def create_version(
    model_id: str,
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
) -> ModelDetail:
    model = _load_model(db, model_id)
    filename, extension = _validate_upload(file.filename)
    blob = await _store_upload(db, file)

    next_version = max((item.version for item in model.versions), default=0) + 1
    version = ModelVersion(
        model_id=model.id,
        version=next_version,
        original_blob_id=blob.id,
        source_format=extension.lstrip("."),
        source_filename=filename,
        size=blob.size,
        sha256=blob.id,
        status="processing",
    )
    db.add(version)
    model.updated_at = utcnow()
    db.commit()

    background_tasks.add_task(process_version, version.id)
    return model_detail(_load_model(db, model.id))


@router.post("/models/{model_id}/versions/{version}/retry", response_model=ModelDetail)
def retry_version(
    model_id: str,
    version: int,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
) -> ModelDetail:
    row = _load_version(db, model_id, version)
    background_tasks.add_task(process_version, row.id)
    return model_detail(_load_model(db, model_id))


@router.get("/models/{model_id}", response_model=ModelDetail)
def get_model(
    model_id: str,
    db: Session = Depends(get_db),
) -> ModelDetail:
    return model_detail(_load_model(db, model_id))


@router.patch("/models/{model_id}", response_model=ModelDetail)
def update_model(
    model_id: str,
    payload: ModelUpdate,
    db: Session = Depends(get_db),
) -> ModelDetail:
    model = _load_model(db, model_id)
    if payload.name is not None:
        model.name = payload.name.strip()[:255] or "Untitled"
    if payload.description is not None:
        model.description = payload.description.strip()
    model.refresh_search_text()

    if payload.tags is not None:
        _apply_tags(db, model, payload.tags)
    if payload.collection_ids is not None:
        if not payload.collection_ids:
            model.collections = []
        else:
            collections = db.scalars(
                select(Collection).where(Collection.id.in_(payload.collection_ids))
            ).all()
            found = {item.id for item in collections}
            if len(found) != len(set(payload.collection_ids)):
                raise HTTPException(status_code=404, detail="Collection not found")
            model.collections = list(collections)

    model.updated_at = utcnow()
    db.commit()
    return model_detail(_load_model(db, model_id))


@router.delete("/models/{model_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_model(
    model_id: str,
    permanent: bool = Query(default=False),
    db: Session = Depends(get_db),
) -> Response:
    model = _load_model(db, model_id, include_deleted=True)
    if permanent:
        db.delete(model)
    else:
        model.deleted_at = utcnow()
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/models/{model_id}/restore", response_model=ModelDetail)
def restore_model(
    model_id: str,
    db: Session = Depends(get_db),
) -> ModelDetail:
    model = _load_model(db, model_id, include_deleted=True)
    model.deleted_at = None
    model.updated_at = utcnow()
    db.commit()
    return model_detail(_load_model(db, model_id))


@router.post(
    "/models/{model_id}/versions/{version}/gcode",
    response_model=VersionRead,
)
async def attach_gcode(
    model_id: str,
    version: int,
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
) -> VersionRead:
    model_version = _load_version(db, model_id, version)
    filename = Path(file.filename or "").name
    if Path(filename).suffix.lower() not in GCODE_EXTENSIONS:
        raise HTTPException(status_code=415, detail="Only .gcode and .bgcode files are accepted")

    blob = await _store_upload(db, file)
    model_version.gcode_blob_id = blob.id
    model_version.gcode_filename = filename
    db.commit()
    return version_read(model_version)


@router.get("/models/{model_id}/files/{variant}")
def get_file(
    model_id: str,
    variant: str,
    version: int | None = Query(default=None),
    download: bool = Query(default=False),
    db: Session = Depends(get_db),
) -> FileResponse:
    if variant not in {"original", "glb", "thumb", "gcode"}:
        raise HTTPException(status_code=404, detail="Unknown file variant")

    model_version = _load_version(db, model_id, version)
    blob_id = {
        "original": model_version.original_blob_id,
        "glb": model_version.glb_blob_id,
        "thumb": model_version.thumb_blob_id,
        "gcode": model_version.gcode_blob_id,
    }[variant]
    if not blob_id:
        raise HTTPException(status_code=404, detail="File is not available")

    blob = db.get(Blob, blob_id)
    if blob is None:
        raise HTTPException(status_code=404, detail="Blob is missing")

    path = storage.path_for(blob)
    if not path.is_file():
        raise HTTPException(status_code=404, detail="File is missing on disk")

    media_type = {
        "original": blob.mime or "application/octet-stream",
        "glb": "model/gltf-binary",
        "thumb": "image/png",
        "gcode": "text/plain",
    }[variant]

    stem = Path(model_version.source_filename).stem or f"model-v{model_version.version}"
    filename = {
        "original": model_version.source_filename,
        "glb": f"{stem}-v{model_version.version}.glb",
        "thumb": f"{stem}-v{model_version.version}.png",
        "gcode": model_version.gcode_filename or f"{stem}-v{model_version.version}.gcode",
    }[variant]

    headers = {}
    if variant in {"glb", "thumb"}:
        headers["Cache-Control"] = "public, max-age=31536000, immutable"

    return FileResponse(
        path,
        media_type=media_type,
        filename=filename if download else None,
        headers=headers,
    )


@router.get("/models/{model_id}/versions/{version}/export")
def export_model(
    model_id: str,
    version: int,
    format: str = Query(default="stl"),
    db: Session = Depends(get_db),
) -> Response:
    model_version = _load_version(db, model_id, version)
    try:
        data, media_type, extension = export_version(db, model_version, format)
    except ExportError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    filename = export_filename(model_version, extension)
    return Response(
        content=data,
        media_type=media_type,
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


@router.get(
    "/models/{model_id}/versions/{version}/printability",
    response_model=PrintabilityRead,
)
def model_printability(
    model_id: str,
    version: int,
    printer_id: str | None = Query(default=None),
    db: Session = Depends(get_db),
) -> PrintabilityRead:
    model_version = _load_version(db, model_id, version)
    profile = None
    if printer_id:
        printer = db.get(Printer, printer_id)
        if printer is None:
            raise HTTPException(status_code=404, detail="Printer not found")
        profile = printer.profile_json or {}
    result = analyze_printability(model_version, profile, db)
    return PrintabilityRead(**result)


@router.get("/slicers", response_model=list[SlicerRead])
def list_slicers() -> list[SlicerRead]:
    return [SlicerRead(**item) for item in find_slicers()]


@router.post("/slicers", response_model=list[SlicerRead])
def add_slicer(payload: SlicerCreate) -> list[SlicerRead]:
    try:
        slicers = add_custom_slicer(payload.name, payload.path)
    except SlicerError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return [SlicerRead(**item) for item in slicers]


@router.delete("/slicers/{slicer_id}", response_model=list[SlicerRead])
def delete_slicer(slicer_id: str) -> list[SlicerRead]:
    chosen = next((item for item in find_slicers() if item["id"] == slicer_id), None)
    if chosen is None or not slicer_id.startswith("custom-"):
        raise HTTPException(status_code=404, detail="Пользовательский слайсер не найден")
    return [SlicerRead(**item) for item in remove_custom_slicer(chosen["path"])]


@router.post(
    "/models/{model_id}/versions/{version}/open-in-slicer",
    response_model=OpenInSlicerRead,
)
def open_model_in_slicer(
    model_id: str,
    version: int,
    payload: OpenInSlicerRequest,
    db: Session = Depends(get_db),
) -> OpenInSlicerRead:
    model_version = _load_version(db, model_id, version)
    try:
        result = open_in_slicer(db, model_version, payload.slicer_id)
    except SlicerError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return OpenInSlicerRead(**result)
