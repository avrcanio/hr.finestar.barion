package pos.finestar.barion.domain.repo

interface AuthRepository {
    suspend fun bootstrapSession(): Boolean
    suspend fun loginWithPin(pin: String, username: String? = null, deviceId: String? = null)
    suspend fun verifyPin(pin: String)
    suspend fun logout()
}
