package hr.finestar.barion.domain.usecase

import javax.inject.Inject
import hr.finestar.barion.domain.model.CheckSession
import hr.finestar.barion.domain.repo.CheckRepository

class UpdateCheckItemQtyUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(checkId: Long, itemId: Long, qty: Int): CheckSession {
        return repository.updateItem(checkId = checkId, itemId = itemId, qty = qty)
    }
}
