package hr.finestar.barion.domain.model

enum class SettlementMethod {
    CASH,
    CARD
}

enum class SettlementPartStatus {
    PREPARED,
    PAID,
    FAILED
}

data class SettlementSelection(
    val checkItemId: Long,
    val qty: Int
)

data class SettlementPart(
    val partId: Long,
    val method: SettlementMethod? = null,
    val status: SettlementPartStatus = SettlementPartStatus.PREPARED,
    val amount: Double,
    val tipAmount: Double = 0.0,
    val totalCharged: Double = amount + tipAmount,
    val providerRef: String? = null,
    val action: String? = null,
    val issuedReceiptId: Long? = null,
    val receiptPdfUrl: String? = null
)

data class SettlementPrepareResult(
    val part: SettlementPart,
    val remainingByItemId: Map<Long, Int> = emptyMap()
)

data class SettlementState(
    val checkStatus: String = "",
    val paymentStatus: String? = null,
    val settlementStatus: String? = null,
    val remainingTotal: Double? = null,
    val canIssueReceipt: Boolean? = null,
    val posReceiptId: Long? = null,
    val posReceiptIds: List<Long> = emptyList(),
    val issuedReceiptId: Long? = null,
    val receiptPdfUrl: String? = null,
    val receipts: List<SettlementReceipt> = emptyList(),
    val items: List<SettlementStateItem> = emptyList(),
    val parts: List<SettlementStatePart> = emptyList()
) {
    val isClosed: Boolean
        get() = checkStatus.equals("CLOSED", ignoreCase = true) ||
            checkStatus.equals("FREE", ignoreCase = true)

    val isSettled: Boolean
        get() = isClosed ||
            paymentStatus.equals("PAID", ignoreCase = true) ||
            settlementStatus.equals("COMPLETE", ignoreCase = true) ||
            settlementStatus.equals("READY_FOR_ISSUE", ignoreCase = true) ||
            issuedReceiptId != null

    val isPaidOrIssued: Boolean
        get() = paymentStatus.equals("PAID", ignoreCase = true) || issuedReceiptId != null

    val shouldLockPayment: Boolean
        get() = isClosed || ((remainingTotal ?: Double.NaN) == 0.0 && canIssueReceipt == false)

    val preparedPart: SettlementStatePart?
        get() = parts.firstOrNull { it.status.equals("PREPARED", ignoreCase = true) }
}

data class SettlementReceipt(
    val id: Long,
    val receiptNumber: Int? = null,
    val totalAmount: Double? = null,
    val status: String? = null,
    val pdfUrl: String? = null,
    val paymentMethod: String? = null,
    val cardBrand: String? = null,
    val cardMaskedPan: String? = null
)

data class FiscalizeReceiptResult(
    val checkId: Long,
    val receiptId: Long,
    val action: String? = null,
    val status: String? = null,
    val receiptNumber: Int? = null,
    val totalAmount: Double? = null,
    val zki: String? = null,
    val jir: String? = null,
    val qr: String? = null,
    val pdfUrl: String? = null,
    val receipts: List<SettlementReceipt> = emptyList()
)

data class SettlementStateItem(
    val id: Long,
    val artiklId: Long? = null,
    val name: String? = null,
    val imageUrl: String? = null,
    val roundNumber: Int? = null,
    val quantity: Int,
    val paidQuantity: Double,
    val remainingQuantity: Int,
    val totalAmount: Double,
    val paidAmount: Double,
    val remainingAmount: Double
)

data class SettlementStatePart(
    val partId: Long,
    val status: String,
    val method: SettlementMethod? = null,
    val methodDisplay: String? = null,
    val amount: Double? = null,
    val tipAmount: Double? = null,
    val issuedReceiptId: Long? = null,
    val cardBrand: String? = null,
    val cardMaskedPan: String? = null
)

data class CheckRoundState(
    val checkId: Long,
    val status: String? = null,
    val items: List<CheckRoundStateItem> = emptyList(),
    val updatedAt: String? = null
)

data class CheckRoundStateItem(
    val id: Long,
    val checkId: Long,
    val artiklId: Long? = null,
    val name: String? = null,
    val roundNumber: Int? = null,
    val sourceQuantity: Int,
    val soldQuantity: Double,
    val stornoQuantity: Double,
    val gratisQuantity: Double,
    val otpisQuantity: Double,
    val remainingQuantity: Int,
    val strikeMain: Boolean,
    val paidLine: CheckRoundStatePaidLine? = null
)

data class CheckRoundStatePaidLine(
    val lineType: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalAmount: Double,
    val uiColor: String? = null
)
