package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.Category
import pos.finestar.barion.domain.repo.CatalogRepository

class GetCategoriesUseCase @Inject constructor(
    private val repository: CatalogRepository
) {
    suspend operator fun invoke(
        includeInactive: Boolean = false,
        level: Int? = null,
        forceRefresh: Boolean = false
    ): List<Category> {
        return repository.getCategories(
            includeInactive = includeInactive,
            level = level,
            forceRefresh = forceRefresh
        )
    }
}
