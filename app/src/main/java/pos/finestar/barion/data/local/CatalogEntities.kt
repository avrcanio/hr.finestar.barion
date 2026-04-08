package pos.finestar.barion.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_products")
data class CatalogProductEntity(
    @PrimaryKey
    val id: Long,
    val rmId: Long? = null,
    val name: String,
    val code: String? = null,
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

@Entity(tableName = "catalog_categories")
data class CatalogCategoryEntity(
    @PrimaryKey
    val id: Long,
    val name: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val popularityScore: Double? = null
)

@Entity(tableName = "catalog_layout_snapshots")
data class CatalogLayoutSnapshotEntity(
    @PrimaryKey
    val id: Long,
    val name: String,
    val isActive: Boolean = true,
    val updatedAt: String? = null,
    val zonesJson: String = "[]",
    val tablesJson: String = "[]"
)

@Entity(tableName = "catalog_sync_metadata")
data class CatalogSyncMetadataEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val lastCatalogVersion: Long = 0L,
    val activeModeRaw: String? = null,
    val inFlightTargetVersion: Long? = null,
    val inFlightAppliedThroughVersion: Long? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val SINGLETON_ID: Int = 1
    }
}

@Entity(tableName = "product_modifier_cache")
data class ProductModifierCacheEntity(
    @PrimaryKey
    val productId: Long,
    val modifierVersion: Long? = null,
    val payloadJson: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
