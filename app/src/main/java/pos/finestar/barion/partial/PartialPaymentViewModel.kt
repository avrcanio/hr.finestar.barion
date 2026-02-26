package pos.finestar.barion.partial

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pos.finestar.barion.domain.model.CheckRoundStateItem
import pos.finestar.barion.domain.model.CheckItem
import pos.finestar.barion.domain.model.SettlementMethod
import pos.finestar.barion.domain.model.SettlementSelection
import pos.finestar.barion.domain.model.SettlementState
import pos.finestar.barion.domain.model.SettlementStateItem
import pos.finestar.barion.domain.model.SettlementStatePart
import pos.finestar.barion.domain.usecase.GetCheckRoundStateUseCase
import pos.finestar.barion.domain.usecase.ConfirmSettlementPartCardUseCase
import pos.finestar.barion.domain.usecase.GetCheckByIdUseCase
import pos.finestar.barion.domain.usecase.GetSettlementStateUseCase
import pos.finestar.barion.domain.usecase.PaySettlementPartCashUseCase
import pos.finestar.barion.domain.usecase.PrepareSettlementPartUseCase
import pos.finestar.barion.ui.navigation.NavRoutes

@HiltViewModel
class PartialPaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSettlementStateUseCase: GetSettlementStateUseCase,
    private val getCheckRoundStateUseCase: GetCheckRoundStateUseCase,
    private val getCheckByIdUseCase: GetCheckByIdUseCase,
    private val prepareSettlementPartUseCase: PrepareSettlementPartUseCase,
    private val paySettlementPartCashUseCase: PaySettlementPartCashUseCase,
    private val confirmSettlementPartCardUseCase: ConfirmSettlementPartCardUseCase
) : ViewModel() {
    companion object {
        private const val TAG = "PartialPaymentVM"
    }

    data class ItemUi(
        val id: Long,
        val name: String,
        val imageUrl: String?,
        val roundNumber: Int?,
        val sourceQty: Int,
        val remainingQty: Int,
        val remainingAmount: Double,
        val strikeMain: Boolean,
        val paidLine: PaidLineUi? = null,
        val fallbackUnitPrice: Double = 0.0,
        val selectedQty: Int = 0
    ) {
        val unitAmount: Double
            get() = when {
                remainingQty <= 0 -> 0.0
                remainingAmount > 0.0 -> remainingAmount / remainingQty
                else -> fallbackUnitPrice
            }
        val selectedAmount: Double
            get() = unitAmount.coerceAtLeast(0.0) * selectedQty
    }

    data class PaidLineUi(
        val quantity: Double,
        val unitPrice: Double,
        val totalAmount: Double,
        val lineType: String,
        val uiColor: String?
    )

    data class UiState(
        val checkId: Long = 0L,
        val tableName: String = "",
        val isLoading: Boolean = true,
        val isMutating: Boolean = false,
        val message: String? = null,
        val error: String? = null,
        val checkStatus: String = "",
        val paymentStatus: String? = null,
        val checkTotal: Double? = null,
        val confirmedTotal: Double? = null,
        val remainingTotal: Double? = null,
        val preparedPartId: Long? = null,
        val items: List<ItemUi> = emptyList(),
        val showMethodDialog: Boolean = false
    ) {
        val selectedTotal: Double
            get() = items.sumOf { it.selectedAmount }
        val hasSelection: Boolean
            get() = items.any { it.selectedQty > 0 }
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
    private var checkItemMap: Map<Long, CheckItem> = emptyMap()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            Log.d(TAG, "refresh start checkId=$checkId")
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val settlement = getSettlementStateUseCase(checkId)
                val check = runCatching { getCheckByIdUseCase(checkId, forceRefresh = true) }.getOrNull()
                checkItemMap = check?.items
                    ?.asSequence()
                    ?.mapNotNull { item -> item.itemId?.let { id -> id to item } }
                    ?.toMap()
                    .orEmpty()
                settlement
            }.onSuccess { settlement ->
                val roundStateItems = runCatching { getCheckRoundStateUseCase(checkId).items }
                    .onFailure { error ->
                        Log.w(TAG, "refresh round-state failed checkId=$checkId message=${error.message}")
                    }
                    .getOrElse { emptyList() }
                Log.d(
                    TAG,
                    "refresh success checkId=$checkId status=${settlement.checkStatus} paymentStatus=${settlement.paymentStatus} remaining=${settlement.remainingTotal} parts=${settlement.parts.size} items=${settlement.items.size}"
                )
                applySettlementState(settlement = settlement, roundStateItems = roundStateItems)
            }
                .onFailure { error ->
                    Log.e(TAG, "refresh failed checkId=$checkId message=${error.message}", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Ne mogu učitati partial stanje."
                        )
                    }
                }
        }
    }

    fun onSelectAllRound() {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item -> item.copy(selectedQty = item.remainingQty) }
            )
        }
    }

    fun onToggleRound(roundNumber: Int?) {
        _uiState.update { state ->
            val inRound = state.items.filter { it.roundNumber == roundNumber }
            val allSelected = inRound.isNotEmpty() && inRound.all { it.selectedQty == it.remainingQty }
            state.copy(
                items = state.items.map { item ->
                    if (item.roundNumber != roundNumber) item
                    else item.copy(selectedQty = if (allSelected) 0 else item.remainingQty)
                }
            )
        }
    }

    fun onIncrease(itemId: Long) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.id != itemId) item
                    else item.copy(selectedQty = (item.selectedQty + 1).coerceAtMost(item.remainingQty))
                }
            )
        }
    }

    fun onDecrease(itemId: Long) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.id != itemId) item
                    else item.copy(selectedQty = (item.selectedQty - 1).coerceAtLeast(0))
                }
            )
        }
    }

    fun onPay() {
        if (!_uiState.value.hasSelection) {
            _uiState.update { it.copy(message = "Odaberite stavke za naplatu.") }
            return
        }
        _uiState.update { it.copy(showMethodDialog = true) }
    }

    fun onDismissMethodDialog() {
        _uiState.update { it.copy(showMethodDialog = false) }
    }

    fun onPayCash() {
        settleSelectedItems(isCard = false)
    }

    fun onPayCard() {
        settleSelectedItems(isCard = true)
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun settleSelectedItems(isCard: Boolean) {
        val flowId = UUID.randomUUID().toString().take(8)
        val selected = _uiState.value.items.filter { it.selectedQty > 0 }
        if (selected.isEmpty()) return
        val selections = selected.map { SettlementSelection(checkItemId = it.id, qty = it.selectedQty) }
        val amount = selected.sumOf { it.selectedAmount }
        if (amount < 0.01) {
            _uiState.update {
                it.copy(
                    message = "Odabrane stavke nemaju naplativ iznos (min 0.01 EUR)."
                )
            }
            Log.w(
                TAG,
                "[flow:$flowId] settleSelectedItems aborted checkId=$checkId reason=non_positive_amount selected=${selected.joinToString { "${it.id}:${it.selectedQty}@${"%.2f".format(it.selectedAmount)}" }}"
            )
            return
        }
        Log.d(
            TAG,
            "[flow:$flowId] settleSelectedItems start checkId=$checkId method=${if (isCard) "CARD" else "CASH"} selectedCount=${selected.size} amount=${"%.2f".format(amount)} selections=${selections.joinToString { "${it.checkItemId}:${it.qty}" }}"
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, message = null, showMethodDialog = false) }
            runCatching {
                if (isCard) {
                    val preparedTarget = resolveTargetPart(
                        selections = selections,
                        requestedAmount = amount,
                        method = SettlementMethod.CARD,
                        flowId = flowId
                    )
                    val partId = preparedTarget.partId
                    val payAmount = preparedTarget.amount
                    Log.d(
                        TAG,
                        "[flow:$flowId] card confirm request checkId=$checkId partId=$partId amount=${"%.2f".format(payAmount)}"
                    )
                    confirmSettlementPartCardUseCase(
                        checkId = checkId,
                        partId = partId,
                        amount = payAmount,
                        tipAmount = 0.0,
                        approved = true,
                        providerRef = "debug-${flowId}-${System.currentTimeMillis()}",
                        clientTransactionId = "flow-${flowId}-check-${checkId}-part-${partId}-${System.currentTimeMillis()}"
                    )
                } else {
                    val preparedTarget = resolveTargetPart(
                        selections = selections,
                        requestedAmount = amount,
                        method = SettlementMethod.CASH,
                        flowId = flowId
                    )
                    Log.d(
                        TAG,
                        "[flow:$flowId] cash pay request checkId=$checkId partId=${preparedTarget.partId} amount=${"%.2f".format(preparedTarget.amount)} itemCount=${selections.size}"
                    )
                    paySettlementPartCashUseCase(
                        checkId = checkId,
                        partId = preparedTarget.partId,
                        amount = preparedTarget.amount,
                        selections = selections
                    )
                }
                getSettlementStateUseCase(checkId)
            }.onSuccess { settlementState ->
                val roundStateItems = runCatching { getCheckRoundStateUseCase(checkId).items }
                    .onFailure { error ->
                        Log.w(TAG, "[flow:$flowId] round-state refresh failed checkId=$checkId message=${error.message}")
                    }
                    .getOrElse { emptyList() }
                Log.d(
                    TAG,
                    "[flow:$flowId] settleSelectedItems success checkId=$checkId status=${settlementState.checkStatus} paymentStatus=${settlementState.paymentStatus} remaining=${settlementState.remainingTotal}"
                )
                applySettlementState(settlement = settlementState, roundStateItems = roundStateItems)
                _uiState.update {
                    it.copy(
                        isMutating = false,
                        message = if (isCard) "Kartica naplaćena." else "Gotovina naplaćena."
                    )
                }
            }.onFailure { error ->
                Log.e(
                    TAG,
                    "[flow:$flowId] settleSelectedItems failed checkId=$checkId method=${if (isCard) "CARD" else "CASH"} message=${error.message}",
                    error
                )
                _uiState.update {
                    it.copy(
                        isMutating = false,
                        message = error.message ?: "Naplata nije uspjela."
                    )
                }
            }
        }
    }

    private suspend fun resolveTargetPart(
        selections: List<SettlementSelection>,
        requestedAmount: Double,
        method: SettlementMethod,
        flowId: String
    ): TargetPart {
        val settlementState = getSettlementStateUseCase(checkId)
        val reusablePart = findReusablePart(settlementState.parts, method)
        if (reusablePart != null) {
            val reusableAmount = reusablePart.amount ?: requestedAmount
            Log.d(
                TAG,
                "[flow:$flowId] resolveTargetPart reused existing part checkId=$checkId partId=${reusablePart.partId} method=${method.name} amount=${"%.2f".format(reusableAmount)}"
            )
            return TargetPart(
                partId = reusablePart.partId,
                amount = if (method == SettlementMethod.CASH) requestedAmount else reusableAmount
            )
        }

        return try {
            val remainingTotal = settlementState.remainingTotal
            Log.d(
                TAG,
                "[flow:$flowId] resolveTargetPart prepare request checkId=$checkId method=${method.name} amount=${"%.2f".format(requestedAmount)} remainingTotal=${remainingTotal?.let { "%.2f".format(it) }} selections=${selections.joinToString { "${it.checkItemId}:${it.qty}" }}"
            )
            val prepared = prepareSettlementPartUseCase(
                checkId = checkId,
                selections = selections,
                amount = requestedAmount,
                method = method,
                remainingTotal = remainingTotal
            )
            val preparedPartId = prepared.part.partId.takeIf { it > 0L }
                ?: getSettlementStateUseCase(checkId).preparedPart?.partId
                ?: 0L
            val preparedAmount = prepared.part.amount.takeIf { it > 0.0 } ?: requestedAmount
            Log.d(
                TAG,
                "[flow:$flowId] resolveTargetPart prepare response checkId=$checkId resolvedPartId=$preparedPartId resolvedAmount=${"%.2f".format(preparedAmount)} rawPartId=${prepared.part.partId}"
            )
            TargetPart(partId = preparedPartId, amount = preparedAmount)
        } catch (error: Throwable) {
            if (!isPrepareConflict(error)) throw error
            Log.w(TAG, "[flow:$flowId] resolveTargetPart prepare conflict checkId=$checkId message=${error.message}")
            val current = getSettlementStateUseCase(checkId)
            val preparedPart = findReusablePart(current.parts, method)
                ?: current.preparedPart
                ?: throw error
            val preparedAmount = preparedPart.amount ?: requestedAmount
            Log.d(
                TAG,
                "[flow:$flowId] resolveTargetPart reused prepared part checkId=$checkId partId=${preparedPart.partId} amount=${"%.2f".format(preparedAmount)}"
            )
            TargetPart(
                partId = preparedPart.partId,
                // For strict item-level pay-cash, keep requested amount (sum of selected items).
                amount = if (method == SettlementMethod.CASH) requestedAmount else preparedAmount
            )
        }
    }

    private fun findReusablePart(parts: List<SettlementStatePart>, method: SettlementMethod): SettlementStatePart? {
        return parts.firstOrNull { part ->
            val status = part.status.uppercase()
            val reusableStatus = status != "PAID" && status != "FAILED" && status != "VOID" && status != "CANCELLED"
            reusableStatus && (part.method == null || part.method == method)
        }
    }

    private fun isPrepareConflict(error: Throwable): Boolean {
        val message = error.message.orEmpty().uppercase()
        return when (error) {
            is HttpException -> error.code() == 409
            else -> "HTTP 409" in message ||
                "CONFLICT" in message ||
                "PONOVNO PRIPREMITI" in message ||
                "NIJE MOGUĆE PONOVNO PRIPREMITI" in message ||
                "NIJE MOGUCE PONOVNO PRIPREMITI" in message
        }
    }

    private data class TargetPart(
        val partId: Long,
        val amount: Double
    )

    private fun applySettlementState(
        settlement: SettlementState,
        roundStateItems: List<CheckRoundStateItem> = emptyList()
    ) {
        Log.d(
            TAG,
            "applySettlementState checkId=$checkId status=${settlement.checkStatus} paymentStatus=${settlement.paymentStatus} remaining=${settlement.remainingTotal} preparedPart=${settlement.preparedPart?.partId} items=${settlement.items.size}"
        )
        val roundStateByItemId = roundStateItems.associateBy { it.id }
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                error = null,
                checkStatus = settlement.checkStatus,
                paymentStatus = settlement.paymentStatus,
                remainingTotal = settlement.remainingTotal,
                preparedPartId = settlement.preparedPart?.partId,
                items = settlement.items.map { item ->
                    item.toUi(
                        previousSelection = state.items.firstOrNull { it.id == item.id }?.selectedQty ?: 0,
                        roundState = roundStateByItemId[item.id]
                    )
                }
            )
        }
    }

    private fun SettlementStateItem.toUi(
        previousSelection: Int,
        roundState: CheckRoundStateItem?
    ): ItemUi {
        val checkItem = checkItemMap[id]
        val resolvedName = roundState?.name ?: name ?: checkItem?.name ?: "Stavka #$id"
        val resolvedImage = imageUrl ?: checkItem?.imageUrl
        val resolvedRound = roundState?.roundNumber ?: roundNumber ?: checkItem?.roundNumber
        val resolvedRemainingQty = roundState?.remainingQuantity ?: remainingQuantity
        return ItemUi(
            id = id,
            name = resolvedName,
            imageUrl = resolvedImage,
            roundNumber = resolvedRound,
            sourceQty = roundState?.sourceQuantity ?: quantity,
            remainingQty = resolvedRemainingQty,
            remainingAmount = remainingAmount,
            strikeMain = roundState?.strikeMain ?: (resolvedRemainingQty == 0),
            paidLine = roundState?.paidLine?.let { paid ->
                PaidLineUi(
                    quantity = paid.quantity,
                    unitPrice = paid.unitPrice,
                    totalAmount = paid.totalAmount,
                    lineType = paid.lineType,
                    uiColor = paid.uiColor
                )
            },
            fallbackUnitPrice = checkItem?.price ?: 0.0,
            selectedQty = previousSelection.coerceIn(0, resolvedRemainingQty)
        )
    }
}
