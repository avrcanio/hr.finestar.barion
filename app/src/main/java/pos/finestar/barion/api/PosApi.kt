package pos.finestar.barion.api

import com.google.gson.JsonObject
import pos.finestar.barion.api.model.ActiveLayoutDto
import pos.finestar.barion.api.model.AddCheckItemRequestDto
import pos.finestar.barion.api.model.CheckDto
import pos.finestar.barion.api.model.CreateCheckRequestDto
import pos.finestar.barion.api.model.CreateCheckResponseDto
import pos.finestar.barion.api.model.IssueReceiptRequestDto
import pos.finestar.barion.api.model.MeResponseDto
import pos.finestar.barion.api.model.PinLoginRequestDto
import pos.finestar.barion.api.model.PinLoginResponseDto
import pos.finestar.barion.api.model.PinVerifyRequestDto
import pos.finestar.barion.api.model.PinVerifyResponseDto
import pos.finestar.barion.api.model.TableStatusDto
import pos.finestar.barion.api.model.UpdateCheckItemQtyRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PosApi {
    @POST("/api/pos/pin/login/")
    suspend fun pinLogin(
        @Body request: PinLoginRequestDto
    ): PinLoginResponseDto

    @POST("/api/pos/pin/verify/")
    suspend fun pinVerify(
        @Body request: PinVerifyRequestDto
    ): PinVerifyResponseDto

    @GET("/api/me/")
    suspend fun me(): MeResponseDto

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

    @GET("/api/pos/checks/{checkId}/items/")
    suspend fun getCheckItems(
        @Path("checkId") checkId: Long
    ): JsonObject

    @POST("/api/pos/checks/{checkId}/items/")
    suspend fun addCheckItem(
        @Path("checkId") checkId: Long,
        @Body request: AddCheckItemRequestDto
    ): JsonObject

    @PATCH("/api/pos/check-items/{itemId}/")
    suspend fun updateCheckItemQty(
        @Path("itemId") itemId: Long,
        @Body request: UpdateCheckItemQtyRequestDto
    ): JsonObject

    @DELETE("/api/pos/check-items/{itemId}/")
    suspend fun deleteCheckItem(
        @Path("itemId") itemId: Long
    ): Response<Unit>

    @POST("/api/pos/checks/{checkId}/issue-receipt/")
    suspend fun issueReceipt(
        @Path("checkId") checkId: Long,
        @Body request: IssueReceiptRequestDto = IssueReceiptRequestDto()
    ): JsonObject
}
