from __future__ import annotations

import tempfile
from dataclasses import dataclass
from pathlib import Path

import trimesh
from trimesh.transformations import translation_matrix

from .cad import CadError, convert_cad_to_stl, find_freecad, has_cadquery


class GeometryError(Exception):
    pass


SUPPORTED_EXTENSIONS = {
    ".stl",
    ".obj",
    ".3mf",
    ".glb",
    ".gltf",
    ".ply",
    ".amf",
    ".step",
    ".stp",
    ".iges",
    ".igs",
    ".gcode",
    ".bgcode",
    ".lys",
}

GCODE_EXTENSIONS = {".gcode", ".bgcode"}
CAD_EXTENSIONS = {".step", ".stp", ".iges", ".igs"}


@dataclass
class GeometryResult:
    glb: bytes | None = None
    bbox_x: float | None = None
    bbox_y: float | None = None
    bbox_z: float | None = None
    volume: float | None = None
    triangles: int = 0
    watertight: bool | None = None
    units: str = "mm"


def _safe_volume(mesh: trimesh.Trimesh) -> float:
    try:
        return abs(float(mesh.volume))
    except Exception:
        return 0.0


def _safe_watertight(mesh: trimesh.Trimesh) -> bool:
    try:
        return bool(mesh.is_watertight)
    except Exception:
        return False


def _cad_error(extension: str) -> GeometryError:
    if extension in {".step", ".stp"}:
        return GeometryError(
            "STEP import requires FreeCAD or CadQuery. Install FreeCAD "
            '(auto-detected) or run: uv pip install -e ".[cad]"'
        )
    return GeometryError(
        "IGES import requires FreeCAD. Install FreeCAD or set "
        "MESHVault_FREECAD_PATH to FreeCADCmd."
    )


def _load_cad_with_freecad(path: Path, extension: str) -> trimesh.Scene:
    with tempfile.TemporaryDirectory(prefix="meshvault-cad-") as directory:
        stl_path = Path(directory) / "model.stl"
        try:
            convert_cad_to_stl(path, stl_path, extension)
            return trimesh.load(stl_path, file_type="stl", force="scene")
        except CadError as exc:
            raise GeometryError(f"Could not read CAD file: {exc}") from exc
        except Exception as exc:
            raise GeometryError(f"Could not read CAD file: {exc}") from exc


def _load_cad_with_cadquery(path: Path) -> trimesh.Scene:
    try:
        import cadquery as cq
    except ImportError as exc:
        raise _cad_error(path.suffix.lower()) from exc

    try:
        shape = cq.importers.importStep(str(path))
        with tempfile.TemporaryDirectory(prefix="meshvault-cad-") as directory:
            stl_path = Path(directory) / "model.stl"
            cq.exporters.export(
                shape,
                str(stl_path),
                exportType="STL",
                tolerance=0.05,
                angularTolerance=0.1,
            )
            return trimesh.load(stl_path, file_type="stl", force="scene")
    except GeometryError:
        raise
    except Exception as exc:
        raise GeometryError(f"Could not read CAD file: {exc}") from exc


def _load_cad(path: Path, extension: str) -> trimesh.Scene:
    extension = extension.lower()
    if not extension.startswith("."):
        extension = f".{extension}"
    if find_freecad():
        try:
            return _load_cad_with_freecad(path, extension)
        except GeometryError:
            if extension not in {".step", ".stp"} or not has_cadquery():
                raise
    if extension in {".step", ".stp"} and has_cadquery():
        return _load_cad_with_cadquery(path)
    raise _cad_error(extension)


def _scene_meshes(scene: trimesh.Scene) -> list[trimesh.Trimesh]:
    return [
        geometry
        for geometry in scene.geometry.values()
        if isinstance(geometry, trimesh.Trimesh)
    ]


def analyze_file(path: Path, extension: str) -> GeometryResult:
    ext = extension.lower()
    if not ext.startswith("."):
        ext = f".{ext}"

    if ext in GCODE_EXTENSIONS:
        return GeometryResult()

    if ext in CAD_EXTENSIONS:
        scene = _load_cad(path, ext)
    else:
        try:
            scene = trimesh.load(path, file_type=ext, force="scene")
        except Exception as exc:
            raise GeometryError(f"Could not read {ext} file: {exc}") from exc

    meshes = _scene_meshes(scene)
    if not meshes:
        raise GeometryError("The file does not contain any triangle meshes.")

    try:
        bounds = scene.bounds
    except Exception as exc:
        raise GeometryError(f"Could not calculate model bounds: {exc}") from exc
    if bounds is None:
        raise GeometryError("Could not calculate model bounds.")

    extents = bounds[1] - bounds[0]

    # GLB для вьювера центрируем в начале координат: камера Qt Quick 3D
    # наводится на (0, 0, 0), а оригиналы часто лежат в углу стола.
    if ext != ".glb":
        center = (bounds[0] + bounds[1]) / 2.0
        scene.apply_transform(translation_matrix(-center))

    volume = sum(_safe_volume(mesh) for mesh in meshes)
    triangles = sum(len(mesh.faces) for mesh in meshes)
    watertight = all(_safe_watertight(mesh) for mesh in meshes)

    if ext == ".glb":
        glb = path.read_bytes()
    else:
        for mesh in meshes:
            visual = getattr(mesh, "visual", None)
            if visual is None or getattr(visual, "material", None) is None:
                mesh.visual = trimesh.visual.TextureVisuals(
                    material=trimesh.visual.material.SimpleMaterial(
                        diffuse=(210, 214, 220, 255)
                    )
                )
        try:
            glb = scene.export(file_type="glb", include_normals=True)
        except Exception as exc:
            raise GeometryError(f"Could not create a GLB preview: {exc}") from exc

    return GeometryResult(
        glb=bytes(glb),
        bbox_x=round(float(extents[0]), 3),
        bbox_y=round(float(extents[1]), 3),
        bbox_z=round(float(extents[2]), 3),
        volume=round(volume, 3),
        triangles=triangles,
        watertight=watertight,
    )
