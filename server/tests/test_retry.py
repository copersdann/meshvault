import io

import trimesh
from fastapi.testclient import TestClient
from sqlalchemy import select

from app.db import SessionLocal
from app.models import ModelVersion


def test_retry_failed_version(client: TestClient) -> None:
    buffer = io.BytesIO()
    trimesh.creation.box(extents=(71, 73, 79)).export(buffer, file_type="stl")
    created = client.post(
        "/api/models",
        files={"file": ("retry.stl", buffer.getvalue(), "application/octet-stream")},
    )
    assert created.status_code == 201, created.text
    model_id = created.json()["id"]

    db = SessionLocal()
    try:
        row = db.scalar(select(ModelVersion).where(ModelVersion.model_id == model_id))
        assert row is not None
        row.status = "failed"
        row.error = "тестовая ошибка"
        db.commit()
    finally:
        db.close()

    response = client.post(f"/api/models/{model_id}/versions/1/retry")
    assert response.status_code == 200, response.text

    latest = client.get(f"/api/models/{model_id}").json()["latest_version"]
    assert latest["status"] == "ready"
    assert not latest["error"]
