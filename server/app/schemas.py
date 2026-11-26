from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ORMModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class TagRead(ORMModel):
    id: str
    name: str


class CollectionRead(ORMModel):
    id: str
    name: str
    description: str = ""


class VersionRead(BaseModel):
    id: str
    version: int
    source_format: str
    source_filename: str
    size: int
    sha256: str
    bbox_x: float | None = None
    bbox_y: float | None = None
    bbox_z: float | None = None
    volume: float | None = None
    triangles: int = 0
    watertight: bool | None = None
    units: str = "mm"
    status: str
    error: str | None = None
    created_at: datetime
    original_url: str
    glb_url: str | None = None
    thumb_url: str | None = None
    gcode_url: str | None = None
    gcode_filename: str | None = None


class ModelSummary(BaseModel):
    id: str
    name: str
    description: str = ""
    created_at: datetime
    updated_at: datetime
    deleted_at: datetime | None = None
    source_url: str | None = None
    source_author: str | None = None
    source_license: str | None = None
    status: str = "empty"
    tags: list[TagRead] = Field(default_factory=list)
    collections: list[CollectionRead] = Field(default_factory=list)
    latest_version: VersionRead | None = None


class ModelDetail(ModelSummary):
    versions: list[VersionRead] = Field(default_factory=list)


class ModelList(BaseModel):
    items: list[ModelSummary]
    total: int


class ModelUpdate(BaseModel):
    name: str | None = None
    description: str | None = None
    tags: list[str] | None = None
    collection_ids: list[str] | None = None


class PrinterProfile(BaseModel):
    bed_x: float = 220.0
    bed_y: float = 220.0
    bed_z: float = 250.0
    nozzle: float = 0.4

    model_config = ConfigDict(extra="allow")


class PrinterCreate(BaseModel):
    name: str = Field(min_length=1, max_length=128)
    host: str = Field(min_length=1, max_length=512)
    api_key: str | None = None
    kind: str = "moonraker"
    profile_json: PrinterProfile = Field(default_factory=PrinterProfile)
    enabled: bool = True


class PrinterUpdate(BaseModel):
    name: str | None = None
    host: str | None = None
    api_key: str | None = None
    profile_json: PrinterProfile | None = None
    enabled: bool | None = None


class PrinterRead(ORMModel):
    id: str
    name: str
    kind: str
    host: str
    enabled: bool
    profile_json: dict[str, Any]
    has_api_key: bool
    created_at: datetime


class PrinterDiscoveryRead(BaseModel):
    host: str
    name: str
    version: str = ""
    kind: str = "moonraker"


class PrinterProbe(BaseModel):
    host: str
    api_key: str | None = None


class PrinterStatusRead(BaseModel):
    state: str
    filename: str | None = None
    progress: float = 0.0
    bed_temp: float | None = None
    bed_target: float | None = None
    nozzle_temp: float | None = None
    nozzle_target: float | None = None
    message: str = ""


class ProbeResult(BaseModel):
    ok: bool
    klippy_state: str | None = None
    message: str = ""


class PrintRequest(BaseModel):
    printer_id: str
    gcode_blob_id: str | None = None
    filename: str | None = None


class JobRead(ORMModel):
    id: str
    model_id: str | None = None
    version_id: str | None = None
    printer_id: str | None = None
    model_name: str
    printer_name: str
    status: str
    filename: str
    gcode_blob_id: str | None = None
    progress: float = 0.0
    message: str = ""
    created_at: datetime
    started_at: datetime | None = None
    finished_at: datetime | None = None


class CategoryPreview(BaseModel):
    id: str
    name: str
    format: str = ""
    status: str = ""
    thumb_url: str | None = None


class SubcategoryRead(BaseModel):
    name: str
    count: int


class CategoryRead(BaseModel):
    name: str
    count: int
    subcategories: list[SubcategoryRead] = Field(default_factory=list)
    previews: list[CategoryPreview] = Field(default_factory=list)


class HealthRead(BaseModel):
    status: str
    version: str
    formats: list[str]
    cad: bool = False
    slicer: bool
    slicers: list[str] = Field(default_factory=list)
    auth_required: bool


class PrintabilityIssue(BaseModel):
    code: str
    severity: str
    message: str


class PrintabilityRead(BaseModel):
    watertight: bool | None = None
    overhang_fraction: float = 0.0
    issues: list[PrintabilityIssue] = Field(default_factory=list)


class SlicerRead(BaseModel):
    id: str
    name: str
    path: str


class SlicerCreate(BaseModel):
    name: str = ""
    path: str


class OpenInSlicerRequest(BaseModel):
    slicer_id: str | None = None


class OpenInSlicerRead(BaseModel):
    slicer: str
    file: str
