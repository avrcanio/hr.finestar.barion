package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.ProductModifiersConfig
import pos.finestar.barion.domain.repo.CatalogRepository

class GetProductModifiersUseCase @Inject constructor(
    private val repository: CatalogRepository
) {
    suspend operator fun invoke(
        productId: Long,
        forceRefresh: Boolean = false
    ): ProductModifiersConfig {
        return repository.getProductModifiers(
            productId = productId,
            forceRefresh = forceRefresh
        )
    }
}
