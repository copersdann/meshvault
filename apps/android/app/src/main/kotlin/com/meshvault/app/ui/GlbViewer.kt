package com.meshvault.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.meshvault.app.data.ApiClient
import com.meshvault.app.data.MeshData
import com.meshvault.app.data.StlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun GlbViewer(
    url: String,
    modifier: Modifier,
    autoRotate: Boolean = false,
    wireframe: Boolean = false,
    grid: Boolean = false,
    rotateSpeed: Float = 1f,
    color: FloatArray = floatArrayOf(0.80f, 0.84f, 0.90f),
    showAxes: Boolean = true,
    onViewReady: (MeshGLSurfaceView?) -> Unit = {},
) {
    val context = LocalContext.current
    var mesh by remember(url) { mutableStateOf<MeshData?>(null) }
    var loadError by remember(url) { mutableStateOf<String?>(null) }
    var glView by remember(url) { mutableStateOf<MeshGLSurfaceView?>(null) }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (url == "selftest://cube") {
                    StlParser.cube()
                } else {
                    val bytes = readBytes(context, url)
                    val isGlb = bytes.size >= 4 &&
                        String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "glTF"
                    val stlBytes = if (isGlb) {
                        val model = ApiClient.uploadGlb(bytes)
                            ?: error("Не удалось загрузить GLB на сервер")
                        val version = model.latest_version?.version ?: 1
                        download(ApiClient.exportUrl(model.id, version, "stl"))
                    } else {
                        bytes
                    }
                    StlParser.parse(stlBytes)
                }
            }.onSuccess { result ->
                mesh = result
                loadError = null
            }.onFailure { exc ->
                mesh = null
                loadError = exc.message ?: "Ошибка загрузки"
            }
        }
    }

    LaunchedEffect(mesh, glView) {
        mesh?.let { glView?.setMesh(it) }
    }

    key(url) {
        Box(modifier) {
            AndroidView(
                factory = { ctx ->
                    MeshGLSurfaceView(ctx).also { view ->
                        glView = view
                        onViewReady(view)
                        view.clear()
                    }
                },
                update = { view ->
                    view.setAutoRotate(autoRotate)
                    view.setWireframe(wireframe)
                    view.setGrid(grid)
                    view.setRotateSpeed(rotateSpeed)
                    view.setColor(color[0], color[1], color[2])
                    view.setAxes(showAxes)
                },
                modifier = Modifier.fillMaxSize(),
            )
            when {
                loadError != null -> Text(
                    loadError!!,
                    Modifier.align(Alignment.Center).padding(12.dp),
                    color = Color.White,
                )
                mesh == null -> Text(
                    "Загрузка модели...",
                    Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }
        }
    }
}

private fun readBytes(context: android.content.Context, url: String): ByteArray =
    if (url.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(url))?.use { it.readBytes() }
            ?: error("Не удалось прочитать файл")
    } else {
        download(url)
    }

private fun download(url: String): ByteArray =
    OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        response.body?.bytes() ?: error("Пустой ответ")
    }
