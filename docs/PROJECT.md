# MeshVault — описание проекта

## Что это
MeshVault — локальный каталог и библиотека 3D-моделей для 3D-печати. Сервер на ПК хранит модели и метаданные, готовит превью и метрики, отдаёт их Qt-клиенту и управляет печатью через Moonraker. Это self-hosted решение для дома/мастерской, не облачный сервис.

## Зачем
- Держать все STL/3MF/OBJ/STEP/IGES и другие модели в одном месте с поиском, тегами и коллекциями.
- Быстро смотреть 3D-превью и габариты без открытия тяжёлых CAD/слайсеров.
- Готовить модель к печати: проверка пригодности, экспорт в нужный формат, отправка в установленный слайсер.
- Печатать на Klipper-принтере через Moonraker и видеть очередь/историю.

## Ключевые возможности (реализовано)
- Загрузка и автоимпорт из каталога, дедупликация по sha256.
- Форматы: STL, 3MF, OBJ, GLB/glTF, PLY, AMF, STEP/STP, IGES/IGS, G-code/BGCode, LYS.
- STEP/IGES через FreeCADCmd (headless) с запасным CadQuery для STEP.
- Нормализация в GLB и миниатюры встроенными средствами (trimesh/Pillow), габариты/объём/треугольники/замкнутость.
- Библиотека: поиск, теги, коллекции, версии, карточка модели.
- Экспорт в STL/OBJ/GLB/PLY/3MF.
- Корзина: мягкое удаление, восстановление, окончательное удаление.
- Printability: замкнутость, нависания, масштаб, соответствие столу принтера.
- Отправка модели в любой установленный слайсер (PrusaSlicer/Orca/Cura/Flashforge).
- Печать через Moonraker: загрузка G-code, запуск, пауза/отмена, статус, история.
- Drag&drop и пакетная загрузка в Qt-клиенте.
- Опциональный Bearer-токен, шифрование API-ключей принтеров.

## Архитектура
```
Qt Desktop (QML + Qt Quick 3D, C++ ApiClient)
        │ HTTP/JSON
FastAPI (server/app)
  ├─ api/        REST: models, printers, jobs, meta, importer, health
  ├─ services/   geometry, cad, export, printability, slicer, processor,
  │              storage, thumbnails, moonraker, importer, security
  └─ freecad/    headless-скрипт STEP/IGES → STL
SQLite (data/meshvault.db) + content-addressed блобы (data/blobs)
```
Принципы: база только на сервере; оригиналы неизменяемы; тяжёлая геометрия на сервере; файлы не в БД.

## Стек
- Сервер: Python 3.10+, FastAPI, uvicorn, SQLAlchemy 2, SQLite (WAL), trimesh/numpy, Pillow, networkx/lxml (3MF), cryptography, httpx.
- Клиент: Qt 6.8+ (Quick, QuickControls2, Quick3D, QuickDialogs2), C++17, CMake/Ninja/MSVC.
- Внешние (опционально): FreeCAD, GUI-слайсер, Moonraker.

## Данные
- `models`, `model_versions`, `blobs`, `tags`, `collections`, `printers`, `print_jobs`, `app_settings`, `import_files`.
- Блобы: `data/blobs/<aa>/<bb>/<sha256>`. Производные: GLB, PNG, G-code.
- Мягкое удаление: `models.deleted_at`.
- Миграции: лёгкие аддитивные через `db.ensure_schema()` (без Alembic).

## API (основное)
```
GET  /api/health
GET/POST /api/models            (?trash=true для корзины)
GET/PATCH/DELETE /api/models/{id}   (?permanent=true)
POST /api/models/{id}/restore
POST /api/models/{id}/versions
GET  /api/models/{id}/files/{original|glb|thumb|gcode}
GET  /api/models/{id}/versions/{v}/export?format=stl|obj|glb|ply|3mf
GET  /api/models/{id}/versions/{v}/printability?printer_id=
GET  /api/slicers
POST /api/models/{id}/versions/{v}/open-in-slicer
POST /api/models/{id}/versions/{v}/gcode
POST /api/models/{id}/print
GET/POST/PATCH/DELETE /api/printers...
GET  /api/jobs ; POST /api/jobs/{id}/pause|resume|cancel
GET/PUT /api/import/config ; POST /api/import/scan
```

## Статус
- Backend и Qt-клиент собираются и работают; тесты сервера зелёные.
- Реализован спринт: отправка в слайсер, drag&drop, экспорт, корзина, printability.
- Серверный слайсинг сознательно не делается — используется внешний слайсер.
- Web-клиент (`apps/web`) не реализован; Android-клиент (`apps/android`, Kotlin/Compose) работает:
  библиотека, загрузка файлов с телефона, серверный поиск/фильтры, редактирование модели,
  прикрепление G-code, отправка в слайсер и печать, 3D-просмотр с инструментами и послойным просмотром G-code.
- Ядро слайсера в Android не встраивается (AGPL-3.0, >2 ГБ нативных библиотек): используется
  отправка модели в установленный слайсер через сервер. Слайсинг на сервере не добавляется.
- Git-репозиторий в корне проекта, ветка `main`: https://github.com/copersdann/meshvault.
- Постоянный каталог автоимпорта: `H:\01_ПРОЕКТЫ\3D_pechat` (1328 файлов, ~735 МБ; задан в `.env` и в БД через `/api/import/config`, интервал 15 мин).

## Дорожная карта (отложено, топ-20)
Избранные/недавние, LOD для больших моделей, измерения/сечения в 3D, расширенный поиск,
поиск дубликатов, доп. форматы (FBX/DAE/архивы), проекты/коллекции 2.0, дашборд,
бэкап/восстановление, уведомления, очередь/профили материалов, web-клиент,
мультипользовательский режим.

## Документы
- [README.md](../README.md) — быстрый старт.
- [ARCHITECTURE.md](ARCHITECTURE.md) — архитектура и решения.
- [REQUIREMENTS.md](REQUIREMENTS.md) — требования и зависимости.
- [MOONRAKER.md](MOONRAKER.md) — настройка печати.
- [ANDROID.md](ANDROID.md) — Android-клиент (Kotlin/Compose).
- [DEVELOPMENT.md](DEVELOPMENT.md) — архитектура и практики разработки.
