package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.AllowedLayout
import pos.finestar.barion.domain.repo.FloorPlanRepository

class GetAllowedLayoutsUseCase @Inject constructor(
    private val repository: FloorPlanRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): List<AllowedLayout> {
        return repository.getAllowedLayouts(forceRefresh = forceRefresh)
    }
}
