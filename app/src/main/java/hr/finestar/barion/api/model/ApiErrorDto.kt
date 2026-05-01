package hr.finestar.barion.api.model

import com.google.gson.annotations.SerializedName

data class ApiErrorDto(
    @SerializedName("detail")
    val detail: String?
)
