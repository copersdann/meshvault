package com.meshvault.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshvault.app.AppViewModel
import com.meshvault.app.Screen
import com.meshvault.app.ui.theme.MeshVaultTheme

private data class TopDestination(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

private val DESTINATIONS = listOf(
    TopDestination(Screen.Library, "Библиотека", Icons.Default.Home),
    TopDestination(Screen.Categories, "Категории", Icons.Default.Category),
    TopDestination(Screen.Printers, "Принтеры", Icons.Default.Print),
    TopDestination(Screen.Jobs, "Очередь", Icons.AutoMirrored.Filled.List),
    TopDestination(Screen.Settings, "Настройки", Icons.Default.Settings),
)

private fun isSelected(current: Screen, destination: Screen): Boolean = when (current) {
    is Screen.CategoryModels -> destination is Screen.Categories
    is Screen.Model, is Screen.LocalModel -> false
    else -> current == destination
}

@Composable
fun App(externalUri: String? = null, vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MeshVaultTheme(
        themeMode = state.settings.themeMode,
        accent = state.settings.accent,
    ) {
        AppRoot(state, vm, externalUri)
    }
}

@Composable
private fun AppRoot(state: com.meshvault.app.AppState, vm: AppViewModel, externalUri: String?) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    LaunchedEffect(externalUri) {
        externalUri?.let { vm.openExternal(it) }
    }

    // Обновление очереди печати, пока открыт экран заданий
    LaunchedEffect(state.screen) {
        if (state.screen is Screen.Jobs) {
            while (true) {
                vm.refreshJobs()
                delay(3_000)
            }
        }
    }

    // Автоскрытие всплывающего сообщения
    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(2_800)
            vm.clearToast()
        }
    }

    BackHandler(enabled = state.canGoBack) { vm.back() }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                DESTINATIONS.forEach { destination ->
                    NavigationRailItem(
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                        selected = isSelected(state.screen, destination.screen),
                        onClick = { vm.navigateTopLevel(destination.screen) },
                    )
                }
            }
            AppContent(state, vm)
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    DESTINATIONS.forEach { destination ->
                        NavigationBarItem(
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                            selected = isSelected(state.screen, destination.screen),
                            onClick = { vm.navigateTopLevel(destination.screen) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AppContent(state, vm)
            }
        }
    }
}

@Composable
private fun AppContent(state: com.meshvault.app.AppState, vm: AppViewModel) {
    Box(Modifier.fillMaxSize()) {
        when (val screen = state.screen) {
            Screen.Library -> LibraryScreen(state, vm)
            Screen.Categories -> CategoriesScreen(state, vm)
            is Screen.CategoryModels -> CategoryModelsScreen(screen.name, state, vm)
            Screen.Printers -> PrintersScreen(state, vm)
            Screen.Jobs -> JobsScreen(state, vm)
            Screen.Settings -> SettingsScreen(state, vm)
            is Screen.Model -> ModelScreen(state, vm)
            is Screen.LocalModel -> LocalModelScreen(screen.uri, screen.name, vm)
        }

        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
        state.error?.let { message ->
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    message,
                    Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        state.toast?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 6.dp,
            ) {
                Text(
                    message,
                    Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
