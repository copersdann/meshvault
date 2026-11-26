from __future__ import annotations

import glob
import importlib.util
import logging
import os
import shutil
import subprocess
import tempfile
from functools import lru_cache
from pathlib import Path

from ..config import get_settings

logger = logging.getLogger("meshvault.cad")

CONVERT_SCRIPT = Path(__file__).resolve().parents[1] / "freecad" / "cad_to_stl.py"
CONVERT_TIMEOUT = 240


class CadError(Exception):
    pass


def has_cadquery() -> bool:
    return importlib.util.find_spec("cadquery") is not None


@lru_cache(maxsize=1)
def find_freecad() -> str | None:
    configured = (get_settings().freecad_path or "").strip()
    if configured:
        path = Path(configured).expanduser()
        if path.is_file():
            return str(path)
        if path.is_dir():
            for name in ("FreeCADCmd.exe", "FreeCADCmd", "freecadcmd"):
                candidate = path / name
                if candidate.is_file():
                    return str(candidate)
                candidate = path / "bin" / name
                if candidate.is_file():
                    return str(candidate)
        logger.warning("Configured FreeCAD path is not an executable: %s", configured)

    for name in ("FreeCADCmd", "freecadcmd"):
        on_path = shutil.which(name)
        if on_path:
            return on_path

    patterns = [
        r"C:\Program Files\FreeCAD*\bin\FreeCADCmd.exe",
        r"C:\Program Files (x86)\FreeCAD*\bin\FreeCADCmd.exe",
        "/usr/bin/freecadcmd",
        "/usr/local/bin/freecadcmd",
        "/snap/bin/freecad.cmd",
        "/Applications/FreeCAD.app/Contents/MacOS/FreeCADCmd",
    ]
    candidates: list[str] = []
    for pattern in patterns:
        candidates.extend(glob.glob(pattern))
    if not candidates:
        return None
    return max(candidates)


@lru_cache(maxsize=1)
def _temp_root() -> Path:
    """Return an ASCII-only temporary directory.

    FreeCADCmd 1.1 on Windows cannot start a script or read arguments whose
    paths contain non-ASCII characters, so conversions are staged in a neutral
    directory even when the project lives under a Cyrillic path.
    """
    candidates = [Path(tempfile.gettempdir())]
    system_root = os.environ.get("SystemRoot")
    if system_root:
        candidates.append(Path(system_root) / "Temp")
    program_data = os.environ.get("ProgramData")
    if program_data:
        candidates.append(Path(program_data) / "MeshVault" / "tmp")
    candidates.append(Path.home() / ".meshvault" / "tmp")

    for candidate in candidates:
        if not str(candidate).isascii():
            continue
        try:
            candidate.mkdir(parents=True, exist_ok=True)
            probe = candidate / ".meshvault-write-test"
            probe.write_text("ok", encoding="ascii")
            probe.unlink()
        except OSError:
            continue
        return candidate

    raise CadError(
        "FreeCAD needs an ASCII-only temporary directory. "
        "Set TEMP to a path without non-ASCII characters."
    )


def convert_cad_to_stl(source: Path, destination: Path, extension: str = ".step") -> Path:
    """Mesh a STEP/IGES file with FreeCADCmd and write an STL file."""
    ext = extension.lower()
    if not ext.startswith("."):
        ext = f".{ext}"
    freecad = find_freecad()
    if not freecad:
        raise CadError(
            "FreeCAD was not found. Install FreeCAD or set MESHVault_FREECAD_PATH "
            "to FreeCADCmd.exe."
        )
    if not CONVERT_SCRIPT.is_file():
        raise CadError(f"FreeCAD conversion script is missing: {CONVERT_SCRIPT}")

    try:
        with tempfile.TemporaryDirectory(prefix="meshvault-cad-", dir=_temp_root()) as tmp:
            workdir = Path(tmp)
            local_script = workdir / "cad_to_stl.py"
            local_source = workdir / f"source{ext}"
            local_output = workdir / "model.stl"
            shutil.copyfile(CONVERT_SCRIPT, local_script)
            shutil.copyfile(source, local_source)

            command = [
                freecad,
                str(local_script),
                "--pass",
                str(local_source),
                str(local_output),
            ]
            completed = subprocess.run(
                command,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=CONVERT_TIMEOUT,
                check=False,
            )

            if not local_output.is_file() or local_output.stat().st_size < 100:
                output = "\n".join(
                    part.strip()
                    for part in (completed.stdout, completed.stderr)
                    if part and part.strip()
                )
                tail = "\n".join(output.splitlines()[-12:])
                message = f"FreeCAD could not convert {source.name} to a mesh."
                if tail:
                    message = f"{message}\n{tail}"
                raise CadError(message)

            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(local_output, destination)
    except subprocess.TimeoutExpired as exc:
        raise CadError(
            f"FreeCAD did not finish within {CONVERT_TIMEOUT} seconds."
        ) from exc
    except OSError as exc:
        raise CadError(f"Could not run FreeCAD conversion: {exc}") from exc

    logger.info("Converted CAD file %s via FreeCAD", source.name)
    return destination

