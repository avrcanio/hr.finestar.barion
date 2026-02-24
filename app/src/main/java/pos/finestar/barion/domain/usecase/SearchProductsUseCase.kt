package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.CatalogProduct
import pos.finestar.barion.domain.repo.CatalogRepository

class SearchProductsUseCase @Inject constructor(
    private val repository: CatalogRepository
) {
    suspend operator fun invoke(
        query: String?,
        drinkCategoryId: Long?,
        forceRefresh: Boolean = false
    ): List<CatalogProduct> {
        return repository.searchProducts(
            query = query,
            drinkCategoryId = drinkCategoryId,
            forceRefresh = forceRefresh
        )
    }
}
