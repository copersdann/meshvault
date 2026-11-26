# Android-клиент MeshVault

## Решение
Нативный клиент на **Kotlin + Jetpack Compose**. Серверный REST API переиспользуется полностью.
Ядро слайсера **не встраивается** (Orca/Prusa/SliceBeam — AGPL-3.0 и >2 ГБ нативных библиотек);
вместо этого модель отправляется в установленный слайсер через серверный эндпоинт
`POST /api/models/{id}/versions/{v}/open-in-slicer`. Основной фокус приложения — библиотека,
3D-просмотр и подготовка данных к печати.

## Окружение (проверено)
- Android Studio: `C:\Program Files\Android\Android Studio` (JBR 25 — для Gradle не используем).
- Gradle JDK: **JDK 21** `C:\tools\jdk-21\jdk-21.0.13.1+2`.
- SDK: `%LOCALAPPDATA%\Android\Sdk`, установлены `platforms/android-36`, `build-tools/35.0.0`.
- Проект: `apps/android`, AGP **9.5.2**, Gradle **9.6.0**, Kotlin 2.3.20, compileSdk/targetSdk 36, minSdk 26.
- Кириллица в пути → `android.overridePathCheck=true` в `gradle.properties`.
- Устройство: Samsung M21 (SM-M215F) по adb-WiFi.

## Команды
```powershell
cd apps\android
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:JAVA_HOME="C:\tools\jdk-21\jdk-21.0.13.1+2"
.\gradlew.bat assembleDebug          # APK: app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat installDebug
```

## Архитектура
- MVVM: `AppViewModel` + единый `AppState` (StateFlow), собственный backStack.
- Сеть: Retrofit + OkHttp + kotlinx.serialization; Bearer-токен или `?token=` для медиа.
- Хранение настроек: DataStore (адрес сервера, токен, вид списка, тема, акцент, настройки 3D).
- 3D: собственный **OpenGL ES 3.0**-рендер (`MeshGLSurfaceView` + `MeshRenderer`), STL парсится
  на устройстве (`StlParser`). GLB с сервера конвертируется через STL-экспорт; для локального GLB
  используется временная загрузка на сервер (адрес берётся из настроек, передаётся в `ViewerActivity`).
  Заявленный ранее SceneView/Filament **не подключён** —dependency объявлена, но не используется.

## Экраны и возможности
- **Библиотека** (`LibraryScreen`): поиск **на сервере** (`?q`, дебаунс 300 мс), сортировка
  (новые/обновлённые/по имени), фильтр по тегам, корзина, загрузка файлов с телефона через SAF
  (`OpenMultipleDocuments`), счётчик найденного.
- **Категории** (`CategoriesScreen`, `CategoryModelsScreen`): сетка/список, коллекции.
- **Карточка модели** (`ModelScreen`): превью, 3D-просмотр, редактирование имени/описания/тегов,
  printability с выбором принтера, экспорт STL/OBJ/GLB/3MF, **прикрепление G-code**,
  «Открыть в слайсере», печать на включённом принтере, удаление в корзину.
- **Принтеры** (`PrintersScreen`): обнаружение Moonraker, ручное добавление.
- **Очередь** (`JobsScreen`): список заданий с прогрессом, пауза/продолжить/отмена,
  автоматическое обновление каждые 3 с пока экран открыт.
- **Настройки** (`SettingsScreen`): сервер/токен, тема (системная/светлая/тёмная), акцент
  (зелёный/синий/оранжевый/фиолетовый), настройки 3D (сетка, оси, автовращение),
  создание тегов и коллекций, состояние `/api/health`.
- **Локальный файл** (`LocalModelScreen`): 3D-просмотр через `content://`, загрузка в библиотеку,
  «Слайсить в SliceBeam» (если установлен), полный просмотр.

## 3D-просмотрщик (по мотивам UX Slice Beam)
`ViewerActivity` — отдельный процесс `:viewer`. Нижний ряд категорий инструментов:
- **Файл** — снимок (FileProvider → «Поделиться»), поделиться ссылкой, сетка, оси XYZ,
  каркас, сброс вида.
- **Камера** — 7 пресетов (изометрия, сверху, снизу, спереди, сзади, слева, справа),
  ортографическая проекция, автовращение, Fit.
- **Ориентация** — «На грань», поворот на 90°, слайдеры поворота X/Y/Z с живым предпросмотром.
- **Трансформация** — масштаб и позиция X/Y с предпросмотром, «Применить» запекает
  преобразование в вершины.
- **Печать** — «Открыть в слайсере» (список слайсеров с сервера), послойный просмотр G-code,
  запуск печати.

Жесты: 1 палец — вращение, 2 — zoom/pan, двойной тап — сброс, удержание — кольцевое меню
(зум, Fit, авто, скорость, каркас, сетка, цвет, инфо).

**Послойный просмотр G-code** (`GCodeViewer`) — чистый Kotlin/Canvas: парсер команд `G0/G1`
с отслеживанием Z, цветовая раскраска экструзии/перемещений/первых слоёв, два слайдера
диапазона слоёв и анимация «показать по слоям». Нативное ядро не требуется.

## Известные ограничения
- `testDebugUnitTest` падает в текущей связке AGP 9.5.2 + Gradle 9.6.0: Gradle не видит
  скомпилированный тестовый класс (`ClassNotFoundException`), хотя `assembleDebug` проходит.
  Тесты-заглушки заменены на реальные (`app/src/test/java/...`), но их запуск требует исправления
  toolchain (проверить с собранным AGP/Gradle из Android Studio).
- GLB отображается через STL-экспорт сервера: материалы/текстуры теряются.
- `model-viewer.min.js` в `assets` — неиспользуемый актив (загрузка с CDN в `viewer.html`).

## Доступ с телефона
- Сервер: `uvicorn app.main:app --host 0.0.0.0 --port 8000`.
- LAN-IP компьютера: `192.168.1.184` (может меняться). Адрес меняется в Настройках.
- Для эмулятора: `http://10.0.2.2:8000/`.
- Правило фаервола: `netsh advfirewall firewall add rule name="MeshVault API" dir=in action=allow protocol=TCP localport=8000`.

## Память сборки
В `gradle.properties`: `-Xmx3g`, `workers.max=2`, `kotlin.compiler.execution.strategy=in-process`,
`kotlin.daemon.jvmargs=-Xmx2g`. При OOM: `.\gradlew.bat --stop` и уменьшить `-Xmx`.
