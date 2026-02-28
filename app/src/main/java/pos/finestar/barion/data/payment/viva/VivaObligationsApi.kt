package pos.finestar.barion.data.payment.viva

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST

interface VivaObligationsApi {
    @POST("/obligations/v1/obligations")
    suspend fun createObligation(
        @HeaderMap headers: Map<String, String>,
        @Body request: JsonObject
    ): Response<JsonObject>
}
