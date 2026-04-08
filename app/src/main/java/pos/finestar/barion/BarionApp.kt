package pos.finestar.barion

import android.app.Application
import com.google.firebase.FirebaseApp
import android.util.Log
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
        val googleAppIdResId = resources.getIdentifier("google_app_id", "string", packageName)
        val hasGoogleServicesConfig = googleAppIdResId != 0
        val firebaseApps = runCatching { FirebaseApp.getApps(this).size }.getOrDefault(0)
        Log.i(
            TAG,
            "onCreate catalogFcmEnabled=${BuildConfig.CATALOG_FCM_ENABLED} apiBaseUrl=${BuildConfig.BARION_API_BASE_URL} hasGoogleServicesConfig=$hasGoogleServicesConfig firebaseApps=$firebaseApps"
        )
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

    companion object {
        private const val TAG: String = "BarionApp"
    }
}
