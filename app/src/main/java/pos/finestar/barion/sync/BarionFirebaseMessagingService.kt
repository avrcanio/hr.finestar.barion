package pos.finestar.barion.sync

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pos.finestar.barion.BuildConfig
import pos.finestar.barion.data.local.CatalogSyncDao
import pos.finestar.barion.data.local.CatalogSyncMetadataEntity
import pos.finestar.barion.domain.usecase.GetRuntimeModeUseCase
import pos.finestar.barion.domain.usecase.SyncCatalogUseCase

@AndroidEntryPoint
class BarionFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var syncCatalogUseCase: SyncCatalogUseCase

    @Inject
    lateinit var catalogSyncDao: CatalogSyncDao

    @Inject
    lateinit var getRuntimeModeUseCase: GetRuntimeModeUseCase

    @Inject
    lateinit var catalogPresentationEventBus: CatalogPresentationEventBus

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        if (!BuildConfig.CATALOG_FCM_ENABLED) {
            Log.d(TAG, "onMessageReceived ignored: feature flag disabled")
            return
        }

        Log.d(TAG, "onMessageReceived data=${message.data}")
        val parsed = CatalogChangedPushParser.parse(message.data)
        if (parsed == null) {
            Log.d(TAG, "onMessageReceived ignored: payload not catalog_changed")
            return
        }
        serviceScope.launch {
            val lastCatalogVersion = catalogSyncDao.getMetadata()?.lastCatalogVersion ?: 0L
            val shouldTrigger = CatalogChangedPushParser.shouldTriggerSync(parsed.catalogVersion, lastCatalogVersion)
            Log.d(
                TAG,
                "onMessageReceived parsed type=${parsed.type} remoteVersion=${parsed.catalogVersion} localVersion=$lastCatalogVersion shouldTrigger=$shouldTrigger"
            )
            if (!shouldTrigger) return@launch
            runCatching {
                Log.d(TAG, "onMessageReceived triggering syncCatalog source=fcm")
                syncCatalogUseCase(forceBootstrap = false)
                applyRuntimeModeIfChangedAfterSync()
            }.onFailure {
                Log.w(TAG, "onMessageReceived syncCatalog failed: ${it.message}", it)
            }
        }
    }

    private suspend fun applyRuntimeModeIfChangedAfterSync() {
        val metadata = catalogSyncDao.getMetadata()
        val oldModeRaw = normalizeRuntimeModeRaw(metadata?.activeModeRaw)
        val fetchedMode = getRuntimeModeUseCase(forceRefresh = true)
        val newModeRaw = normalizeRuntimeModeRaw(fetchedMode.name.lowercase())

        val modeChanged = oldModeRaw != newModeRaw
        val nextMetadata = (metadata ?: CatalogSyncMetadataEntity()).copy(
            activeModeRaw = newModeRaw,
            updatedAtMillis = System.currentTimeMillis()
        )
        catalogSyncDao.upsertMetadata(nextMetadata)

        Log.d(
            TAG,
            "applyRuntimeModeIfChangedAfterSync oldMode=$oldModeRaw newMode=$newModeRaw changed=$modeChanged"
        )
        if (!modeChanged) return

        // Runtime mode impacts product presentation; invalidate product cache only
        // (without forcing bootstrap) so screens rehydrate via existing search fallback.
        catalogSyncDao.clearProducts()
        catalogSyncDao.clearModifierCaches()
        catalogPresentationEventBus.notifyRuntimeModeChanged(newModeRaw)
        Log.d(TAG, "applyRuntimeModeIfChangedAfterSync invalidated products and emitted event")
    }

    private fun normalizeRuntimeModeRaw(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            "day" -> "day"
            "night" -> "night"
            else -> "unknown"
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG: String = "CatalogPushFCM"
    }
}
