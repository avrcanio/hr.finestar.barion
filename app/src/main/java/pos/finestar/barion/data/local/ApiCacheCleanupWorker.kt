package pos.finestar.barion.data.local

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ApiCacheCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val db = Room.databaseBuilder(
                applicationContext,
                BarionDatabase::class.java,
                BarionDatabase.DB_NAME
            ).build()
            val threshold = System.currentTimeMillis() - RETENTION_MILLIS
            db.apiCacheDao().deleteOlderThan(threshold)
            db.close()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME: String = "api_cache_cleanup_periodic"
        private const val RETENTION_MILLIS: Long = 3L * 24L * 60L * 60L * 1000L // 3 days
    }
}
