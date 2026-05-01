package hr.finestar.barion.domain.usecase

import javax.inject.Inject
import hr.finestar.barion.domain.model.CatalogProduct
import hr.finestar.barion.domain.repo.CatalogRepository

class SearchProductsUseCase @Inject constructor(
    private val repository: CatalogRepository
) {
    suspend operator fun invoke(
        query: String?,
        categoryId: Long?,
        forceRefresh: Boolean = false
    ): List<CatalogProduct> {
        return repository.searchProducts(
            query = query,
            categoryId = categoryId,
            forceRefresh = forceRefresh
        )
    }
}
