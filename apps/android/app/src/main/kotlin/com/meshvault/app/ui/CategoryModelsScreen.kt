package com.meshvault.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meshvault.app.AppState
import com.meshvault.app.AppViewModel
import com.meshvault.app.Screen
import com.meshvault.app.data.ApiClient
import com.meshvault.app.data.ModelDto

@Composable
fun CategoryModelsScreen(name: String, state: AppState, vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        TextButton(onClick = { vm.back() }) { Text("← Категории") }
        Text(name, style = MaterialTheme.typography.titleLarge)
        Text(
            "${state.categoryTotal} моделей",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ViewToggle(state.cardView, vm::setCardView)
        Spacer(Modifier.height(10.dp))

        if (state.categoryModels.isEmpty()) {
            Text("Нет моделей", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (state.cardView == "grid") {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.categoryModels, key = { it.id }) { model -> ModelMiniCard(model, vm) }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.categoryModels, key = { it.id }) { model -> ModelMiniCard(model, vm) }
            }
        }
    }
}

@Composable
private fun ModelMiniCard(model: ModelDto, vm: AppViewModel) {
    Card(Modifier.clickable { vm.openModel(model.id) }) {
        Column {
            AsyncImage(
                model = ApiClient.mediaUrl(model.latest_version?.thumb_url),
                contentDescription = model.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.padding(8.dp)) {
                Text(model.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    "${model.latest_version?.source_format?.uppercase() ?: "—"} · ${model.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
