package hr.finestar.barion.additem

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import hr.finestar.barion.BuildConfig
import hr.finestar.barion.domain.model.CatalogProduct
import hr.finestar.barion.domain.model.ModifierType
import hr.finestar.barion.domain.model.ProductModifierGroup
import hr.finestar.barion.domain.model.ProductModifierOption
import hr.finestar.barion.domain.model.ProductModifiersConfig
import hr.finestar.barion.domain.model.SelectedModifier
import hr.finestar.barion.domain.model.SelectionMode
import hr.finestar.barion.domain.usecase.AddItemToCheckUseCase
import hr.finestar.barion.domain.usecase.GetCatalogBootstrapUseCase
import hr.finestar.barion.domain.usecase.GetProductModifiersUseCase
import hr.finestar.barion.domain.usecase.PreviewBundlePriceUseCase
import hr.finestar.barion.domain.usecase.SearchProductsUseCase
import hr.finestar.barion.domain.usecase.SendToBarUseCase
import hr.finestar.barion.domain.usecase.SyncCatalogUseCase
import hr.finestar.barion.sync.CatalogPresentationEventBus
import hr.finestar.barion.ui.navigation.NavRoutes

@HiltViewModel
class AddItemViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCatalogBootstrapUseCase: GetCatalogBootstrapUseCase,
    private val searchProductsUseCase: SearchProductsUseCase,
    private val syncCatalogUseCase: SyncCatalogUseCase,
    private val getProductModifiersUseCase: GetProductModifiersUseCase,
    private val previewBundlePriceUseCase: PreviewBundlePriceUseCase,
    private val addItemToCheckUseCase: AddItemToCheckUseCase,
    private val sendToBarUseCase: SendToBarUseCase,
    private val catalogPresentationEventBus: CatalogPresentationEventBus
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
        val thumbnailUrl: String?,
        val detailImageUrl: String?,
        val imageVersion: Long?,
        val modifierVersion: Long?,
        val unitPrice: Double
    )

    data class ModifierOptionUi(
        val id: Long,
        val name: String,
        val type: ModifierType,
        val code: String? = null,
        val artiklName: String? = null,
        val priceDelta: Double = 0.0,
        val selected: Boolean = false,
        val quantity: Int = 0
    )

    data class ModifierGroupUi(
        val id: Long,
        val name: String,
        val type: ModifierType,
        val selectionMode: SelectionMode,
        val minSelect: Int,
        val maxSelect: Int?,
        val options: List<ModifierOptionUi>
    )

    data class CartItemUi(
        val lineId: Long,
        val productId: Long,
        val name: String,
        val imageUrl: String?,
        val qty: Int,
        val unitPrice: Double,
        val modifiers: List<SelectedModifier> = emptyList(),
        val note: String? = null,
        val displayLines: List<String> = emptyList()
    ) {
        val lineTotal: Double get() = qty * unitPrice
        val isConfigured: Boolean get() = modifiers.isNotEmpty() || !note.isNullOrBlank()
    }

    data class UiState(
        val checkId: Long = 0L,
        val tableName: String = "",
        val isLoading: Boolean = true,
        val isProductsLoading: Boolean = true,
        val isSubmitting: Boolean = false,
        val categories: List<CategoryUi> = emptyList(),
        val selectedCategoryId: Long? = null,
        val query: String = "",
        val products: List<ProductUi> = emptyList(),
        val hasModifiersByProductId: Map<Long, Boolean> = emptyMap(),
        val cart: List<CartItemUi> = emptyList(),
        val showModifierDialog: Boolean = false,
        val modifierDialogLoading: Boolean = false,
        val modifierDialogProduct: ProductUi? = null,
        val modifierDialogGroups: List<ModifierGroupUi> = emptyList(),
        val modifierDialogNote: String = "",
        val modifierDialogPricePreview: Double? = null,
        val modifierDialogDelta: Double? = null,
        val showCartDialog: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val navigateToCheckRequestId: Long = 0L
    ) {
        val isSearchActive: Boolean get() = query.trim().isNotBlank()
        val cartItemsCount: Int get() = cart.sumOf { it.qty }
        val cartSubtotal: Double get() = cart.sumOf { it.lineTotal }
        val cartQtyByProductId: Map<Long, Int>
            get() = cart.groupBy { it.productId }.mapValues { (_, lines) -> lines.sumOf { it.qty } }
        val cartConfiguredByProductId: Map<Long, Boolean>
            get() = cart.groupBy { it.productId }.mapValues { (_, lines) -> lines.any { it.isConfigured } }
    }

    private val checkId: Long = savedStateHandle[NavRoutes.ARG_CHECK_ID] ?: 0L
    private val tableName: String = savedStateHandle[NavRoutes.ARG_TABLE_NAME] ?: "Unknown"
    private var searchJob: Job? = null
    private var periodicSyncJob: Job? = null
    private val periodicSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastCatalogSyncTriggerAtMs: Long = 0L
    private val modifierHintsInFlight = Collections.synchronizedSet(mutableSetOf<Long>())
    private val cartLineIdGenerator = AtomicLong(1L)
    private val navigateToCheckRequestIdGenerator = AtomicLong(0L)
    private val sendRoundInFlight = AtomicBoolean(false)

    private val _uiState = MutableStateFlow(
        UiState(
            checkId = checkId,
            tableName = java.net.URLDecoder.decode(tableName, Charsets.UTF_8.name())
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observePresentationEvents()
        startPeriodicSync()
        loadInitial()
    }

    fun onForeground() {
        triggerCatalogSync(source = "additem_foreground")
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { state ->
            val wasSearchActive = state.isSearchActive
            val isSearchActive = newQuery.trim().isNotBlank()
            val switchingToCategoryMode = wasSearchActive && !isSearchActive
            state.copy(
                query = newQuery,
                isProductsLoading = if (switchingToCategoryMode) true else state.isProductsLoading,
                products = if (switchingToCategoryMode) emptyList() else state.products,
                error = if (switchingToCategoryMode) null else state.error
            )
        }
        searchProductsDebounced()
    }

    fun onCategorySelected(categoryId: Long?) {
        _uiState.update { state ->
            val categoryMode = !state.isSearchActive
            state.copy(
                selectedCategoryId = categoryId,
                isProductsLoading = if (categoryMode) true else state.isProductsLoading,
                products = if (categoryMode) emptyList() else state.products,
                error = if (categoryMode) null else state.error
            )
        }
        searchProductsDebounced()
    }

    fun onCategorySwipeLeft() {
        shiftCategoryBy(delta = 1)
    }

    fun onCategorySwipeRight() {
        shiftCategoryBy(delta = -1)
    }

    fun onProductTapped(product: ProductUi) {
        addProductToCart(product = product, qty = 1)
    }

    fun onProductLongPressed(product: ProductUi) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showModifierDialog = true,
                    modifierDialogLoading = true,
                    modifierDialogProduct = product,
                    modifierDialogGroups = emptyList(),
                    modifierDialogNote = "",
                    modifierDialogPricePreview = null,
                    modifierDialogDelta = null,
                    error = null
                )
            }

            runCatching {
                getProductModifiersUseCase(
                    productId = product.id,
                    expectedModifierVersion = product.modifierVersion,
                    forceRefresh = false
                )
            }.onSuccess { config ->
                _uiState.update {
                    it.copy(
                        modifierDialogLoading = false,
                        modifierDialogGroups = config.groups.toDialogGroups(),
                        hasModifiersByProductId = it.hasModifiersByProductId + (product.id to config.groups.isNotEmpty())
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        modifierDialogLoading = false,
                        modifierDialogGroups = emptyList(),
                        message = throwable.message ?: "Ne mogu učitati opcije za artikl."
                    )
                }
            }
        }
    }

    fun onModifierDialogDismiss() {
        _uiState.update {
            it.copy(
                showModifierDialog = false,
                modifierDialogLoading = false,
                modifierDialogProduct = null,
                modifierDialogGroups = emptyList(),
                modifierDialogNote = "",
                modifierDialogPricePreview = null,
                modifierDialogDelta = null
            )
        }
    }

    fun onModifierNoteChanged(note: String) {
        _uiState.update { it.copy(modifierDialogNote = note) }
    }

    fun onModifierSimpleToggle(groupId: Long, optionId: Long) {
        _uiState.update { state ->
            val updatedGroups = state.modifierDialogGroups.map { group ->
                if (group.id != groupId || group.type != ModifierType.SIMPLE) return@map group
                val toggled = group.options.map { option ->
                    if (option.id == optionId) {
                        option.copy(selected = !option.selected)
                    } else if (group.selectionMode == SelectionMode.SINGLE) {
                        option.copy(selected = false)
                    } else {
                        option
                    }
                }
                group.copy(options = toggled)
            }
            state.copy(modifierDialogGroups = updatedGroups)
        }
    }

    fun onModifierBundleQtyChange(groupId: Long, optionId: Long, delta: Int) {
        _uiState.update { state ->
            val updatedGroups = state.modifierDialogGroups.map { group ->
                if (group.id != groupId || group.type != ModifierType.BUNDLE) return@map group

                val currentTotal = group.options.sumOf { it.quantity }
                val changedOptions = group.options.map optionMap@{ option ->
                    if (option.id != optionId) return@optionMap option
                    val candidate = (option.quantity + delta).coerceAtLeast(0)
                    if (delta > 0 && group.maxSelect != null && currentTotal >= group.maxSelect) {
                        option
                    } else {
                        option.copy(quantity = candidate)
                    }
                }
                group.copy(options = changedOptions)
            }
            state.copy(modifierDialogGroups = updatedGroups)
        }

        viewModelScope.launch {
            refreshBundlePreview()
        }
    }

    fun onModifierDialogConfirm() {
        val state = _uiState.value
        val product = state.modifierDialogProduct ?: return
        val selectedModifiers = collectSelectedModifiers(state.modifierDialogGroups)
        val note = state.modifierDialogNote.trim().takeIf { it.isNotBlank() }

        if (!validateModifierSelections(state.modifierDialogGroups)) return

        val hasBundle = selectedModifiers.any { it.type == ModifierType.BUNDLE }
        val unitPrice = if (hasBundle) {
            state.modifierDialogPricePreview ?: product.unitPrice
        } else {
            product.unitPrice
        }

        val lines = buildDisplayLines(
            groups = state.modifierDialogGroups,
            note = note
        )

        addProductToCart(
            product = product,
            qty = 1,
            unitPriceOverride = unitPrice,
            modifiers = selectedModifiers,
            note = note,
            displayLines = lines
        )

        _uiState.update {
            it.copy(
                showModifierDialog = false,
                modifierDialogLoading = false,
                modifierDialogProduct = null,
                modifierDialogGroups = emptyList(),
                modifierDialogNote = "",
                modifierDialogPricePreview = null,
                modifierDialogDelta = null,
                message = "Dodano u košaricu: ${product.name}"
            )
        }
    }

    private fun validateModifierSelections(groups: List<ModifierGroupUi>): Boolean {
        val invalid = groups.firstOrNull { group ->
            val selectedCount = when (group.type) {
                ModifierType.SIMPLE -> group.options.count { it.selected }
                ModifierType.BUNDLE -> group.options.sumOf { it.quantity }
            }
            val minFail = selectedCount < group.minSelect
            val maxFail = group.maxSelect?.let { selectedCount > it } == true
            minFail || maxFail
        }

        if (invalid != null) {
            val maxLabel = invalid.maxSelect?.toString() ?: "∞"
            _uiState.update {
                it.copy(message = "Grupa '${invalid.name}' traži odabir između ${invalid.minSelect} i $maxLabel opcija.")
            }
            return false
        }
        return true
    }

    private suspend fun refreshBundlePreview() {
        val state = _uiState.value
        val product = state.modifierDialogProduct ?: return
        val bundleModifiers = collectSelectedModifiers(state.modifierDialogGroups)
            .filter { it.type == ModifierType.BUNDLE }

        if (bundleModifiers.isEmpty()) {
            _uiState.update { it.copy(modifierDialogPricePreview = null, modifierDialogDelta = null) }
            return
        }

        runCatching {
            previewBundlePriceUseCase(productId = product.id, modifiers = bundleModifiers)
        }.onSuccess { preview ->
            _uiState.update {
                it.copy(
                    modifierDialogPricePreview = preview.finalUnitPrice,
                    modifierDialogDelta = preview.mixersDelta
                )
            }
        }.onFailure {
            _uiState.update { it.copy(modifierDialogPricePreview = null, modifierDialogDelta = null) }
        }
    }

    private fun collectSelectedModifiers(groups: List<ModifierGroupUi>): List<SelectedModifier> {
        return groups.flatMap { group ->
            when (group.type) {
                ModifierType.SIMPLE -> group.options.filter { it.selected }.map { option ->
                    SelectedModifier(type = ModifierType.SIMPLE, id = option.id)
                }

                ModifierType.BUNDLE -> group.options.filter { it.quantity > 0 }.map { option ->
                    SelectedModifier(
                        type = ModifierType.BUNDLE,
                        id = option.id,
                        quantity = option.quantity
                    )
                }
            }
        }
    }

    private fun buildDisplayLines(groups: List<ModifierGroupUi>, note: String?): List<String> {
        val lines = mutableListOf<String>()
        groups.forEach { group ->
            when (group.type) {
                ModifierType.SIMPLE -> group.options
                    .filter { it.selected }
                    .forEach { lines.add("• ${it.name}") }

                ModifierType.BUNDLE -> group.options
                    .filter { it.quantity > 0 }
                    .forEach { lines.add("• ${it.name} x${it.quantity}") }
            }
        }
        note?.takeIf { it.isNotBlank() }?.let { lines.add("• Napomena: $it") }
        return lines
    }

    private fun List<ProductModifierGroup>.toDialogGroups(): List<ModifierGroupUi> {
        return map { group ->
            ModifierGroupUi(
                id = group.id,
                name = group.name,
                type = group.type,
                selectionMode = group.selectionMode,
                minSelect = group.minSelect,
                maxSelect = group.maxSelect,
                options = group.options.toDialogOptions()
            )
        }
    }

    private fun List<ProductModifierOption>.toDialogOptions(): List<ModifierOptionUi> {
        return map { option ->
            ModifierOptionUi(
                id = option.id,
                name = option.name,
                type = option.type,
                code = option.code,
                artiklName = option.artiklName,
                priceDelta = option.priceDelta
            )
        }
    }

    private fun addProductToCart(
        product: ProductUi,
        qty: Int,
        unitPriceOverride: Double? = null,
        modifiers: List<SelectedModifier> = emptyList(),
        note: String? = null,
        displayLines: List<String> = emptyList()
    ) {
        if (qty <= 0) return

        val configured = modifiers.isNotEmpty() || !note.isNullOrBlank()
        val unitPrice = unitPriceOverride ?: product.unitPrice

        _uiState.update { state ->
            val updatedCart = if (!configured) {
                val existing = state.cart.firstOrNull { it.productId == product.id && !it.isConfigured }
                if (existing == null) {
                    state.cart + CartItemUi(
                        lineId = cartLineIdGenerator.getAndIncrement(),
                        productId = product.id,
                        name = product.name,
                        imageUrl = product.imageUrl,
                        qty = qty,
                        unitPrice = unitPrice
                    )
                } else {
                    state.cart.map {
                        if (it.lineId == existing.lineId) it.copy(qty = it.qty + qty) else it
                    }
                }
            } else {
                state.cart + CartItemUi(
                    lineId = cartLineIdGenerator.getAndIncrement(),
                    productId = product.id,
                    name = product.name,
                    imageUrl = product.imageUrl,
                    qty = 1,
                    unitPrice = unitPrice,
                    modifiers = modifiers,
                    note = note,
                    displayLines = displayLines
                )
            }

            state.copy(
                cart = updatedCart,
                message = if (configured) {
                    "Dodano konfigurirano: ${product.name}"
                } else {
                    "Dodano u košaricu: ${product.name} x$qty"
                }
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
        if (item.isConfigured) {
            _uiState.update { it.copy(message = "Stavka s opcijama/napomenom ostaje na količini 1.") }
            return
        }
        _uiState.update { state ->
            state.copy(
                cart = state.cart.map {
                    if (it.lineId == item.lineId) it.copy(qty = it.qty + 1) else it
                }
            )
        }
    }

    fun onCartDecrease(item: CartItemUi) {
        _uiState.update { state ->
            state.copy(
                cart = state.cart.mapNotNull {
                    if (it.lineId != item.lineId) return@mapNotNull it
                    val nextQty = it.qty - 1
                    if (nextQty <= 0) null else it.copy(qty = nextQty)
                }
            )
        }
    }

    fun onCartRemove(item: CartItemUi) {
        _uiState.update { state ->
            state.copy(cart = state.cart.filterNot { it.lineId == item.lineId })
        }
    }

    fun onSendRound() {
        if (!sendRoundInFlight.compareAndSet(false, true)) return
        val stateSnapshot = _uiState.value
        val cart = stateSnapshot.cart
        if (cart.isEmpty()) {
            sendRoundInFlight.set(false)
            _uiState.update { it.copy(message = "Košarica je prazna.") }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null, message = null) }
        viewModelScope.launch {
            val addItemsResult = runCatching {
                cart.forEach { item ->
                    val qty = if (item.isConfigured) 1 else item.qty
                    addItemToCheckUseCase(
                        checkId = checkId,
                        productId = item.productId,
                        qty = qty,
                        unitPrice = item.unitPrice,
                        productName = item.name,
                        modifiers = item.modifiers,
                        note = item.note
                    )
                }
            }

            if (addItemsResult.isFailure) {
                val throwable = addItemsResult.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        error = throwable?.message ?: "Dodavanje stavki nije uspjelo.",
                        message = throwable?.message ?: "Dodavanje stavki nije uspjelo."
                    )
                }
                sendRoundInFlight.set(false)
                return@launch
            }

            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    showCartDialog = false,
                    cart = emptyList()
                )
            }

            val sendToBarResult = runCatching { sendToBarUseCase(checkId = checkId) }
            _uiState.update { state ->
                state.copy(
                    message = if (sendToBarResult.isSuccess) {
                        "Runda je poslana na šank."
                    } else {
                        "Stavke su dodane, ali slanje na šank trenutno nije uspjelo."
                    },
                    error = sendToBarResult.exceptionOrNull()?.message,
                    navigateToCheckRequestId = navigateToCheckRequestIdGenerator.incrementAndGet()
                )
            }

            sendRoundInFlight.set(false)
        }
    }

    fun onMessageShown() {
        _uiState.update {
            it.copy(
                message = null,
                error = null
            )
        }
    }

    override fun onCleared() {
        periodicSyncJob?.cancel()
        periodicSyncScope.coroutineContext[Job]?.cancel()
        super.onCleared()
    }

    private fun loadInitial() {
        triggerCatalogSync(source = "additem_init")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isProductsLoading = true, error = null) }
            runCatching {
                val bootstrap = getCatalogBootstrapUseCase(
                    includeProducts = false,
                    forceRefresh = false
                )
                val categories = bootstrap.categories
                    .map { CategoryUi(id = it.id, label = it.name) }
                val resolvedSelectedCategoryId = bootstrap.selectedCategoryId
                    ?: categories.firstOrNull()?.id
                val products = searchProductsUseCase(
                    query = null,
                    categoryId = resolvedSelectedCategoryId,
                    forceRefresh = false
                ).map { it.toUi() }
                Triple(
                    categories,
                    resolvedSelectedCategoryId,
                    products
                )
            }.onSuccess { (categories, selectedCategoryId, products) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isProductsLoading = false,
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        products = products,
                        error = null
                    )
                }
                scheduleModifierHints(products)
                refreshCatalogInBackground(
                    query = _uiState.value.query,
                    selectedCategoryId = selectedCategoryId
                )
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isProductsLoading = false,
                        error = throwable.message ?: "Ne mogu učitati kategorije i artikle."
                    )
                }
            }
        }
    }

    private fun startPeriodicSync() {
        if (periodicSyncJob != null) return
        periodicSyncJob = periodicSyncScope.launch {
            while (true) {
                delay(PERIODIC_SYNC_MILLIS)
                triggerCatalogSync(source = "additem_periodic_5m")
            }
        }
    }

    private fun observePresentationEvents() {
        viewModelScope.launch {
            catalogPresentationEventBus.events.collect { event ->
                when (event) {
                    is CatalogPresentationEventBus.Event.RuntimeModeChanged -> {
                        val snapshot = _uiState.value
                        logDebug(
                            "runtimeModeChanged activeMode=${event.activeModeRaw} query='${snapshot.query}' selectedCategory=${snapshot.selectedCategoryId} -> refresh"
                        )
                        _uiState.update {
                            it.copy(
                                isProductsLoading = true,
                                products = emptyList(),
                                error = null
                            )
                        }
                        refreshCatalogInBackground(
                            query = snapshot.query,
                            selectedCategoryId = snapshot.selectedCategoryId
                        )
                    }
                }
            }
        }
    }

    private fun triggerCatalogSync(source: String) {
        val now = System.currentTimeMillis()
        if (now - lastCatalogSyncTriggerAtMs < CATALOG_SYNC_DEBOUNCE_MS) {
            logDebug("triggerCatalogSync skipped source=$source reason=debounced deltaMs=${now - lastCatalogSyncTriggerAtMs}")
            return
        }
        lastCatalogSyncTriggerAtMs = now
        viewModelScope.launch {
            logDebug("triggerCatalogSync source=$source")
            runCatching { syncCatalogUseCase(forceBootstrap = false) }
                .onFailure { logWarn("triggerCatalogSync failed source=$source msg=${it.message}", it) }
        }
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logWarn(message: String, throwable: Throwable? = null) {
        runCatching { Log.w(TAG, message, throwable) }
    }

    private fun searchProductsDebounced() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            val state = _uiState.value
            val querySnapshot = state.query
            val categorySnapshot = state.selectedCategoryId
            val searchActiveSnapshot = querySnapshot.trim().isNotBlank()
            _uiState.update { latest ->
                if (latest.query == querySnapshot && latest.selectedCategoryId == categorySnapshot) {
                    latest.copy(
                        isProductsLoading = true,
                        products = if (searchActiveSnapshot) latest.products else emptyList(),
                        error = null
                    )
                } else {
                    latest
                }
            }
            runCatching {
                loadProductsForCurrentMode(
                    query = querySnapshot,
                    selectedCategoryId = categorySnapshot,
                    forceRefresh = false
                )
            }.onSuccess { products ->
                val latest = _uiState.value
                if (
                    latest.query == querySnapshot &&
                    latest.selectedCategoryId == categorySnapshot &&
                    latest.isSearchActive == searchActiveSnapshot
                ) {
                    _uiState.update {
                        it.copy(
                            products = products,
                            error = null,
                            isProductsLoading = false
                        )
                    }
                    scheduleModifierHints(products)
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        error = throwable.message ?: "Ne mogu učitati artikle.",
                        isProductsLoading = false
                    )
                }
            }

            runCatching {
                loadProductsForCurrentMode(
                    query = querySnapshot,
                    selectedCategoryId = categorySnapshot,
                    forceRefresh = true
                )
            }.onSuccess { freshProducts ->
                val latest = _uiState.value
                if (
                    latest.query == querySnapshot &&
                    latest.selectedCategoryId == categorySnapshot &&
                    latest.isSearchActive == searchActiveSnapshot
                ) {
                    _uiState.update {
                        it.copy(
                            products = freshProducts,
                            error = null,
                            isProductsLoading = false
                        )
                    }
                    scheduleModifierHints(freshProducts)
                }
            }
        }
    }

    private suspend fun loadProductsForCurrentMode(
        query: String,
        selectedCategoryId: Long?,
        forceRefresh: Boolean
    ): List<ProductUi> {
        val normalizedQuery = query.trim()
        val products = if (normalizedQuery.isBlank()) {
            searchProductsUseCase(
                query = null,
                categoryId = selectedCategoryId,
                forceRefresh = forceRefresh
            )
        } else {
            searchProductsUseCase(
                query = normalizedQuery,
                categoryId = null,
                forceRefresh = forceRefresh
            )
        }
        return products.map { it.toUi() }
    }

    private fun shiftCategoryBy(delta: Int) {
        if (delta == 0) return
        val state = _uiState.value
        if (state.isSearchActive) return
        val categories = state.categories
        if (categories.isEmpty()) return

        val currentIndex = categories.indexOfFirst { it.id == state.selectedCategoryId }
            .takeIf { it >= 0 } ?: 0
        val targetIndex = (currentIndex + delta).coerceIn(0, categories.lastIndex)
        if (targetIndex == currentIndex) return

        _uiState.update {
            it.copy(
                selectedCategoryId = categories[targetIndex].id,
                isProductsLoading = true,
                products = emptyList(),
                error = null
            )
        }
        searchProductsDebounced()
    }

    private fun refreshCatalogInBackground(query: String, selectedCategoryId: Long?) {
        viewModelScope.launch {
            runCatching {
                val bootstrap = getCatalogBootstrapUseCase(
                    includeProducts = false,
                    forceRefresh = true
                )
                val freshCategories = bootstrap.categories
                    .map { CategoryUi(id = it.id, label = it.name) }
                val resolvedSelectedCategoryId = selectedCategoryId
                    ?: bootstrap.selectedCategoryId
                    ?: freshCategories.firstOrNull()?.id
                val freshProducts = if (query.isBlank()) {
                    searchProductsUseCase(
                        query = null,
                        categoryId = resolvedSelectedCategoryId,
                        forceRefresh = false
                    ).map { it.toUi() }
                } else {
                    loadProductsForCurrentMode(
                        query = query,
                        selectedCategoryId = resolvedSelectedCategoryId,
                        forceRefresh = true
                    )
                }
                Triple(freshCategories, resolvedSelectedCategoryId, freshProducts)
            }.onSuccess { (categories, resolvedSelectedCategoryId, products) ->
                val latest = _uiState.value
                if (latest.query == query && latest.selectedCategoryId == selectedCategoryId) {
                    _uiState.update {
                        it.copy(
                            categories = categories,
                            selectedCategoryId = resolvedSelectedCategoryId,
                            products = products,
                            isProductsLoading = false,
                            error = null
                        )
                    }
                    scheduleModifierHints(products)
                }
            }
        }
    }

    private fun scheduleModifierHints(products: List<ProductUi>) {
        val productIds = products.map { it.id }.distinct()
        if (productIds.isEmpty()) return

        productIds.forEach { productId ->
            val known = _uiState.value.hasModifiersByProductId.containsKey(productId)
            if (known) return@forEach
            if (!modifierHintsInFlight.add(productId)) return@forEach

            viewModelScope.launch {
                val hasModifiers = runCatching {
                    val versionHint = products.firstOrNull { it.id == productId }?.modifierVersion
                    getProductModifiersUseCase(
                        productId = productId,
                        expectedModifierVersion = versionHint,
                        forceRefresh = false
                    ).groups.isNotEmpty()
                }.getOrDefault(false)
                _uiState.update {
                    it.copy(hasModifiersByProductId = it.hasModifiersByProductId + (productId to hasModifiers))
                }
                modifierHintsInFlight.remove(productId)
            }
        }
    }

    private fun CatalogProduct.toUi(): ProductUi {
        val resolvedThumbnail = normalizeImageUrl(thumbnailUrl ?: image46x75 ?: image125x200 ?: image)
        val resolvedDetail = normalizeImageUrl(imageUrl ?: image125x200 ?: image46x75 ?: image)
        val versionedThumbnail = appendVersion(resolvedThumbnail, imageVersion)
        return ProductUi(
            id = id,
            name = name,
            code = code,
            imageUrl = versionedThumbnail ?: appendVersion(resolvedDetail, imageVersion),
            thumbnailUrl = versionedThumbnail,
            detailImageUrl = appendVersion(resolvedDetail, imageVersion),
            imageVersion = imageVersion,
            modifierVersion = modifierVersion,
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

    private fun appendVersion(raw: String?, version: Long?): String? {
        if (raw.isNullOrBlank() || version == null) return raw
        val separator = if (raw.contains("?")) "&" else "?"
        return "$raw${separator}v=$version"
    }

    companion object {
        private const val TAG: String = "AddItemCatalogSync"
        private const val PERIODIC_SYNC_MILLIS: Long = 5L * 60L * 1000L
        private const val CATALOG_SYNC_DEBOUNCE_MS: Long = 1_500L
    }

}
