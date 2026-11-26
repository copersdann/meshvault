from __future__ import annotations

import json
import subprocess
from pathlib import Path

import pytest

from app.services.cad import find_freecad
from app.services.geometry import analyze_file

FREECAD = find_freecad()


def _make_box(tmp_path: Path, extension: str) -> Path:
    target = tmp_path / f"box{extension}"
    script = tmp_path / "make_box.py"
    script.write_text(
        "import FreeCAD as App\n"
        "import Part\n"
        "doc = App.newDocument('box')\n"
        "box = doc.addObject('Part::Box', 'Box')\n"
        "box.Length = 10.0\n"
        "box.Width = 20.0\n"
        "box.Height = 30.0\n"
        "doc.recompute()\n"
        f"Part.export([box], {json.dumps(str(target))})\n"
        "App.closeDocument(doc.Name)\n",
        encoding="utf-8",
    )
    completed = subprocess.run(
        [FREECAD, str(script)],
        capture_output=True,
        text=True,
        timeout=180,
        check=False,
    )
    assert target.is_file() and target.stat().st_size > 100, completed.stderr
    return target


@pytest.mark.skipif(not FREECAD, reason="FreeCAD is not installed")
@pytest.mark.parametrize("extension", [".step", ".iges"])
def test_cad_import_via_freecad(tmp_path: Path, extension: str) -> None:
    path = _make_box(tmp_path, extension)

    result = analyze_file(path, extension)

    assert result.bbox_x == pytest.approx(10.0)
    assert result.bbox_y == pytest.approx(20.0)
    assert result.bbox_z == pytest.approx(30.0)
    assert result.triangles >= 12
    assert result.watertight is True
    assert result.glb is not None
    assert result.glb[:4] == b"glTF"
