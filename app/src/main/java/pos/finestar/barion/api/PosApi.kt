package pos.finestar.barion.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import pos.finestar.barion.api.model.ActiveLayoutDto
import pos.finestar.barion.api.model.AllowedLayoutsDto
import pos.finestar.barion.api.model.AddCheckItemRequestDto
import pos.finestar.barion.api.model.CheckDto
import pos.finestar.barion.api.model.CheckItemActionRequestDto
import pos.finestar.barion.api.model.CreateCheckRequestDto
import pos.finestar.barion.api.model.CreateCheckResponseDto
import pos.finestar.barion.api.model.BundlePriceRequestDto
import pos.finestar.barion.api.model.IssueReceiptRequestDto
import pos.finestar.barion.api.model.MeResponseDto
import pos.finestar.barion.api.model.PinLoginRequestDto
import pos.finestar.barion.api.model.PinLoginResponseDto
import pos.finestar.barion.api.model.PinVerifyRequestDto
import pos.finestar.barion.api.model.PinVerifyResponseDto
import pos.finestar.barion.api.model.RuntimeModeDto
import pos.finestar.barion.api.model.SettlementPayCardConfirmRequestDto
import pos.finestar.barion.api.model.SettlementPayCashRequestDto
import pos.finestar.barion.api.model.SettlementPrepareRequestDto
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
    suspend fun getActiveLayout(
        @Query("layout_id") layoutId: Long? = null,
        @Query("include_allowed") includeAllowed: Int? = null
    ): ActiveLayoutDto

    @GET("/api/pos/runtime-mode/")
    suspend fun getRuntimeMode(): RuntimeModeDto

    @GET("/api/pos/layouts/allowed/")
    suspend fun getAllowedLayouts(): AllowedLayoutsDto

    @GET("/api/drink-categories/")
    suspend fun getDrinkCategories(
        @Query("include_inactive") includeInactive: Int? = null,
        @Query("level") level: Int? = null
    ): JsonElement

    @GET("/api/pos/drink-categories/display/")
    suspend fun getDrinkCategoryDisplay(
        @Query("root_id") rootId: Long
    ): JsonElement

    @GET("/api/pos/products/search/")
    suspend fun searchProducts(
        @Query("q") query: String? = null,
        @Query("drink_category_id") drinkCategoryId: Long? = null,
        @Query("limit") limit: Int? = 100,
        @Query("sort") sort: String? = "popular"
    ): JsonElement

    @GET("/api/pos/products/{artiklId}/modifiers/")
    suspend fun getProductModifiers(
        @Path("artiklId") artiklId: Long
    ): JsonObject

    @POST("/api/pos/products/{artiklId}/bundle-price/")
    suspend fun previewBundlePrice(
        @Path("artiklId") artiklId: Long,
        @Body request: BundlePriceRequestDto
    ): JsonObject

    @GET("/api/artikli/")
    suspend fun getArtikli(
        @Query("drink_category_id") drinkCategoryId: Long? = null,
        @Query("q") query: String? = null,
        @Query("is_sellable") isSellable: Int? = 1,
        @Query("is_stock_item") isStockItem: Int? = null
    ): JsonElement

    @GET("/api/pos/table-status/")
    suspend fun getTableStatuses(
        @Query("layout_id") layoutId: Long
    ): List<TableStatusDto>

    @POST("/api/pos/checks/")
    suspend fun createCheck(
        @Body request: CreateCheckRequestDto
    ): CreateCheckResponseDto

    @GET("/api/pos/checks/")
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

    @POST("/api/pos/check-items/{itemId}/storno/")
    suspend fun stornoCheckItem(
        @Path("itemId") itemId: Long,
        @Body request: CheckItemActionRequestDto = CheckItemActionRequestDto()
    ): JsonObject

    @POST("/api/pos/check-items/{itemId}/gratis/")
    suspend fun gratisCheckItem(
        @Path("itemId") itemId: Long,
        @Body request: CheckItemActionRequestDto = CheckItemActionRequestDto()
    ): JsonObject

    @POST("/api/pos/check-items/{itemId}/otpis/")
    suspend fun otpisCheckItem(
        @Path("itemId") itemId: Long,
        @Body request: CheckItemActionRequestDto = CheckItemActionRequestDto()
    ): JsonObject

    @POST("/api/pos/checks/{checkId}/send-to-bar/")
    suspend fun sendToBar(
        @Path("checkId") checkId: Long
    ): JsonObject

    @POST("/api/pos/checks/{checkId}/close/")
    suspend fun closeCheck(
        @Path("checkId") checkId: Long
    ): JsonObject

    @POST("/api/pos/checks/{checkId}/issue-receipt/")
    suspend fun issueReceipt(
        @Path("checkId") checkId: Long,
        @Body request: IssueReceiptRequestDto = IssueReceiptRequestDto()
    ): JsonObject

    @POST("/api/pos/checks/{checkId}/prepare-settlement/")
    suspend fun prepareSettlementPart(
        @Path("checkId") checkId: Long,
        @Body request: SettlementPrepareRequestDto
    ): JsonObject

    @POST("/api/pos/checks/{checkId}/settlements/parts/{partId}/pay-cash/")
    suspend fun paySettlementPartCash(
        @Path("checkId") checkId: Long,
        @Path("partId") partId: Long,
        @Body request: SettlementPayCashRequestDto
    ): JsonObject

    @POST("/api/pos/checks/{checkId}/settlements/parts/{partId}/pay-card/confirm/")
    suspend fun confirmSettlementPartCard(
        @Path("checkId") checkId: Long,
        @Path("partId") partId: Long,
        @Body request: SettlementPayCardConfirmRequestDto
    ): JsonObject

    @POST("/api/pos/checks/{checkId}/pay-card/confirm/")
    suspend fun confirmSettlementCard(
        @Path("checkId") checkId: Long,
        @Body request: SettlementPayCardConfirmRequestDto
    ): JsonObject

    @GET("/api/pos/checks/{checkId}/settlement-state/")
    suspend fun getSettlementState(
        @Path("checkId") checkId: Long
    ): JsonObject

    @GET("/api/pos/checks/{checkId}/round-state/")
    suspend fun getCheckRoundState(
        @Path("checkId") checkId: Long
    ): JsonObject

    @POST("/api/pos/checks/{checkId}/receipts/{receiptId}/fiscalize/")
    suspend fun fiscalizeReceipt(
        @Path("checkId") checkId: Long,
        @Path("receiptId") receiptId: Long
    ): JsonObject
}
