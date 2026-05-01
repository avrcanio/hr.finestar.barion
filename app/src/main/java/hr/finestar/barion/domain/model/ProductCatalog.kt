package hr.finestar.barion.domain.model

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

data class CatalogBootstrap(
    val catalogVersion: Long = 0L,
    val activeMode: String = "unknown",
    val rootId: Long = 0L,
    val displayLevel: Int = 1,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val products: List<CatalogProduct> = emptyList()
)

data class CatalogProduct(
    val id: Long,
    val name: String,
    val code: String? = null,
    val rmId: Long? = null,
    val image: String? = null,
    val image46x75: String? = null,
    val image125x200: String? = null,
    val thumbnailUrl: String? = null,
    val imageUrl: String? = null,
    val imageVersion: Long? = null,
    val modifierVersion: Long? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val isSellable: Boolean = true,
    val isStockItem: Boolean = true,
    val price: Double = 0.0,
    val taxRate: Double? = null,
    val popularityScore: Double? = null
)
