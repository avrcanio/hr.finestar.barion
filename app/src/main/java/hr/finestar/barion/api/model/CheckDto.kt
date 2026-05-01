package hr.finestar.barion.api.model

import com.google.gson.annotations.SerializedName

data class CreateCheckRequestDto(
    @SerializedName("table_id")
    val tableId: Long
)

data class CreateCheckResponseDto(
    @SerializedName("created")
    val created: Boolean,
    @SerializedName("check")
    val check: CheckDto
)

data class CheckDto(
    @SerializedName("id")
    val id: Long,
    @SerializedName("table_id")
    val tableId: Long,
    @SerializedName("status")
    val status: String,
    @SerializedName("opened_at")
    val openedAt: String,
    @SerializedName("closed_at")
    val closedAt: String?
)

data class AddCheckItemRequestDto(
    @SerializedName("artikl_id")
    val artiklId: Long? = null,
    @SerializedName("artikl_name")
    val artiklName: String? = null,
    @SerializedName("product_id")
    val productId: Long? = null,
    @SerializedName("product_name")
    val productName: String? = null,
    @SerializedName("quantity")
    val quantity: String,
    @SerializedName("unit_price")
    val unitPrice: String,
    @SerializedName("vat_rate")
    val vatRate: String? = null,
    @SerializedName("modifiers")
    val modifiers: List<CheckItemModifierInputDto>? = null,
    @SerializedName("note")
    val note: String? = null
)

data class CheckItemModifierInputDto(
    @SerializedName("type")
    val type: String,
    @SerializedName("id")
    val id: Long,
    @SerializedName("quantity")
    val quantity: Int? = null
)

data class UpdateCheckItemQtyRequestDto(
    @SerializedName("qty")
    val qty: Int
)

data class CheckItemActionRequestDto(
    @SerializedName("reason")
    val reason: String? = null,
    @SerializedName("quantity")
    val quantity: String? = null
)

data class IssueReceiptRequestDto(
    @SerializedName("fiscalize")
    val fiscalize: Boolean = true
)

data class SettlementPreparePartDto(
    @SerializedName("method")
    val method: String,
    @SerializedName("amount")
    val amount: String,
    @SerializedName("tip_amount")
    val tipAmount: String? = null
)

data class SettlementPrepareRequestDto(
    @SerializedName("parts")
    val parts: List<SettlementPreparePartDto>
)

data class SettlementPayCashItemDto(
    @SerializedName("item_id")
    val itemId: Long,
    @SerializedName("quantity")
    val quantity: String
)

data class SettlementPayCashRequestDto(
    @SerializedName("amount")
    val amount: String? = null,
    @SerializedName("items")
    val items: List<SettlementPayCashItemDto>? = null,
    @SerializedName("issue_receipt")
    val issueReceipt: Boolean? = null,
    @SerializedName("currency")
    val currency: String = "EUR"
)

data class SettlementPayCardConfirmRequestDto(
    @SerializedName("provider")
    val provider: String = "VIVA",
    @SerializedName("approved")
    val approved: Boolean,
    @SerializedName("provider_ref")
    val providerRef: String,
    @SerializedName("external_txn_id")
    val externalTxnId: String? = null,
    @SerializedName("client_transaction_id")
    val clientTransactionId: String,
    @SerializedName("amount")
    val amount: String,
    @SerializedName("tip_amount")
    val tipAmount: String = "0.00",
    @SerializedName("issue_receipt")
    val issueReceipt: Boolean? = null,
    @SerializedName("currency")
    val currency: String = "EUR",
    @SerializedName("rrn")
    val rrn: String? = null,
    @SerializedName("reference_number")
    val referenceNumber: String? = null,
    @SerializedName("authorisation_code")
    val authorisationCode: String? = null,
    @SerializedName("tid")
    val tid: String? = null,
    @SerializedName("order_code")
    val orderCode: String? = null,
    @SerializedName("short_order_code")
    val shortOrderCode: String? = null,
    @SerializedName("transaction_date")
    val transactionDate: String? = null,
    @SerializedName("payment_method")
    val paymentMethod: String? = null,
    @SerializedName("card_type")
    val cardType: String? = null,
    @SerializedName("account_number")
    val accountNumber: String? = null,
    @SerializedName("verification_method")
    val verificationMethod: String? = null,
    @SerializedName("aid")
    val aid: String? = null,
    @SerializedName("bank_id")
    val bankId: String? = null,
    @SerializedName("transaction_type_id")
    val transactionTypeId: String? = null,
    @SerializedName("transaction_event_id")
    val transactionEventId: String? = null,
    @SerializedName("surcharge_amount")
    val surchargeAmount: String? = null,
    @SerializedName("customer_trns")
    val customerTrns: String? = null,
    @SerializedName("provider_status")
    val providerStatus: String? = null,
    @SerializedName("provider_action")
    val providerAction: String? = null,
    @SerializedName("provider_message")
    val providerMessage: String? = null,
    @SerializedName("provider_payload")
    val providerPayload: Map<String, String>? = null
)

data class BundlePriceRequestDto(
    @SerializedName("modifiers")
    val modifiers: List<CheckItemModifierInputDto>
)

data class TableStatusDto(
    @SerializedName("table_id")
    val tableId: Long,
    @SerializedName("open_check_id")
    val openCheckId: Long?,
    @SerializedName("status")
    val status: String,
    @SerializedName("item_count")
    val itemCount: Int? = null
)
