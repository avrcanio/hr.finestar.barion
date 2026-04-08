package pos.finestar.barion.domain.model

enum class ModifierType {
    SIMPLE,
    BUNDLE;

    companion object {
        fun fromRaw(value: String?): ModifierType {
            return when (value?.lowercase()) {
                "bundle" -> BUNDLE
                else -> SIMPLE
            }
        }
    }
}

enum class SelectionMode {
    SINGLE,
    MULTIPLE;

    companion object {
        fun fromRaw(value: String?): SelectionMode {
            return when (value?.lowercase()) {
                "single" -> SINGLE
                else -> MULTIPLE
            }
        }
    }
}

data class ProductModifierOption(
    val id: Long,
    val name: String,
    val code: String? = null,
    val type: ModifierType = ModifierType.SIMPLE,
    val artiklId: Long? = null,
    val artiklName: String? = null,
    val priceDelta: Double = 0.0
)

data class ProductModifierGroup(
    val id: Long,
    val name: String,
    val code: String? = null,
    val type: ModifierType = ModifierType.SIMPLE,
    val selectionMode: SelectionMode = SelectionMode.MULTIPLE,
    val minSelect: Int = 0,
    val maxSelect: Int? = null,
    val allowNote: Boolean = true,
    val isRequired: Boolean = false,
    val options: List<ProductModifierOption> = emptyList()
)

data class ProductModifiersConfig(
    val artiklId: Long,
    val modifierVersion: Long? = null,
    val groups: List<ProductModifierGroup> = emptyList(),
    val allowNote: Boolean = true
)

data class SelectedModifier(
    val type: ModifierType,
    val id: Long,
    val quantity: Int? = null
)

data class BundlePricePreview(
    val artiklId: Long,
    val baseUnitPrice: Double,
    val mixersDelta: Double,
    val finalUnitPrice: Double
)
