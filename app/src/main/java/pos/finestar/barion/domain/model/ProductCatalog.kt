package pos.finestar.barion.domain.model

data class Category(
    val id: Long,
    val name: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0
)

data class CategoryDisplay(
    val rootId: Long,
    val displayLevel: Int = 1,
    val categories: List<Category> = emptyList()
)

data class CatalogProduct(
    val id: Long,
    val name: String,
    val code: String? = null,
    val image: String? = null,
    val image46x75: String? = null,
    val image125x200: String? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val isSellable: Boolean = true,
    val isStockItem: Boolean = true,
    val price: Double = 0.0
)
