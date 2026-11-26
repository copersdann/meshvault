from __future__ import annotations

from .models import Collection, Model, ModelVersion, Printer, Tag
from .schemas import (
    CollectionRead,
    ModelDetail,
    ModelSummary,
    PrinterRead,
    TagRead,
    VersionRead,
)


def _file_url(model_id: str, variant: str, version: int | None = None) -> str:
    query = f"?version={version}" if version is not None else ""
    return f"/api/models/{model_id}/files/{variant}{query}"


def version_read(version: ModelVersion) -> VersionRead:
    return VersionRead(
        id=version.id,
        version=version.version,
        source_format=version.source_format,
        source_filename=version.source_filename,
        size=version.size,
        sha256=version.sha256,
        bbox_x=version.bbox_x,
        bbox_y=version.bbox_y,
        bbox_z=version.bbox_z,
        volume=version.volume,
        triangles=version.triangles,
        watertight=version.watertight,
        units=version.units,
        status=version.status,
        error=version.error,
        created_at=version.created_at,
        original_url=_file_url(version.model_id, "original", version.version),
        glb_url=_file_url(version.model_id, "glb", version.version)
        if version.glb_blob_id
        else None,
        thumb_url=_file_url(version.model_id, "thumb", version.version)
        if version.thumb_blob_id
        else None,
        gcode_url=_file_url(version.model_id, "gcode", version.version)
        if version.gcode_blob_id
        else None,
        gcode_filename=version.gcode_filename,
    )


def tag_read(tag: Tag) -> TagRead:
    return TagRead(id=tag.id, name=tag.name)


def collection_read(collection: Collection) -> CollectionRead:
    return CollectionRead(
        id=collection.id,
        name=collection.name,
        description=collection.description,
    )


def model_summary(model: Model) -> ModelSummary:
    versions = list(model.versions)
    latest = versions[0] if versions else None
    return ModelSummary(
        id=model.id,
        name=model.name,
        description=model.description,
        created_at=model.created_at,
        updated_at=model.updated_at,
        deleted_at=model.deleted_at,
        source_url=model.source_url,
        source_author=model.source_author,
        source_license=model.source_license,
        status=latest.status if latest else "empty",
        tags=[tag_read(tag) for tag in model.tags],
        collections=[collection_read(item) for item in model.collections],
        latest_version=version_read(latest) if latest else None,
    )


def model_detail(model: Model) -> ModelDetail:
    detail = ModelDetail.model_validate(model_summary(model).model_dump())
    detail.versions = [version_read(version) for version in model.versions]
    return detail


def printer_read(printer: Printer) -> PrinterRead:
    return PrinterRead(
        id=printer.id,
        name=printer.name,
        kind=printer.kind,
        host=printer.host,
        enabled=printer.enabled,
        profile_json=printer.profile_json or {},
        has_api_key=bool(printer.api_key_enc),
        created_at=printer.created_at,
    )
