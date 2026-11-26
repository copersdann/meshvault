package com.meshvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshvault.app.AppViewModel
import com.meshvault.app.AppState

@Composable
fun SettingsScreen(state: AppState, vm: AppViewModel) {
    var url by remember(state.settings.url) { mutableStateOf(state.settings.url) }
    var token by remember(state.settings.token) { mutableStateOf(state.settings.token) }
    var tagName by remember { mutableStateOf("") }
    var collectionName by remember { mutableStateOf("") }
    var showServer by remember { mutableStateOf(true) }
    var showAppearance by remember { mutableStateOf(false) }
    var showViewer by remember { mutableStateOf(false) }
    var showMeta by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)

        Section(title = "Сервер", expanded = showServer, onToggle = { showServer = it }) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Адрес сервера") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("API-токен (если задан)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.saveSettings(url.trim(), token.trim()) }, modifier = Modifier.fillMaxWidth()) {
                Text("Сохранить и подключиться")
            }
            Text(
                "Для эмулятора: http://10.0.2.2:8000/, для телефона — LAN-IP компьютера.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val health = state.health
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Состояние сервера", style = MaterialTheme.typography.titleMedium)
                    if (health == null) {
                        Text("Недоступен", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Статус: ${health.status} · v${health.version}")
                        Text("CAD: ${if (health.cad) "да" else "нет"} · Форматы: ${health.formats.size}")
                        Text("Слайсеры: ${health.slicers.joinToString().ifEmpty { "не найдены" }}")
                        Text("Авторизация: ${if (health.auth_required) "требуется" else "не требуется"}")
                    }
                }
            }
        }

        Section(title = "Оформление", expanded = showAppearance, onToggle = { showAppearance = it }) {
            Text("Тема", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная").forEach { (value, label) ->
                    FilterChip(
                        selected = state.settings.themeMode == value,
                        onClick = { vm.setThemeMode(value) },
                        label = { Text(label) },
                    )
                }
            }
            Text("Акцент", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("green" to "Зелёный", "blue" to "Синий", "orange" to "Оранжевый", "violet" to "Фиолетовый").forEach { (value, label) ->
                    FilterChip(
                        selected = state.settings.accent == value,
                        onClick = { vm.setAccent(value) },
                        label = { Text(label) },
                    )
                }
            }
        }

        Section(title = "3D-просмотр", expanded = showViewer, onToggle = { showViewer = it }) {
            SwitchRow("Сетка стола", state.settings.showGrid) {
                vm.setViewerPrefs(it, state.settings.showAxes, state.settings.autoRotate)
            }
            SwitchRow("оси XYZ", state.settings.showAxes) {
                vm.setViewerPrefs(state.settings.showGrid, it, state.settings.autoRotate)
            }
            SwitchRow("Автовращение", state.settings.autoRotate) {
                vm.setViewerPrefs(state.settings.showGrid, state.settings.showAxes, it)
            }
            Text(
                "В просмотрщике: 1 палец — вращение, 2 — масштаб и сдвиг, двойной тап — сброс вида, удержание — кольцевое меню.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section(title = "Теги и коллекции", expanded = showMeta, onToggle = { showMeta = it }) {
            OutlinedTextField(
                value = tagName,
                onValueChange = { tagName = it },
                label = { Text("Новый тег") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { if (tagName.isNotBlank()) { vm.createTag(tagName.trim()); tagName = "" } }) {
                Text("Создать тег")
            }
            OutlinedTextField(
                value = collectionName,
                onValueChange = { collectionName = it },
                label = { Text("Новая коллекция") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { if (collectionName.isNotBlank()) { vm.createCollection(collectionName.trim()); collectionName = "" } }) {
                Text("Создать коллекцию")
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onToggle(!expanded) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${if (expanded) "▼" else "▶"} $title",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
