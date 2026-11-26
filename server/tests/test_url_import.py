import io

import trimesh
from fastapi.testclient import TestClient

from app.services import url_import


def _stl(extents: tuple[int, int, int]) -> bytes:
    buffer = io.BytesIO()
    trimesh.creation.box(extents=extents).export(buffer, file_type="stl")
    return buffer.getvalue()


def test_import_url_deduplicates_and_saves_source(client: TestClient, monkeypatch) -> None:
    data = _stl((83, 89, 97))
    monkeypatch.setattr(url_import, "fetch_url", lambda url: [(data, "url-cube.stl")])
    url = "https://example.com/models/url-cube.stl"

    first = client.post("/api/import/url", json={"url": url})
    assert first.status_code == 200, first.text
    assert first.json()["added"] == 1

    second = client.post("/api/import/url", json={"url": url})
    assert second.status_code == 200, second.text
    assert second.json()["added"] == 0
    assert second.json()["duplicates"] == 1

    models = client.get("/api/models").json()["items"]
    model = next(item for item in models if item["name"] == "url-cube")
    assert model["source_url"] == url


def test_import_url_rejects_non_http(client: TestClient) -> None:
    response = client.post("/api/import/url", json={"url": "file:///etc/passwd"})
    assert response.status_code == 400
