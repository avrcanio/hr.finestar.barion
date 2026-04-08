package pos.finestar.barion.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogSyncDao {
    @Query("SELECT * FROM catalog_categories ORDER BY sortOrder ASC, name ASC")
    fun observeCategories(): Flow<List<CatalogCategoryEntity>>

    @Query("SELECT * FROM catalog_categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getCategories(): List<CatalogCategoryEntity>

    @Query(
        """
        SELECT * FROM catalog_products
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY popularityScore DESC, name ASC
        """
    )
    suspend fun getProductsByCategory(categoryId: Long?): List<CatalogProductEntity>

    @Query(
        """
        SELECT * FROM catalog_products
        WHERE name LIKE :pattern OR code LIKE :pattern
        ORDER BY popularityScore DESC, name ASC
        """
    )
    suspend fun searchProducts(pattern: String): List<CatalogProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<CatalogProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CatalogCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLayoutSnapshots(layouts: List<CatalogLayoutSnapshotEntity>)

    @Query("DELETE FROM catalog_products WHERE id IN (:ids)")
    suspend fun deleteProducts(ids: List<Long>)

    @Query("DELETE FROM catalog_categories WHERE id IN (:ids)")
    suspend fun deleteCategories(ids: List<Long>)

    @Query("DELETE FROM catalog_layout_snapshots WHERE id IN (:ids)")
    suspend fun deleteLayoutSnapshots(ids: List<Long>)

    @Query("DELETE FROM catalog_products")
    suspend fun clearProducts()

    @Query("DELETE FROM product_modifier_cache")
    suspend fun clearModifierCaches()

    @Query("DELETE FROM catalog_categories")
    suspend fun clearCategories()

    @Query("DELETE FROM catalog_layout_snapshots")
    suspend fun clearLayoutSnapshots()

    @Query("SELECT * FROM catalog_sync_metadata WHERE id = :id")
    suspend fun getMetadata(id: Int = CatalogSyncMetadataEntity.SINGLETON_ID): CatalogSyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: CatalogSyncMetadataEntity)

    @Query("SELECT * FROM product_modifier_cache WHERE productId = :productId")
    suspend fun getModifierCache(productId: Long): ProductModifierCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModifierCache(cache: ProductModifierCacheEntity)

    @Query("DELETE FROM product_modifier_cache WHERE productId = :productId")
    suspend fun deleteModifierCache(productId: Long)
}
