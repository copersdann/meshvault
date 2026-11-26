package com.meshvault.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class GCodeSegment(
    val layer: Int,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val extrude: Boolean,
)

data class GCodeModel(
    val layerCount: Int,
    val segments: List<GCodeSegment>,
    val lengthMm: Float,
)

object GCodeParser {
    private val coord = Regex("""([XYZE])\s*([-+]?\d+(?:\.\d+)?)""")

    fun parse(text: String): GCodeModel {
        var x = 0f; var y = 0f; var z = 0f; var e = 0f
        val zValues = ArrayList<Float>()
        val raw = ArrayList<Pair<Float, GCodeSegment>>()
        var length = 0f

        text.lineSequence().forEach line@{ rawLine ->
            val line = rawLine.substringBefore(';').trim()
            if (line.isEmpty() || (line[0] != 'G' && line[0] != 'g')) return@line
            var newX: Float? = null; var newY: Float? = null
            var newZ: Float? = null; var newE: Float? = null
            for (match in coord.findAll(line)) {
                val value = match.groupValues[2].toFloatOrNull() ?: continue
                when (match.groupValues[1][0]) {
                    'X' -> newX = value
                    'Y' -> newY = value
                    'Z' -> newZ = value
                    'E' -> newE = value
                }
            }
            if (newX == null && newY == null && newZ == null && newE == null) return@line
            newZ?.let {
                z = it
                if (zValues.isEmpty() || z > zValues.last()) zValues.add(z)
            }
            val prevX = x; val prevY = y
            newX?.let { x = it }
            newY?.let { y = it }
            val extrude = newE?.let { it > e } ?: false
            newE?.let { e = it }
            if (newX != null || newY != null) {
                val d = hypot(x - prevX, y - prevY)
                if (d > 0f) length += d
                raw.add(z to GCodeSegment(0, prevX, prevY, x, y, extrude))
            }
        }

        val layers = zValues.distinct().sorted()
        val indexOf = layers.withIndex().associate { it.value to it.index }
        val segments = raw.map { (layerZ, segment) ->
            segment.copy(layer = indexOf[layerZ] ?: 0)
        }
        return GCodeModel(layers.size, segments, length)
    }
}

/** Послойный просмотр G-code (аналог режима «Слои» в Slice Beam) без нативного ядра. */
@Composable
fun GCodeViewer(
    url: String,
    modifier: Modifier = Modifier,
    title: String = "G-code",
) {
    var model by remember(url) { mutableStateOf<GCodeModel?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var from by remember(url) { mutableIntStateOf(0) }
    var to by remember(url) { mutableIntStateOf(Int.MAX_VALUE) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Пустой ответ")
                    if (body.contentLength() > 40L * 1024 * 1024) error("G-code слишком большой")
                    body.string()
                }
                GCodeParser.parse(bytes)
            }.onSuccess { parsed ->
                model = parsed
                from = 0
                to = max(0, parsed.layerCount - 1)
            }.onFailure { error = it.message ?: "Не удалось загрузить G-code" }
        }
    }

    val result = model
    LaunchedEffect(playing, result) {
        if (!playing || result == null) return@LaunchedEffect
        while (from < to) {
            delay(220)
            from++
        }
        playing = false
    }

    Column(modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            result == null -> Text("Загрузка G-code…")
            result.segments.isEmpty() -> Text("В файле нет команд перемещения")
            else -> {
                Text(
                    "Слоёв: ${result.layerCount} · перемещений: ${result.segments.size} · длина: ${
                        "%.0f".format(result.lengthMm)
                    } мм",
                    style = MaterialTheme.typography.bodySmall,
                )
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .height(200.dp)
                ) {
                    val visible = result.segments.filter { it.layer in from..to }
                    if (visible.isEmpty()) return@Canvas
                    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
                    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                    for (segment in visible) {
                        minX = minOf(minX, segment.x1, segment.x2)
                        maxX = maxOf(maxX, segment.x1, segment.x2)
                        minY = minOf(minY, segment.y1, segment.y2)
                        maxY = maxOf(maxY, segment.y1, segment.y2)
                    }
                    val spanX = max(1f, maxX - minX)
                    val spanY = max(1f, maxY - minY)
                    val scale = min(size.width / spanX, size.height / spanY) * 0.92f
                    val cx = (minX + maxX) / 2f
                    val cy = (minY + maxY) / 2f
                    fun map(p: GCodeSegment, first: Boolean): Offset {
                        val px = if (first) p.x1 else p.x2
                        val py = if (first) p.y1 else p.y2
                        return Offset(
                            size.width / 2f + (px - cx) * scale,
                            size.height / 2f - (py - cy) * scale,
                        )
                    }
                    val limit = min(visible.size, 40_000)
                    for (i in 0 until limit) {
                        val segment = visible[i]
                        val color = if (!segment.extrude) Color(0xFF44505E)
                        else if (segment.layer < 2) Color(0xFFFFA726)
                        else Color(0xFF38EF7D)
                        drawLine(
                            color = color,
                            start = map(segment, true),
                            end = map(segment, false),
                            strokeWidth = if (segment.extrude) 1.6f else 0.8f,
                        )
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Слои $from – $to", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = from.toFloat(),
                            onValueChange = { from = it.toInt().coerceIn(0, to) },
                            valueRange = 0f..max(0, result.layerCount - 1).toFloat(),
                        )
                        Slider(
                            value = to.toFloat().coerceAtLeast(from.toFloat()),
                            onValueChange = { to = it.toInt().coerceIn(from, result.layerCount - 1) },
                            valueRange = 0f..max(0, result.layerCount - 1).toFloat(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { playing = !playing }) {
                                Text(if (playing) "Пауза" else "Показать по слоям")
                            }
                            Button(onClick = { from = 0; to = result.layerCount - 1; playing = false }) {
                                Text("Все слои")
                            }
                        }
                        Text(
                            "Зелёный — экструзия, оранжевый — первые слои, серый — перемещения",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
