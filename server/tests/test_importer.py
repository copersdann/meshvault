import io

import trimesh
from fastapi.testclient import TestClient


def _stl(extents: tuple[int, int, int]) -> bytes:
    buffer = io.BytesIO()
    trimesh.creation.box(extents=extents).export(buffer, file_type="stl")
    return buffer.getvalue()


def test_scan_counts_duplicates(client: TestClient, tmp_path) -> None:
    original = _stl((11, 23, 37))
    other = _stl((41, 43, 47))
    (tmp_path / "a.stl").write_bytes(original)
    (tmp_path / "b.stl").write_bytes(original)  # побайтовая копия
    (tmp_path / "c.stl").write_bytes(other)

    config = client.put(
        "/api/import/config",
        json={"directory": str(tmp_path), "interval_min": 15},
    )
    assert config.status_code == 200, config.text

    scan = client.post("/api/import/scan")
    assert scan.status_code == 200, scan.text
    result = scan.json()
    assert result["found"] == 3
    assert result["added"] == 2
    assert result["skipped"] == 1
    assert result["duplicates"] == 1
    assert result["errors"] == 0

    # Повторное сканирование не создаёт новые модели и помнит дубликат.
    again = client.post("/api/import/scan").json()
    assert again["added"] == 0
    assert again["duplicates"] == 1

    reported = client.get("/api/import/config").json()["last_result"]
    assert reported["duplicates"] == 1


def test_dry_run_does_not_write(client: TestClient, tmp_path) -> None:
    data = _stl((53, 59, 61))
    (tmp_path / "new.stl").write_bytes(data)
    (tmp_path / "new-copy.stl").write_bytes(data)

    config = client.put(
        "/api/import/config",
        json={"directory": str(tmp_path), "interval_min": 15},
    )
    assert config.status_code == 200, config.text
    before = client.get("/api/models").json()["total"]

    preview = client.post("/api/import/scan", params={"dry_run": True})
    assert preview.status_code == 200, preview.text
    result = preview.json()
    assert result["found"] == 2
    assert result["added"] == 1
    assert result["duplicates"] == 1

    # Предпросмотр не меняет ни модели, ни отчёт последнего сканирования.
    assert client.get("/api/models").json()["total"] == before
    last_result = client.get("/api/import/config").json()["last_result"]
    assert last_result is None or last_result.get("found") != 2
