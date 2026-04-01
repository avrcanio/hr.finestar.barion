package pos.finestar.barion.additem

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pos.finestar.barion.domain.model.BundlePricePreview
import pos.finestar.barion.domain.model.CatalogProduct
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.model.Category
import pos.finestar.barion.domain.model.ProductModifiersConfig
import pos.finestar.barion.domain.model.SelectedModifier
import pos.finestar.barion.domain.repo.CatalogRepository
import pos.finestar.barion.domain.repo.CheckRepository
import pos.finestar.barion.domain.usecase.AddItemToCheckUseCase
import pos.finestar.barion.domain.usecase.GetCategoriesUseCase
import pos.finestar.barion.domain.usecase.GetProductModifiersUseCase
import pos.finestar.barion.domain.usecase.PreviewBundlePriceUseCase
import pos.finestar.barion.domain.usecase.SearchProductsUseCase
import pos.finestar.barion.domain.usecase.SendToBarUseCase
import pos.finestar.barion.testutil.MainDispatcherRule
import pos.finestar.barion.ui.navigation.NavRoutes

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

        assertEquals(10L, vm.uiState.value.selectedCategoryId)
        assertEquals(listOf("Kava espresso", "Latte"), vm.uiState.value.products.map { it.name })
    }

    @Test
    fun `swipe right on first category stays on first category`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCategorySwipeRight()
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.selectedCategoryId)
        assertEquals(listOf("Kava espresso", "Latte", "Pivo", "Lager"), vm.uiState.value.products.map { it.name })
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
    fun `query matching category name returns union of name and category results`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onQueryChanged("kav")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf("Kava espresso", "Latte"), vm.uiState.value.products.map { it.name })
    }

    @Test
    fun `query matching selected category expands only selected category`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCategorySelected(20L)
        advanceTimeBy(300)
        advanceUntilIdle()

        vm.onQueryChanged("piv")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf("Pivo", "Lager"), vm.uiState.value.products.map { it.name })
    }

    @Test
    fun `union removes duplicates when product matches both name and category expansion`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onQueryChanged("kav")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf(1L, 3L), vm.uiState.value.products.map { it.id })
    }

    @Test
    fun `blank query does not trigger category expansion calls`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        val vm = createViewModel(catalogRepo)
        advanceUntilIdle()
        catalogRepo.clearSearchCalls()

        vm.onCategorySelected(20L)
        advanceTimeBy(300)
        advanceUntilIdle()

        val nullQueryCalls = catalogRepo.searchCalls.count { it.query == null }
        assertEquals(0, nullQueryCalls)
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
            getCategoriesUseCase = GetCategoriesUseCase(catalogRepo),
            searchProductsUseCase = SearchProductsUseCase(catalogRepo),
            getProductModifiersUseCase = GetProductModifiersUseCase(catalogRepo),
            previewBundlePriceUseCase = PreviewBundlePriceUseCase(catalogRepo),
            addItemToCheckUseCase = AddItemToCheckUseCase(checkRepo),
            sendToBarUseCase = SendToBarUseCase(checkRepo)
        )
    }

    private class FakeCatalogRepository : CatalogRepository {
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

        override suspend fun getCategories(
            includeInactive: Boolean,
            level: Int?,
            forceRefresh: Boolean
        ): List<Category> = categories

        override suspend fun getCategoryDisplay(rootId: Long) = error("unused")

        override suspend fun searchProducts(
            query: String?,
            categoryId: Long?,
            forceRefresh: Boolean
        ): List<CatalogProduct> {
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
