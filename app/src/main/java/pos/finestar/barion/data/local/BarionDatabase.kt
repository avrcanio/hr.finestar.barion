package pos.finestar.barion.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ApiCacheEntity::class,
        CatalogProductEntity::class,
        CatalogCategoryEntity::class,
        CatalogLayoutSnapshotEntity::class,
        CatalogSyncMetadataEntity::class,
        ProductModifierCacheEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class BarionDatabase : RoomDatabase() {
    abstract fun apiCacheDao(): ApiCacheDao
    abstract fun catalogSyncDao(): CatalogSyncDao

    companion object {
        const val DB_NAME: String = "barion.db"
    }
}
