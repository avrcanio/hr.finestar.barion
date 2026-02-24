package pos.finestar.barion.check

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.repo.AuthRepository
import pos.finestar.barion.domain.usecase.AddItemToCheckUseCase
import pos.finestar.barion.domain.usecase.GetCheckByIdUseCase
import pos.finestar.barion.domain.usecase.IssueReceiptUseCase
import pos.finestar.barion.domain.usecase.RemoveItemFromCheckUseCase
import pos.finestar.barion.domain.usecase.SendToBarUseCase
import pos.finestar.barion.domain.usecase.StornoCheckItemUseCase
import pos.finestar.barion.domain.usecase.UpdateCheckItemQtyUseCase
import pos.finestar.barion.domain.usecase.GratisCheckItemUseCase
import pos.finestar.barion.domain.usecase.OtpisCheckItemUseCase
import pos.finestar.barion.domain.usecase.CloseCheckUseCase
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
    private val issueReceiptUseCase: IssueReceiptUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

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
        val showPayPinDialog: Boolean = false,
        val payPin: String = "",
        val payPinError: String? = null
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
        _uiState.update {
            it.copy(
                showPayPinDialog = true,
                payPin = "",
                payPinError = null,
                message = null
            )
        }
    }

    fun onFree() {
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

    fun onPayPinChanged(pin: String) {
        val digitsOnly = pin.filter { ch -> ch.isDigit() }.take(6)
        _uiState.update { it.copy(payPin = digitsOnly, payPinError = null) }
    }

    fun onPayPinDismiss() {
        _uiState.update {
            it.copy(
                showPayPinDialog = false,
                payPin = "",
                payPinError = null
            )
        }
    }

    fun onConfirmPay() {
        val pin = _uiState.value.payPin
        if (pin.isBlank()) {
            _uiState.update { it.copy(payPinError = "PIN je obavezan.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, payPinError = null, message = null) }
            runCatching {
                authRepository.verifyPin(pin)
                issueReceiptUseCase(checkId = checkId)
            }.onSuccess {
                runCatching { getCheckByIdUseCase(checkId, forceRefresh = true) }
                    .onSuccess { updated -> if (updated != null) applyLoadedCheck(updated) }
                _uiState.update {
                    it.copy(
                        isMutating = false,
                        showPayPinDialog = false,
                        payPin = "",
                        payPinError = null,
                        message = "Naplata uspjesna."
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isMutating = false,
                        payPinError = throwable.message ?: "PIN potvrda nije uspjela.",
                        message = null
                    )
                }
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun refresh() {
        loadCheck(showLoading = false)
    }

    private fun mutateCheck(
        onSuccessMessage: String? = null,
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
                    applyLoadedCheck(updated)
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
                        viewModelScope.launch {
                            runCatching { getCheckByIdUseCase(checkId, forceRefresh = true) }
                                .onSuccess { fresh ->
                                    if (fresh != null) {
                                        _uiState.update { it.copy(isLoading = false, error = null) }
                                        applyLoadedCheck(fresh)
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
            it.copy(
                status = check.status.name,
                items = check.items,
                hasUnsentItems = check.items.any { item -> !item.sentToBar },
                subtotal = check.subtotal,
                tax = check.tax,
                total = check.total,
                error = null
            )
        }
    }

    private fun calculateAvailableActionQty(item: CheckItem): Int {
        val itemId = item.itemId ?: return 0
        val sourceQty = kotlin.math.abs(item.qty)
        val consumedByActions = _uiState.value.items
            .asSequence()
            .filter {
                it.lineType.equals("STORNO", ignoreCase = true) ||
                    it.lineType.equals("OTPIS", ignoreCase = true)
            }
            .filter { actionTargetsItem(it.note, itemId) }
            .sumOf { kotlin.math.abs(it.qty) }
        return (sourceQty - consumedByActions).coerceAtLeast(0)
    }

    private fun actionTargetsItem(note: String?, itemId: Long): Boolean {
        if (note.isNullOrBlank()) return false
        val pattern = Regex("""\[(?:storno|otpis)_of:(\d+)]""", RegexOption.IGNORE_CASE)
        val targetId = pattern.find(note)?.groupValues?.getOrNull(1)?.toLongOrNull()
        return targetId == itemId
    }
}
