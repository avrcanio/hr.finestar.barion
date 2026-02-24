package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.repo.CheckRepository

class OtpisCheckItemUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession {
        return repository.otpisItem(checkId = checkId, itemId = itemId, reason = reason, qty = qty)
    }
}
