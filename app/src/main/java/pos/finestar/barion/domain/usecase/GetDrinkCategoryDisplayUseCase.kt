package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.DrinkCategoryDisplay
import pos.finestar.barion.domain.repo.CatalogRepository

class GetDrinkCategoryDisplayUseCase @Inject constructor(
    private val repository: CatalogRepository
) {
    suspend operator fun invoke(rootId: Long): DrinkCategoryDisplay {
        return repository.getDrinkCategoryDisplay(rootId = rootId)
    }
}
