package hr.finestar.barion.domain.usecase

import javax.inject.Inject
import hr.finestar.barion.domain.model.CatalogBootstrap
import hr.finestar.barion.domain.repo.CatalogRepository

class GetCatalogBootstrapUseCase @Inject constructor(
    private val repository: CatalogRepository
) {
    suspend operator fun invoke(
        includeProducts: Boolean = true,
        forceRefresh: Boolean = false
    ): CatalogBootstrap {
        return repository.getCatalogBootstrap(
            includeProducts = includeProducts,
            forceRefresh = forceRefresh
        )
    }
}
