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
import pos.finestar.barion.domain.model.DrinkCategory
import pos.finestar.barion.domain.model.ProductModifiersConfig
import pos.finestar.barion.domain.model.SelectedModifier
import pos.finestar.barion.domain.repo.CatalogRepository
import pos.finestar.barion.domain.repo.CheckRepository
import pos.finestar.barion.domain.usecase.AddItemToCheckUseCase
import pos.finestar.barion.domain.usecase.GetDrinkCategoriesUseCase
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
        assertEquals(listOf("Kava espresso"), vm.uiState.value.products.map { it.name })
    }

    @Test
    fun `swipe right on first category stays on first category`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onCategorySwipeRight()
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.selectedCategoryId)
        assertEquals(listOf("Kava espresso", "Pivo"), vm.uiState.value.products.map { it.name })
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
        assertEquals(listOf("Pivo"), vm.uiState.value.products.map { it.name })
    }

    private fun createViewModel(): AddItemViewModel {
        val catalogRepo = FakeCatalogRepository()
        val checkRepo = FakeCheckRepository()
        return AddItemViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    NavRoutes.ARG_CHECK_ID to 15L,
                    NavRoutes.ARG_TABLE_NAME to "T1"
                )
            ),
            getDrinkCategoriesUseCase = GetDrinkCategoriesUseCase(catalogRepo),
            searchProductsUseCase = SearchProductsUseCase(catalogRepo),
            getProductModifiersUseCase = GetProductModifiersUseCase(catalogRepo),
            previewBundlePriceUseCase = PreviewBundlePriceUseCase(catalogRepo),
            addItemToCheckUseCase = AddItemToCheckUseCase(checkRepo),
            sendToBarUseCase = SendToBarUseCase(checkRepo)
        )
    }

    private class FakeCatalogRepository : CatalogRepository {
        private val categories = listOf(
            DrinkCategory(id = 10L, name = "Kave", sortOrder = 1),
            DrinkCategory(id = 20L, name = "Pivo", sortOrder = 2)
        )
        private val products = listOf(
            CatalogProduct(id = 1L, name = "Kava espresso", drinkCategoryId = 10L, price = 1.8),
            CatalogProduct(id = 2L, name = "Pivo", drinkCategoryId = 20L, price = 3.4)
        )

        override suspend fun getDrinkCategories(
            includeInactive: Boolean,
            level: Int?,
            forceRefresh: Boolean
        ): List<DrinkCategory> = categories

        override suspend fun getDrinkCategoryDisplay(rootId: Long) = error("unused")

        override suspend fun searchProducts(
            query: String?,
            drinkCategoryId: Long?,
            forceRefresh: Boolean
        ): List<CatalogProduct> {
            val byCategory = products.filter { drinkCategoryId == null || it.drinkCategoryId == drinkCategoryId }
            return byCategory.filter { query.isNullOrBlank() || it.name.contains(query, ignoreCase = true) }
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
