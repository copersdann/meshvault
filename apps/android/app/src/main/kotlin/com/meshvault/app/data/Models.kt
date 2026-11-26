package com.meshvault.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TagDto(val id: String = "", val name: String = "")

@Serializable
data class CollectionDto(val id: String = "", val name: String = "", val description: String = "")

@Serializable
data class VersionDto(
    val id: String = "",
    val version: Int = 1,
    val source_format: String = "",
    val source_filename: String = "",
    val size: Long = 0,
    val sha256: String = "",
    val bbox_x: Double? = null,
    val bbox_y: Double? = null,
    val bbox_z: Double? = null,
    val volume: Double? = null,
    val triangles: Int = 0,
    val watertight: Boolean? = null,
    val units: String = "mm",
    val status: String = "",
    val error: String? = null,
    val original_url: String = "",
    val glb_url: String? = null,
    val thumb_url: String? = null,
    val gcode_url: String? = null,
    val gcode_filename: String? = null,
)

@Serializable
data class ModelDto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val status: String = "empty",
    val deleted_at: String? = null,
    val tags: List<TagDto> = emptyList(),
    val collections: List<CollectionDto> = emptyList(),
    val latest_version: VersionDto? = null,
    val versions: List<VersionDto> = emptyList(),
)

@Serializable
data class ModelListDto(val items: List<ModelDto> = emptyList(), val total: Int = 0)

@Serializable
data class PrinterDiscoveryDto(
    val host: String = "",
    val name: String = "",
    val version: String = "",
    val kind: String = "moonraker",
)

@Serializable
data class PrinterCreateDto(
    val name: String,
    val host: String,
    val api_key: String = "",
    val kind: String = "moonraker",
    val profile_json: Map<String, Double> = mapOf(
        "bed_x" to 220.0,
        "bed_y" to 220.0,
        "bed_z" to 250.0,
        "nozzle" to 0.4,
    ),
    val enabled: Boolean = true,
)

@Serializable
data class PrinterDto(
    val id: String = "",
    val name: String = "",
    val kind: String = "moonraker",
    val host: String = "",
    val enabled: Boolean = true,
    val profile_json: JsonObject = JsonObject(emptyMap()),
    val has_api_key: Boolean = false,
)

@Serializable
data class JobDto(
    val id: String = "",
    val model_name: String = "",
    val printer_name: String = "",
    val status: String = "",
    val filename: String = "",
    val progress: Double = 0.0,
    val message: String = "",
)

@Serializable
data class CategoryPreviewDto(
    val id: String = "",
    val name: String = "",
    val format: String = "",
    val status: String = "",
    val thumb_url: String? = null,
)

@Serializable
data class SubcategoryDto(val name: String = "", val count: Int = 0)

@Serializable
data class CategoryDto(
    val name: String = "",
    val count: Int = 0,
    val subcategories: List<SubcategoryDto> = emptyList(),
    val previews: List<CategoryPreviewDto> = emptyList(),
)

@Serializable
data class HealthDto(
    val status: String = "",
    val version: String = "",
    val formats: List<String> = emptyList(),
    val cad: Boolean = false,
    val slicer: Boolean = false,
    val slicers: List<String> = emptyList(),
    val auth_required: Boolean = false,
)

@Serializable
data class PrintabilityIssueDto(val code: String = "", val severity: String = "info", val message: String = "")

@Serializable
data class PrintabilityDto(
    val watertight: Boolean? = null,
    val overhang_fraction: Double = 0.0,
    val issues: List<PrintabilityIssueDto> = emptyList(),
)

@Serializable
data class PrintRequestDto(
    val printer_id: String,
    val gcode_blob_id: String? = null,
    val filename: String? = null,
)

@Serializable
data class ModelUpdateDto(
    val name: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val collection_ids: List<String>? = null,
)

@Serializable
data class TagCreateDto(val name: String)

@Serializable
data class CollectionCreateDto(val name: String, val description: String = "")

@Serializable
data class SlicerDto(
    val id: String = "",
    val name: String = "",
    val path: String = "",
)

@Serializable
data class SlicerRequestDto(val slicer_id: String? = null)

@Serializable
data class OpenInSlicerDto(
    val slicer: String = "",
    val file: String = "",
)

