package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.repo.CheckRepository

class GetOpenCheckForTableUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(tableId: Long): CheckSession = repository.getOpenCheckByTable(tableId)
}
