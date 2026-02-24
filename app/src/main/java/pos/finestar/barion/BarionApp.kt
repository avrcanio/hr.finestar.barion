package pos.finestar.barion

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import pos.finestar.barion.data.local.ApiCacheCleanupWorker

@HiltAndroidApp
class BarionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleApiCacheCleanup()
    }

    private fun scheduleApiCacheCleanup() {
        val request = PeriodicWorkRequestBuilder<ApiCacheCleanupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(12, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ApiCacheCleanupWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
