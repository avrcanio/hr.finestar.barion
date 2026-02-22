package pos.finestar.barion.api.model

import com.google.gson.annotations.SerializedName

data class CreateCheckRequestDto(
    @SerializedName("table_id")
    val tableId: Long
)

data class CreateCheckResponseDto(
    @SerializedName("created")
    val created: Boolean,
    @SerializedName("check")
    val check: CheckDto
)

data class CheckDto(
    @SerializedName("id")
    val id: Long,
    @SerializedName("table_id")
    val tableId: Long,
    @SerializedName("status")
    val status: String,
    @SerializedName("opened_at")
    val openedAt: String,
    @SerializedName("closed_at")
    val closedAt: String?
)

data class AddCheckItemRequestDto(
    @SerializedName("product_id")
    val productId: Long,
    @SerializedName("qty")
    val qty: Int
)

data class UpdateCheckItemQtyRequestDto(
    @SerializedName("qty")
    val qty: Int
)

data class TableStatusDto(
    @SerializedName("table_id")
    val tableId: Long,
    @SerializedName("open_check_id")
    val openCheckId: Long?,
    @SerializedName("status")
    val status: String
)
