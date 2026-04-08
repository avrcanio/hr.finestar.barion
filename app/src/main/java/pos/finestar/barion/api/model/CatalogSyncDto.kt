package pos.finestar.barion.api.model

import com.google.gson.annotations.SerializedName

data class CatalogBootstrapDto(
    @SerializedName("catalog_version")
    val catalogVersion: Long = 0L,
    @SerializedName("active_mode")
    val activeMode: String? = null,
    @SerializedName("root_id")
    val rootId: Long? = null,
    @SerializedName("display_level")
    val displayLevel: Int? = null,
    @SerializedName("categories")
    val categories: List<CatalogCategoryDto> = emptyList(),
    @SerializedName("selected_category_id")
    val selectedCategoryId: Long? = null,
    @SerializedName("products")
    val products: List<CatalogProductDto> = emptyList()
)

data class CatalogProductDto(
    @SerializedName("id")
    val id: Long,
    @SerializedName("rm_id")
    val rmId: Long? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("code")
    val code: String? = null,
    @SerializedName("image")
    val image: String? = null,
    @SerializedName("image_46x75")
    val image46x75: String? = null,
    @SerializedName("image_125x200")
    val image125x200: String? = null,
    @SerializedName("thumbnail_url")
    val thumbnailUrl: String? = null,
    @SerializedName("image_url")
    val imageUrl: String? = null,
    @SerializedName("image_version")
    val imageVersion: Long? = null,
    @SerializedName("modifier_version")
    val modifierVersion: Long? = null,
    @SerializedName("category_id")
    val categoryId: Long? = null,
    @SerializedName("category_name")
    val categoryName: String? = null,
    @SerializedName("is_sellable")
    val isSellable: Boolean? = null,
    @SerializedName("is_stock_item")
    val isStockItem: Boolean? = null,
    @SerializedName("unit_price")
    val unitPrice: String? = null,
    @SerializedName("price")
    val price: String? = null,
    @SerializedName("price_with_tax")
    val priceWithTax: String? = null,
    @SerializedName("tax_rate")
    val taxRate: String? = null,
    @SerializedName("popularity_score")
    val popularityScore: String? = null
)

data class CatalogCategoryDto(
    @SerializedName("id")
    val id: Long,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("parent_id")
    val parentId: Long? = null,
    @SerializedName("sort_order")
    val sortOrder: Int? = null,
    @SerializedName("popularity_score")
    val popularityScore: String? = null
)

data class CatalogChangesResponseDto(
    @SerializedName("requiresFullSync")
    val requiresFullSync: Boolean = false,
    @SerializedName("baseVersion")
    val baseVersion: Long = 0L,
    @SerializedName("appliedThroughVersion")
    val appliedThroughVersion: Long = 0L,
    @SerializedName("targetVersion")
    val targetVersion: Long = 0L,
    @SerializedName("catalogVersion")
    val catalogVersion: Long = 0L,
    @SerializedName("layouts")
    val layouts: CatalogLayoutChangesDto = CatalogLayoutChangesDto(),
    @SerializedName("categories")
    val categories: CatalogCategoryChangesDto = CatalogCategoryChangesDto(),
    @SerializedName("products")
    val products: CatalogProductChangesDto = CatalogProductChangesDto(),
    @SerializedName("hasMore")
    val hasMore: Boolean = false
)

data class CatalogLayoutChangesDto(
    @SerializedName("updated")
    val updated: List<CatalogLayoutSnapshotDto> = emptyList(),
    @SerializedName("deleted")
    val deleted: List<Long> = emptyList()
)

data class CatalogCategoryChangesDto(
    @SerializedName("updated")
    val updated: List<CatalogCategoryDto> = emptyList(),
    @SerializedName("deleted")
    val deleted: List<Long> = emptyList()
)

data class CatalogProductChangesDto(
    @SerializedName("updated")
    val updated: List<CatalogProductDto> = emptyList(),
    @SerializedName("deleted")
    val deleted: List<Long> = emptyList()
)

data class CatalogLayoutSnapshotDto(
    @SerializedName("id")
    val id: Long,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    @SerializedName("zones")
    val zones: List<ZoneDto> = emptyList(),
    @SerializedName("tables")
    val tables: List<LayoutTableDto> = emptyList()
)
