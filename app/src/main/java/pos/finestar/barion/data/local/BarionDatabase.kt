package pos.finestar.barion.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ApiCacheEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BarionDatabase : RoomDatabase() {
    abstract fun apiCacheDao(): ApiCacheDao

    companion object {
        const val DB_NAME: String = "barion.db"
    }
}
