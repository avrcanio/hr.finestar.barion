package hr.finestar.barion.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ApiCacheDao {
    @Query("SELECT * FROM api_cache WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): ApiCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ApiCacheEntity)

    @Query("DELETE FROM api_cache WHERE updatedAtMillis < :thresholdMillis")
    suspend fun deleteOlderThan(thresholdMillis: Long)
}
