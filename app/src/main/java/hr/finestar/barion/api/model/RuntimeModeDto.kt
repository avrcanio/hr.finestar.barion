package hr.finestar.barion.api.model

import com.google.gson.annotations.SerializedName

data class RuntimeModeDto(
    @SerializedName("active_mode")
    val activeMode: String
)
