package pos.finestar.barion.api.model

import com.google.gson.annotations.SerializedName

data class AllowedLayoutsDto(
    @SerializedName("layouts")
    val layouts: List<AllowedLayoutDto> = emptyList()
)

data class AllowedLayoutDto(
    @SerializedName("id")
    val id: Long,
    @SerializedName("name")
    val name: String,
    @SerializedName("is_default")
    val isDefault: Boolean = false
)
