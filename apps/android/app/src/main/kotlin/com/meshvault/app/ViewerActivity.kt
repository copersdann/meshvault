package com.meshvault.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.meshvault.app.data.ApiClient
import com.meshvault.app.data.PrintRequestDto
import com.meshvault.app.data.SlicerDto
import com.meshvault.app.data.SlicerRequestDto
import com.meshvault.app.ui.GCodeViewer
import com.meshvault.app.ui.GlbViewer
import com.meshvault.app.ui.CameraPreset
import com.meshvault.app.ui.MeshGLSurfaceView
import com.meshvault.app.ui.ModelTransform
import com.meshvault.app.ui.formatBytes
import com.meshvault.app.ui.theme.MeshVaultTheme
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        intent.getStringExtra(EXTRA_TOKEN)?.let { ApiClient.token = it }
        intent.getStringExtra(EXTRA_BASE_URL)?.let { ApiClient.baseUrl = it }
        val meta = mapOf(
            "name" to intent.getStringExtra(EXTRA_NAME).orEmpty(),
            "format" to intent.getStringExtra(EXTRA_FORMAT).orEmpty(),
            "size" to intent.getLongExtra(EXTRA_SIZE, 0L).toString(),
            "triangles" to intent.getIntExtra(EXTRA_TRIANGLES, 0).toString(),
            "bbox" to intent.getStringExtra(EXTRA_BBOX).orEmpty(),
            "status" to intent.getStringExtra(EXTRA_STATUS).orEmpty(),
        )
        val modelId = intent.getStringExtra(EXTRA_MODEL_ID).orEmpty()
        val version = intent.getIntExtra(EXTRA_VERSION, 0)
        val gcodeUrl = intent.getStringExtra(EXTRA_GCODE_URL)
        enableEdgeToEdge()
        setContent {
            MeshVaultTheme {
                ViewerScreen(
                    url = url,
                    meta = meta,
                    modelId = modelId,
                    version = version,
                    gcodeUrl = gcodeUrl,
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_BASE_URL = "base_url"
        const val EXTRA_NAME = "name"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_SIZE = "size"
        const val EXTRA_TRIANGLES = "triangles"
        const val EXTRA_BBOX = "bbox"
        const val EXTRA_STATUS = "status"
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_VERSION = "version"
        const val EXTRA_GCODE_URL = "gcode_url"

        fun open(
            context: android.content.Context,
            url: String,
            token: String,
            name: String = "",
            format: String = "",
            size: Long = 0,
            triangles: Int = 0,
            bbox: String = "",
            status: String = "",
            modelId: String = "",
            version: Int = 0,
            gcodeUrl: String? = null,
        ) {
            val intent = Intent(context, ViewerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_BASE_URL, ApiClient.baseUrl)
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_FORMAT, format)
                putExtra(EXTRA_SIZE, size)
                putExtra(EXTRA_TRIANGLES, triangles)
                putExtra(EXTRA_BBOX, bbox)
                putExtra(EXTRA_STATUS, status)
                putExtra(EXTRA_MODEL_ID, modelId)
                putExtra(EXTRA_VERSION, version)
                putExtra(EXTRA_GCODE_URL, gcodeUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (url.startsWith("content://")) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(intent)
        }
    }
}

private enum class ToolCategory(val label: String) {
    FILE("Файл"), CAMERA("Камера"), ORIENT("Ориент."), TRANSFORM("Трансф."), PRINT("Печать"),
}

@Composable
fun ViewerScreen(
    url: String,
    meta: Map<String, String>,
    modelId: String = "",
    version: Int = 0,
    gcodeUrl: String? = null,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf<ToolCategory?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(true) }
    var showAxes by remember { mutableStateOf(true) }
    var wireframe by remember { mutableStateOf(false) }
    var ortho by remember { mutableStateOf(false) }
    var autoRotate by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }
    var zoomLevel by remember { mutableStateOf(1f) }
    var colorIndex by remember { mutableStateOf(0) }
    var showGcode by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<MeshGLSurfaceView?>(null) }
    var ringVisible by remember { mutableStateOf(false) }
    var ringCenter by remember { mutableStateOf(Offset.Zero) }
    var toast by remember { mutableStateOf<String?>(null) }
    var slicers by remember { mutableStateOf<List<SlicerDto>>(emptyList()) }
    var slicerId by remember { mutableStateOf<String?>(null) }
    var slicerTrigger by remember { mutableIntStateOf(0) }
    var printTrigger by remember { mutableIntStateOf(0) }

    var scale by remember { mutableStateOf(1f) }
    var rotX by remember { mutableStateOf(0f) }
    var rotY by remember { mutableStateOf(0f) }
    var rotZ by remember { mutableStateOf(0f) }
    var offX by remember { mutableStateOf(0f) }
    var offY by remember { mutableStateOf(0f) }

    val density = LocalDensity.current.density
    val colors = listOf(
        floatArrayOf(0.80f, 0.84f, 0.90f),
        floatArrayOf(0.22f, 0.94f, 0.49f),
        floatArrayOf(0.95f, 0.62f, 0.25f),
        floatArrayOf(0.75f, 0.55f, 0.95f),
    )

    LaunchedEffect(Unit) {
        if (modelId.isNotEmpty()) {
            slicers = runCatching { ApiClient.create().slicers() }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(scale, rotX, rotY, rotZ, offX, offY) {
        view?.setTransform(
            ModelTransform(
                scaleX = scale, scaleY = scale, scaleZ = scale,
                rotX = rotX, rotY = rotY, rotZ = rotZ,
                offsetX = offX, offsetY = offY,
            )
        )
    }

    LaunchedEffect(view) {
        view?.onLongPress = { x, y ->
            ringCenter = Offset(x, y)
            ringVisible = true
        }
        view?.onRingSelect = { index ->
            when (index) {
                0 -> { zoomLevel = (zoomLevel / 1.25f).coerceIn(0.25f, 8f); view?.setZoom(zoomLevel) }
                1 -> { zoomLevel = (zoomLevel * 1.25f).coerceIn(0.25f, 8f); view?.setZoom(zoomLevel) }
                2 -> view?.resetView()
                3 -> autoRotate = !autoRotate
                4 -> { speed = (speed + 0.5f).coerceAtMost(4f); view?.setRotateSpeed(speed) }
                5 -> { speed = (speed - 0.5f).coerceAtLeast(0.2f); view?.setRotateSpeed(speed) }
                6 -> wireframe = !wireframe
                7 -> showGrid = !showGrid
                8 -> colorIndex = (colorIndex + 1) % colors.size
                9 -> showInfo = !showInfo
            }
            ringVisible = false
        }
        view?.onRingClose = { ringVisible = false }
        view?.onDoubleTap = { view?.resetView(); toast = "Вид сброшен" }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_600)
            toast = null
        }
    }

    LaunchedEffect(slicerTrigger) {
        if (slicerTrigger == 0 || modelId.isEmpty()) return@LaunchedEffect
        val result = runCatching {
            ApiClient.create().openInSlicer(modelId, version, SlicerRequestDto(slicerId))
        }
        toast = result.getOrNull()?.let { "Открыто в ${it.slicer.ifEmpty { "слайсере" }}" }
            ?: result.exceptionOrNull()?.message ?: "Слайсер не найден"
    }

    LaunchedEffect(printTrigger) {
        if (printTrigger == 0 || modelId.isEmpty()) return@LaunchedEffect
        val api = ApiClient.create()
        val printer = runCatching { api.printers() }.getOrDefault(emptyList()).firstOrNull { it.enabled }
        if (printer == null) {
            toast = "Нет включённых принтеров"
        } else {
            val result = runCatching { api.print(modelId, PrintRequestDto(printer.id)) }
            toast = if (result.isSuccess) "Печать запущена" else "Сначала прикрепите G-code"
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (showGcode && gcodeUrl != null) {
            GCodeViewer(url = gcodeUrl, modifier = Modifier.fillMaxSize(), title = meta["name"] ?: "G-code")
        } else {
            GlbViewer(
                url = url,
                modifier = Modifier.fillMaxSize(),
                autoRotate = autoRotate,
                wireframe = wireframe,
                grid = showGrid,
                rotateSpeed = speed,
                color = colors[colorIndex],
                showAxes = showAxes,
                onViewReady = { view = it },
            )
        }

        Column(Modifier.align(Alignment.TopStart).padding(8.dp).fillMaxWidth(0.95f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("←", color = Color.White) }
                Text(
                    meta["name"] ?: "Модель",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showInfo = !showInfo }) {
                    Text(if (showInfo) "Скрыть" else "Инфо", color = Color.White)
                }
            }
            if (showInfo) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        InfoLine("Формат", meta["format"] ?: "—")
                        val size = meta["size"]?.toLongOrNull() ?: 0
                        InfoLine("Размер", if (size > 0) formatBytes(size) else "—")
                        InfoLine("Треугольники", meta["triangles"] ?: "—")
                        InfoLine("Габариты", meta["bbox"] ?: "—")
                        InfoLine("Статус", meta["status"] ?: "—")
                        Text(
                            "Жесты: 1 палец — вращение, 2 — zoom/pan, двойной тап — сброс, удержание — меню.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = zoomLevel,
                            onValueChange = { zoomLevel = it; view?.setZoom(it) },
                            valueRange = 0.25f..8f,
                        )
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            category?.let { active ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        ToolGrid(active, context, view, slicers, slicerId, { slicerId = it },
                            showGrid, { showGrid = it }, showAxes, { showAxes = it },
                            wireframe, { wireframe = it }, ortho, { ortho = it },
                            autoRotate, { autoRotate = it },
                            showGcode, gcodeUrl != null, { showGcode = it },
                            scale, { scale = it }, rotX, { rotX = it }, rotY, { rotY = it },
                            rotZ, { rotZ = it }, offX, { offX = it }, offY, { offY = it },
                            onApply = {
                                view?.applyTransform()
                                scale = 1f; rotX = 0f; rotY = 0f; rotZ = 0f; offX = 0f; offY = 0f
                                toast = "Трансформация применена"
                            },
                            onReset = {
                                scale = 1f; rotX = 0f; rotY = 0f; rotZ = 0f; offX = 0f; offY = 0f
                                view?.resetView(); toast = "Вид сброшен"
                            },
                            onCamera = { preset -> view?.setPreset(preset); toast = preset.label },
                            onSlicer = { slicerTrigger++ },
                            onPrint = { printTrigger++ },
                            onSnapshot = { snapshot(context, view, { toast = it }) },
                            onShare = { shareUrl(context, gcodeUrl ?: url) },
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                ToolCategory.entries.forEach { item ->
                    ToolButton(
                        label = item.label,
                        selected = category == item,
                        onClick = { category = if (category == item) null else item },
                    )
                    Spacer(Modifier.size(6.dp))
                }
            }
        }

        if (ringVisible) {
            val labels = listOf("−", "+", "Fit", "Auto", "S+", "S−", "Wire", "Grid", "Color", "Info")
            val cxDp = (ringCenter.x / density).dp
            val cyDp = (ringCenter.y / density).dp
            val configuration = LocalConfiguration.current
            val minSide = min(configuration.screenWidthDp, configuration.screenHeightDp)
            val radiusDp = (minSide * 0.32f).dp
            val boxSize = radiusDp * 2 + 32.dp
            val labelRadius = radiusDp * 0.72f

            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.offset(cxDp - boxSize / 2, cyDp - boxSize / 2).size(boxSize)) {
                    labels.forEachIndexed { index, _ ->
                        drawArc(
                            color = Color(0x992A313B),
                            startAngle = -90f + index * 36f,
                            sweepAngle = 36f,
                            useCenter = true,
                        )
                    }
                    drawCircle(color = Color(0xCC11151B), radius = 36.dp.toPx())
                }
                labels.forEachIndexed { index, label ->
                    val angle = Math.toRadians((-72 + index * 36).toDouble())
                    Text(
                        label,
                        color = Color.White,
                        modifier = Modifier.offset(
                            cxDp + labelRadius * cos(angle).toFloat() - 16.dp,
                            cyDp + labelRadius * sin(angle).toFloat() - 10.dp,
                        ),
                    )
                }
            }
        }

        toast?.let { message ->
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                shadowElevation = 8.dp,
            ) {
                Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick) { Text(if (selected) "● $label" else label) }
}

@Composable
private fun ToolGrid(
    category: ToolCategory,
    context: Context,
    view: MeshGLSurfaceView?,
    slicers: List<SlicerDto>,
    slicerId: String?,
    onSlicerId: (String?) -> Unit,
    showGrid: Boolean,
    onGrid: (Boolean) -> Unit,
    showAxes: Boolean,
    onAxes: (Boolean) -> Unit,
    wireframe: Boolean,
    onWireframe: (Boolean) -> Unit,
    ortho: Boolean,
    onOrtho: (Boolean) -> Unit,
    autoRotate: Boolean,
    onAutoRotate: (Boolean) -> Unit,
    showGcode: Boolean,
    hasGcode: Boolean,
    onGcode: (Boolean) -> Unit,
    scale: Float,
    onScale: (Float) -> Unit,
    rotX: Float,
    onRotX: (Float) -> Unit,
    rotY: Float,
    onRotY: (Float) -> Unit,
    rotZ: Float,
    onRotZ: (Float) -> Unit,
    offX: Float,
    onOffX: (Float) -> Unit,
    offY: Float,
    onOffY: (Float) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onCamera: (CameraPreset) -> Unit,
    onSlicer: () -> Unit,
    onPrint: () -> Unit,
    onSnapshot: () -> Unit,
    onShare: () -> Unit,
) {
    val rowModifier = Modifier
        .horizontalScroll(rememberScrollState())
        .fillMaxWidth()
    when (category) {
        ToolCategory.FILE -> Row(rowModifier) {
            ToolButton("Снимок", false, onSnapshot)
            ToolButton("Поделиться", false, onShare)
            ToolButton(if (showGrid) "Сетка ✓" else "Сетка", showGrid, { onGrid(!showGrid) })
            ToolButton(if (showAxes) "Оси ✓" else "Оси", showAxes, { onAxes(!showAxes) })
            ToolButton(if (wireframe) "Каркас ✓" else "Каркас", wireframe, { onWireframe(!wireframe) })
            ToolButton("Сброс", false, onReset)
        }

        ToolCategory.CAMERA -> {
            Row(rowModifier) {
                CameraPreset.entries.forEach { ToolButton(it.label, false, { onCamera(it) }) }
            }
            Row(rowModifier) {
                ToolButton(if (ortho) "Орто ✓" else "Орто", ortho, { onOrtho(!ortho) })
                ToolButton(if (autoRotate) "Авто ✓" else "Авто", autoRotate, { onAutoRotate(!autoRotate) })
                ToolButton("Fit", false, { view?.resetView() })
            }
        }

        ToolCategory.ORIENT -> Column {
            Row(rowModifier) {
                ToolButton("На грань", false, { onRotX(0f); onRotY(0f); onRotZ(0f) })
                ToolButton("90° Z", false, { onRotZ((rotZ + 90f) % 360f) })
                ToolButton("90° X", false, { onRotX((rotX + 90f) % 360f) })
            }
            Slider(label = "Поворот X", value = rotX, range = -180f..180f, onChange = onRotX)
            Slider(label = "Поворот Y", value = rotY, range = -180f..180f, onChange = onRotY)
            Slider(label = "Поворот Z", value = rotZ, range = -180f..180f, onChange = onRotZ)
        }

        ToolCategory.TRANSFORM -> Column {
            Slider(label = "Масштаб", value = scale, range = 0.1f..5f, onChange = onScale)
            Slider(label = "Позиция X", value = offX, range = -100f..100f, onChange = onOffX)
            Slider(label = "Позиция Y", value = offY, range = -100f..100f, onChange = onOffY)
            Row(rowModifier) {
                ToolButton("Применить", false, onApply)
                ToolButton("Сбросить", false, onReset)
            }
        }

        ToolCategory.PRINT -> Column {
            Row(rowModifier) {
                ToolButton("Открыть в слайсере", false, onSlicer)
                if (hasGcode) {
                    ToolButton(if (showGcode) "Слои ✓" else "Слои", showGcode, { onGcode(!showGcode) })
                }
                ToolButton("Печать", false, onPrint)
            }
            if (slicers.isNotEmpty()) {
                Text(
                    "Слайсеры: ${slicers.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(rowModifier) {
                    ToolButton("По умолчанию", slicerId == null, { onSlicerId(null) })
                    slicers.forEach { ToolButton(it.name, slicerId == it.id, { onSlicerId(it.id) }) }
                }
            } else {
                Text(
                    "Слайсер не найден. Укажите путь в настройках сервера.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Slider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun snapshot(context: Context, view: MeshGLSurfaceView?, onResult: (String) -> Unit) {
    if (view == null) {
        onResult("3D-вид ещё загружается")
        return
    }
    view.snapshot { bitmap ->
        if (bitmap == null) {
            onResult("Не удалось сделать снимок")
            return@snapshot
        }
        val file = File(context.cacheDir, "meshvault-shot-${System.currentTimeMillis()}.png")
        runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться снимком"))
        }.onFailure { onResult(it.message ?: "Ошибка сохранения снимка") }
    }
}

private fun shareUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
