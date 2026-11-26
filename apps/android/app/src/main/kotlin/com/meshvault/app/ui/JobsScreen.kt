package com.meshvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshvault.app.AppViewModel
import com.meshvault.app.AppState

@Composable
fun JobsScreen(state: AppState, vm: AppViewModel) {
    if (state.jobs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Очередь печати пуста", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.jobs, key = { it.id }) { job ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(job.model_name.ifEmpty { job.filename }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${job.printer_name} · ${job.status}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { (job.progress / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        if (job.status == "printing") {
                            TextButton(onClick = { vm.jobAction(job.id, "pause") }) { Text("Пауза") }
                        }
                        if (job.status == "paused") {
                            TextButton(onClick = { vm.jobAction(job.id, "resume") }) { Text("Продолжить") }
                        }
                        if (job.status in listOf("queued", "uploading", "printing", "paused")) {
                            TextButton(onClick = { vm.jobAction(job.id, "cancel") }) { Text("Отмена") }
                        }
                    }
                }
            }
        }
    }
}
