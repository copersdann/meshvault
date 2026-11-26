package com.meshvault.app.ui

fun formatBytes(value: Long): String = when {
    value < 1024 -> "$value Б"
    value < 1024 * 1024 -> "%.1f КБ".format(value / 1024.0)
    else -> "%.1f МБ".format(value / (1024.0 * 1024.0))
}
