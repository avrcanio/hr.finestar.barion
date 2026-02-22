package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.FloorTable
import pos.finestar.barion.domain.repo.FloorPlanRepository

class GetFloorTablesUseCase @Inject constructor(
    private val repository: FloorPlanRepository
) {
    suspend operator fun invoke(): List<FloorTable> = repository.getTables()
}
