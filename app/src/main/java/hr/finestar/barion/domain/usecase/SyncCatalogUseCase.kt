package hr.finestar.barion.domain.usecase

import javax.inject.Inject
import hr.finestar.barion.domain.repo.CatalogRepository

class SyncCatalogUseCase @Inject constructor(
    private val repository: CatalogRepository
) {
    suspend operator fun invoke(forceBootstrap: Boolean = false) {
        repository.syncCatalog(forceBootstrap = forceBootstrap)
    }
}
