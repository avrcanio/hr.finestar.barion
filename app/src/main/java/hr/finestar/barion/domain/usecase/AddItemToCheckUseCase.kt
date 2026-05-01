package hr.finestar.barion.domain.usecase

import javax.inject.Inject
import hr.finestar.barion.domain.model.CheckSession
import hr.finestar.barion.domain.model.SelectedModifier
import hr.finestar.barion.domain.repo.CheckRepository

class AddItemToCheckUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(
        checkId: Long,
        productId: Long,
        qty: Int,
        unitPrice: Double,
        productName: String? = null,
        modifiers: List<SelectedModifier> = emptyList(),
        note: String? = null
    ): CheckSession {
        return repository.addItem(
            checkId = checkId,
            productId = productId,
            qty = qty,
            unitPrice = unitPrice,
            productName = productName,
            modifiers = modifiers,
            note = note
        )
    }
}
