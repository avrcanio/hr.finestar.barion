package pos.finestar.barion.domain.repo

import pos.finestar.barion.domain.model.CatalogProduct
import pos.finestar.barion.domain.model.DrinkCategory
import pos.finestar.barion.domain.model.DrinkCategoryDisplay

interface CatalogRepository {
    suspend fun getDrinkCategories(includeInactive: Boolean = false): List<DrinkCategory>
    suspend fun getDrinkCategoryDisplay(rootId: Long): DrinkCategoryDisplay
    suspend fun searchProducts(query: String?, drinkCategoryId: Long?): List<CatalogProduct>
}
