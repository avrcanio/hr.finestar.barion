package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.DrinkCategory
import pos.finestar.barion.domain.repo.CatalogRepository

class GetDrinkCategoriesUseCase @Inject constructor(
    private val repository: CatalogRepository
) {
    suspend operator fun invoke(includeInactive: Boolean = false): List<DrinkCategory> {
        return repository.getDrinkCategories(includeInactive = includeInactive)
    }
}
