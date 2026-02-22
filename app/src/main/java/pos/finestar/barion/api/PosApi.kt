package pos.finestar.barion.api

import pos.finestar.barion.api.model.ActiveLayoutDto
import pos.finestar.barion.api.model.CheckDto
import pos.finestar.barion.api.model.CreateCheckRequestDto
import pos.finestar.barion.api.model.CreateCheckResponseDto
import pos.finestar.barion.api.model.TableStatusDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface PosApi {
    @GET("/api/pos/active-layout/")
    suspend fun getActiveLayout(): ActiveLayoutDto

    @GET("/api/pos/table-status/")
    suspend fun getTableStatuses(
        @Query("layout_id") layoutId: Long
    ): List<TableStatusDto>

    @POST("/api/pos/checks")
    suspend fun createCheck(
        @Body request: CreateCheckRequestDto
    ): CreateCheckResponseDto

    @GET("/api/pos/checks")
    suspend fun getOpenCheckByTable(
        @Query("table_id") tableId: Long
    ): CheckDto
}
