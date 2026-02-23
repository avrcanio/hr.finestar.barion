package pos.finestar.barion.data.repo

import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.ResponseBody
import pos.finestar.barion.api.PosApi
import pos.finestar.barion.api.model.ApiErrorDto
import pos.finestar.barion.api.model.PinLoginRequestDto
import pos.finestar.barion.api.model.PinVerifyRequestDto
import pos.finestar.barion.data.auth.SessionStore
import pos.finestar.barion.domain.repo.AuthRepository
import retrofit2.HttpException

@Singleton
class RemoteAuthRepository @Inject constructor(
    private val api: PosApi,
    private val sessionStore: SessionStore,
    private val gson: Gson
) : AuthRepository {

    override suspend fun bootstrapSession(): Boolean {
        val token = sessionStore.currentToken()
        if (token.isNullOrBlank()) return false

        return runCatching {
            val me = api.me()
            sessionStore.saveSession(
                token = token,
                userId = me.id,
                username = me.username,
                firstName = me.firstName,
                lastName = me.lastName
            )
        }.onFailure {
            sessionStore.clear()
        }.isSuccess
    }

    override suspend fun loginWithPin(pin: String, username: String?, deviceId: String?) {
        try {
            val login = api.pinLogin(
                PinLoginRequestDto(
                    pin = pin,
                    username = username,
                    deviceId = deviceId
                )
            )
            sessionStore.saveSession(
                token = login.token,
                userId = login.userId,
                username = login.username
            )

            // Auth bootstrap verification after login.
            val me = api.me()
            sessionStore.saveSession(
                token = login.token,
                userId = me.id,
                username = me.username,
                firstName = me.firstName,
                lastName = me.lastName
            )
        } catch (httpException: HttpException) {
            val detail = parseErrorDetail(httpException.response()?.errorBody())
            throw IllegalStateException(detail ?: "PIN login failed.")
        }
    }

    override suspend fun verifyPin(pin: String) {
        try {
            api.pinVerify(PinVerifyRequestDto(pin = pin))
        } catch (httpException: HttpException) {
            val detail = parseErrorDetail(httpException.response()?.errorBody())
            throw IllegalStateException(detail ?: "PIN verify failed.")
        }
    }

    override suspend fun currentUserDisplayName(): String? = sessionStore.currentDisplayName()

    override suspend fun logout() {
        sessionStore.clear()
    }

    private fun parseErrorDetail(errorBody: ResponseBody?): String? {
        val raw = errorBody?.string().orEmpty()
        if (raw.isBlank()) return null

        return runCatching {
            gson.fromJson(raw, ApiErrorDto::class.java).detail
        }.getOrNull()
    }
}
