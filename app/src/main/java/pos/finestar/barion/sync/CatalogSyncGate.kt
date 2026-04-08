package pos.finestar.barion.sync

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class CatalogSyncGate {
    data class State(
        val isSyncRunning: Boolean,
        val pendingRefresh: Boolean
    )

    private val isRunning = AtomicBoolean(false)
    private val pending = AtomicBoolean(false)

    suspend fun requestSync(block: suspend () -> Unit) {
        if (!isRunning.compareAndSet(false, true)) {
            pending.set(true)
            logDebug("requestSync queued pendingRefresh=true")
            return
        }

        try {
            do {
                pending.set(false)
                logDebug("requestSync executing block")
                block()
            } while (pending.getAndSet(false).also { queuedAgain ->
                    if (queuedAgain) logDebug("requestSync detected queued refresh, running follow-up")
                })
        } finally {
            isRunning.set(false)
            logDebug("requestSync finished")
        }
    }

    fun state(): State = State(
        isSyncRunning = isRunning.get(),
        pendingRefresh = pending.get()
    )

    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    companion object {
        private const val TAG: String = "CatalogSyncGate"
    }
}
