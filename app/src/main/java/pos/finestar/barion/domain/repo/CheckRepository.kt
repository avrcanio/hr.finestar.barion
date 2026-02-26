package pos.finestar.barion.domain.repo

import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.model.SettlementMethod
import pos.finestar.barion.domain.model.SettlementPart
import pos.finestar.barion.domain.model.SettlementPrepareResult
import pos.finestar.barion.domain.model.CheckRoundState
import pos.finestar.barion.domain.model.FiscalizeReceiptResult
import pos.finestar.barion.domain.model.SettlementState
import pos.finestar.barion.domain.model.SettlementSelection

interface CheckRepository {
    suspend fun createCheck(tableId: Long): CheckSession
    suspend fun getOpenCheckByTable(tableId: Long): CheckSession
    suspend fun getCheck(checkId: Long, forceRefresh: Boolean = false): CheckSession?
    suspend fun addItem(
        checkId: Long,
        productId: Long,
        qty: Int,
        unitPrice: Double,
        productName: String? = null
    ): CheckSession
    suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession
    suspend fun removeItem(checkId: Long, itemId: Long): CheckSession
    suspend fun stornoItem(checkId: Long, itemId: Long, reason: String? = null, qty: Int? = null): CheckSession
    suspend fun gratisItem(checkId: Long, itemId: Long, reason: String? = null, qty: Int? = null): CheckSession
    suspend fun otpisItem(checkId: Long, itemId: Long, reason: String? = null, qty: Int? = null): CheckSession
    suspend fun sendToBar(checkId: Long): CheckSession
    suspend fun closeCheck(checkId: Long): CheckSession?
    suspend fun issueReceipt(checkId: Long, fiscalize: Boolean = true): CheckSession?
    suspend fun prepareSettlementPart(
        checkId: Long,
        selections: List<SettlementSelection>,
        amount: Double? = null,
        method: SettlementMethod = SettlementMethod.CASH,
        tipAmount: Double = 0.0,
        remainingTotal: Double? = null
    ): SettlementPrepareResult {
        throw UnsupportedOperationException("prepareSettlementPart is not implemented.")
    }

    suspend fun paySettlementPartCash(
        checkId: Long,
        partId: Long,
        amount: Double,
        selections: List<SettlementSelection> = emptyList()
    ): SettlementPart {
        throw UnsupportedOperationException("paySettlementPartCash is not implemented.")
    }

    suspend fun confirmSettlementPartCard(
        checkId: Long,
        partId: Long,
        amount: Double,
        tipAmount: Double,
        approved: Boolean,
        providerRef: String,
        clientTransactionId: String
    ): SettlementPart {
        throw UnsupportedOperationException("confirmSettlementPartCard is not implemented.")
    }

    suspend fun confirmSettlementCard(
        checkId: Long,
        amount: Double,
        tipAmount: Double,
        approved: Boolean,
        providerRef: String,
        clientTransactionId: String
    ): SettlementPart {
        throw UnsupportedOperationException("confirmSettlementCard is not implemented.")
    }

    suspend fun getSettlementState(checkId: Long): SettlementState {
        throw UnsupportedOperationException("getSettlementState is not implemented.")
    }

    suspend fun getCheckRoundState(checkId: Long): CheckRoundState {
        throw UnsupportedOperationException("getCheckRoundState is not implemented.")
    }

    suspend fun fiscalizeReceipt(checkId: Long, receiptId: Long): FiscalizeReceiptResult {
        throw UnsupportedOperationException("fiscalizeReceipt is not implemented.")
    }
}
