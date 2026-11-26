package com.meshvault.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshvault.app.AppViewModel
import com.meshvault.app.ViewerActivity

private const val SLICEBEAM_PACKAGE = "ru.ytkab0bp.slicebeam"

@Composable
fun LocalModelScreen(uri: String, name: String, vm: AppViewModel) {
    val context = LocalContext.current
    val sliceBeamInstalled = isSliceBeamInstalled(context)

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        TextButton(onClick = { vm.back() }) { Text("← Библиотека") }
        Text(name, style = MaterialTheme.typography.titleLarge)
        GlbViewer(
            url = uri,
            modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(12.dp)),
        )
        Text(
            "Модель открыта из другого приложения. Вращайте и масштабируйте жестами.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = {
                ViewerActivity.open(
                    context = context,
                    url = uri,
                    token = "",
                    name = name,
                    status = "локальный файл",
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text("Открыть полный просмотр")
        }
        Button(
            onClick = { vm.uploadFiles(listOf(Uri.parse(uri))) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Загрузить в библиотеку")
        }
        Button(
            onClick = { openInSliceBeam(context, uri) },
            enabled = sliceBeamInstalled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(if (sliceBeamInstalled) "Слайсить в SliceBeam" else "SliceBeam не установлен")
        }
    }
}

private fun isSliceBeamInstalled(context: Context): Boolean =
    context.packageManager.getLaunchIntentForPackage(SLICEBEAM_PACKAGE) != null

private fun openInSliceBeam(context: Context, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
        setPackage(SLICEBEAM_PACKAGE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
