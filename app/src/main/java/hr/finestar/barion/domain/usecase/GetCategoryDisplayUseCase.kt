package hr.finestar.barion.domain.usecase

import javax.inject.Inject
import hr.finestar.barion.domain.model.CategoryDisplay
import hr.finestar.barion.domain.repo.CatalogRepository

class GetCategoryDisplayUseCase @Inject constructor(
    private val repository: CatalogRepository
) {
    suspend operator fun invoke(rootId: Long): CategoryDisplay {
        return repository.getCategoryDisplay(rootId = rootId)
    }
}
