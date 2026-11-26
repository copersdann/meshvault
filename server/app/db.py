from collections.abc import Generator

from sqlalchemy import create_engine, event, inspect, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import get_settings

settings = get_settings()
settings.data_dir.mkdir(parents=True, exist_ok=True)

_is_sqlite = settings.db_url.startswith("sqlite")

engine = create_engine(
    settings.db_url,
    connect_args={"check_same_thread": False} if _is_sqlite else {},
    future=True,
)

if _is_sqlite:

    @event.listens_for(engine, "connect")
    def _set_sqlite_pragma(dbapi_connection, _connection_record) -> None:
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA journal_mode=WAL")
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()


SessionLocal = sessionmaker(
    bind=engine,
    autoflush=False,
    autocommit=False,
    expire_on_commit=False,
)


class Base(DeclarativeBase):
    pass


def ensure_schema() -> None:
    """Create tables and apply lightweight additive migrations."""
    Base.metadata.create_all(engine)
    inspector = inspect(engine)
    if "models" in inspector.get_table_names():
        columns = {column["name"] for column in inspector.get_columns("models")}
        if "deleted_at" not in columns:
            with engine.begin() as conn:
                conn.execute(text("ALTER TABLE models ADD COLUMN deleted_at DATETIME"))
        for name, definition in (
            ("source_url", "VARCHAR(2048)"),
            ("source_author", "VARCHAR(255)"),
            ("source_license", "VARCHAR(64)"),
        ):
            if name not in columns:
                with engine.begin() as conn:
                    conn.execute(text(f"ALTER TABLE models ADD COLUMN {name} {definition}"))
    tables = set(inspector.get_table_names())
    if "import_files" in tables:
        columns = {column["name"] for column in inspector.get_columns("import_files")}
        if "duplicate_of" not in columns:
            with engine.begin() as conn:
                conn.execute(text("ALTER TABLE import_files ADD COLUMN duplicate_of VARCHAR(36)"))
        # Заполняем ссылку на оригинал для дубликатов, импортированных до миграции.
        if "model_versions" in tables:
            with engine.begin() as conn:
                conn.execute(
                    text(
                        "UPDATE import_files SET duplicate_of = ("
                        "  SELECT model_id FROM model_versions"
                        "  WHERE model_versions.sha256 = import_files.sha256 LIMIT 1"
                        ") WHERE model_id = '' AND duplicate_of IS NULL"
                    )
                )


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
