package com.meshvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshvault.app.AppState
import com.meshvault.app.AppViewModel

@Composable
fun PrintersScreen(state: AppState, vm: AppViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newHost by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Button(
            onClick = { vm.discoverPrinters() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Найти принтеры в сети") }
        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Добавить вручную") }

        if (state.discoveredPrinters.isNotEmpty()) {
            Text(
                "Найдены в сети:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 10.dp),
            )
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.discoveredPrinters, key = { it.host }) { found ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(found.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${found.host} · ${found.version}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(onClick = { vm.addPrinter(found.name, found.host) }) {
                                Text("Добавить")
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Добавленные принтеры:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 10.dp),
        )
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.printers, key = { it.id }) { printer ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(printer.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${printer.host} · ${printer.kind}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (printer.enabled) "Включён" else "Выключен",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (printer.enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        Dialog(onDismissRequest = { showAdd = false }) {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Добавить принтер", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newHost,
                        onValueChange = { newHost = it },
                        label = { Text("IP или host:port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("API-ключ (для Moonraker)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { showAdd = false }) { Text("Отмена") }
                        Button(onClick = {
                            if (newName.isNotBlank() && newHost.isNotBlank()) {
                                vm.addPrinter(newName.trim(), newHost.trim())
                                showAdd = false
                            }
                        }) { Text("Сохранить") }
                    }
                }
            }
        }
    }
}
