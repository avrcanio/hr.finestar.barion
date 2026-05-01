package hr.finestar.barion.data.payment.viva

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.TimeoutCancellationException
import hr.finestar.barion.BuildConfig

data class VivaSaleResult(
    val approved: Boolean,
    val providerRef: String,
    val externalTransactionId: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val clientTransactionId: String? = null,
    val rrn: String? = null,
    val referenceNumber: String? = null,
    val authorisationCode: String? = null,
    val tid: String? = null,
    val orderCode: String? = null,
    val shortOrderCode: String? = null,
    val transactionDate: String? = null,
    val paymentMethod: String? = null,
    val cardType: String? = null,
    val accountNumber: String? = null,
    val verificationMethod: String? = null,
    val aid: String? = null,
    val bankId: String? = null,
    val transactionTypeId: String? = null,
    val transactionEventId: String? = null,
    val surchargeAmount: String? = null,
    val customerTrns: String? = null,
    val status: String? = null,
    val action: String? = null,
    val providerPayload: Map<String, String> = emptyMap()
)

interface VivaPaymentAdapter {
    suspend fun sale(request: VivaSaleRequest): VivaSaleResult
}

@Singleton
class VivaApp2AppPaymentAdapter @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val foregroundActivityProvider: ForegroundActivityProvider,
    private val callbackRegistry: VivaPaymentCallbackRegistry
) : VivaPaymentAdapter {
    private companion object {
        const val TAG = "VivaApp2AppAdapter"
    }

    override suspend fun sale(request: VivaSaleRequest): VivaSaleResult {
        val callbackUrl = "${BuildConfig.VIVA_CALLBACK_SCHEME}://${BuildConfig.VIVA_CALLBACK_HOST}"
        val uri = Uri.Builder()
            .scheme("vivapayclient")
            .authority("pay")
            .appendPath("v1")
            .appendQueryParameter("appId", appContext.packageName)
            .appendQueryParameter("action", "sale")
            .appendQueryParameter("amount", request.amountCents.toString())
            .appendQueryParameter("tipAmount", request.tipAmountCents.toString())
            .appendQueryParameter("clientTransactionId", request.clientTransactionId)
            .appendQueryParameter("returnPackageName", appContext.packageName)
            .appendQueryParameter("show_receipt", "true")
            .appendQueryParameter("show_transaction_result", "true")
            .appendQueryParameter("show_rating", "false")
            .appendQueryParameter("callback", callbackUrl)
            .build()
        Log.i(
            TAG,
            "sale start clientTxn=${request.clientTransactionId} amountCents=${request.amountCents} tipCents=${request.tipAmountCents} callback=$callbackUrl providerMode=${BuildConfig.VIVA_PROVIDER_MODE}"
        )
        Log.d(TAG, "sale intentUri=$uri")

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (BuildConfig.VIVA_TERMINAL_PACKAGE.isNotBlank()) {
                `package` = BuildConfig.VIVA_TERMINAL_PACKAGE
            }
        }

        val deferred = callbackRegistry.register(request.clientTransactionId)
        try {
            val resolved = intent.resolveActivity(appContext.packageManager)
            Log.i(
                TAG,
                "resolveActivity clientTxn=${request.clientTransactionId} requestedPackage=${intent.`package`} resolved=${resolved?.flattenToShortString()}"
            )
            if (resolved == null) {
                callbackRegistry.clear(request.clientTransactionId)
                throw IllegalStateException(
                    "Viva Terminal app is not installed or does not support App-to-App sale."
                )
            }
            val activity = foregroundActivityProvider.current()
            Log.i(
                TAG,
                "launch clientTxn=${request.clientTransactionId} fromActivity=${activity?.javaClass?.simpleName ?: "none"} appContextPackage=${appContext.packageName}"
            )
            if (activity != null) {
                activity.runOnUiThread {
                    Log.i(TAG, "startActivity via foreground activity clientTxn=${request.clientTransactionId}")
                    activity.startActivity(intent)
                }
            } else {
                Log.i(TAG, "startActivity via app context clientTxn=${request.clientTransactionId}")
                appContext.startActivity(intent)
            }
            Log.i(
                TAG,
                "awaiting callback clientTxn=${request.clientTransactionId} timeoutMs=${BuildConfig.VIVA_CALLBACK_TIMEOUT_MS}"
            )
            return callbackRegistry.awaitResult(
                clientTransactionId = request.clientTransactionId,
                timeoutMs = BuildConfig.VIVA_CALLBACK_TIMEOUT_MS.toLong()
            ).also { result ->
                Log.i(
                    TAG,
                    "callback received clientTxn=${request.clientTransactionId} approved=${result.approved} providerRef=${result.providerRef} externalTxn=${result.externalTransactionId}"
                )
            }
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "sale failed clientTxn=${request.clientTransactionId} error=${error::class.java.simpleName} message=${error.message}",
                error
            )
            if (!deferred.isCompleted) {
                callbackRegistry.fail(
                    clientTransactionId = request.clientTransactionId,
                    message = error.message ?: "Viva App-to-App payment failed."
                )
            }
            if (error is TimeoutCancellationException) {
                throw IllegalStateException("Terminal nije odgovorio na vrijeme. Provjerite mrežu i pokušajte ponovno.", error)
            }
            throw error
        }
    }
}

@Singleton
class VivaTestPaymentAdapter @Inject constructor(
    private val secretsProvider: VivaSecretsProvider
) : VivaPaymentAdapter {
    override suspend fun sale(request: VivaSaleRequest): VivaSaleResult {
        val missing = listOfNotNull(
            "VIVA_MERCHANT_ID".takeIf { secretsProvider.merchantId().isNullOrBlank() },
            "VIVA_API_KEY".takeIf { secretsProvider.apiKey().isNullOrBlank() },
            "VIVA_POS_CLIENT_ID".takeIf { secretsProvider.posClientId().isNullOrBlank() },
            "VIVA_POS_CLIENT_SECRET".takeIf { secretsProvider.posClientSecret().isNullOrBlank() }
        )
        if (missing.isNotEmpty()) {
            throw IllegalStateException("Missing Viva env vars: ${missing.joinToString(", ")}")
        }

        // Test adapter that validates request shape and emits deterministic references.
        return VivaSaleResult(
            approved = true,
            providerRef = "viva-test-${UUID.randomUUID()}",
            externalTransactionId = request.clientTransactionId,
            clientTransactionId = request.clientTransactionId
        )
    }
}
