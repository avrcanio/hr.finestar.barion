package pos.finestar.barion.domain.repo

import pos.finestar.barion.domain.model.CatalogProduct
import pos.finestar.barion.domain.model.BundlePricePreview
import pos.finestar.barion.domain.model.DrinkCategory
import pos.finestar.barion.domain.model.DrinkCategoryDisplay
import pos.finestar.barion.domain.model.ProductModifiersConfig
import pos.finestar.barion.domain.model.SelectedModifier

interface CatalogRepository {
    suspend fun getDrinkCategories(
        includeInactive: Boolean = false,
        level: Int? = null,
        forceRefresh: Boolean = false
    ): List<DrinkCategory>
    suspend fun getDrinkCategoryDisplay(rootId: Long): DrinkCategoryDisplay
    suspend fun searchProducts(
        query: String?,
        drinkCategoryId: Long?,
        forceRefresh: Boolean = false
    ): List<CatalogProduct>

    suspend fun getProductModifiers(
        productId: Long,
        forceRefresh: Boolean = false
    ): ProductModifiersConfig

    suspend fun previewBundlePrice(
        productId: Long,
        modifiers: List<SelectedModifier>
    ): BundlePricePreview
}
