package pos.finestar.barion.check

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pos.finestar.barion.domain.model.CheckItem
import pos.finestar.barion.domain.model.CheckRoundStateItem
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.model.SettlementMethod
import pos.finestar.barion.domain.model.SettlementStatePart
import pos.finestar.barion.domain.model.SettlementPartStatus
import pos.finestar.barion.domain.model.SettlementReceipt
import pos.finestar.barion.domain.model.SettlementSelection
import pos.finestar.barion.domain.repo.AuthRepository
import pos.finestar.barion.domain.usecase.AddItemToCheckUseCase
import pos.finestar.barion.domain.usecase.CloseCheckUseCase
import pos.finestar.barion.domain.usecase.ConfirmSettlementPartCardUseCase
import pos.finestar.barion.domain.usecase.FiscalizeReceiptUseCase
import pos.finestar.barion.domain.usecase.GetCheckByIdUseCase
import pos.finestar.barion.domain.usecase.GetCheckRoundStateUseCase
import pos.finestar.barion.domain.usecase.GetSettlementStateUseCase
import pos.finestar.barion.domain.usecase.GratisCheckItemUseCase
import pos.finestar.barion.domain.usecase.OtpisCheckItemUseCase
import pos.finestar.barion.domain.usecase.PaySettlementPartCashUseCase
import pos.finestar.barion.domain.usecase.PrepareSettlementPartUseCase
import pos.finestar.barion.domain.usecase.RemoveItemFromCheckUseCase
import pos.finestar.barion.domain.usecase.SendToBarUseCase
import pos.finestar.barion.domain.usecase.StornoCheckItemUseCase
import pos.finestar.barion.domain.usecase.UpdateCheckItemQtyUseCase
import pos.finestar.barion.ui.navigation.NavRoutes

@HiltViewModel
class CheckViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCheckByIdUseCase: GetCheckByIdUseCase,
    private val addItemToCheckUseCase: AddItemToCheckUseCase,
    private val updateCheckItemQtyUseCase: UpdateCheckItemQtyUseCase,
    private val removeItemFromCheckUseCase: RemoveItemFromCheckUseCase,
    private val stornoCheckItemUseCase: StornoCheckItemUseCase,
    private val gratisCheckItemUseCase: GratisCheckItemUseCase,
    private val otpisCheckItemUseCase: OtpisCheckItemUseCase,
    private val closeCheckUseCase: CloseCheckUseCase,
    private val sendToBarUseCase: SendToBarUseCase,
    private val prepareSettlementPartUseCase: PrepareSettlementPartUseCase,
    private val paySettlementPartCashUseCase: PaySettlementPartCashUseCase,
    private val confirmSettlementPartCardUseCase: ConfirmSettlementPartCardUseCase,
    private val fiscalizeReceiptUseCase: FiscalizeReceiptUseCase,
    private val getSettlementStateUseCase: GetSettlementStateUseCase,
    private val getCheckRoundStateUseCase: GetCheckRoundStateUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {
    companion object {
        private const val TAG = "CheckViewModel"
    }

    data class PaymentItemUi(
        val checkItemId: Long,
        val name: String,
        val price: Double,
        val totalQty: Int,
        val remainingQty: Int,
        val selectedQty: Int = 0
    )

    data class SplitPartUi(
        val partId: Long,
        val label: String,
        val amount: Double,
        val status: SettlementPartStatus,
        val method: SettlementMethod? = null
    )

    data class PaymentFlowState(
        val showChoiceDialog: Boolean = false,
        val canSplitPayment: Boolean = true,
        val showSplitDialog: Boolean = false,
        val isSplitSummary: Boolean = false,
        val splitStepIndex: Int = 1,
        val showMethodDialog: Boolean = false,
        val methodTargetPartId: Long? = null,
        val methodTargetAmount: Double = 0.0,
        val methodTargetLabel: String = "",
        val payableItems: List<PaymentItemUi> = emptyList(),
        val splitParts: List<SplitPartUi> = emptyList()
    ) {
        val hasSelection: Boolean get() = payableItems.any { it.selectedQty > 0 }
        val totalRemainingQty: Int get() = payableItems.sumOf { it.remainingQty }
        val allPartsPaid: Boolean get() = splitParts.isNotEmpty() && splitParts.all { it.status == SettlementPartStatus.PAID }
        val canCloseCheck: Boolean get() = totalRemainingQty == 0 && allPartsPaid
    }

    data class UiState(
        val isLoading: Boolean = true,
        val isMutating: Boolean = false,
        val checkId: Long = 0L,
        val tableName: String = "",
        val status: String = "OPEN",
        val items: List<CheckItem> = emptyList(),
        val hasUnsentItems: Boolean = false,
        val subtotal: Double = 0.0,
        val tax: Double = 0.0,
        val total: Double = 0.0,
        val error: String? = null,
        val message: String? = null,
        val showItemActionDialog: Boolean = false,
        val selectedActionItem: CheckItem? = null,
        val actionReason: String = "",
        val actionQty: String = "",
        val actionMaxQty: Int = 0,
        val paymentFlow: PaymentFlowState = PaymentFlowState(),
        val settlementReceiptPdfUrl: String? = null,
        val settlementReceipts: List<SettlementReceipt> = emptyList(),
        val settlementCompleted: Boolean = false,
        val settlementRemainingByItemId: Map<Long, Int> = emptyMap(),
        val roundStateByItemId: Map<Long, RoundStateUi> = emptyMap(),
        val showFiscalizeDialog: Boolean = false,
        val fiscalizeReceiptId: Long? = null,
        val fiscalizePin: String = "",
        val settlementOpenSubtotal: Double? = null,
        val settlementOpenTax: Double? = null,
        val settlementOpenTotal: Double? = null
    )

    data class RoundStateUi(
        val sourceQuantity: Int,
        val remainingQuantity: Int,
        val strikeMain: Boolean,
        val paidLine: PaidLineUi? = null
    )

    data class PaidLineUi(
        val lineType: String,
        val quantity: Double,
        val unitPrice: Double,
        val totalAmount: Double,
        val uiColor: String?
    )

    sealed interface Event {
        data class OpenAddItems(val checkId: Long, val tableName: String) : Event
    }

    private val checkId: Long = savedStateHandle[NavRoutes.ARG_CHECK_ID] ?: 0L
    private val tableName: String = savedStateHandle[NavRoutes.ARG_TABLE_NAME] ?: "Unknown"

    private val _uiState = MutableStateFlow(
        UiState(
            checkId = checkId,
            tableName = java.net.URLDecoder.decode(tableName, Charsets.UTF_8.name())
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    init {
        loadCheck()
    }

    fun onOpenAddItems() {
        viewModelScope.launch {
            _events.emit(
                Event.OpenAddItems(
                    checkId = _uiState.value.checkId,
                    tableName = _uiState.value.tableName
                )
            )
        }
    }

    fun onAddItem(productId: Long, qty: Int, unitPrice: Double) {
        mutateCheck {
            addItemToCheckUseCase(
                checkId = checkId,
                productId = productId,
                qty = qty,
                unitPrice = unitPrice
            )
        }
    }

    fun onIncreaseQty(item: CheckItem) {
        val itemId = item.itemId ?: return
        mutateCheck {
            updateCheckItemQtyUseCase(checkId = checkId, itemId = itemId, qty = item.qty + 1)
        }
    }

    fun onDecreaseQty(item: CheckItem) {
        val itemId = item.itemId ?: return
        if (item.qty <= 1) {
            onRemoveItem(item)
            return
        }
        mutateCheck {
            updateCheckItemQtyUseCase(checkId = checkId, itemId = itemId, qty = item.qty - 1)
        }
    }

    fun onRemoveItem(item: CheckItem) {
        val itemId = item.itemId ?: return
        mutateCheck {
            removeItemFromCheckUseCase(checkId = checkId, itemId = itemId)
        }
    }

    fun onItemLongPress(item: CheckItem) {
        if (item.itemId == null) return
        if (!item.lineType.equals("NORMAL", ignoreCase = true)) {
            _uiState.update { it.copy(message = "Akcija je dozvoljena samo za normalne stavke.") }
            return
        }
        val availableQty = calculateAvailableActionQty(item)
        if (availableQty <= 0) {
            _uiState.update { it.copy(message = "Nema slobodne količine za storno/gratis/otpis.") }
            return
        }
        _uiState.update {
            it.copy(
                showItemActionDialog = true,
                selectedActionItem = item,
                actionReason = "",
                actionQty = availableQty.toString(),
                actionMaxQty = availableQty
            )
        }
    }

    fun onItemActionReasonChanged(reason: String) {
        _uiState.update { it.copy(actionReason = reason) }
    }

    fun onItemActionQtyChanged(qty: String) {
        val sanitized = qty.filter { it.isDigit() }.take(3)
        _uiState.update { it.copy(actionQty = sanitized) }
    }

    fun onDismissItemActionDialog() {
        _uiState.update {
            it.copy(
                showItemActionDialog = false,
                selectedActionItem = null,
                actionReason = "",
                actionQty = "",
                actionMaxQty = 0
            )
        }
    }

    fun onConfirmStorno() {
        val item = _uiState.value.selectedActionItem ?: return
        val itemId = item.itemId ?: return
        val reason = _uiState.value.actionReason
        val maxQty = _uiState.value.actionMaxQty
        val qty = _uiState.value.actionQty.toIntOrNull()
        if (qty == null || qty < 1 || qty > maxQty) {
            _uiState.update { it.copy(message = "Količina mora biti između 1 i $maxQty.") }
            return
        }
        mutateCheck(
            onSuccessMessage = "Storno je evidentiran.",
            refreshFromBackend = true,
            action = {
                stornoCheckItemUseCase(
                    checkId = checkId,
                    itemId = itemId,
                    reason = reason,
                    qty = qty
                )
            }
        )
    }

    fun onConfirmGratis() {
        val item = _uiState.value.selectedActionItem ?: return
        val itemId = item.itemId ?: return
        val reason = _uiState.value.actionReason
        val maxQty = _uiState.value.actionMaxQty
        val qty = _uiState.value.actionQty.toIntOrNull()
        if (qty == null || qty < 1 || qty > maxQty) {
            _uiState.update { it.copy(message = "Količina mora biti između 1 i $maxQty.") }
            return
        }
        mutateCheck(
            onSuccessMessage = "Gratis je evidentiran.",
            refreshFromBackend = true,
            action = {
                gratisCheckItemUseCase(
                    checkId = checkId,
                    itemId = itemId,
                    reason = reason,
                    qty = qty
                )
            }
        )
    }

    fun onConfirmOtpis() {
        val item = _uiState.value.selectedActionItem ?: return
        val itemId = item.itemId ?: return
        val reason = _uiState.value.actionReason
        val maxQty = _uiState.value.actionMaxQty
        val qty = _uiState.value.actionQty.toIntOrNull()
        if (qty == null || qty < 1 || qty > maxQty) {
            _uiState.update { it.copy(message = "Količina mora biti između 1 i $maxQty.") }
            return
        }
        mutateCheck(
            onSuccessMessage = "Otpis je evidentiran.",
            refreshFromBackend = true,
            action = {
                otpisCheckItemUseCase(
                    checkId = checkId,
                    itemId = itemId,
                    reason = reason,
                    qty = qty
                )
            }
        )
    }

    fun onSendToBar() {
        if (!_uiState.value.hasUnsentItems) {
            _uiState.update { it.copy(message = "Nema novih stavki za slanje na sank.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, message = null) }
            runCatching { sendToBarUseCase(checkId = checkId) }
                .onSuccess { updated ->
                    _uiState.update { it.copy(isMutating = false, message = "Runda je poslana na sank.") }
                    applyLoadedCheck(updated)
                    refreshSettlementStateSnapshot()
                    refreshRoundStateSnapshot()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            message = throwable.message ?: "Slanje na sank nije uspjelo."
                        )
                    }
                }
        }
    }

    fun onPay() {
        if (_uiState.value.settlementCompleted) {
            _uiState.update { it.copy(message = "Račun je već podmiren.") }
            return
        }
        val state = _uiState.value
        val paymentItems = buildPaymentItems(
            items = state.items,
            remainingByItemId = state.settlementRemainingByItemId
        )
        if (paymentItems.none { it.remainingQty > 0 }) {
            _uiState.update { it.copy(message = "Nema preostalih stavki za naplatu.") }
            return
        }
        _uiState.update {
            it.copy(
                paymentFlow = it.paymentFlow.copy(
                    payableItems = paymentItems,
                    canSplitPayment = paymentItems.sumOf { item -> item.remainingQty } > 1,
                    showChoiceDialog = true
                ),
                message = null
            )
        }
    }

    fun onDismissPaymentChoice() {
        resetPaymentFlow()
    }

    fun onStartFullPayment() {
        _uiState.update { it.copy(paymentFlow = it.paymentFlow.copy(showChoiceDialog = false)) }
        val selections = _uiState.value.paymentFlow.payableItems
            .filter { it.remainingQty > 0 }
            .map { SettlementSelection(checkItemId = it.checkItemId, qty = it.remainingQty) }
        if (selections.isEmpty()) {
            _uiState.update { it.copy(message = "Nema preostalih stavki za naplatu.") }
            return
        }
        val totalAmount = calculateSelectionAmount(selections)
        _uiState.update { state ->
            state.copy(
                paymentFlow = state.paymentFlow.copy(
                    showChoiceDialog = false,
                    showMethodDialog = true,
                    methodTargetPartId = 0L,
                    methodTargetAmount = totalAmount,
                    methodTargetLabel = "Full"
                )
            )
        }
    }

    fun onStartSplitPayment() {
        val current = _uiState.value
        if (!current.paymentFlow.canSplitPayment) {
            _uiState.update { it.copy(message = "Split nije dostupan za jednu stavku/količinu.") }
            return
        }
        val paymentItems = buildPaymentItems(
            items = current.items,
            remainingByItemId = current.settlementRemainingByItemId
        )
        if (paymentItems.none { it.remainingQty > 0 }) {
            _uiState.update { it.copy(message = "Nema preostalih stavki za split.") }
            return
        }
        _uiState.update {
            it.copy(
                paymentFlow = it.paymentFlow.copy(
                    payableItems = paymentItems,
                    showChoiceDialog = false,
                    showSplitDialog = true,
                    isSplitSummary = false
                )
            )
        }
    }

    fun onDismissSplitDialog() {
        resetPaymentFlow()
    }

    fun onSplitQtyIncrease(checkItemId: Long) {
        updateSplitSelection(checkItemId = checkItemId, delta = 1)
    }

    fun onSplitQtyDecrease(checkItemId: Long) {
        updateSplitSelection(checkItemId = checkItemId, delta = -1)
    }

    fun onSplitNext() {
        val selections = currentSplitSelections()
        if (selections.isEmpty()) {
            _uiState.update { it.copy(message = "Odaberite barem jednu stavku za sljedeći part.") }
            return
        }
        preparePart(selections = selections, payNow = false)
    }

    fun onSplitPayNow() {
        val selections = currentSplitSelections()
        if (selections.isEmpty()) {
            _uiState.update { it.copy(message = "Odaberite barem jednu stavku za naplatu.") }
            return
        }
        preparePart(selections = selections, payNow = true)
    }

    fun onSplitShowSummary() {
        _uiState.update { it.copy(paymentFlow = it.paymentFlow.copy(isSplitSummary = true, showSplitDialog = true)) }
    }

    fun onSplitPayPart(partId: Long) {
        val part = _uiState.value.paymentFlow.splitParts.firstOrNull { it.partId == partId } ?: return
        if (part.status == SettlementPartStatus.PAID) return
        _uiState.update {
            it.copy(
                paymentFlow = it.paymentFlow.copy(
                    showMethodDialog = true,
                    methodTargetPartId = part.partId,
                    methodTargetAmount = part.amount,
                    methodTargetLabel = part.label
                )
            )
        }
    }

    fun onDismissMethodDialog() {
        resetPaymentFlow()
    }

    fun onChooseCash() {
        val target = _uiState.value.paymentFlow.methodTargetPartId ?: return
        val amount = _uiState.value.paymentFlow.methodTargetAmount
        Log.i(TAG, "onChooseCash checkId=$checkId partId=$target amount=$amount")
        settlePart(
            partId = target,
            method = SettlementMethod.CASH,
            isFullTarget = _uiState.value.paymentFlow.methodTargetLabel == "Full",
            action = {
                val resolvedPartId = resolvePartIdForMethod(
                    preferredPartId = target,
                    amount = amount,
                    method = SettlementMethod.CASH,
                    tipAmount = 0.0
                )
                paySettlementPartCashUseCase(
                    checkId = checkId,
                    partId = resolvedPartId,
                    amount = amount
                )
            }
        )
    }

    fun onChooseCard(tipAmount: Double) {
        val target = _uiState.value.paymentFlow.methodTargetPartId ?: return
        val amount = _uiState.value.paymentFlow.methodTargetAmount
        val normalizedTip = normalizeMoney(tipAmount)
        if (normalizedTip > amount) {
            _uiState.update { it.copy(message = "Napojnica ne može biti veća od iznosa naplate.") }
            return
        }
        val providerRef = "debug-${System.currentTimeMillis()}"
        val clientTransactionId = "check-${checkId}-part-${target}-${System.currentTimeMillis()}"
        Log.i(
            TAG,
            "onChooseCard checkId=$checkId partId=$target amount=$amount tip=$normalizedTip providerRef=$providerRef clientTxn=$clientTransactionId"
        )
        settlePart(
            partId = target,
            method = SettlementMethod.CARD,
            isFullTarget = _uiState.value.paymentFlow.methodTargetLabel == "Full",
            action = {
                val resolvedPartId = resolvePartIdForMethod(
                    preferredPartId = target,
                    amount = amount,
                    method = SettlementMethod.CARD,
                    tipAmount = normalizedTip
                )
                confirmSettlementPartCardUseCase(
                    checkId = checkId,
                    partId = resolvedPartId,
                    amount = amount,
                    tipAmount = normalizedTip,
                    approved = true,
                    providerRef = providerRef,
                    clientTransactionId = clientTransactionId
                )
            }
        )
    }

    fun onSplitCloseCheck() {
        if (!_uiState.value.paymentFlow.canCloseCheck) {
            _uiState.update { it.copy(message = "Check se može zatvoriti tek kad su svi partovi plaćeni.") }
            return
        }
        closeCheckAfterSettlement()
    }

    fun onFree() {
        if (_uiState.value.status.equals("FREE", ignoreCase = true)) {
            _uiState.update { it.copy(message = "Check je već zatvoren.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, message = null) }
            runCatching {
                closeCheckUseCase(checkId = checkId)
            }.onSuccess {
                runCatching { getCheckByIdUseCase(checkId) }
                    .onSuccess { updated -> if (updated != null) applyLoadedCheck(updated) }
                _uiState.update {
                    it.copy(
                        isMutating = false,
                        message = "Check je zatvoren kao FREE."
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isMutating = false,
                        message = throwable.message ?: "Free zatvaranje nije uspjelo."
                    )
                }
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun onStartFiscalizeReceipt(receiptId: Long) {
        _uiState.update {
            it.copy(
                showFiscalizeDialog = true,
                fiscalizeReceiptId = receiptId,
                fiscalizePin = ""
            )
        }
    }

    fun onDismissFiscalizeDialog() {
        _uiState.update {
            it.copy(
                showFiscalizeDialog = false,
                fiscalizeReceiptId = null,
                fiscalizePin = ""
            )
        }
    }

    fun onFiscalizePinChanged(value: String) {
        val sanitized = value.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(fiscalizePin = sanitized) }
    }

    fun onConfirmFiscalizeReceipt() {
        val state = _uiState.value
        val receiptId = state.fiscalizeReceiptId ?: return
        val pin = state.fiscalizePin
        if (pin.length < 4) {
            _uiState.update { it.copy(message = "Unesite ispravan PIN (min 4 znamenke).") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, message = null) }
            runCatching {
                authRepository.verifyPin(pin)
                fiscalizeReceiptUseCase(checkId = checkId, receiptId = receiptId)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isMutating = false,
                        showFiscalizeDialog = false,
                        fiscalizeReceiptId = null,
                        fiscalizePin = "",
                        message = if (result.action.equals("already_fiscalized", ignoreCase = true)) {
                            "Račun je već fiskaliziran."
                        } else {
                            "Račun je fiskaliziran."
                        },
                        settlementReceipts = if (result.receipts.isNotEmpty()) result.receipts else it.settlementReceipts
                    )
                }
                refreshSettlementStateNow()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isMutating = false,
                        message = error.message ?: "Fiskalizacija nije uspjela."
                    )
                }
            }
        }
    }

    fun refresh() {
        loadCheck(showLoading = false)
    }

    private fun preparePart(
        selections: List<SettlementSelection>,
        payNow: Boolean,
        partLabelPrefix: String = "Part"
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, message = null) }
            val requestedAmount = calculateSelectionAmount(selections)
            runCatching {
                prepareSettlementPartUseCase(
                    checkId = checkId,
                    selections = selections,
                    amount = requestedAmount
                )
            }.onSuccess { prepared ->
                _uiState.update { state ->
                    val newPartIndex = state.paymentFlow.splitParts.size + 1
                    val label = if (partLabelPrefix == "Full") {
                        "Full"
                    } else {
                        "Part $newPartIndex"
                    }
                    val updatedParts = state.paymentFlow.splitParts + SplitPartUi(
                        partId = prepared.part.partId,
                        label = label,
                        amount = prepared.part.amount.takeIf { it > 0.0 }
                            ?: requestedAmount,
                        status = prepared.part.status,
                        method = prepared.part.method
                    )
                    val selectedByItem = selections.associateBy({ it.checkItemId }, { it.qty })
                    val updatedItems = state.paymentFlow.payableItems.map { item ->
                        val selectedQty = selectedByItem[item.checkItemId] ?: 0
                        val remaining = (item.remainingQty - selectedQty).coerceAtLeast(0)
                        item.copy(remainingQty = remaining, selectedQty = 0)
                    }
                    val allSelected = updatedItems.none { it.remainingQty > 0 }
                    state.copy(
                        isMutating = false,
                        paymentFlow = state.paymentFlow.copy(
                            splitParts = updatedParts,
                            payableItems = updatedItems,
                            showSplitDialog = state.paymentFlow.showSplitDialog,
                            splitStepIndex = if (allSelected) state.paymentFlow.splitStepIndex else state.paymentFlow.splitStepIndex + 1,
                            isSplitSummary = allSelected || state.paymentFlow.isSplitSummary,
                            showMethodDialog = payNow || partLabelPrefix == "Full",
                            methodTargetPartId = prepared.part.partId,
                            methodTargetAmount = prepared.part.amount.takeIf { it > 0.0 }
                                ?: requestedAmount,
                            methodTargetLabel = label
                        )
                    )
                }
                refreshSettlementStateNow()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isMutating = false,
                        message = throwable.message ?: "Priprema parta nije uspjela."
                    )
                }
            }
        }
    }

    private fun settlePart(
        partId: Long,
        method: SettlementMethod,
        isFullTarget: Boolean,
        action: suspend () -> pos.finestar.barion.domain.model.SettlementPart
    ) {
        Log.i(TAG, "settlePart start checkId=$checkId partId=$partId method=${method.name} isFullTarget=$isFullTarget")
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, message = null) }
            runCatching { action() }
                .onSuccess { settled ->
                    val resolvedPartId = settled.partId.takeIf { it > 0L } ?: partId
                    Log.i(
                        TAG,
                        "settlePart success checkId=$checkId partId=$partId resolvedPartId=$resolvedPartId method=${method.name} status=${settled.status} action=${settled.action} amount=${settled.amount}"
                    )
                    _uiState.update { state ->
                        var matched = false
                        val updatedParts = state.paymentFlow.splitParts.map { part ->
                            if (part.partId == resolvedPartId || part.partId == partId) {
                                matched = true
                                part.copy(
                                    partId = resolvedPartId,
                                    amount = settled.amount,
                                    status = settled.status,
                                    method = method
                                )
                            } else {
                                part
                            }
                        }
                        val mergedParts = if (matched) {
                            updatedParts
                        } else {
                            updatedParts + SplitPartUi(
                                partId = resolvedPartId,
                                label = state.paymentFlow.methodTargetLabel.ifBlank { "Part" },
                                amount = settled.amount,
                                status = settled.status,
                                method = method
                            )
                        }
                        state.copy(
                            isMutating = false,
                            message = when {
                                method == SettlementMethod.CARD && settled.action.equals("failed", ignoreCase = true) ->
                                    "Kartica odbijena. Part ostaje za retry."
                                method == SettlementMethod.CARD && settled.status == SettlementPartStatus.PAID ->
                                    "CARD naplata uspješna."
                                else -> null
                            },
                            paymentFlow = state.paymentFlow.copy(
                                splitParts = mergedParts,
                                showMethodDialog = false,
                                methodTargetPartId = null,
                                methodTargetAmount = 0.0,
                                methodTargetLabel = "",
                                isSplitSummary = !isFullTarget,
                                showSplitDialog = !isFullTarget
                            )
                        )
                    }
                    runCatching { getCheckByIdUseCase(checkId, forceRefresh = true) }
                        .onSuccess { updated -> if (updated != null) applyLoadedCheck(updated) }
                    val settlementState = refreshSettlementStateNow()
                    if (method == SettlementMethod.CASH) {
                        _uiState.update { state ->
                            state.copy(
                                message = when {
                                    settlementState?.isPaidOrIssued == true -> "Gotovina naplaćena."
                                    settlementState?.shouldLockPayment == true -> "Naplata zaključena."
                                    else -> "Gotovina evidentirana."
                                }
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    Log.e(
                        TAG,
                        "settlePart failed checkId=$checkId partId=$partId method=${method.name} message=${throwable.message}",
                        throwable
                    )
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            message = throwable.message ?: "Naplata parta nije uspjela.",
                            paymentFlow = it.paymentFlow.copy(showMethodDialog = false)
                        )
                    }
                }
        }
    }

    private suspend fun resolvePartIdForMethod(
        preferredPartId: Long,
        amount: Double,
        method: SettlementMethod,
        tipAmount: Double
    ): Long {
        val state = runCatching { getSettlementStateUseCase(checkId) }.getOrNull()
        val preferredPart = state
            ?.parts
            ?.firstOrNull { it.partId == preferredPartId }
        if (preferredPart != null && preferredPart.isReusableFor(method, tipAmount)) {
            Log.i(
                TAG,
                "resolvePartIdForMethod reused preferred checkId=$checkId preferredPartId=$preferredPartId method=${method.name} tip=${"%.2f".format(tipAmount)}"
            )
            return preferredPartId
        }
        if (
            preferredPart != null &&
            method == SettlementMethod.CARD &&
            preferredPart.method == SettlementMethod.CARD &&
            !sameMoneyInCents(preferredPart.tipAmount ?: 0.0, tipAmount)
        ) {
            Log.i(
                TAG,
                "resolvePartIdForMethod reprepare_due_tip_mismatch checkId=$checkId preferredPartId=$preferredPartId existingTip=${"%.2f".format(preferredPart.tipAmount ?: 0.0)} requestedTip=${"%.2f".format(tipAmount)}"
            )
        }
        val reusable = state
            ?.parts
            ?.firstOrNull { it.isReusableFor(method, tipAmount) }
            ?.partId
        if (reusable != null && reusable > 0L) {
            Log.i(
                TAG,
                "resolvePartIdForMethod reused checkId=$checkId preferredPartId=$preferredPartId resolvedPartId=$reusable method=${method.name} tip=${"%.2f".format(tipAmount)}"
            )
            return reusable
        }

        val prepared = prepareSettlementPartUseCase(
            checkId = checkId,
            selections = emptyList(),
            amount = amount,
            method = method,
            tipAmount = tipAmount,
            remainingTotal = state?.remainingTotal
        )
        val refreshedState = runCatching { getSettlementStateUseCase(checkId) }.getOrNull()
        val preparedPartId = refreshedState
            ?.parts
            ?.firstOrNull { it.isReusableFor(method, tipAmount) }
            ?.partId
            ?: prepared.part.partId.takeIf { it > 0L }
            ?: preferredPartId
        val preparedPartIsValidForMethod = refreshedState
            ?.parts
            ?.firstOrNull { it.partId == preparedPartId }
            ?.isReusableFor(method, tipAmount)
            ?: (prepared.part.method == method)
        if (method == SettlementMethod.CARD && !preparedPartIsValidForMethod) {
            throw IllegalStateException("Nije pronađen pripremljeni CARD part. Osvježi i pokušaj ponovno.")
        }
        Log.i(
            TAG,
            "resolvePartIdForMethod prepared checkId=$checkId preferredPartId=$preferredPartId resolvedPartId=$preparedPartId rawPreparedPartId=${prepared.part.partId} method=${method.name} amount=${"%.2f".format(amount)} tip=${"%.2f".format(tipAmount)}"
        )
        return preparedPartId
    }

    private fun closeCheckAfterSettlement() {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, message = null) }
            runCatching { closeCheckUseCase(checkId = checkId) }
                .onSuccess {
                    runCatching { getCheckByIdUseCase(checkId, forceRefresh = true) }
                        .onSuccess { updated -> if (updated != null) applyLoadedCheck(updated) }
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            message = "Check je zatvoren.",
                            paymentFlow = PaymentFlowState()
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            message = throwable.message ?: "Zatvaranje checka nije uspjelo."
                        )
                    }
                }
        }
    }

    private fun buildPaymentItems(
        items: List<CheckItem>,
        remainingByItemId: Map<Long, Int>
    ): List<PaymentItemUi> {
        return items.asSequence()
            .filter { it.itemId != null }
            .filter { it.lineType.equals("NORMAL", ignoreCase = true) }
            .filter { it.qty > 0 }
            .mapNotNull { item ->
                val itemId = item.itemId ?: return@mapNotNull null
                val remainingQty = if (remainingByItemId.isNotEmpty()) {
                    remainingByItemId[itemId] ?: 0
                } else {
                    item.qty
                }
                if (remainingQty <= 0) return@mapNotNull null
                PaymentItemUi(
                    checkItemId = itemId,
                    name = item.name,
                    price = item.price,
                    totalQty = item.qty,
                    remainingQty = remainingQty,
                    selectedQty = 0
                )
            }
            .toList()
    }

    private fun currentSplitSelections(): List<SettlementSelection> {
        return _uiState.value.paymentFlow.payableItems
            .asSequence()
            .filter { it.selectedQty > 0 }
            .map { SettlementSelection(checkItemId = it.checkItemId, qty = it.selectedQty) }
            .toList()
    }

    private fun updateSplitSelection(checkItemId: Long, delta: Int) {
        _uiState.update { state ->
            val updatedItems = state.paymentFlow.payableItems.map { item ->
                if (item.checkItemId != checkItemId) return@map item
                val next = (item.selectedQty + delta).coerceIn(0, item.remainingQty)
                item.copy(selectedQty = next)
            }
            state.copy(paymentFlow = state.paymentFlow.copy(payableItems = updatedItems))
        }
    }

    private fun mutateCheck(
        onSuccessMessage: String? = null,
        refreshFromBackend: Boolean = false,
        action: suspend () -> CheckSession
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            runCatching { action() }
                .onSuccess { updated ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            showItemActionDialog = false,
                            selectedActionItem = null,
                            actionReason = "",
                            actionQty = "",
                            actionMaxQty = 0,
                            message = onSuccessMessage
                        )
                    }
                    if (refreshFromBackend) {
                        runCatching { getCheckByIdUseCase(checkId, forceRefresh = true) }
                            .onSuccess { fresh -> if (fresh != null) applyLoadedCheck(fresh) }
                            .onFailure { applyLoadedCheck(updated) }
                    } else {
                        applyLoadedCheck(updated)
                    }
                    refreshSettlementStateSnapshot()
                    refreshRoundStateSnapshot()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            message = throwable.message ?: "Greska pri izmjeni stavki."
                        )
                    }
                }
        }
    }

    private fun loadCheck(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(error = null) }
            }

            runCatching { getCheckByIdUseCase(checkId, forceRefresh = false) }
                .onSuccess { check ->
                    if (check != null) {
                        _uiState.update { it.copy(isLoading = false, error = null) }
                        applyLoadedCheck(check)
                        refreshSettlementStateSnapshot()
                        refreshRoundStateSnapshot()
                        viewModelScope.launch {
                            runCatching { getCheckByIdUseCase(checkId, forceRefresh = true) }
                                .onSuccess { fresh ->
                                    if (fresh != null) {
                                        _uiState.update { it.copy(isLoading = false, error = null) }
                                        applyLoadedCheck(fresh)
                                        refreshSettlementStateSnapshot()
                                        refreshRoundStateSnapshot()
                                    }
                                }
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Check nije pronaden."
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Greska pri ucitavanju checka."
                        )
                    }
                }
        }
    }

    private fun applyLoadedCheck(check: CheckSession) {
        _uiState.update {
            val nextPaymentFlow = if (check.status.name == "FREE") {
                PaymentFlowState()
            } else {
                it.paymentFlow
            }
            it.copy(
                status = check.status.name,
                items = check.items,
                hasUnsentItems = check.items.any { item -> !item.sentToBar },
                subtotal = check.subtotal,
                tax = check.tax,
                total = check.total,
                error = null,
                paymentFlow = nextPaymentFlow,
                settlementReceiptPdfUrl = it.settlementReceiptPdfUrl,
                settlementReceipts = it.settlementReceipts,
                settlementCompleted = it.settlementCompleted || check.status.name.equals("FREE", ignoreCase = true)
            )
        }
    }

    private fun refreshSettlementStateSnapshot() {
        viewModelScope.launch { refreshSettlementStateNow() }
    }

    private fun refreshRoundStateSnapshot() {
        viewModelScope.launch { refreshRoundStateNow() }
    }

    private suspend fun refreshSettlementStateNow(): pos.finestar.barion.domain.model.SettlementState? {
        var latest: pos.finestar.barion.domain.model.SettlementState? = null
        runCatching { getSettlementStateUseCase(checkId) }
            .onSuccess { settlementState ->
                latest = settlementState
                applySettlementState(settlementState)
            }
        refreshRoundStateNow()
        return latest
    }

    private suspend fun refreshRoundStateNow() {
        runCatching { getCheckRoundStateUseCase(checkId) }
            .onSuccess { roundState ->
                applyRoundState(roundState.items)
            }
    }

    private fun applySettlementState(settlementState: pos.finestar.barion.domain.model.SettlementState) {
        _uiState.update { state ->
            val receiptId = settlementState.issuedReceiptId
                ?: settlementState.posReceiptId
                ?: settlementState.posReceiptIds.lastOrNull()
            val shouldLockPayment = settlementState.shouldLockPayment
            val paidParts = settlementState.parts
                .filter { it.status.equals("PAID", ignoreCase = true) }
            val paidPartsByReceiptId = paidParts
                .mapNotNull { part -> part.issuedReceiptId?.let { receiptIdKey -> receiptIdKey to part } }
                .toMap()
            val unmatchedPaidParts = paidParts
                .sortedByDescending { it.partId }
                .toMutableList()
            fun consumePart(part: SettlementStatePart?) {
                if (part == null) return
                val index = unmatchedPaidParts.indexOfFirst { it.partId == part.partId }
                if (index >= 0) {
                    unmatchedPaidParts.removeAt(index)
                }
            }
            fun resolvePartForReceipt(receiptId: Long?, totalAmount: Double?): SettlementStatePart? {
                val byReceiptId = receiptId?.let { paidPartsByReceiptId[it] }
                if (byReceiptId != null) {
                    consumePart(byReceiptId)
                    return byReceiptId
                }
                val byAmount = totalAmount?.let { amount ->
                    unmatchedPaidParts.firstOrNull { part ->
                        val partAmount = part.amount ?: return@firstOrNull false
                        sameMoneyInCents(partAmount, amount)
                    }
                }
                if (byAmount != null) {
                    consumePart(byAmount)
                    return byAmount
                }
                return unmatchedPaidParts.firstOrNull().also { consumePart(it) }
            }
            fun logReceiptPartMatch(receiptId: Long, matchedPart: SettlementStatePart?) {
                val method = matchedPart?.methodDisplay ?: matchedPart?.method?.name
                runCatching {
                    Log.d(
                        TAG,
                        "receiptMapping receiptId=$receiptId matchedPartId=${matchedPart?.partId} method=$method"
                    )
                }
            }
            val receiptsForUi = if (settlementState.receipts.isNotEmpty()) {
                settlementState.receipts.map { receipt ->
                    if (!receipt.paymentMethod.isNullOrBlank() || !receipt.cardMaskedPan.isNullOrBlank()) {
                        logReceiptPartMatch(receipt.id, null)
                        receipt
                    } else {
                        val part = resolvePartForReceipt(
                            receiptId = receipt.id,
                            totalAmount = receipt.totalAmount
                        )
                        logReceiptPartMatch(receipt.id, part)
                        receipt.copy(
                            paymentMethod = part?.methodDisplay ?: part?.method?.name,
                            cardBrand = part?.cardBrand,
                            cardMaskedPan = part?.cardMaskedPan
                        )
                    }
                }
            } else {
                settlementState.posReceiptIds.map { id ->
                    val part = resolvePartForReceipt(receiptId = id, totalAmount = null)
                    logReceiptPartMatch(id, part)
                    SettlementReceipt(
                        id = id,
                        pdfUrl = if (id == receiptId) settlementState.receiptPdfUrl else null,
                        paymentMethod = part?.methodDisplay ?: part?.method?.name,
                        cardBrand = part?.cardBrand,
                        cardMaskedPan = part?.cardMaskedPan
                    )
                }
            }
            val remainingByItemId = settlementState.items.associateBy({ it.id }, { it.remainingQuantity })
            val openTotals = calculateOpenTotals(state = state, remainingTotal = settlementState.remainingTotal)
            state.copy(
                settlementCompleted = shouldLockPayment,
                settlementReceiptPdfUrl = if (shouldLockPayment) {
                    settlementState.receiptPdfUrl ?: state.settlementReceiptPdfUrl
                } else {
                    null
                },
                settlementReceipts = receiptsForUi,
                paymentFlow = if (shouldLockPayment) PaymentFlowState() else state.paymentFlow,
                settlementRemainingByItemId = remainingByItemId,
                settlementOpenSubtotal = openTotals?.first,
                settlementOpenTax = openTotals?.second,
                settlementOpenTotal = openTotals?.third
            )
        }
    }

    private fun applyRoundState(items: List<CheckRoundStateItem>) {
        val mapped = items.associate { item ->
            item.id to RoundStateUi(
                sourceQuantity = item.sourceQuantity,
                remainingQuantity = item.remainingQuantity,
                strikeMain = item.strikeMain,
                paidLine = item.paidLine?.let { paid ->
                    PaidLineUi(
                        lineType = paid.lineType,
                        quantity = paid.quantity,
                        unitPrice = paid.unitPrice,
                        totalAmount = paid.totalAmount,
                        uiColor = paid.uiColor
                    )
                }
            )
        }
        _uiState.update { state ->
            state.copy(
                roundStateByItemId = mapped,
                settlementRemainingByItemId = state.settlementRemainingByItemId.ifEmpty {
                    mapped.mapValues { (_, value) -> value.remainingQuantity }
                }
            )
        }
    }

    private fun calculateSelectionAmount(selections: List<SettlementSelection>): Double {
        val state = _uiState.value
        return selections.sumOf { selection ->
            state.paymentFlow.payableItems
                .firstOrNull { it.checkItemId == selection.checkItemId }
                ?.let { it.price * selection.qty }
                ?: 0.0
        }
    }

    private fun resetPaymentFlow() {
        _uiState.update {
            it.copy(
                paymentFlow = PaymentFlowState(),
                message = null
            )
        }
    }

    private fun calculateAvailableActionQty(item: CheckItem): Int {
        val itemId = item.itemId ?: return 0
        val sourceQty = abs(item.qty)
        val consumedByActions = _uiState.value.items
            .asSequence()
            .filter {
                it.lineType.equals("STORNO", ignoreCase = true) ||
                    it.lineType.equals("OTPIS", ignoreCase = true)
            }
            .filter { actionTargetsItem(it.note, itemId) }
            .sumOf { abs(it.qty) }
        val availableByActions = (sourceQty - consumedByActions).coerceAtLeast(0)
        val availableBySettlement = _uiState.value.settlementRemainingByItemId[itemId]
        return if (availableBySettlement != null) {
            minOf(availableByActions, availableBySettlement.coerceAtLeast(0))
        } else {
            availableByActions
        }
    }

    private fun actionTargetsItem(note: String?, itemId: Long): Boolean {
        if (note.isNullOrBlank()) return false
        val pattern = Regex("""\[(?:storno|otpis)_of:(\d+)]""", RegexOption.IGNORE_CASE)
        val targetId = pattern.find(note)?.groupValues?.getOrNull(1)?.toLongOrNull()
        return targetId == itemId
    }

    private fun calculateOpenTotals(
        state: UiState,
        remainingTotal: Double?
    ): Triple<Double, Double, Double>? {
        val openTotal = remainingTotal ?: return null
        if (openTotal < 0.0) return null
        val checkTotal = state.total
        if (checkTotal <= 0.0) {
            return Triple(openTotal, 0.0, openTotal)
        }
        val ratio = (openTotal / checkTotal).coerceIn(0.0, 1.0)
        val openSubtotal = state.subtotal * ratio
        val openTax = openTotal - openSubtotal
        return Triple(openSubtotal, openTax, openTotal)
    }

    private fun normalizeMoney(amount: Double): Double {
        return BigDecimal.valueOf(amount.coerceAtLeast(0.0))
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }
}

private fun SettlementStatePart.isReusableFor(method: SettlementMethod, requestedTipAmount: Double): Boolean {
    val normalizedStatus = status.uppercase()
    val reusableStatus = normalizedStatus != "PAID" &&
        normalizedStatus != "FAILED" &&
        normalizedStatus != "VOID" &&
        normalizedStatus != "CANCELLED"
    if (!reusableStatus || this.method != method) {
        return false
    }
    if (method != SettlementMethod.CARD) {
        return true
    }
    return sameMoneyInCents(this.tipAmount ?: 0.0, requestedTipAmount)
}

private fun sameMoneyInCents(left: Double, right: Double): Boolean {
    val leftCents = kotlin.math.round(left * 100.0).toLong()
    val rightCents = kotlin.math.round(right * 100.0).toLong()
    return leftCents == rightCents
}
