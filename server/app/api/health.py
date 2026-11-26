from __future__ import annotations

from fastapi import APIRouter

from ..config import get_settings
from ..schemas import HealthRead
from ..services.cad import find_freecad, has_cadquery
from ..services.geometry import SUPPORTED_EXTENSIONS
from ..services.slicer import find_slicers

router = APIRouter(prefix="/api", tags=["health"])


@router.get("/health", response_model=HealthRead)
def health() -> HealthRead:
    settings = get_settings()
    return HealthRead(
        status="ok",
        version=settings.version,
        formats=sorted(SUPPORTED_EXTENSIONS),
        cad=find_freecad() is not None or has_cadquery(),
        slicer=bool(find_slicers()),
        slicers=[item["name"] for item in find_slicers()],
        auth_required=bool(settings.api_token),
    )
