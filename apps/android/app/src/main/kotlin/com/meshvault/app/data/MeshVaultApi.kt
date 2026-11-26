package com.meshvault.app.data

import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface MeshVaultApi {
    @GET("api/health")
    suspend fun health(): HealthDto

    @GET("api/models")
    suspend fun models(
        @Query("limit") limit: Int = 200,
        @Query("trash") trash: Boolean? = null,
        @Query("q") q: String? = null,
        @Query("sort") sort: String? = null,
        @Query("tag") tag: String? = null,
    ): ModelListDto

    @GET("api/models/{id}")
    suspend fun model(@Path("id") id: String): ModelDto

    @DELETE("api/models/{id}")
    suspend fun delete(@Path("id") id: String, @Query("permanent") permanent: Boolean? = null): Response<Unit>

    @POST("api/models/{id}/restore")
    suspend fun restore(@Path("id") id: String): ModelDto

    @GET("api/models/{id}/versions/{v}/printability")
    suspend fun printability(
        @Path("id") id: String,
        @Path("v") version: Int,
        @Query("printer_id") printerId: String? = null,
    ): PrintabilityDto

    @Multipart
    @POST("api/models")
    suspend fun upload(@Part file: MultipartBody.Part): ModelDto

    @GET("api/categories")
    suspend fun categories(): List<CategoryDto>

    @GET("api/categories/{name}/models")
    suspend fun categoryModels(
        @Path("name") name: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): ModelListDto

    @GET("api/printers/discover")
    suspend fun discoverPrinters(): List<PrinterDiscoveryDto>

    @POST("api/printers")
    suspend fun addPrinter(@Body body: PrinterCreateDto): PrinterDto

    @GET("api/printers")
    suspend fun printers(): List<PrinterDto>

    @GET("api/jobs")
    suspend fun jobs(@Query("active_only") activeOnly: Boolean = false): List<JobDto>

    @POST("api/models/{id}/print")
    suspend fun print(@Path("id") id: String, @Body body: PrintRequestDto): JsonElement

    @POST("api/jobs/{id}/pause")
    suspend fun pause(@Path("id") id: String): JsonElement

    @POST("api/jobs/{id}/resume")
    suspend fun resume(@Path("id") id: String): JsonElement

    @POST("api/jobs/{id}/cancel")
    suspend fun cancel(@Path("id") id: String): JsonElement

    @GET("api/jobs/{id}")
    suspend fun job(@Path("id") id: String): JobDto

    @PATCH("api/models/{id}")
    suspend fun updateModel(
        @Path("id") id: String,
        @Body body: ModelUpdateDto,
    ): ModelDto

    @Multipart
    @POST("api/models/{id}/versions/{v}/gcode")
    suspend fun attachGcode(
        @Path("id") id: String,
        @Path("v") version: Int,
        @Part file: MultipartBody.Part,
    ): VersionDto

    @GET("api/slicers")
    suspend fun slicers(): List<SlicerDto>

    @POST("api/models/{id}/versions/{v}/open-in-slicer")
    suspend fun openInSlicer(
        @Path("id") id: String,
        @Path("v") version: Int,
        @Body body: SlicerRequestDto,
    ): OpenInSlicerDto

    @GET("api/tags")
    suspend fun tags(): List<TagDto>

    @POST("api/tags")
    suspend fun createTag(@Body body: TagCreateDto): TagDto

    @GET("api/collections")
    suspend fun collections(): List<CollectionDto>

    @POST("api/collections")
    suspend fun createCollection(@Body body: CollectionCreateDto): CollectionDto
}
