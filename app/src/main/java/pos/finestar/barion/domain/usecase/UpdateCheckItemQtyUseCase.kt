package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.repo.CheckRepository

class UpdateCheckItemQtyUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(checkId: Long, itemId: Long, qty: Int): CheckSession {
        return repository.updateItem(checkId = checkId, itemId = itemId, qty = qty)
    }
}
