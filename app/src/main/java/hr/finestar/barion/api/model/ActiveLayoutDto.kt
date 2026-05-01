package hr.finestar.barion.api.model

import com.google.gson.annotations.SerializedName

data class ActiveLayoutDto(
    @SerializedName("resolved_by")
    val resolvedBy: String? = null,
    @SerializedName("allowed_layouts")
    val allowedLayouts: List<AllowedLayoutDto> = emptyList(),
    @SerializedName("layout")
    val layout: LayoutDto,
    @SerializedName("zones")
    val zones: List<ZoneDto>,
    @SerializedName("tables")
    val tables: List<LayoutTableDto>
)

data class LayoutDto(
    @SerializedName("id")
    val id: Long,
    @SerializedName("name")
    val name: String,
    @SerializedName("updated_at")
    val updatedAt: String
)

data class ZoneDto(
    @SerializedName("id")
    val id: Long,
    @SerializedName("name")
    val name: String,
    @SerializedName("order")
    val order: Int
)

data class LayoutTableDto(
    @SerializedName("table_id")
    val tableId: Long,
    @SerializedName("label")
    val label: String,
    @SerializedName("shape")
    val shape: String,
    @SerializedName("capacity")
    val capacity: Int,
    @SerializedName("is_vip")
    val isVip: Boolean,
    @SerializedName("x")
    val x: Float,
    @SerializedName("y")
    val y: Float,
    @SerializedName("w")
    val w: Float,
    @SerializedName("h")
    val h: Float,
    @SerializedName("rotation")
    val rotation: Float,
    @SerializedName("zone_id")
    val zoneId: Long
)
