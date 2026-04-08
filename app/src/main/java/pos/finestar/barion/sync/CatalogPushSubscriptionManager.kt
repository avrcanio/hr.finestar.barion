package pos.finestar.barion.sync

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import pos.finestar.barion.BuildConfig
import pos.finestar.barion.data.auth.SessionStore

@Singleton
class CatalogPushSubscriptionManager @Inject constructor(
    private val sessionStore: SessionStore
) {
    suspend fun ensureSubscribedIfEnabled() {
        if (!BuildConfig.CATALOG_FCM_ENABLED) {
            Log.d(TAG, "ensureSubscribedIfEnabled skipped: feature flag disabled")
            return
        }
        val token = sessionStore.currentToken()
        if (token.isNullOrBlank()) {
            Log.d(TAG, "ensureSubscribedIfEnabled skipped: no session token")
            return
        }

        runCatching {
            FirebaseMessaging.getInstance().token.await()
        }.onSuccess { fcmToken ->
            val preview = fcmToken.take(12)
            Log.d(
                TAG,
                "fcm token acquired len=${fcmToken.length} preview=${preview}..."
            )
        }.onFailure {
            Log.w(TAG, "fcm token fetch failed msg=${it.message}", it)
        }

        runCatching {
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_BARION_CATALOG).await()
        }.onSuccess {
            Log.d(TAG, "subscribed topic=$TOPIC_BARION_CATALOG")
        }.onFailure {
            Log.w(TAG, "subscribe failed topic=$TOPIC_BARION_CATALOG msg=${it.message}", it)
        }
    }

    suspend fun unsubscribeIfEnabled() {
        if (!BuildConfig.CATALOG_FCM_ENABLED) {
            Log.d(TAG, "unsubscribeIfEnabled skipped: feature flag disabled")
            return
        }
        runCatching {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_BARION_CATALOG).await()
        }.onSuccess {
            Log.d(TAG, "unsubscribed topic=$TOPIC_BARION_CATALOG")
        }.onFailure {
            Log.w(TAG, "unsubscribe failed topic=$TOPIC_BARION_CATALOG msg=${it.message}", it)
        }
    }

    companion object {
        private const val TAG: String = "CatalogPushSub"
        const val TOPIC_BARION_CATALOG: String = "barion_catalog"
    }
}
