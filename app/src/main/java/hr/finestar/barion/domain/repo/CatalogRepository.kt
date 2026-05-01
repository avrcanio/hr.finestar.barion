package hr.finestar.barion.domain.repo

import hr.finestar.barion.domain.model.CatalogProduct
import hr.finestar.barion.domain.model.BundlePricePreview
import hr.finestar.barion.domain.model.CatalogBootstrap
import hr.finestar.barion.domain.model.CategoryDisplay
import hr.finestar.barion.domain.model.ProductModifiersConfig
import hr.finestar.barion.domain.model.SelectedModifier

interface CatalogRepository {
    suspend fun syncCatalog(forceBootstrap: Boolean = false)

    suspend fun getCatalogBootstrap(
        includeProducts: Boolean = true,
        forceRefresh: Boolean = false
    ): CatalogBootstrap
    suspend fun getCategoryDisplay(rootId: Long): CategoryDisplay
    suspend fun searchProducts(
        query: String?,
        categoryId: Long?,
        forceRefresh: Boolean = false
    ): List<CatalogProduct>

    suspend fun getProductModifiers(
        productId: Long,
        expectedModifierVersion: Long? = null,
        forceRefresh: Boolean = false
    ): ProductModifiersConfig

    suspend fun previewBundlePrice(
        productId: Long,
        modifiers: List<SelectedModifier>
    ): BundlePricePreview
}
