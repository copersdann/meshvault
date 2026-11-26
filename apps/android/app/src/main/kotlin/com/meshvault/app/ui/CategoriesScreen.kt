package com.meshvault.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meshvault.app.AppState
import com.meshvault.app.AppViewModel
import com.meshvault.app.data.ApiClient
import com.meshvault.app.data.CategoryDto

@Composable
fun ViewToggle(cardView: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onChange("grid") }) {
            Text(if (cardView == "grid") "▦ Сетка ✓" else "▦ Сетка")
        }
        Button(onClick = { onChange("list") }) {
            Text(if (cardView == "list") "☰ Список ✓" else "☰ Список")
        }
    }
}

@Composable
fun CategoriesScreen(state: AppState, vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        ViewToggle(state.cardView, vm::setCardView)
        Spacer(Modifier.height(10.dp))

        if (state.categories.isEmpty()) {
            Text("Категории не загружены", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (state.cardView == "grid") {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(190.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.categories, key = { it.name }) { category ->
                    CategoryCard(category, vm)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.categories, key = { it.name }) { category ->
                    CategoryCard(category, vm)
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: CategoryDto, vm: AppViewModel) {
    Card(Modifier.clickable { vm.openCategory(category.name) }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(category.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                "${category.count} моделей",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (category.subcategories.isNotEmpty()) {
                Text(
                    category.subcategories.take(3).joinToString(", ") { "${it.name} (${it.count})" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                category.previews.take(3).forEach { preview ->
                    AsyncImage(
                        model = ApiClient.mediaUrl(preview.thumb_url),
                        contentDescription = preview.name,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}
