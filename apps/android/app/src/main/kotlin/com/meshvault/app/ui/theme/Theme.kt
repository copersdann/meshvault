package com.meshvault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun MeshVaultTheme(
    /** system | light | dark */
    themeMode: String = "system",
    /** green | blue | orange | violet */
    accent: String = "green",
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val accent = accentColor(accent)
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = accentViolet,
        )
    } else {
        lightColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = accentViolet,
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content,
    )
}
