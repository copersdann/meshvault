from __future__ import annotations

from datetime import datetime, timezone
from uuid import uuid4

from sqlalchemy import (
    JSON,
    Boolean,
    Column,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    Table,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def utcnow() -> datetime:
    """Naive UTC timestamp (SQLite does not preserve timezone info)."""
    return datetime.now(timezone.utc).replace(tzinfo=None)


def new_id() -> str:
    return str(uuid4())


model_tags = Table(
    "model_tags",
    Base.metadata,
    Column("model_id", ForeignKey("models.id", ondelete="CASCADE"), primary_key=True),
    Column("tag_id", ForeignKey("tags.id", ondelete="CASCADE"), primary_key=True),
)

model_collections = Table(
    "model_collections",
    Base.metadata,
    Column("model_id", ForeignKey("models.id", ondelete="CASCADE"), primary_key=True),
    Column("collection_id", ForeignKey("collections.id", ondelete="CASCADE"), primary_key=True),
)


class Blob(Base):
    __tablename__ = "blobs"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    path: Mapped[str] = mapped_column(String(1024))
    mime: Mapped[str] = mapped_column(String(128), default="application/octet-stream")
    size: Mapped[int] = mapped_column(Integer)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Model(Base):
    __tablename__ = "models"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    name: Mapped[str] = mapped_column(String(255), index=True)
    description: Mapped[str] = mapped_column(Text, default="")
    search_text: Mapped[str] = mapped_column(Text, default="", index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, onupdate=utcnow)
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True, index=True)
    source_url: Mapped[str | None] = mapped_column(String(2048), nullable=True)
    source_author: Mapped[str | None] = mapped_column(String(255), nullable=True)
    source_license: Mapped[str | None] = mapped_column(String(64), nullable=True)

    versions: Mapped[list[ModelVersion]] = relationship(
        back_populates="model",
        cascade="all, delete-orphan",
        order_by="ModelVersion.version.desc()",
    )
    tags: Mapped[list[Tag]] = relationship(
        secondary=model_tags,
        back_populates="models",
        lazy="selectin",
    )
    collections: Mapped[list[Collection]] = relationship(
        secondary=model_collections,
        back_populates="models",
        lazy="selectin",
    )

    def refresh_search_text(self) -> None:
        self.search_text = f"{self.name} {self.description}".lower()


class ModelVersion(Base):
    __tablename__ = "model_versions"
    __table_args__ = (
        UniqueConstraint("model_id", "version", name="uq_model_version"),
        Index("ix_model_versions_status", "status"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    model_id: Mapped[str] = mapped_column(
        ForeignKey("models.id", ondelete="CASCADE"), index=True
    )
    version: Mapped[int] = mapped_column(Integer, default=1)

    original_blob_id: Mapped[str] = mapped_column(ForeignKey("blobs.id"))
    glb_blob_id: Mapped[str | None] = mapped_column(
        ForeignKey("blobs.id"), nullable=True
    )
    thumb_blob_id: Mapped[str | None] = mapped_column(
        ForeignKey("blobs.id"), nullable=True
    )
    gcode_blob_id: Mapped[str | None] = mapped_column(
        ForeignKey("blobs.id"), nullable=True
    )

    source_format: Mapped[str] = mapped_column(String(32))
    source_filename: Mapped[str] = mapped_column(String(512))
    size: Mapped[int] = mapped_column(Integer, default=0)
    sha256: Mapped[str] = mapped_column(String(64), index=True)

    bbox_x: Mapped[float | None] = mapped_column(Float, nullable=True)
    bbox_y: Mapped[float | None] = mapped_column(Float, nullable=True)
    bbox_z: Mapped[float | None] = mapped_column(Float, nullable=True)
    volume: Mapped[float | None] = mapped_column(Float, nullable=True)
    triangles: Mapped[int] = mapped_column(Integer, default=0)
    watertight: Mapped[bool | None] = mapped_column(Boolean, nullable=True)
    units: Mapped[str] = mapped_column(String(16), default="mm")

    status: Mapped[str] = mapped_column(String(16), default="processing")
    error: Mapped[str | None] = mapped_column(Text, nullable=True)

    gcode_filename: Mapped[str | None] = mapped_column(String(512), nullable=True)

    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    processed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    model: Mapped[Model] = relationship(back_populates="versions")


class Tag(Base):
    __tablename__ = "tags"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    name: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    models: Mapped[list[Model]] = relationship(
        secondary=model_tags, back_populates="tags"
    )


class Collection(Base):
    __tablename__ = "collections"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    name: Mapped[str] = mapped_column(String(128), unique=True, index=True)
    description: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    models: Mapped[list[Model]] = relationship(
        secondary=model_collections, back_populates="collections"
    )


def default_printer_profile() -> dict:
    return {"bed_x": 220.0, "bed_y": 220.0, "bed_z": 250.0, "nozzle": 0.4}


class Printer(Base):
    __tablename__ = "printers"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    name: Mapped[str] = mapped_column(String(128))
    kind: Mapped[str] = mapped_column(String(32), default="moonraker")
    host: Mapped[str] = mapped_column(String(512))
    api_key_enc: Mapped[str | None] = mapped_column(Text, nullable=True)
    profile_json: Mapped[dict] = mapped_column(JSON, default=default_printer_profile)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, onupdate=utcnow)


class AppSetting(Base):
    __tablename__ = "app_settings"

    key: Mapped[str] = mapped_column(String(128), primary_key=True)
    value: Mapped[str] = mapped_column(Text, default="")


class ImportFile(Base):
    __tablename__ = "import_files"

    source_path: Mapped[str] = mapped_column(String(2048), primary_key=True)
    sha256: Mapped[str] = mapped_column(String(64), index=True)
    model_id: Mapped[str] = mapped_column(String(36), index=True)
    duplicate_of: Mapped[str | None] = mapped_column(String(36), nullable=True, index=True)
    imported_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class PrintJob(Base):
    __tablename__ = "print_jobs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    model_id: Mapped[str | None] = mapped_column(
        ForeignKey("models.id", ondelete="SET NULL"), nullable=True, index=True
    )
    version_id: Mapped[str | None] = mapped_column(
        ForeignKey("model_versions.id", ondelete="SET NULL"), nullable=True
    )
    printer_id: Mapped[str | None] = mapped_column(
        ForeignKey("printers.id", ondelete="SET NULL"), nullable=True, index=True
    )
    model_name: Mapped[str] = mapped_column(String(255), default="")
    printer_name: Mapped[str] = mapped_column(String(128), default="")

    status: Mapped[str] = mapped_column(String(32), default="queued", index=True)
    filename: Mapped[str] = mapped_column(String(512), default="")
    gcode_blob_id: Mapped[str | None] = mapped_column(
        ForeignKey("blobs.id", ondelete="SET NULL"), nullable=True
    )
    progress: Mapped[float] = mapped_column(Float, default=0.0)
    message: Mapped[str] = mapped_column(Text, default="")

    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    started_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
