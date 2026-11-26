package com.meshvault.app.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    @Volatile
    var baseUrl: String = "http://192.168.1.184:8000/"

    @Volatile
    var token: String = ""

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun create(): MeshVaultApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                if (token.isNotEmpty()) {
                    builder.header("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .build()

        val url = baseUrl.trim().trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MeshVaultApi::class.java)
    }

    fun mediaUrl(relative: String?): String? {
        if (relative.isNullOrEmpty()) return null
        val url = if (relative.startsWith("/")) baseUrl.trim().trimEnd('/') + relative else relative
        if (token.isEmpty()) return url
        val sep = if (url.contains("?")) "&" else "?"
        return "$url${sep}token=$token"
    }

    fun exportUrl(modelId: String, version: Int, format: String): String {
        val url = "${baseUrl.trim().trimEnd('/')}/api/models/$modelId/versions/$version/export?format=$format"
        if (token.isEmpty()) return url
        return "$url&token=$token"
    }

    fun fileUrl(modelId: String, version: Int, variant: String): String {
        val url = "${baseUrl.trim().trimEnd('/')}/api/models/$modelId/files/$variant?v=$version"
        if (token.isEmpty()) return url
        return "$url&token=$token"
    }

    suspend fun uploadBytes(
        fileName: String,
        mime: String,
        bytes: ByteArray,
    ): ModelDto? {
        val part = MultipartBody.Part.createFormData(
            "file",
            fileName,
            bytes.toRequestBody(mime.toMediaType()),
        )
        return runCatching { create().upload(part) }.getOrNull()
    }

    suspend fun uploadGlb(bytes: ByteArray): ModelDto? =
        uploadBytes("model.glb", "model/gltf-binary", bytes)
}
