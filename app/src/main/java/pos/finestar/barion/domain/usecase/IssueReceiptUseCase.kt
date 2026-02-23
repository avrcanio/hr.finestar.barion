package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.repo.CheckRepository

class IssueReceiptUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(checkId: Long, fiscalize: Boolean = true): CheckSession? {
        return repository.issueReceipt(checkId = checkId, fiscalize = fiscalize)
    }
}
