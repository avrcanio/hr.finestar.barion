package hr.finestar.barion.domain.usecase

import javax.inject.Inject
import hr.finestar.barion.domain.model.ProductModifiersConfig
import hr.finestar.barion.domain.repo.CatalogRepository

class GetProductModifiersUseCase @Inject constructor(
    private val repository: CatalogRepository
) {
    suspend operator fun invoke(
        productId: Long,
        expectedModifierVersion: Long? = null,
        forceRefresh: Boolean = false
    ): ProductModifiersConfig {
        return repository.getProductModifiers(
            productId = productId,
            expectedModifierVersion = expectedModifierVersion,
            forceRefresh = forceRefresh
        )
    }
}
