package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.RuntimeMode
import pos.finestar.barion.domain.repo.FloorPlanRepository

class GetRuntimeModeUseCase @Inject constructor(
    private val repository: FloorPlanRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): RuntimeMode {
        return repository.getRuntimeMode(forceRefresh = forceRefresh)
    }
}
