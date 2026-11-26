from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

REPO_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="MESHVault_",
        env_file=REPO_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_name: str = "MeshVault"
    version: str = "0.1.0"

    data_dir: Path = REPO_ROOT / "data"
    database_url: str | None = None

    api_token: str | None = None
    secret_key: str = "change-me-in-production"

    cors_origins: str = (
        "http://localhost:5173,http://127.0.0.1:5173,"
        "http://localhost:4173,http://localhost,"
        "https://localhost,capacitor://localhost"
    )

    freecad_path: str | None = None
    slicer_path: str | None = None
    thumbnail_size: int = 512

    curaengine_path: str | None = None
    prusaslicer_path: str | None = None

    max_upload_mb: int = 512

    import_dir: str | None = None
    import_interval_min: int = 5

    @property
    def db_url(self) -> str:
        if self.database_url:
            return self.database_url
        return f"sqlite:///{(self.data_dir / 'meshvault.db').as_posix()}"

    @property
    def cors_origin_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def web_dist(self) -> Path:
        return REPO_ROOT / "apps" / "web" / "dist"


@lru_cache
def get_settings() -> Settings:
    return Settings()
