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
import pos.finestar.barion.domain.usecase.AddItemToCheckUseCase
import pos.finestar.barion.domain.usecase.GetCheckByIdUseCase
import pos.finestar.barion.domain.usecase.RemoveItemFromCheckUseCase
import pos.finestar.barion.domain.usecase.UpdateCheckItemQtyUseCase
import pos.finestar.barion.ui.navigation.NavRoutes

@HiltViewModel
class CheckViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCheckByIdUseCase: GetCheckByIdUseCase,
    private val addItemToCheckUseCase: AddItemToCheckUseCase,
    private val updateCheckItemQtyUseCase: UpdateCheckItemQtyUseCase,
    private val removeItemFromCheckUseCase: RemoveItemFromCheckUseCase
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
        _uiState.update { it.copy(message = "Naplata: placeholder") }
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
                            message = throwable.message ?: "Greška pri izmjeni stavki."
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
                                error = "Check nije pronađen."
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Greška pri učitavanju checka."
                        )
                    }
                }
        }
    }

    private fun applyLoadedCheck(check: CheckSession) {
        val displayItems = if (check.items.isEmpty()) {
            listOf(
                CheckItem(name = "Dummy stavka", qty = 1, price = 5.0),
                CheckItem(name = "Dummy stavka 2", qty = 2, price = 3.0)
            )
        } else {
            check.items
        }

        _uiState.update {
            it.copy(
                status = check.status.name,
                items = displayItems,
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
