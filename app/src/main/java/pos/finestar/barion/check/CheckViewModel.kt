package pos.finestar.barion.check

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
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
import pos.finestar.barion.domain.usecase.GetDrinkCategoriesUseCase
import pos.finestar.barion.domain.usecase.GetDrinkCategoryDisplayUseCase
import pos.finestar.barion.domain.usecase.IssueReceiptUseCase
import pos.finestar.barion.domain.usecase.RemoveItemFromCheckUseCase
import pos.finestar.barion.domain.usecase.SearchProductsUseCase
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
    private val authRepository: AuthRepository,
    private val getDrinkCategoriesUseCase: GetDrinkCategoriesUseCase,
    private val getDrinkCategoryDisplayUseCase: GetDrinkCategoryDisplayUseCase,
    private val searchProductsUseCase: SearchProductsUseCase
) : ViewModel() {

    data class ProductOption(
        val id: Long,
        val label: String
    )

    data class CategoryOption(
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
        val showAddDialog: Boolean = false,
        val isCatalogLoading: Boolean = false,
        val catalogError: String? = null,
        val addItemQuery: String = "",
        val categoryOptions: List<CategoryOption> = emptyList(),
        val selectedCategoryId: Long? = null,
        val productOptions: List<ProductOption> = emptyList()
    )

    private val checkId: Long = savedStateHandle[NavRoutes.ARG_CHECK_ID] ?: 0L
    private val tableName: String = savedStateHandle[NavRoutes.ARG_TABLE_NAME] ?: "Unknown"
    private var searchJob: Job? = null

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

    fun onOpenAddDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                addItemQuery = "",
                catalogError = null
            )
        }
        loadCatalogAndProducts()
    }

    fun onDismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun onAddItemQueryChanged(query: String) {
        _uiState.update { it.copy(addItemQuery = query) }
        searchProducts()
    }

    fun onCategorySelected(categoryId: Long?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        searchProducts()
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

    private fun loadCatalogAndProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCatalogLoading = true, catalogError = null) }
            runCatching {
                val categories = getDrinkCategoriesUseCase()
                val rootId = categories.firstOrNull { it.parentId == null }?.id
                val displayCategories = rootId?.let {
                    runCatching { getDrinkCategoryDisplayUseCase(it) }
                        .getOrNull()
                        ?.categories
                }.orEmpty()

                val preferredCategories = when {
                    displayCategories.isNotEmpty() -> displayCategories
                    rootId != null -> categories.filter { it.parentId == rootId }
                    else -> categories
                }

                val selectedCategoryId = preferredCategories.firstOrNull()?.id
                val products = searchProductsUseCase(query = null, drinkCategoryId = selectedCategoryId)

                Triple(
                    preferredCategories.map { CategoryOption(id = it.id, label = it.name) },
                    selectedCategoryId,
                    products.map { ProductOption(id = it.id, label = "${it.name} (${"%.2f".format(it.price)} EUR)") }
                )
            }.onSuccess { (categoryOptions, selectedCategoryId, productOptions) ->
                _uiState.update {
                    it.copy(
                        isCatalogLoading = false,
                        categoryOptions = categoryOptions,
                        selectedCategoryId = selectedCategoryId,
                        productOptions = productOptions,
                        catalogError = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isCatalogLoading = false,
                        categoryOptions = emptyList(),
                        selectedCategoryId = null,
                        productOptions = emptyList(),
                        catalogError = throwable.message ?: "Ne mogu ucitati kategorije/artikle."
                    )
                }
            }
        }
    }

    private fun searchProducts() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isCatalogLoading = true, catalogError = null) }
            runCatching {
                searchProductsUseCase(
                    query = state.addItemQuery,
                    drinkCategoryId = state.selectedCategoryId
                )
            }.onSuccess { products ->
                _uiState.update {
                    it.copy(
                        isCatalogLoading = false,
                        productOptions = products.map { p ->
                            ProductOption(
                                id = p.id,
                                label = "${p.name} (${"%.2f".format(p.price)} EUR)"
                            )
                        }
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isCatalogLoading = false,
                        productOptions = emptyList(),
                        catalogError = throwable.message ?: "Ne mogu ucitati artikle."
                    )
                }
            }
        }
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
}
