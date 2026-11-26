"""Convert a STEP/IGES file to STL inside FreeCADCmd.

Usage:
    FreeCADCmd.exe cad_to_stl.py --pass <source> <destination>
"""

from __future__ import annotations

import sys

import FreeCAD  # noqa: F401  # Initializes the FreeCAD runtime.
import MeshPart
import Part

LINEAR_DEFLECTION = 0.05
ANGULAR_DEFLECTION = 0.1


def _arguments() -> list[str]:
    argv = sys.argv
    if "--pass" in argv:
        return argv[argv.index("--pass") + 1 :]
    return argv[1:]


def main() -> int:
    args = _arguments()
    if len(args) < 2:
        print("usage: cad_to_stl.py --pass <source> <destination>", file=sys.stderr)
        return 2

    source, destination = args[-2], args[-1]
    shape = Part.read(source)
    if shape.isNull():
        print("FreeCAD produced an empty shape", file=sys.stderr)
        return 1

    mesh = MeshPart.meshFromShape(
        Shape=shape,
        LinearDeflection=LINEAR_DEFLECTION,
        AngularDeflection=ANGULAR_DEFLECTION,
        Relative=False,
    )
    if not mesh.Facets:
        print("FreeCAD produced an empty mesh", file=sys.stderr)
        return 1

    mesh.write(destination)
    print(f"CAD mesh: {len(mesh.Facets)} facets")
    return 0


main()
