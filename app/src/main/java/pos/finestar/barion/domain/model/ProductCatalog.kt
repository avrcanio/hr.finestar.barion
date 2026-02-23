package pos.finestar.barion.domain.model

data class DrinkCategory(
    val id: Long,
    val name: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0
)

data class DrinkCategoryDisplay(
    val rootId: Long,
    val displayLevel: Int = 1,
    val categories: List<DrinkCategory> = emptyList()
)

data class CatalogProduct(
    val id: Long,
    val name: String,
    val price: Double = 0.0
)
