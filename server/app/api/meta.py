from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import Collection, Tag
from ..schemas import CollectionRead, TagRead
from ..services.security import require_auth

router = APIRouter(
    prefix="/api",
    tags=["meta"],
    dependencies=[Depends(require_auth)],
)


class TagCreate(BaseModel):
    name: str = Field(min_length=1, max_length=64)


class CollectionCreate(BaseModel):
    name: str = Field(min_length=1, max_length=128)
    description: str = ""


@router.get("/tags", response_model=list[TagRead])
def list_tags(db: Session = Depends(get_db)) -> list[TagRead]:
    tags = db.scalars(select(Tag).order_by(Tag.name.asc())).all()
    return [TagRead.model_validate(tag) for tag in tags]


@router.post("/tags", response_model=TagRead, status_code=status.HTTP_201_CREATED)
def create_tag(payload: TagCreate, db: Session = Depends(get_db)) -> TagRead:
    name = payload.name.strip()
    existing = db.scalar(select(Tag).where(Tag.name == name))
    if existing is not None:
        return TagRead.model_validate(existing)
    tag = Tag(name=name)
    db.add(tag)
    db.commit()
    return TagRead.model_validate(tag)


@router.get("/collections", response_model=list[CollectionRead])
def list_collections(db: Session = Depends(get_db)) -> list[CollectionRead]:
    collections = db.scalars(select(Collection).order_by(Collection.name.asc())).all()
    return [CollectionRead.model_validate(item) for item in collections]


@router.post(
    "/collections",
    response_model=CollectionRead,
    status_code=status.HTTP_201_CREATED,
)
def create_collection(
    payload: CollectionCreate,
    db: Session = Depends(get_db),
) -> CollectionRead:
    name = payload.name.strip()
    existing = db.scalar(select(Collection).where(Collection.name == name))
    if existing is not None:
        raise HTTPException(status_code=409, detail="Collection already exists")
    collection = Collection(name=name, description=payload.description.strip())
    db.add(collection)
    db.commit()
    return CollectionRead.model_validate(collection)


@router.delete("/collections/{collection_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_collection(collection_id: str, db: Session = Depends(get_db)) -> None:
    collection = db.get(Collection, collection_id)
    if collection is None:
        raise HTTPException(status_code=404, detail="Collection not found")
    db.delete(collection)
    db.commit()
