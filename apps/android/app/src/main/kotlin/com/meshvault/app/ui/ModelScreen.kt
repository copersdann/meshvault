package com.meshvault.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meshvault.app.AppViewModel
import com.meshvault.app.AppState
import com.meshvault.app.ViewerActivity
import com.meshvault.app.data.ApiClient
import com.meshvault.app.data.ModelDto
import com.meshvault.app.data.PrintabilityDto
import com.meshvault.app.data.PrinterDto
import com.meshvault.app.data.VersionDto

@Composable
fun ModelScreen(state: AppState, vm: AppViewModel) {
    val model = state.current ?: return
    val context = LocalContext.current
    val version = model.latest_version
    var editing by remember(model.id) { mutableStateOf(false) }

    val gcodePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && version != null) vm.attachGcode(model.id, version.version, uri)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.back() }) { Text("← Назад") }
                Text(
                    model.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            AsyncImage(
                model = ApiClient.mediaUrl(version?.thumb_url),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
            if (version != null) {
                Button(
                    onClick = {
                        ViewerActivity.open(
                            context = context,
                            url = ApiClient.exportUrl(model.id, version.version, "stl"),
                            token = ApiClient.token,
                            name = model.name,
                            format = version.source_format,
                            size = version.size,
                            triangles = version.triangles,
                            bbox = dims(version.bbox_x, version.bbox_y, version.bbox_z),
                            status = version.status,
                            modelId = model.id,
                            version = version.version,
                            gcodeUrl = gcodeUrl(model, version),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("Открыть 3D-просмотр")
                }
            }
        }
        item { InfoCard(model) }
        item {
            EditCard(model, editing, { editing = it }, vm)
        }
        item { PrintabilityCard(state.printability, state.printers, vm) }
        item { ExportCard(model, context) }
        item { GCodeCard(model, version, state.slicers, vm, gcodePicker, context) }
        item { PrintCard(model, state.printers, vm) }
        item {
            TextButton(onClick = { vm.deleteModel(model.id) }) {
                Text("Удалить (в корзину)", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun gcodeUrl(model: ModelDto, version: VersionDto): String? =
    if (version.gcode_url != null) ApiClient.fileUrl(model.id, version.version, "gcode") else null

@Composable
private fun InfoCard(model: ModelDto) {
    val v = model.latest_version
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Информация", style = MaterialTheme.typography.titleMedium)
            InfoRow("Формат", v?.source_format?.uppercase() ?: "—")
            InfoRow("Размер", v?.size?.let { formatBytes(it) } ?: "—")
            InfoRow("Габариты", dims(v?.bbox_x, v?.bbox_y, v?.bbox_z))
            InfoRow("Треугольники", (v?.triangles ?: 0).toString())
            InfoRow("Замкнутый", if (v?.watertight == true) "да" else "нет")
            InfoRow("Статус", v?.status ?: model.status)
            if (model.tags.isNotEmpty()) {
                InfoRow("Теги", model.tags.joinToString { it.name })
            }
            if (model.collections.isNotEmpty()) {
                InfoRow("Коллекции", model.collections.joinToString { it.name })
            }
        }
    }
}

@Composable
private fun EditCard(
    model: ModelDto,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    vm: AppViewModel,
) {
    var name by remember(model.id, model.name) { mutableStateOf(model.name) }
    var description by remember(model.id, model.description) { mutableStateOf(model.description) }
    var tags by remember(model.id) { mutableStateOf(model.tags.joinToString(", ") { it.name }) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Описание", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onEditing(!editing) }) {
                    Text(if (editing) "Отмена" else "Изменить")
                }
            }
            if (editing) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Теги через запятую") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        vm.updateModel(
                            model.id,
                            name.trim(),
                            description.trim(),
                            tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        )
                        onEditing(false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сохранить") }
            } else if (model.description.isNotBlank()) {
                Text(model.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun dims(x: Double?, y: Double?, z: Double?): String =
    if (x != null && y != null && z != null) "%.0f × %.0f × %.0f мм".format(x, y, z) else "—"

@Composable
private fun PrintabilityCard(result: PrintabilityDto?, printers: List<PrinterDto>, vm: AppViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Пригодность к печати", style = MaterialTheme.typography.titleMedium)
            if (result == null) {
                Text("Загрузка…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (result.issues.isEmpty()) {
                Text("Проблем не обнаружено", color = MaterialTheme.colorScheme.primary)
            } else {
                result.issues.forEach { issue ->
                    val color = when (issue.severity) {
                        "error" -> MaterialTheme.colorScheme.error
                        "warning" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text("• ${issue.message}", color = color, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (printers.isNotEmpty()) {
                TextButton(onClick = { vm.refreshPrintability(printers.first().id) }) {
                    Text("Проверить с принтером «${printers.first().name}»")
                }
            }
        }
    }
}

@Composable
private fun ExportCard(model: ModelDto, context: Context) {
    val version = model.latest_version?.version ?: 1
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Экспорт", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("stl", "obj", "glb", "3mf").forEach { format ->
                    Button(onClick = { openUrl(context, ApiClient.exportUrl(model.id, version, format)) }) {
                        Text(format.uppercase())
                    }
                }
            }
        }
    }
}

@Composable
private fun GCodeCard(
    model: ModelDto,
    version: VersionDto?,
    slicers: List<com.meshvault.app.data.SlicerDto>,
    vm: AppViewModel,
    gcodePicker: androidx.activity.result.ActivityResultLauncher<String>,
    context: Context,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("G-code и слайсер", style = MaterialTheme.typography.titleMedium)
            Text(
                if (version?.gcode_url != null) "G-code: ${version.gcode_filename ?: "прикреплён"}"
                else "G-code не прикреплён — печать будет недоступна",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { gcodePicker.launch("*/*") }) {
                    Text("Прикрепить G-code")
                }
                if (version?.gcode_url != null) {
                    Button(onClick = { openUrl(context, ApiClient.fileUrl(model.id, version.version, "gcode")) }) {
                        Text("Скачать")
                    }
                }
            }
            Button(
                onClick = { version?.let { vm.openInSlicer(model.id, it.version, null) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Открыть модели в слайсере")
            }
            if (slicers.isNotEmpty()) {
                Text(
                    "Найдены: ${slicers.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PrintCard(model: ModelDto, printers: List<PrinterDto>, vm: AppViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Печать", style = MaterialTheme.typography.titleMedium)
            val enabled = printers.filter { it.enabled }
            if (enabled.isEmpty()) {
                Text("Нет доступных принтеров", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                enabled.forEach { printer ->
                    Button(
                        onClick = { vm.startPrint(model.id, printer.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Печать на «${printer.name}»") }
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
