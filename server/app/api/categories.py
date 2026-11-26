from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, select
from sqlalchemy.orm import Session, selectinload

from ..db import get_db
from ..models import Model
from ..schemas import CategoryRead, ModelList
from ..serializers import model_summary
from ..services.categories import build_categories, category_model_ids
from ..services.security import require_auth

router = APIRouter(prefix="/api", tags=["categories"], dependencies=[Depends(require_auth)])


@router.get("/categories", response_model=list[CategoryRead])
def list_categories(db: Session = Depends(get_db)) -> list[CategoryRead]:
    return [CategoryRead(**item) for item in build_categories(db)]


@router.get("/categories/{name}/models", response_model=ModelList)
def category_models(
    name: str,
    limit: int = Query(default=100, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> ModelList:
    ids = category_model_ids(db, name)
    if not ids:
        return ModelList(items=[], total=0)

    stmt = (
        select(Model)
        .options(selectinload(Model.versions))
        .where(Model.id.in_(ids))
        .order_by(Model.name.asc())
    )
    total = db.scalar(select(func.count(Model.id)).where(Model.id.in_(ids))) or 0
    models = db.scalars(stmt.limit(limit).offset(offset)).unique().all()
    return ModelList(items=[model_summary(model) for model in models], total=total)
