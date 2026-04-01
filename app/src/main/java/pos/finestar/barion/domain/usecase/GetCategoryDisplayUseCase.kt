package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.CategoryDisplay
import pos.finestar.barion.domain.repo.CatalogRepository

class GetCategoryDisplayUseCase @Inject constructor(
    private val repository: CatalogRepository
) {
    suspend operator fun invoke(rootId: Long): CategoryDisplay {
        return repository.getCategoryDisplay(rootId = rootId)
    }
}
