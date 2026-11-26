# MeshVault — руководство разработчика

Документ описывает архитектуру, принятые решения и практические детали сборки.
Краткие команды и описание проекта — в `README.md` и `docs/PROJECT.md`.

---

## 1. Обзор проекта

MeshVault — локальный (self-hosted) каталог 3D-моделей для печати:

- **Сервер** FastAPI + SQLite + content-addressed блобы (`data/blobs/<aa>/<bb>/<sha256>`).
- **Десктоп-клиент** Qt 6.10 / QML / Qt Quick 3D (C++17, CMake/Ninja/MSVC).
- **Android-клиент** Kotlin + Jetpack Compose (вторичный, развивается отдельно).
- Оригиналы неизменяемы; производные (GLB, PNG-превью, G-code) — отдельные блобы.
- Серверный слайсинг сознательно не используется: только «Открыть в слайсере» (`services/slicer.py`).

---

## 2. Пути и запуск

| Что | Где |
|---|---|
| Проект | `F:\10_Разработка\Dev_programm\MeshVault` (кириллица в пути — нормально) |
| Git-корень | `F:\10_Разработка\Dev_programm\MeshVault` |
| Сервер | `server/`, venv `server/.venv` (Python 3.15) |
| Десктоп | `apps/desktop/`, сборка `build.bat` строго из этой папки |
| Результат сборки | `C:\tools\meshvault-build\MeshVaultDesktop.exe` |
| Тестовый релиз | `release/MeshVault-0.1.1-win64.zip`, ярлык на Рабочем столе |
| Данные | `data/` — не удалять; блобы и БД создаются автоматически |

Команды (PowerShell):

```powershell
cd server
$env:OPENBLAS_NUM_THREADS="1"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m ruff check app tests

cd ..\apps\desktop
cmd /c build.bat
```

Для телефона сервер слушает `0.0.0.0:8000`. Тесты используют ASCII-корень во временной
папке: FreeCADCmd 1.1 падает на кириллических временных путях.

---

## 3. Архитектура сервера

- `app/api/` — тонкие эндпоинты. `models.py` содержит и `/api/slicers` (GET/POST/DELETE).
- `app/services/processor.py` — фоновый конвейер: анализ → GLB → PNG → статус `ready`/`failed`.
- `app/services/geometry.py` — trimesh/FreeCAD/CadQuery → `GeometryResult`. GLB центрируется
  (`scene.apply_transform(translation_matrix(-center))`), чтобы камера смотрела в центр модели.
  Для `.glb`-оригиналов байты отдаются как есть.
- `app/services/thumbnails.py` — генерация миниатюр на trimesh + Pillow: изометрическая
  проекция с затенением по нормалям, сглаживание 2×, мягкая тень.
- `app/services/importer.py` — сканирование каталога с дедупликацией по SHA-256; дубликаты
  сохраняются в `import_files.duplicate_of` и не создают новые модели. Есть `dry_run`.
- `app/services/url_import.py` — локальный импорт по прямой ссылке STL/3MF/OBJ/GLB/PLY/STEP/ZIP.
- `app/services/slicer.py` — автопоиск слайсеров + пользовательские пути в `AppSetting`
  (`slicer_custom_paths`), редактируются через `/api/slicers`.
- `app/main.py` — при старте находит `status="processing"`, помечает `failed` и перезапускает
  через `asyncio.to_thread(process_version)`. Периодический импорт — из `import_dir`.

---

## 4. Архитектура десктоп-клиента

### C++ (бизнес-логика и адаптеры)

| Файл | Роль |
|---|---|
| `src/GlbParser.h/.cpp` | Разбор GLB (чанки JSON+BIN), узлы сцены, матрицы/TRS, примитивы, индексы uint16/uint32, нормали. `flatShading=true` дублирует вершины по граням. |
| `src/MeshGeometry.h/.cpp` | Адаптер `QQuick3DGeometry`: атрибуты Position/Normal/Index, stride 24. |
| `src/ViewerDecorGeometry.h/.cpp` | Сетка стола, оси X/Y/Z, габаритный контур. Vertex colors, `PrimitiveType::Lines`. |
| `src/ApiClient.h/.cpp` | REST-клиент, настройки QSettings, профили раскладки, shelf-кнопки. |
| `src/main.cpp` | Запуск QGuiApplication, логирование Qt/QML в файл, регистрация QML-типов. |

### QML (UI)

| Файл | Роль |
|---|---|
| `ModelViewer3D.qml` | 3D-сцена, камера, свет, тени, декор; состояние берёт из `ViewerSettings`. |
| `ViewerSettings.qml` | Модель настроек вьювера (QtObject): сетка, оси, свет, SSAO, тени, габариты, камера. |
| `ViewerControls.qml` | Панель настроек вьювера. |
| `CatalogSettings.qml` | Панель настроек каталога; отделяется в отдельное окно. |
| `LibraryPage.qml` | Поиск, фильтры, сортировка, статистика, GridView; профили раскладки. |
| `ModelCard.qml` | Миниатюра, статус, формат, треугольники, размер, габариты, дата, теги. |
| `ShelfDialog.qml`, `ProfilesDialog.qml`, `UrlImportDialog.qml` | Кастомизация кнопок, профили, импорт по ссылке. |

### Практические находки по Qt Quick 3D

1. Qt Quick 3D не загружает GLB/OBJ в рантайме — только родной `.mesh`. Поэтому GLB
   парсится самостоятельно в `GlbParser` и отдаётся через `geometry:`.
2. `OrbitCameraController` требует `origin: Node`, камера должна быть его ребёнком.
   ЛКМ — вращение, Ctrl+ЛКМ — пан, колесо — зум.
3. `OrthographicCamera` использует `horizontalMagnification`/`verticalMagnification`, не `viewWidth`.
4. Имена enum'ов: `Material.BackFaceCulling`, `Material.NoCulling`,
   `SceneEnvironment.TonemapModeAces`; `QtQuick.Dialogs` импортируется без версии.
5. `Rectangle` не имеет `padding` — используйте `anchors.margins`.
6. Тени: `DirectionalLight.castShadow`, `shadowFactor`, `shadowBias`;
   у `Model` — `castsShadows`/`receivesShadows`.
7. Плоские грани включаются через `GlbParser::parse(..., flatShading=true)`.

---

## 5. Практики вьювера (по образцу OrcaSlicer)

1. Камера: орбита, пан, зум, пресеты видов, ортографическая проекция, «вписать модель».
2. Геометрия без сглаживания по умолчанию; переключатель сглаженных нормалей.
3. Окружение: сетка стола, цветные оси, принимающая тени поверхность, SSAO, контровой свет.
4. Измерение: габаритный контур и цифры X×Y×Z в углу.
5. Модульность: рендер отделён от UI, состояние — в `ViewerSettings`.
6. GPU: вся геометрия — VBO/индексы, разбор GLB — в нативном слое, не в UI-потоке.
7. Перспектива для больших мешей: LOD/упрощение и отложенная загрузка GLB.
8. Цвета по данным: подсветка проблем (watertight/overhang) в панели пригодности к печати.

Android-клиент развивается отдельно; эти практики являются ориентиром для его вьювера.

---

## 6. Текущее состояние

- ✅ Сервер: 15 тестов зелёные, ruff clean, `/api/health` ok, LAN `0.0.0.0:8000`.
- ✅ GLB и миниатюры генерируются встроенными trimesh + Pillow.
- ✅ Десктоп: рендер, тени, сетка, оси, габариты, плоские грани, панели настроек,
  кастомизация интерфейса.
- ✅ Релиз Windows: `scripts/package_release.ps1` собирает папку и ZIP без моделей.
- ⏳ Android unit-тесты: `ClassNotFoundException` при выполнении (компиляция проходит).
- ⚠️ При быстром переключении страниц возможны безобидные QML-варнинги `Loader`.
