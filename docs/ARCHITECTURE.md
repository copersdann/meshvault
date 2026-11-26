# Архитектура MeshVault

## Принципы

1. **База только на сервере.** Клиенты не хранят авторитетную БД — только кэш миниатюр и GLB.
2. **Тяжёлая геометрия на сервере.** Импорт, конвертация, миниатюры, слайсинг, отправка на принтер.
3. **Оригинал не изменяется.** Рядом создаются производные: GLB для просмотра, PNG-миниатюра, G-code.
4. **Файлы не в БД.** В SQLite только метаданные; блобы лежат в `data/blobs/<aa>/<bb>/<sha256>`.

## Слои

```
Qt Desktop (QML)
  ├─ Theme.qml, ModelCard.qml, ModelViewer3D.qml, страницы
  └─ ApiClient (C++) — REST, токен, загрузка, кэш GLB
        │  HTTP/JSON
        ▼
FastAPI (server/)
  ├─ API: /api/models, /api/printers, /api/jobs, /api/tags, /api/health
  ├─ storage.py   — content-addressed блобы
  ├─ geometry.py  — trimesh/Assimp/OCCT → GLB, габариты, объём
  ├─ thumbnails.py  — PNG-миниатюры через Pillow (без внешних программ)
  ├─ moonraker.py — адаптер принтера
  └─ processor.py — фоновый конвейер обработки версии
        │
  SQLite + data/blobs
```

## Модель данных

- `models` — название, описание, поисковый текст.
- `model_versions` — формат, sha256, bbox, объём, треугольники, статус, ссылки на блобы (original/glb/thumb/gcode).
- `blobs` — sha256, путь, MIME, размер.
- `tags`, `collections` — связи many-to-many.
- `printers` — хост, зашифрованный API-ключ, профиль стола.
- `print_jobs` — история печати: модель, принтер, G-code, статус, прогресс.

## Конвейер обработки версии

1. Загрузка оригинала → sha256 → blob.
2. Создание `ModelVersion(status="processing")`, фоновая задача.
3. `analyze_file()` читает файл через trimesh; STEP/IGES — через FreeCADCmd (headless), с запасным путём CadQuery для STEP.
4. Экспорт в GLB; расчёт габаритов, объёма, замкнутости.
5. Встроенный генератор через Pillow создаёт PNG-миниатюру (внешние рендеры не нужны).
6. Статус `ready` / `failed` с текстом ошибки.

## Автоимпорт

`services/importer.py` обходит настроенную папку, дедуплицирует по sha256, создаёт модели/версии, теги из имён папок и коллекции из верхней папки. Состояние хранится в таблицах `app_settings` и `import_files`. Фоновое сканирование запускается в `lifespan` сервера; ручное — через `POST /api/import/scan`.

## Печать

```
модель + принтер → G-code (уже загружен) → Moonraker upload → start
                  ← статус каждые 3 с ← /printer/objects/query
```

Серверный слайсинг не планируется: модель отправляется во внешний GUI-слайсер. Для CLI-адаптера в `.env` предусмотрен `MESHVault_PRUSASLICER_PATH`; OrcaSlicer определяется автоматически или добавляется вручную.

## API (основное)

```
GET    /api/health
POST   /api/models
GET    /api/models?q=&tag=&collection_id=
GET    /api/models/{id}
PATCH  /api/models/{id}
DELETE /api/models/{id}
POST   /api/models/{id}/versions
POST   /api/models/{id}/versions/{v}/gcode
GET    /api/models/{id}/files/{variant}?version=&download=
POST   /api/models/{id}/print
GET/POST/DELETE /api/printers...
GET    /api/jobs
POST   /api/jobs/{id}/pause|resume|cancel
```

## Решения и риски

- **GLB вместо нативного парсинга на клиенте** — Qt Quick 3D умеет glTF из коробки, сервер нормализует все форматы.
- **Единицы:** STL безразмерный, считаем мм; glTF формально метры, но в 3D-печати почти всегда мм — показываем мм.
- **Большие меши:** сервер может отдавать упрощённый GLB/LOD; сейчас этого нет.
- **STEP/IGES:** конвертируются в STL через FreeCADCmd (`app/freecad/cad_to_stl.py`); если FreeCAD нет, для STEP доступен CadQuery из extra `cad`.
- **Лицензии:** Qt — LGPLv3/коммерческая; PrusaSlicer/Orca — AGPL; OCCT — LGPL с исключением.
