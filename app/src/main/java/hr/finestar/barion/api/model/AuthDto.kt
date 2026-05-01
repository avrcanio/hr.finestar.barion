package hr.finestar.barion.api.model

import com.google.gson.annotations.SerializedName

data class PinLoginRequestDto(
    @SerializedName("pin")
    val pin: String,
    @SerializedName("username")
    val username: String? = null,
    @SerializedName("device_id")
    val deviceId: String? = null
)

data class PinLoginResponseDto(
    @SerializedName("token")
    val token: String,
    @SerializedName("user_id")
    val userId: Long,
    @SerializedName("username")
    val username: String
)

data class PinVerifyRequestDto(
    @SerializedName("pin")
    val pin: String
)

data class PinVerifyResponseDto(
    @SerializedName("ok")
    val ok: Boolean
)

data class MeResponseDto(
    @SerializedName("id")
    val id: Long,
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String? = null,
    @SerializedName("first_name")
    val firstName: String? = null,
    @SerializedName("last_name")
    val lastName: String? = null
)
