package pos.finestar.barion.check

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import pos.finestar.barion.domain.usecase.UpdateCheckItemQtyUseCase
import pos.finestar.barion.ui.navigation.NavRoutes

@HiltViewModel
class CheckViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCheckByIdUseCase: GetCheckByIdUseCase,
    private val addItemToCheckUseCase: AddItemToCheckUseCase,
    private val updateCheckItemQtyUseCase: UpdateCheckItemQtyUseCase,
    private val removeItemFromCheckUseCase: RemoveItemFromCheckUseCase,
    private val issueReceiptUseCase: IssueReceiptUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    data class ProductOption(
        val id: Long,
        val label: String
    )

    data class UiState(
        val isLoading: Boolean = true,
        val isMutating: Boolean = false,
        val checkId: Long = 0L,
        val tableName: String = "",
        val status: String = "OPEN",
        val items: List<CheckItem> = emptyList(),
        val subtotal: Double = 0.0,
        val tax: Double = 0.0,
        val total: Double = 0.0,
        val error: String? = null,
        val message: String? = null,
        val showPayPinDialog: Boolean = false,
        val payPin: String = "",
        val payPinError: String? = null,
        val productOptions: List<ProductOption> = defaultProducts()
    )

    private val checkId: Long = savedStateHandle[NavRoutes.ARG_CHECK_ID] ?: 0L
    private val tableName: String = savedStateHandle[NavRoutes.ARG_TABLE_NAME] ?: "Unknown"

    private val _uiState = MutableStateFlow(
        UiState(
            checkId = checkId,
            tableName = java.net.URLDecoder.decode(tableName, Charsets.UTF_8.name())
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadCheck()
    }

    fun onAddItem(productId: Long, qty: Int) {
        mutateCheck {
            addItemToCheckUseCase(checkId = checkId, productId = productId, qty = qty)
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
                runCatching { getCheckByIdUseCase(checkId) }
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

    private fun mutateCheck(action: suspend () -> CheckSession) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            runCatching { action() }
                .onSuccess { updated ->
                    _uiState.update { it.copy(isMutating = false) }
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

    private fun loadCheck() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            runCatching { getCheckByIdUseCase(checkId) }
                .onSuccess { check ->
                    if (check != null) {
                        _uiState.update { it.copy(isLoading = false, error = null) }
                        applyLoadedCheck(check)
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
                subtotal = check.subtotal,
                tax = check.tax,
                total = check.total,
                error = null
            )
        }
    }

    companion object {
        private fun defaultProducts(): List<ProductOption> {
            return listOf(
                ProductOption(1L, "Espresso"),
                ProductOption(2L, "Coca-Cola"),
                ProductOption(3L, "Voda"),
                ProductOption(4L, "Gin Tonic")
            )
        }
    }
}
