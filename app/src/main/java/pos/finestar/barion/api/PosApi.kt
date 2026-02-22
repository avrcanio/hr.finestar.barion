package pos.finestar.barion.api

import retrofit2.http.GET

interface PosApi {
    @GET("/health")
    suspend fun healthCheck(): Map<String, String>
}
