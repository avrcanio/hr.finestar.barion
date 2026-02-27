package pos.finestar.barion.domain.usecase

import javax.inject.Inject
import pos.finestar.barion.domain.model.BundlePricePreview
import pos.finestar.barion.domain.model.SelectedModifier
import pos.finestar.barion.domain.repo.CatalogRepository

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
