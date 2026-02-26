package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.CheckRoundState
import pos.finestar.barion.domain.model.FiscalizeReceiptResult
import pos.finestar.barion.domain.model.SettlementPrepareResult
import pos.finestar.barion.domain.model.SettlementState
import pos.finestar.barion.domain.model.SettlementSelection
import pos.finestar.barion.domain.model.SettlementMethod
import pos.finestar.barion.domain.repo.CheckRepository

class PrepareSettlementPartUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(
        checkId: Long,
        selections: List<SettlementSelection>,
        amount: Double? = null,
        method: SettlementMethod = SettlementMethod.CASH,
        tipAmount: Double = 0.0,
        remainingTotal: Double? = null
    ): SettlementPrepareResult {
        return repository.prepareSettlementPart(
            checkId = checkId,
            selections = selections,
            amount = amount,
            method = method,
            tipAmount = tipAmount,
            remainingTotal = remainingTotal
        )
    }
}

class PaySettlementPartCashUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(
        checkId: Long,
        partId: Long,
        amount: Double,
        selections: List<SettlementSelection> = emptyList()
    ) = repository.paySettlementPartCash(
        checkId = checkId,
        partId = partId,
        amount = amount,
        selections = selections
    )
}

class ConfirmSettlementPartCardUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(
        checkId: Long,
        partId: Long,
        amount: Double,
        tipAmount: Double,
        approved: Boolean,
        providerRef: String,
        clientTransactionId: String
    ) = repository.confirmSettlementPartCard(
        checkId = checkId,
        partId = partId,
        amount = amount,
        tipAmount = tipAmount,
        approved = approved,
        providerRef = providerRef,
        clientTransactionId = clientTransactionId
    )
}

class ConfirmSettlementCardUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(
        checkId: Long,
        amount: Double,
        tipAmount: Double,
        approved: Boolean,
        providerRef: String,
        clientTransactionId: String
    ) = repository.confirmSettlementCard(
        checkId = checkId,
        amount = amount,
        tipAmount = tipAmount,
        approved = approved,
        providerRef = providerRef,
        clientTransactionId = clientTransactionId
    )
}

class GetSettlementStateUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(checkId: Long): SettlementState = repository.getSettlementState(checkId)
}

class GetCheckRoundStateUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(checkId: Long): CheckRoundState = repository.getCheckRoundState(checkId)
}

class FiscalizeReceiptUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(checkId: Long, receiptId: Long): FiscalizeReceiptResult {
        return repository.fiscalizeReceipt(checkId = checkId, receiptId = receiptId)
    }
}
