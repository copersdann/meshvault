from __future__ import annotations

from collections import defaultdict
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from ..config import get_settings
from ..models import ImportFile, Model
from .importer import get_setting


def _relative_parts(source: str, root: str) -> tuple[str, ...]:
    path = Path(source)
    if root:
        try:
            return path.relative_to(Path(root).expanduser()).parts
        except ValueError:
            pass
    return path.parts[-3:-1] if len(path.parts) >= 3 else path.parts[:-1]


def build_categories(db: Session) -> list[dict]:
    """Group imported models by the first subdirectory of the import root."""
    settings = get_settings()
    root = get_setting(db, "import_dir", settings.import_dir or "").strip()

    rows = db.execute(select(ImportFile.source_path, ImportFile.model_id)).all()
    models = {
        model.id: model
        for model in db.scalars(select(Model).options(selectinload(Model.versions))).all()
    }

    grouped: dict[str, list[str]] = defaultdict(list)
    subgroups: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
    seen: set[tuple[str, str]] = set()

    for source, model_id in rows:
        if not source or model_id not in models:
            continue
        parts = _relative_parts(source, root)
        category = parts[0] if parts else "Прочее"
        key = (category, model_id)
        if key in seen:
            continue
        seen.add(key)
        grouped[category].append(model_id)
        if len(parts) > 2:
            subgroups[category][parts[1]].add(model_id)

    result: list[dict] = []
    for name, ids in grouped.items():
        previews: list[dict] = []
        for model_id in ids[:6]:
            model = models[model_id]
            latest = model.versions[0] if model.versions else None
            previews.append(
                {
                    "id": model.id,
                    "name": model.name,
                    "format": latest.source_format if latest else "",
                    "status": latest.status if latest else "empty",
                    "thumb_url": (
                        f"/api/models/{model.id}/files/thumb?version={latest.version}"
                        if latest and latest.thumb_blob_id
                        else None
                    ),
                }
            )
        result.append(
            {
                "name": name,
                "count": len(ids),
                "subcategories": [
                    {"name": sub, "count": len(sub_ids)}
                    for sub, sub_ids in sorted(
                        subgroups[name].items(), key=lambda item: -len(item[1])
                    )
                ],
                "previews": previews,
            }
        )

    return sorted(result, key=lambda item: -item["count"])


def category_model_ids(db: Session, name: str) -> list[str]:
    settings = get_settings()
    root = get_setting(db, "import_dir", settings.import_dir or "").strip()
    rows = db.execute(select(ImportFile.source_path, ImportFile.model_id)).all()
    ids: list[str] = []
    seen: set[str] = set()
    for source, model_id in rows:
        parts = _relative_parts(source, root)
        category = parts[0] if parts else "Прочее"
        if category == name and model_id not in seen:
            seen.add(model_id)
            ids.append(model_id)
    return ids
