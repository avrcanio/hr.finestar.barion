package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.FloorPlanData
import pos.finestar.barion.domain.repo.FloorPlanRepository

class GetFloorTablesUseCase @Inject constructor(
    private val repository: FloorPlanRepository
) {
    suspend operator fun invoke(
        layoutId: Long? = null,
        forceRefresh: Boolean = false
    ): FloorPlanData = repository.getTables(layoutId, forceRefresh = forceRefresh)
}
