package com.meshvault.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meshvault.app.AppViewModel
import com.meshvault.app.AppState
import com.meshvault.app.data.ApiClient
import com.meshvault.app.data.ModelDto

@Composable
fun LibraryScreen(state: AppState, vm: AppViewModel) {
    val uploader = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.uploadFiles(uris)
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                label = { Text("Поиск по библиотеке") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { uploader.launch(arrayOf("*/*")) }) {
                Text("Загрузить")
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf(
                "created_desc" to "Новые",
                "updated_desc" to "Обновлённые",
                "name_asc" to "По имени",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = state.sort == value,
                    onClick = { vm.setSort(value) },
                    label = { Text(label) },
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(
                selected = state.activeTag == null,
                onClick = { vm.setActiveTag(null) },
                label = { Text("Все теги") },
            )
            Spacer(Modifier.width(6.dp))
            state.tags.forEach { tag ->
                FilterChip(
                    selected = state.activeTag == tag.name,
                    onClick = { vm.setActiveTag(if (state.activeTag == tag.name) null else tag.name) },
                    label = { Text(tag.name) },
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::toggleTrash) {
                Text(if (state.trash) "Корзина ✓" else "Корзина")
            }
            Text(
                "Найдено: ${state.modelsTotal}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.models.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.trash) "Корзина пуста"
                    else "Нет моделей. Загрузите файлы кнопкой «Загрузить».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.models, key = { it.id }) { model ->
                ModelRow(
                    model = model,
                    trash = state.trash,
                    onClick = { vm.openModel(model.id) },
                    onRestore = { vm.restoreModel(model.id) },
                )
            }
        }
    }
}

@Composable
private fun ModelRow(model: ModelDto, trash: Boolean, onClick: () -> Unit, onRestore: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onClick).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ApiClient.mediaUrl(model.latest_version?.thumb_url),
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${model.latest_version?.source_format?.uppercase() ?: "—"} · ${model.status}" +
                        if (model.tags.isNotEmpty()) " · ${model.tags.joinToString { it.name }}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trash) {
                TextButton(onClick = onRestore) { Text("Восстановить") }
            }
        }
    }
}
