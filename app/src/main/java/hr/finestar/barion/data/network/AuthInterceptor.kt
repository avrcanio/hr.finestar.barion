package hr.finestar.barion.data.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import hr.finestar.barion.data.auth.SessionStore

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { sessionStore.currentToken() }
        val requestBuilder = chain.request().newBuilder()
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Token $token")
        }
        val request = requestBuilder.build()
        return chain.proceed(request)
    }
}
