package pos.finestar.barion.data.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import pos.finestar.barion.data.auth.SessionStore

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { sessionStore.currentToken() }
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Token $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
