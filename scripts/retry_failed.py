"""Re-process model versions left in 'failed' after a server restart."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

from sqlalchemy import select  # noqa: E402

from app.db import SessionLocal  # noqa: E402
from app.models import ModelVersion  # noqa: E402
from app.services.processor import process_version  # noqa: E402


def main() -> None:
    db = SessionLocal()
    ids = list(
        db.scalars(select(ModelVersion.id).where(ModelVersion.status == "failed")).all()
    )
    db.close()
    print(f"retrying {len(ids)} versions", flush=True)
    for index, version_id in enumerate(ids, 1):
        try:
            process_version(version_id)
        except Exception as exc:  # noqa: BLE001
            print(f"{index}: error {exc}", flush=True)
        if index % 25 == 0:
            print(f"processed {index}/{len(ids)}", flush=True)
    print("DONE", flush=True)


if __name__ == "__main__":
    main()
