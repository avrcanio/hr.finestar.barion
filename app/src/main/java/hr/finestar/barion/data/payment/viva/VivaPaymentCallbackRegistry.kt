package hr.finestar.barion.data.payment.viva

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

@Singleton
class VivaPaymentCallbackRegistry @Inject constructor() {
    private companion object {
        const val TAG = "VivaCallbackRegistry"
    }

    private val pending = ConcurrentHashMap<String, CompletableDeferred<VivaSaleResult>>()

    fun register(clientTransactionId: String): CompletableDeferred<VivaSaleResult> {
        val deferred = CompletableDeferred<VivaSaleResult>()
        pending[clientTransactionId] = deferred
        Log.i(TAG, "register clientTxn=$clientTransactionId pendingCount=${pending.size}")
        return deferred
    }

    fun complete(result: VivaSaleResult) {
        val transactionId = result.clientTransactionId
        Log.i(
            TAG,
            "complete called clientTxn=$transactionId approved=${result.approved} providerRef=${result.providerRef} pendingCount=${pending.size}"
        )
        if (transactionId != null) {
            val completed = pending.remove(transactionId)?.complete(result) ?: false
            Log.i(TAG, "complete by clientTxn=$transactionId matched=$completed pendingCount=${pending.size}")
            return
        }
        // Fallback only when there is a single pending request.
        if (pending.size == 1) {
            val key = pending.keys.firstOrNull() ?: return
            val completed = pending.remove(key)?.complete(result) ?: false
            Log.w(TAG, "complete fallback used key=$key matched=$completed pendingCount=${pending.size}")
        } else {
            Log.w(TAG, "complete ignored: missing clientTxn and pendingCount=${pending.size}")
        }
    }

    fun fail(clientTransactionId: String, message: String) {
        Log.e(TAG, "fail clientTxn=$clientTransactionId message=$message pendingCount=${pending.size}")
        val completed = pending.remove(clientTransactionId)?.completeExceptionally(
            IllegalStateException(message)
        ) ?: false
        Log.e(TAG, "fail delivered clientTxn=$clientTransactionId delivered=$completed pendingCount=${pending.size}")
    }

    fun clear(clientTransactionId: String) {
        pending.remove(clientTransactionId)
        Log.i(TAG, "clear clientTxn=$clientTransactionId pendingCount=${pending.size}")
    }

    suspend fun awaitResult(
        clientTransactionId: String,
        timeoutMs: Long
    ): VivaSaleResult {
        Log.i(TAG, "awaitResult start clientTxn=$clientTransactionId timeoutMs=$timeoutMs pendingCount=${pending.size}")
        val deferred = pending[clientTransactionId]
            ?: throw IllegalStateException("No pending Viva request for clientTransactionId=$clientTransactionId")
        return try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "awaitResult failed clientTxn=$clientTransactionId error=${error::class.java.simpleName} message=${error.message}",
                error
            )
            throw error
        } finally {
            pending.remove(clientTransactionId)
            Log.i(TAG, "awaitResult end clientTxn=$clientTransactionId pendingCount=${pending.size}")
        }
    }
}
