package hr.finestar.barion.domain.usecase

import javax.inject.Inject
import hr.finestar.barion.domain.model.BundlePricePreview
import hr.finestar.barion.domain.model.SelectedModifier
import hr.finestar.barion.domain.repo.CatalogRepository

class PreviewBundlePriceUseCase @Inject constructor(
    private val repository: CatalogRepository
) {
    suspend operator fun invoke(
        productId: Long,
        modifiers: List<SelectedModifier>
    ): BundlePricePreview {
        return repository.previewBundlePrice(
            productId = productId,
            modifiers = modifiers
        )
    }
}
