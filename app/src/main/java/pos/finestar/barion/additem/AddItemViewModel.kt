package pos.finestar.barion.additem

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pos.finestar.barion.BuildConfig
import pos.finestar.barion.domain.model.CatalogProduct
import pos.finestar.barion.domain.usecase.AddItemToCheckUseCase
import pos.finestar.barion.domain.usecase.GetDrinkCategoriesUseCase
import pos.finestar.barion.domain.usecase.SendToBarUseCase
import pos.finestar.barion.domain.usecase.SearchProductsUseCase
import pos.finestar.barion.ui.navigation.NavRoutes

@HiltViewModel
class AddItemViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDrinkCategoriesUseCase: GetDrinkCategoriesUseCase,
    private val searchProductsUseCase: SearchProductsUseCase,
    private val addItemToCheckUseCase: AddItemToCheckUseCase,
    private val sendToBarUseCase: SendToBarUseCase
) : ViewModel() {

    data class CategoryUi(
        val id: Long?,
        val label: String
    )

    data class ProductUi(
        val id: Long,
        val name: String,
        val code: String?,
        val imageUrl: String?,
        val unitPrice: Double
    )

    data class CartItemUi(
        val productId: Long,
        val name: String,
        val imageUrl: String?,
        val qty: Int,
        val unitPrice: Double
    ) {
        val lineTotal: Double get() = qty * unitPrice
    }

    data class UiState(
        val checkId: Long = 0L,
        val tableName: String = "",
        val isLoading: Boolean = true,
        val isSubmitting: Boolean = false,
        val categories: List<CategoryUi> = emptyList(),
        val selectedCategoryId: Long? = null,
        val query: String = "",
        val products: List<ProductUi> = emptyList(),
        val cart: List<CartItemUi> = emptyList(),
        val showQtyDialog: Boolean = false,
        val qtyDialogProduct: ProductUi? = null,
        val qtyDialogQty: Int = 1,
        val showCartDialog: Boolean = false,
        val error: String? = null,
        val message: String? = null
    ) {
        val cartItemsCount: Int get() = cart.sumOf { it.qty }
        val cartSubtotal: Double get() = cart.sumOf { it.lineTotal }
    }

    sealed interface Event {
        data object NavigateBack : Event
    }

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

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    init {
        loadInitial()
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchProductsDebounced()
    }

    fun onCategorySelected(categoryId: Long?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        searchProductsDebounced()
    }

    fun onProductTapped(product: ProductUi) {
        _uiState.update {
            it.copy(
                showQtyDialog = true,
                qtyDialogProduct = product,
                qtyDialogQty = 1
            )
        }
    }

    fun onQtyChanged(delta: Int) {
        _uiState.update {
            it.copy(qtyDialogQty = (it.qtyDialogQty + delta).coerceAtLeast(1))
        }
    }

    fun onQtyDialogDismiss() {
        _uiState.update {
            it.copy(showQtyDialog = false, qtyDialogProduct = null, qtyDialogQty = 1)
        }
    }

    fun onQtyDialogAdd() {
        val product = _uiState.value.qtyDialogProduct ?: return
        val qty = _uiState.value.qtyDialogQty

        _uiState.update { state ->
            val existing = state.cart.firstOrNull { it.productId == product.id }
            val updatedCart = if (existing == null) {
                state.cart + CartItemUi(
                    productId = product.id,
                    name = product.name,
                    imageUrl = product.imageUrl,
                    qty = qty,
                    unitPrice = product.unitPrice
                )
            } else {
                state.cart.map {
                    if (it.productId == product.id) it.copy(qty = it.qty + qty) else it
                }
            }

            state.copy(
                cart = updatedCart,
                showQtyDialog = false,
                qtyDialogProduct = null,
                qtyDialogQty = 1,
                message = "Dodano u košaricu: ${product.name} x$qty"
            )
        }
    }

    fun onCartOpen() {
        _uiState.update { it.copy(showCartDialog = true) }
    }

    fun onCartDismiss() {
        _uiState.update { it.copy(showCartDialog = false) }
    }

    fun onCartIncrease(item: CartItemUi) {
        _uiState.update { state ->
            state.copy(
                cart = state.cart.map {
                    if (it.productId == item.productId) it.copy(qty = it.qty + 1) else it
                }
            )
        }
    }

    fun onCartDecrease(item: CartItemUi) {
        _uiState.update { state ->
            state.copy(
                cart = state.cart.mapNotNull {
                    if (it.productId != item.productId) return@mapNotNull it
                    val nextQty = it.qty - 1
                    if (nextQty <= 0) null else it.copy(qty = nextQty)
                }
            )
        }
    }

    fun onCartRemove(item: CartItemUi) {
        _uiState.update { state ->
            state.copy(cart = state.cart.filterNot { it.productId == item.productId })
        }
    }

    fun onSendRound() {
        val cart = _uiState.value.cart
        if (cart.isEmpty()) {
            _uiState.update { it.copy(message = "Košarica je prazna.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, message = null) }
            runCatching {
                cart.forEach { item ->
                    addItemToCheckUseCase(
                        checkId = checkId,
                        productId = item.productId,
                        qty = item.qty,
                        unitPrice = item.unitPrice,
                        productName = item.name
                    )
                }
                sendToBarUseCase(checkId = checkId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        showCartDialog = false,
                        cart = emptyList(),
                        message = "Runda je poslana na šank."
                    )
                }
                _events.emit(Event.NavigateBack)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        error = throwable.message ?: "Slanje runde nije uspjelo.",
                        message = throwable.message ?: "Slanje runde nije uspjelo."
                    )
                }
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val categories = getDrinkCategoriesUseCase(level = 2, forceRefresh = false)
                    .sortedBy { it.labelForSort() }
                    .map { CategoryUi(id = it.id, label = it.name) }
                val categoriesWithAll = listOf(CategoryUi(id = null, label = "Sve")) + categories
                val initialCategoryId: Long? = null
                val products = searchProductsUseCase(
                    query = null,
                    drinkCategoryId = initialCategoryId,
                    forceRefresh = false
                ).map { it.toUi() }

                Triple(categoriesWithAll, initialCategoryId, products)
            }.onSuccess { (categories, selectedCategoryId, products) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        products = products,
                        error = null
                    )
                }
                refreshCatalogInBackground(
                    query = _uiState.value.query,
                    selectedCategoryId = selectedCategoryId
                )
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = throwable.message ?: "Ne mogu učitati kategorije i artikle."
                    )
                }
            }
        }
    }

    private fun searchProductsDebounced() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            val state = _uiState.value
            val querySnapshot = state.query
            val categorySnapshot = state.selectedCategoryId
            runCatching {
                searchProductsUseCase(
                    query = querySnapshot,
                    drinkCategoryId = categorySnapshot,
                    forceRefresh = false
                ).map { it.toUi() }
            }.onSuccess { products ->
                val latest = _uiState.value
                if (latest.query == querySnapshot && latest.selectedCategoryId == categorySnapshot) {
                    _uiState.update { it.copy(products = products, error = null) }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(error = throwable.message ?: "Ne mogu učitati artikle.")
                }
            }

            runCatching {
                searchProductsUseCase(
                    query = querySnapshot,
                    drinkCategoryId = categorySnapshot,
                    forceRefresh = true
                ).map { it.toUi() }
            }.onSuccess { freshProducts ->
                val latest = _uiState.value
                if (latest.query == querySnapshot && latest.selectedCategoryId == categorySnapshot) {
                    _uiState.update { it.copy(products = freshProducts, error = null) }
                }
            }
        }
    }

    private fun refreshCatalogInBackground(query: String, selectedCategoryId: Long?) {
        viewModelScope.launch {
            runCatching {
                val freshCategories = getDrinkCategoriesUseCase(level = 2, forceRefresh = true)
                    .sortedBy { it.labelForSort() }
                    .map { CategoryUi(id = it.id, label = it.name) }
                val categoriesWithAll = listOf(CategoryUi(id = null, label = "Sve")) + freshCategories
                val freshProducts = searchProductsUseCase(
                    query = query.takeIf { it.isNotBlank() },
                    drinkCategoryId = selectedCategoryId,
                    forceRefresh = true
                ).map { it.toUi() }
                categoriesWithAll to freshProducts
            }.onSuccess { (categories, products) ->
                val latest = _uiState.value
                if (latest.query == query && latest.selectedCategoryId == selectedCategoryId) {
                    _uiState.update {
                        it.copy(
                            categories = categories,
                            products = products,
                            error = null
                        )
                    }
                }
            }
        }
    }

    private fun CatalogProduct.toUi(): ProductUi {
        return ProductUi(
            id = id,
            name = name,
            code = code,
            imageUrl = normalizeImageUrl(image46x75 ?: image125x200 ?: image),
            unitPrice = price
        )
    }

    private fun normalizeImageUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw

        val base = BuildConfig.BARION_API_BASE_URL.trimEnd('/')
        val path = raw.trimStart('/')
        return "$base/$path"
    }

    private fun pos.finestar.barion.domain.model.DrinkCategory.labelForSort(): String {
        return "${sortOrder.toString().padStart(6, '0')}_${name.lowercase()}"
    }
}
