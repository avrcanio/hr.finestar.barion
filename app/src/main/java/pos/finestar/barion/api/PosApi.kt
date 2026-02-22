package pos.finestar.barion.api

import pos.finestar.barion.api.model.ActiveLayoutDto
import retrofit2.http.GET

interface PosApi {
    @GET("/api/pos/active-layout/")
    suspend fun getActiveLayout(): ActiveLayoutDto
}
