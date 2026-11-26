package com.meshvault.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshvault.app.data.ApiClient
import com.meshvault.app.data.CategoryDto
import com.meshvault.app.data.CollectionCreateDto
import com.meshvault.app.data.CollectionDto
import com.meshvault.app.data.HealthDto
import com.meshvault.app.data.JobDto
import com.meshvault.app.data.ModelDto
import com.meshvault.app.data.ModelUpdateDto
import com.meshvault.app.data.PrintRequestDto
import com.meshvault.app.data.PrinterCreateDto
import com.meshvault.app.data.PrinterDiscoveryDto
import com.meshvault.app.data.PrintabilityDto
import com.meshvault.app.data.PrinterDto
import com.meshvault.app.data.Settings
import com.meshvault.app.data.SettingsStore
import com.meshvault.app.data.SlicerDto
import com.meshvault.app.data.SlicerRequestDto
import com.meshvault.app.data.TagCreateDto
import com.meshvault.app.data.TagDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

sealed class Screen {
    data object Library : Screen()
    data object Categories : Screen()
    data class CategoryModels(val name: String) : Screen()
    data object Printers : Screen()
    data object Jobs : Screen()
    data object Settings : Screen()
    data class Model(val id: String) : Screen()
    data class LocalModel(val uri: String, val name: String = "Модель") : Screen()
}

data class AppState(
    val settings: Settings = Settings(),
    val screen: Screen = Screen.Library,
    val canGoBack: Boolean = false,
    val trash: Boolean = false,
    val search: String = "",
    val sort: String = "created_desc",
    val activeTag: String? = null,
    val models: List<ModelDto> = emptyList(),
    val modelsTotal: Int = 0,
    val categories: List<CategoryDto> = emptyList(),
    val categoryModels: List<ModelDto> = emptyList(),
    val categoryTotal: Int = 0,
    val cardView: String = "grid",
    val printers: List<PrinterDto> = emptyList(),
    val discoveredPrinters: List<PrinterDiscoveryDto> = emptyList(),
    val jobs: List<JobDto> = emptyList(),
    val health: HealthDto? = null,
    val tags: List<TagDto> = emptyList(),
    val collections: List<CollectionDto> = emptyList(),
    val slicers: List<SlicerDto> = emptyList(),
    val current: ModelDto? = null,
    val printability: PrintabilityDto? = null,
    val loading: Boolean = false,
    val uploading: Boolean = false,
    val toast: String? = null,
    val error: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private var api = ApiClient.create()
    private val backStack = mutableStateListOf<Screen>(Screen.Library)
    private var searchJob: Job? = null

    fun currentScreen(): Screen = backStack.last()

    init {
        viewModelScope.launch {
            settingsStore.flow.collect { settings ->
                ApiClient.baseUrl = settings.url
                ApiClient.token = settings.token
                api = ApiClient.create()
                _state.update { it.copy(settings = settings, cardView = settings.cardView) }
                refreshAll()
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val health = runCatching { api.health() }.getOrNull()
                val list = api.models(
                    q = _state.value.search,
                    sort = _state.value.sort,
                    tag = _state.value.activeTag,
                    trash = if (_state.value.trash) true else null,
                )
                val printers = runCatching { api.printers() }.getOrDefault(emptyList())
                val jobs = runCatching { api.jobs() }.getOrDefault(emptyList())
                val categories = runCatching { api.categories() }.getOrDefault(emptyList())
                val tags = runCatching { api.tags() }.getOrDefault(emptyList())
                val collections = runCatching { api.collections() }.getOrDefault(emptyList())
                val slicers = runCatching { api.slicers() }.getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        loading = false,
                        health = health,
                        models = list.items,
                        modelsTotal = list.total,
                        categories = categories,
                        printers = printers,
                        jobs = jobs,
                        tags = tags,
                        collections = collections,
                        slicers = slicers,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Ошибка сети") }
            }
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            runCatching {
                api.models(
                    q = _state.value.search,
                    sort = _state.value.sort,
                    tag = _state.value.activeTag,
                    trash = if (_state.value.trash) true else null,
                )
            }.onSuccess { list ->
                _state.update { it.copy(models = list.items, modelsTotal = list.total) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Ошибка загрузки") }
            }
        }
    }

    fun refreshJobs() {
        viewModelScope.launch {
            val jobs = runCatching { api.jobs() }.getOrDefault(emptyList())
            _state.update { it.copy(jobs = jobs) }
        }
    }

    fun setScreen(screen: Screen) {
        if (backStack.last() != screen) backStack.add(screen)
        syncScreen()
    }

    fun navigateTopLevel(screen: Screen) {
        backStack.clear()
        backStack.add(Screen.Library)
        if (screen != Screen.Library) backStack.add(screen)
        syncScreen()
    }

    private fun syncScreen() {
        _state.update { it.copy(screen = backStack.last(), canGoBack = backStack.size > 1) }
        onScreenEntered(backStack.last())
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        syncScreen()
        return true
    }

    private fun onScreenEntered(screen: Screen) {
        when (screen) {
            is Screen.Library -> refreshAll()
            is Screen.Categories -> loadCategories()
            is Screen.CategoryModels -> loadCategoryModels(screen.name)
            else -> Unit
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            val result = runCatching { api.categories() }.getOrDefault(emptyList())
            _state.update { it.copy(categories = result) }
        }
    }

    fun openCategory(name: String) = setScreen(Screen.CategoryModels(name))

    fun loadCategoryModels(name: String) {
        _state.update { it.copy(categoryModels = emptyList()) }
        viewModelScope.launch {
            val result = runCatching { api.categoryModels(name, limit = 200) }.getOrNull()
            _state.update {
                it.copy(
                    categoryModels = result?.items ?: emptyList(),
                    categoryTotal = result?.total ?: 0,
                )
            }
        }
    }

    fun setCardView(value: String) {
        _state.update { it.copy(cardView = value) }
        viewModelScope.launch { settingsStore.setCardView(value) }
    }

    fun setThemeMode(value: String) {
        viewModelScope.launch { settingsStore.setThemeMode(value) }
    }

    fun setAccent(value: String) {
        viewModelScope.launch { settingsStore.setAccent(value) }
    }

    fun setViewerPrefs(grid: Boolean, axes: Boolean, autoRotate: Boolean) {
        viewModelScope.launch { settingsStore.setViewerPrefs(grid, axes, autoRotate) }
    }

    fun discoverPrinters() {
        viewModelScope.launch {
            val result = runCatching { api.discoverPrinters() }.getOrDefault(emptyList())
            _state.update { it.copy(discoveredPrinters = result) }
        }
    }

    fun addPrinter(name: String, host: String, kind: String = "moonraker", apiKey: String = "") {
        viewModelScope.launch {
            runCatching {
                api.addPrinter(
                    PrinterCreateDto(name = name, host = host, kind = kind, api_key = apiKey)
                )
            }.onSuccess { toast("Принтер добавлен") }
                .onFailure { toast(it.message ?: "Не удалось добавить принтер") }
            refreshAll()
        }
    }

    fun openExternal(uri: String) {
        val name = runCatching { Uri.parse(uri).lastPathSegment }.getOrNull() ?: "Модель"
        setScreen(Screen.LocalModel(uri, name))
        _state.update { it.copy(error = null) }
    }

    fun toggleTrash() {
        _state.update { it.copy(trash = !it.trash) }
        refreshAll()
    }

    fun setSearch(query: String) {
        _state.update { it.copy(search = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            refreshModels()
        }
    }

    fun setSort(value: String) {
        _state.update { it.copy(sort = value) }
        refreshModels()
    }

    fun setActiveTag(value: String?) {
        _state.update { it.copy(activeTag = value) }
        refreshModels()
    }

    fun openModel(id: String) {
        setScreen(Screen.Model(id))
        viewModelScope.launch {
            try {
                val model = api.model(id)
                _state.update { it.copy(current = model, printability = null) }
                val version = model.latest_version?.version ?: 1
                val printability = runCatching { api.printability(id, version) }.getOrNull()
                _state.update { it.copy(printability = printability) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Не удалось открыть модель") }
            }
        }
    }

    fun refreshPrintability(printerId: String?) {
        val model = _state.value.current ?: return
        val version = model.latest_version?.version ?: 1
        viewModelScope.launch {
            val result = runCatching { api.printability(model.id, version, printerId) }.getOrNull()
            _state.update { it.copy(printability = result) }
        }
    }

    /** Загрузка моделей с телефона через SAF. */
    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(uploading = true) }
            var ok = 0
            for (uri in uris) {
                val name = uriFileName(uri)
                val mime = mimeFor(name)
                val bytes = runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                if (bytes == null) {
                    toast("Не удалось прочитать $name")
                    continue
                }
                val created = ApiClient.uploadBytes(name, mime, bytes)
                if (created != null) ok++ else toast("Не удалось загрузить $name")
            }
            _state.update { it.copy(uploading = false) }
            if (ok > 0) toast("Загружено моделей: $ok")
            refreshAll()
        }
    }

    /** Прикрепление G-code к версии модели — без него печать невозможна. */
    fun attachGcode(modelId: String, version: Int, uri: Uri) {
        viewModelScope.launch {
            val name = uriFileName(uri)
            val bytes = runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                toast("Не удалось прочитать $name")
                return@launch
            }
            val part = MultipartBody.Part.createFormData(
                "file", name, bytes.toRequestBody("text/plain".toMediaType())
            )
            runCatching { api.attachGcode(modelId, version, part) }
                .onSuccess {
                    toast("G-code прикреплён")
                    openModel(modelId)
                }
                .onFailure { toast(it.message ?: "Не удалось прикрепить G-code") }
        }
    }

    fun updateModel(id: String, name: String, description: String, tags: List<String>) {
        viewModelScope.launch {
            runCatching {
                api.updateModel(id, ModelUpdateDto(name = name, description = description, tags = tags))
            }.onSuccess { model ->
                _state.update { it.copy(current = model) }
                toast("Модель обновлена")
                refreshModels()
            }.onFailure { toast(it.message ?: "Не удалось сохранить модель") }
        }
    }

    fun createTag(name: String) {
        viewModelScope.launch {
            runCatching { api.createTag(TagCreateDto(name)) }
                .onSuccess { toast("Тег создан") }
                .onFailure { toast(it.message ?: "Не удалось создать тег") }
            refreshAll()
        }
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            runCatching { api.createCollection(CollectionCreateDto(name)) }
                .onSuccess { toast("Коллекция создана") }
                .onFailure { toast(it.message ?: "Не удалось создать коллекцию") }
            refreshAll()
        }
    }

    fun openInSlicer(modelId: String, version: Int, slicerId: String?) {
        viewModelScope.launch {
            runCatching { api.openInSlicer(modelId, version, SlicerRequestDto(slicerId)) }
                .onSuccess { toast(if (it.slicer.isNotEmpty()) "Открыто в ${it.slicer}" else "Отправлено в слайсер") }
                .onFailure { toast(it.message ?: "Слайсер не найден") }
        }
    }

    fun deleteModel(id: String, permanent: Boolean = false) {
        viewModelScope.launch {
            runCatching { api.delete(id, if (permanent) true else null) }
                .onFailure { toast(it.message ?: "Не удалось удалить") }
            _state.update { it.copy(current = null) }
            navigateTopLevel(Screen.Library)
        }
    }

    fun restoreModel(id: String) {
        viewModelScope.launch {
            runCatching { api.restore(id) }
            refreshAll()
        }
    }

    fun saveSettings(url: String, token: String) {
        viewModelScope.launch { settingsStore.save(url, token) }
    }

    fun startPrint(modelId: String, printerId: String) {
        viewModelScope.launch {
            runCatching { api.print(modelId, PrintRequestDto(printerId)) }
                .onSuccess { toast("Печать запущена") }
                .onFailure { toast(it.message ?: "Сначала прикрепите G-code") }
            refreshAll()
            navigateTopLevel(Screen.Jobs)
        }
    }

    fun jobAction(id: String, action: String) {
        viewModelScope.launch {
            runCatching {
                when (action) {
                    "pause" -> api.pause(id)
                    "resume" -> api.resume(id)
                    else -> api.cancel(id)
                }
            }
            refreshJobs()
        }
    }

    fun toast(message: String) {
        _state.update { it.copy(toast = message) }
    }

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun uriFileName(uri: Uri): String {
        val cursor = runCatching {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)
        }.getOrNull()
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) {
                val name = it.getString(index)
                if (!name.isNullOrBlank()) return name
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "model.stl"
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "stl" -> "model/stl"
        "3mf" -> "model/3mf"
        "obj" -> "model/obj"
        "glb" -> "model/gltf-binary"
        "gltf" -> "model/gltf+json"
        "ply" -> "application/octet-stream"
        "amf" -> "application/octet-stream"
        "step", "stp" -> "application/octet-stream"
        "iges", "igs" -> "application/octet-stream"
        else -> "application/octet-stream"
    }
}
