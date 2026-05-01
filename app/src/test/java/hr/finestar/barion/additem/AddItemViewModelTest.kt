package hr.finestar.barion.additem

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import hr.finestar.barion.domain.model.BundlePricePreview
import hr.finestar.barion.domain.model.CatalogBootstrap
import hr.finestar.barion.domain.model.CatalogProduct
import hr.finestar.barion.domain.model.CheckSession
import hr.finestar.barion.domain.model.Category
import hr.finestar.barion.domain.model.ProductModifiersConfig
import hr.finestar.barion.domain.model.SelectedModifier
import hr.finestar.barion.domain.repo.CatalogRepository
import hr.finestar.barion.domain.repo.CheckRepository
import hr.finestar.barion.domain.usecase.AddItemToCheckUseCase
import hr.finestar.barion.domain.usecase.GetCatalogBootstrapUseCase
import hr.finestar.barion.domain.usecase.GetProductModifiersUseCase
import hr.finestar.barion.domain.usecase.PreviewBundlePriceUseCase
import hr.finestar.barion.domain.usecase.SearchProductsUseCase
import hr.finestar.barion.domain.usecase.SendToBarUseCase
import hr.finestar.barion.domain.usecase.SyncCatalogUseCase
import hr.finestar.barion.sync.CatalogPresentationEventBus
import hr.finestar.barion.testutil.MainDispatcherRule
import hr.finestar.barion.ui.navigation.NavRoutes

@OptIn(ExperimentalCoroutinesApi::class)
class AddItemViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `swipe left moves to next category and updates products`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCategorySwipeLeft()
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(20L, vm.uiState.value.selectedCategoryId)
        assertEquals(listOf("Pivo", "Lager"), vm.uiState.value.products.map { it.name })
    }

    @Test
    fun `swipe right on first category stays on first category`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCategorySwipeRight()
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(10L, vm.uiState.value.selectedCategoryId)
        assertEquals(listOf("Kava espresso", "Latte"), vm.uiState.value.products.map { it.name })
    }

    @Test
    fun `swipe left on last category stays on last category`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCategorySwipeLeft()
        vm.onCategorySwipeLeft()
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(20L, vm.uiState.value.selectedCategoryId)

        vm.onCategorySwipeLeft()
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(20L, vm.uiState.value.selectedCategoryId)
        assertEquals(listOf("Pivo", "Lager"), vm.uiState.value.products.map { it.name })
    }

    @Test
    fun `global query filters by text across all categories`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCategorySelected(20L)
        advanceTimeBy(300)
        advanceUntilIdle()

        vm.onQueryChanged("kav")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.isSearchActive)
        assertEquals(listOf("Kava espresso"), vm.uiState.value.products.map { it.name })
    }

    @Test
    fun `startup uses bootstrap selected category and products`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isProductsLoading)
        assertEquals(10L, vm.uiState.value.selectedCategoryId)
        assertEquals(listOf("Kava espresso", "Latte"), vm.uiState.value.products.map { it.name })
    }

    @Test
    fun `startup keeps products loading true until bootstrap resolves`() = runTest {
        val catalogRepo = FakeCatalogRepository(bootstrapDelayMs = 1_000L)
        val vm = createViewModel(catalogRepo)
        runCurrent()

        assertEquals(true, vm.uiState.value.isLoading)
        assertEquals(true, vm.uiState.value.isProductsLoading)

        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isLoading)
        assertEquals(false, vm.uiState.value.isProductsLoading)
    }

    @Test
    fun `bootstrap failure shows startup error and does not use legacy category bootstrap`() = runTest {
        val catalogRepo = FakeCatalogRepository(
            failBootstrap = true
        )
        val vm = createViewModel(catalogRepo)
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.selectedCategoryId)
        assertEquals(emptyList<String>(), vm.uiState.value.products.map { it.name })
        assertTrue(vm.uiState.value.error?.contains("bootstrap failed") == true)
    }

    @Test
    fun `empty bootstrap keeps screen stable with empty categories and products`() = runTest {
        val catalogRepo = FakeCatalogRepository(
            bootstrap = CatalogBootstrap(
                activeMode = "day",
                rootId = 38L,
                displayLevel = 2,
                categories = emptyList(),
                selectedCategoryId = null,
                products = emptyList()
            )
        )
        val vm = createViewModel(catalogRepo)
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.selectedCategoryId)
        assertEquals(emptyList<String>(), vm.uiState.value.products.map { it.name })
        assertEquals(emptyList<Long?>(), vm.uiState.value.categories.map { it.id })
    }

    @Test
    fun `search ignores selected category and filters globally by query`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCategorySelected(20L)
        advanceTimeBy(300)
        advanceUntilIdle()

        vm.onQueryChanged("piv")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf("Pivo"), vm.uiState.value.products.map { it.name })
    }

    @Test
    fun `search uses global category null filter`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        val vm = createViewModel(catalogRepo)
        advanceUntilIdle()
        catalogRepo.clearSearchCalls()

        vm.onQueryChanged("kav")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf(null, null), catalogRepo.searchCalls.map { it.categoryId })
    }

    @Test
    fun `search shows products loading spinner until results arrive`() = runTest {
        val catalogRepo = FakeCatalogRepository(searchDelayMs = 1_000L)
        val vm = createViewModel(catalogRepo)
        advanceUntilIdle()

        vm.onQueryChanged("kav")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(true, vm.uiState.value.isProductsLoading)

        advanceTimeBy(1_000)
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.isProductsLoading)
        assertEquals(listOf("Kava espresso"), vm.uiState.value.products.map { it.name })
    }

    @Test
    fun `category change clears stale products and shows loading until new products arrive`() = runTest {
        val catalogRepo = FakeCatalogRepository(searchDelayMs = 1_000L)
        val vm = createViewModel(catalogRepo)
        advanceUntilIdle()

        assertEquals(listOf("Kava espresso", "Latte"), vm.uiState.value.products.map { it.name })

        vm.onCategorySelected(20L)
        runCurrent()
        assertEquals(true, vm.uiState.value.isProductsLoading)
        assertEquals(emptyList<String>(), vm.uiState.value.products.map { it.name })

        advanceTimeBy(300)
        runCurrent()
        assertEquals(true, vm.uiState.value.isProductsLoading)
        assertEquals(emptyList<String>(), vm.uiState.value.products.map { it.name })

        advanceTimeBy(1_000)
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.isProductsLoading)
        assertEquals(listOf("Pivo", "Lager"), vm.uiState.value.products.map { it.name })
    }

    @Test
    fun `category change failure stops loading and exposes error`() = runTest {
        val catalogRepo = FakeCatalogRepository(
            searchDelayMs = 1_000L,
            failSearchCategoryId = 20L
        )
        val vm = createViewModel(catalogRepo)
        advanceUntilIdle()

        vm.onCategorySelected(20L)
        runCurrent()
        assertEquals(true, vm.uiState.value.isProductsLoading)
        assertEquals(emptyList<String>(), vm.uiState.value.products.map { it.name })

        advanceTimeBy(300)
        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isProductsLoading)
        assertTrue(vm.uiState.value.error?.contains("search failed") == true)
    }

    @Test
    fun `clear query restores selected category filtering`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        val vm = createViewModel(catalogRepo)
        advanceUntilIdle()
        catalogRepo.clearSearchCalls()

        vm.onQueryChanged("kav")
        advanceTimeBy(300)
        advanceUntilIdle()

        vm.onCategorySelected(20L)
        advanceTimeBy(300)
        advanceUntilIdle()

        vm.onQueryChanged("")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isSearchActive)
        assertEquals(20L, vm.uiState.value.selectedCategoryId)
        assertEquals(listOf("Pivo", "Lager"), vm.uiState.value.products.map { it.name })
        assertEquals(20L, catalogRepo.searchCalls.last().categoryId)
    }

    private fun createViewModel(catalogRepo: FakeCatalogRepository = FakeCatalogRepository()): AddItemViewModel {
        val checkRepo = FakeCheckRepository()
        return AddItemViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    NavRoutes.ARG_CHECK_ID to 15L,
                    NavRoutes.ARG_TABLE_NAME to "T1"
                )
            ),
            getCatalogBootstrapUseCase = GetCatalogBootstrapUseCase(catalogRepo),
            searchProductsUseCase = SearchProductsUseCase(catalogRepo),
            syncCatalogUseCase = SyncCatalogUseCase(catalogRepo),
            getProductModifiersUseCase = GetProductModifiersUseCase(catalogRepo),
            previewBundlePriceUseCase = PreviewBundlePriceUseCase(catalogRepo),
            addItemToCheckUseCase = AddItemToCheckUseCase(checkRepo),
            sendToBarUseCase = SendToBarUseCase(checkRepo),
            catalogPresentationEventBus = CatalogPresentationEventBus()
        )
    }

    private class FakeCatalogRepository(
        private val failBootstrap: Boolean = false,
        private val bootstrap: CatalogBootstrap? = null,
        private val bootstrapDelayMs: Long = 0L,
        private val searchDelayMs: Long = 0L,
        private val failSearchCategoryId: Long? = null
    ) : CatalogRepository {
        data class SearchCall(
            val query: String?,
            val categoryId: Long?,
            val forceRefresh: Boolean
        )

        val searchCalls = mutableListOf<SearchCall>()

        private val categories = listOf(
            Category(id = 10L, name = "Kave", sortOrder = 1),
            Category(id = 20L, name = "Pivo", sortOrder = 2)
        )
        private val products = listOf(
            CatalogProduct(id = 1L, name = "Kava espresso", categoryId = 10L, price = 1.8),
            CatalogProduct(id = 3L, name = "Latte", categoryId = 10L, price = 2.4),
            CatalogProduct(id = 2L, name = "Pivo", categoryId = 20L, price = 3.4),
            CatalogProduct(id = 4L, name = "Lager", categoryId = 20L, price = 3.6)
        )

        override suspend fun syncCatalog(forceBootstrap: Boolean) = Unit

        override suspend fun getCatalogBootstrap(
            includeProducts: Boolean,
            forceRefresh: Boolean
        ): CatalogBootstrap {
            if (failBootstrap) {
                error("bootstrap failed")
            }
            if (bootstrapDelayMs > 0L) kotlinx.coroutines.delay(bootstrapDelayMs)
            return bootstrap ?: CatalogBootstrap(
                activeMode = "day",
                rootId = 38L,
                displayLevel = 2,
                categories = categories,
                selectedCategoryId = 10L,
                products = products.filter { it.categoryId == 10L }
            )
        }

        override suspend fun getCategoryDisplay(rootId: Long) = error("unused")

        override suspend fun searchProducts(
            query: String?,
            categoryId: Long?,
            forceRefresh: Boolean
        ): List<CatalogProduct> {
            if (searchDelayMs > 0L) kotlinx.coroutines.delay(searchDelayMs)
            if (failSearchCategoryId != null && categoryId == failSearchCategoryId && query.isNullOrBlank()) {
                error("search failed")
            }
            searchCalls += SearchCall(
                query = query,
                categoryId = categoryId,
                forceRefresh = forceRefresh
            )
            val byCategory = products.filter { categoryId == null || it.categoryId == categoryId }
            return byCategory.filter { query.isNullOrBlank() || it.name.contains(query, ignoreCase = true) }
        }

        fun clearSearchCalls() {
            searchCalls.clear()
        }

        override suspend fun getProductModifiers(
            productId: Long,
            expectedModifierVersion: Long?,
            forceRefresh: Boolean
        ): ProductModifiersConfig = ProductModifiersConfig(artiklId = productId, groups = emptyList())

        override suspend fun previewBundlePrice(
            productId: Long,
            modifiers: List<SelectedModifier>
        ): BundlePricePreview {
            return BundlePricePreview(
                artiklId = productId,
                baseUnitPrice = 0.0,
                mixersDelta = 0.0,
                finalUnitPrice = 0.0
            )
        }
    }

    private class FakeCheckRepository : CheckRepository {
        override suspend fun createCheck(tableId: Long): CheckSession = error("unused")
        override suspend fun getOpenCheckByTable(tableId: Long): CheckSession = error("unused")
        override suspend fun getCheck(checkId: Long, forceRefresh: Boolean): CheckSession? = error("unused")
        override suspend fun addItem(
            checkId: Long,
            productId: Long,
            qty: Int,
            unitPrice: Double,
            productName: String?,
            modifiers: List<SelectedModifier>,
            note: String?
        ): CheckSession = error("unused")
        override suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession = error("unused")
        override suspend fun removeItem(checkId: Long, itemId: Long): CheckSession = error("unused")
        override suspend fun stornoItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
        override suspend fun gratisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
        override suspend fun otpisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession = error("unused")
        override suspend fun sendToBar(checkId: Long): CheckSession = error("unused")
        override suspend fun closeCheck(checkId: Long): CheckSession? = error("unused")
        override suspend fun issueReceipt(checkId: Long, fiscalize: Boolean): CheckSession? = error("unused")
    }
}
