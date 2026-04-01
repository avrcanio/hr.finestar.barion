package pos.finestar.barion.domain.repo

import pos.finestar.barion.domain.model.CatalogProduct
import pos.finestar.barion.domain.model.BundlePricePreview
import pos.finestar.barion.domain.model.Category
import pos.finestar.barion.domain.model.CategoryDisplay
import pos.finestar.barion.domain.model.ProductModifiersConfig
import pos.finestar.barion.domain.model.SelectedModifier

interface CatalogRepository {
    suspend fun getCategories(
        includeInactive: Boolean = false,
        level: Int? = null,
        forceRefresh: Boolean = false
    ): List<Category>
    suspend fun getCategoryDisplay(rootId: Long): CategoryDisplay
    suspend fun searchProducts(
        query: String?,
        categoryId: Long?,
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
