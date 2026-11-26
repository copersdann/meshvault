import io

import trimesh
from fastapi.testclient import TestClient


def _stl_bytes() -> bytes:
    box = trimesh.creation.box(extents=(20, 30, 40))
    buffer = io.BytesIO()
    box.export(buffer, file_type="stl")
    return buffer.getvalue()


def test_health(client: TestClient) -> None:
    response = client.get("/api/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert ".stl" in data["formats"]
    assert "cad" in data


def test_upload_and_convert(client: TestClient) -> None:
    response = client.post(
        "/api/models",
        files={"file": ("cube.stl", _stl_bytes(), "application/octet-stream")},
        data={"name": "Тестовый куб", "tags": "тест, куб"},
    )
    assert response.status_code == 201, response.text
    model = response.json()
    assert model["name"] == "Тестовый куб"
    assert [tag["name"] for tag in model["tags"]] == ["тест", "куб"]

    detail = client.get(f"/api/models/{model['id']}")
    assert detail.status_code == 200
    version = detail.json()["latest_version"]
    assert version["status"] == "ready", version
    assert version["glb_url"] is not None
    assert version["bbox_x"] == 20.0
    assert version["bbox_y"] == 30.0
    assert version["bbox_z"] == 40.0
    assert version["triangles"] == 12

    glb = client.get(version["glb_url"])
    assert glb.status_code == 200
    assert glb.content[:4] == b"glTF"


def test_search_and_tags(client: TestClient) -> None:
    response = client.get("/api/models", params={"q": "куб"})
    assert response.status_code == 200
    assert response.json()["total"] >= 1

    tags = client.get("/api/tags")
    assert any(tag["name"] == "тест" for tag in tags.json())


def test_printer_and_print_validation(client: TestClient) -> None:
    response = client.post(
        "/api/printers",
        json={
            "name": "Voron",
            "host": "192.168.1.50:7125",
            "api_key": "secret",
            "profile_json": {"bed_x": 300, "bed_y": 300},
        },
    )
    assert response.status_code == 201, response.text
    printer = response.json()
    assert printer["has_api_key"] is True
    assert "api_key" not in printer

    models = client.get("/api/models").json()["items"]
    model_id = models[0]["id"]
    response = client.post(
        f"/api/models/{model_id}/print",
        json={"printer_id": printer["id"]},
    )
    assert response.status_code == 400
    assert "G-code" in response.json()["detail"]
