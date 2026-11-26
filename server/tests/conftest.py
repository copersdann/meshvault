import os
import tempfile
from pathlib import Path

# Отдельный ASCII-корень для временных файлов pytest: системный pytest-of-Admin
# может быть недоступен (WinError 5), а FreeCADCmd падает на кириллических путях.
TEMP_ROOT = Path(tempfile.gettempdir()) / "meshvault-pytest"
TEMP_ROOT.mkdir(parents=True, exist_ok=True)
os.environ["PYTEST_DEBUG_TEMPROOT"] = str(TEMP_ROOT)

TEST_DIR = tempfile.mkdtemp(prefix="meshvault-test-")
os.environ["MESHVault_DATA_DIR"] = TEST_DIR
os.environ["MESHVault_THUMBNAILS_ENABLED"] = "false"

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture(scope="session")
def client() -> TestClient:
    with TestClient(app) as test_client:
        yield test_client
