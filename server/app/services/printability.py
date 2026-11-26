from __future__ import annotations

import trimesh
from sqlalchemy.orm import Session

from ..models import Blob, ModelVersion
from .storage import storage

OVERHANG_NORMAL_Z = -0.7071  # ~45 degrees from vertical
BED_CONTACT_EPS = 0.5  # mm


def _issue(code: str, severity: str, message: str) -> dict:
    return {"code": code, "severity": severity, "message": message}


def _load_mesh(db: Session, version: ModelVersion) -> trimesh.Trimesh | None:
    if not version.glb_blob_id:
        return None
    blob = db.get(Blob, version.glb_blob_id)
    if blob is None:
        return None
    path = storage.path_for(blob)
    if not path.is_file():
        return None
    try:
        scene = trimesh.load(path, file_type="glb", force="scene")
        meshes = [
            geometry
            for geometry in scene.geometry.values()
            if isinstance(geometry, trimesh.Trimesh)
        ]
        if not meshes:
            return None
        if len(meshes) == 1:
            return meshes[0]
        return trimesh.util.concatenate(meshes)
    except Exception:
        return None


def analyze_printability(
    version: ModelVersion,
    printer_profile: dict | None = None,
    db: Session | None = None,
) -> dict:
    issues: list[dict] = []

    if version.watertight is False:
        issues.append(
            _issue(
                "not_watertight",
                "warning",
                "Меш не замкнут; слайсеру может потребоваться исправление.",
            )
        )

    overhang_fraction = 0.0
    mesh = _load_mesh(db, version) if db is not None else None
    if mesh is not None and len(mesh.faces):
        normals = mesh.face_normals
        areas = mesh.area_faces
        centers = mesh.triangles_center
        min_z = float(mesh.bounds[0][2])
        steep = normals[:, 2] < OVERHANG_NORMAL_Z
        above_bed = centers[:, 2] > min_z + BED_CONTACT_EPS
        mask = steep & above_bed
        total_area = float(areas.sum()) or 1.0
        overhang_fraction = float(areas[mask].sum()) / total_area
        if overhang_fraction > 0.05:
            issues.append(
                _issue(
                    "overhangs",
                    "warning",
                    f"{overhang_fraction * 100:.0f}% поверхности — крутые нависания; "
                    "могут понадобиться поддержки.",
                )
            )
        if not mesh.is_winding_consistent:
            issues.append(
                _issue(
                    "winding",
                    "warning",
                    "Несогласованная ориентация граней; часть нормалей может быть перевёрнута.",
                )
            )

    bx, by, bz = version.bbox_x, version.bbox_y, version.bbox_z
    if None not in (bx, by, bz):
        max_extent = max(bx, by, bz)
        min_extent = min(bx, by, bz)
        if max_extent > 1000:
            issues.append(
                _issue(
                    "scale_large",
                    "warning",
                    "Модель больше 1000 мм; проверьте единицы измерения и масштаб.",
                )
            )
        if min_extent < 0.5:
            issues.append(
                _issue(
                    "scale_small",
                    "warning",
                    "Есть размер меньше 0.5 мм; проверьте единицы измерения и масштаб.",
                )
            )
        if printer_profile:
            bed_x = float(printer_profile.get("bed_x", 220))
            bed_y = float(printer_profile.get("bed_y", 220))
            bed_z = float(printer_profile.get("bed_z", 250))
            if bx > bed_x or by > bed_y or bz > bed_z:
                issues.append(
                    _issue(
                        "bed_fit",
                        "error",
                        f"Модель ({bx:.0f}×{by:.0f}×{bz:.0f} мм) больше стола принтера "
                        f"({bed_x:.0f}×{bed_y:.0f}×{bed_z:.0f} мм).",
                    )
                )

    if version.triangles and version.triangles > 1_000_000:
        issues.append(
            _issue(
                "heavy_mesh",
                "info",
                "Более 1 млн треугольников; просмотр и слайсинг могут быть медленными.",
            )
        )

    return {
        "watertight": version.watertight,
        "overhang_fraction": round(overhang_fraction, 4),
        "issues": issues,
    }
