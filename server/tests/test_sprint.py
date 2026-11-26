from __future__ import annotations

import io
from pathlib import Path

import trimesh
from fastapi.testclient import TestClient


def _stl_bytes() -> bytes:
    box = trimesh.creation.box(extents=(20, 30, 40))
    buffer = io.BytesIO()
    box.export(buffer, file_type="stl")
    return buffer.getvalue()


def _upload(client: TestClient, name: str = "sprint-cube") -> dict:
    response = client.post(
        "/api/models",
        files={"file": (f"{name}.stl", _stl_bytes(), "application/octet-stream")},
        data={"name": name},
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_trash_and_restore(client: TestClient) -> None:
    model = _upload(client, "trash-me")
    model_id = model["id"]

    assert client.delete(f"/api/models/{model_id}").status_code == 204
    assert client.get(f"/api/models/{model_id}").status_code == 404

    listed = client.get("/api/models").json()["items"]
    assert all(item["id"] != model_id for item in listed)

    trash = client.get("/api/models", params={"trash": True}).json()["items"]
    assert any(item["id"] == model_id for item in trash)

    restored = client.post(f"/api/models/{model_id}/restore")
    assert restored.status_code == 200
    assert restored.json()["deleted_at"] is None
    assert client.get(f"/api/models/{model_id}").status_code == 200

    assert client.delete(f"/api/models/{model_id}", params={"permanent": True}).status_code == 204
    assert client.get(f"/api/models/{model_id}").status_code == 404


def test_export_formats(client: TestClient) -> None:
    model = _upload(client, "export-me")
    model_id = model["id"]
    base = f"/api/models/{model_id}/versions/1/export"

    glb = client.get(base, params={"format": "glb"})
    assert glb.status_code == 200
    assert glb.content[:4] == b"glTF"

    stl = client.get(base, params={"format": "stl"})
    assert stl.status_code == 200
    assert len(stl.content) > 0

    obj = client.get(base, params={"format": "obj"})
    assert obj.status_code == 200
    assert b"v " in obj.content

    ply = client.get(base, params={"format": "ply"})
    assert ply.status_code == 200
    assert ply.content[:3] == b"ply"

    threemf = client.get(base, params={"format": "3mf"})
    assert threemf.status_code == 200
    assert threemf.content[:2] == b"PK"

    bad = client.get(base, params={"format": "xyz"})
    assert bad.status_code == 400


def test_printability(client: TestClient) -> None:
    model = _upload(client, "printability-me")
    model_id = model["id"]
    url = f"/api/models/{model_id}/versions/1/printability"

    result = client.get(url).json()
    assert result["watertight"] is True
    assert not any(issue["code"] == "bed_fit" for issue in result["issues"])

    printer = client.post(
        "/api/printers",
        json={
            "name": "Tiny",
            "host": "192.168.1.60:7125",
            "profile_json": {"bed_x": 5, "bed_y": 5, "bed_z": 5},
        },
    ).json()
    result = client.get(url, params={"printer_id": printer["id"]}).json()
    assert any(issue["code"] == "bed_fit" for issue in result["issues"])


def test_slicers_and_open(client: TestClient, monkeypatch) -> None:
    model = _upload(client, "slicer-me")
    model_id = model["id"]
    fake = [{"id": "fake", "name": "FakeSlicer", "path": "C:/fake/slicer.exe"}]

    monkeypatch.setattr("app.api.models.find_slicers", lambda: fake)
    monkeypatch.setattr("app.services.slicer.find_slicers", lambda: fake)

    launched: list[list[str]] = []

    class FakePopen:
        def __init__(self, cmd, **kwargs):
            launched.append(cmd)

    monkeypatch.setattr("app.services.slicer.subprocess.Popen", FakePopen)

    listed = client.get("/api/slicers")
    assert listed.status_code == 200
    assert listed.json()[0]["name"] == "FakeSlicer"

    response = client.post(
        f"/api/models/{model_id}/versions/1/open-in-slicer", json={}
    )
    assert response.status_code == 200, response.text
    data = response.json()
    assert data["slicer"] == "FakeSlicer"
    assert Path(data["file"]).is_file()
    assert launched and launched[0][0] == "C:/fake/slicer.exe"
