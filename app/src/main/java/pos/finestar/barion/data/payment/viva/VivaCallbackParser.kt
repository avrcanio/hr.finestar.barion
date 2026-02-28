package pos.finestar.barion.data.payment.viva

import android.net.Uri
import android.os.Bundle
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VivaCallbackParser @Inject constructor() {
    private companion object {
        const val TAG = "VivaCallbackParser"
    }

    fun parse(uri: Uri): VivaSaleResult {
        Log.i(TAG, "parse uri=$uri")
        val raw = uri.queryParameterNames.associateWith { key ->
            uri.getQueryParameter(key).orEmpty()
        }
        return buildResult(raw)
    }

    fun parse(extras: Bundle): VivaSaleResult {
        Log.i(TAG, "parse extras keys=${extras.keySet().joinToString(",")}")
        val raw = extras.keySet().associateWith { key ->
            extras.get(key)?.toString().orEmpty()
        }
        return buildResult(raw)
    }

    private fun buildResult(raw: Map<String, String>): VivaSaleResult {
        fun pick(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
            raw[key]?.takeIf { it.isNotBlank() }
        }

        val status = pick("status", "result", "transactionResult")
        val clientTransactionId = pick("clientTransactionId", "client_transaction_id", "merchantTrns")
        val rrn = pick("rrn")
        val transactionId = pick("transactionId")
        val orderCode = pick("orderCode")
        val referenceNumber = pick("referenceNumber")
        val errorCode = pick("errorCode", "transactionEventId")
        val errorMessage = pick("errorText", "message")
        val resolvedStatus = status ?: "unknown"

        val approved = resolvedStatus.equals("success", ignoreCase = true) ||
            resolvedStatus.equals("approved", ignoreCase = true) ||
            resolvedStatus.equals("ok", ignoreCase = true)

        val providerRef = rrn
            ?: transactionId
            ?: orderCode
            ?: referenceNumber
            ?: "viva-${clientTransactionId ?: "unknown"}-$resolvedStatus"

        val externalTxnId = transactionId ?: orderCode

        val result = VivaSaleResult(
            approved = approved,
            providerRef = providerRef,
            externalTransactionId = externalTxnId,
            errorCode = errorCode ?: if (approved) null else "DECLINED",
            errorMessage = errorMessage ?: if (approved) null else "Card payment declined or cancelled.",
            clientTransactionId = clientTransactionId,
            rrn = rrn,
            referenceNumber = referenceNumber,
            authorisationCode = pick("authorisationCode"),
            tid = pick("tid"),
            orderCode = orderCode,
            shortOrderCode = pick("shortOrderCode"),
            transactionDate = pick("transactionDate"),
            paymentMethod = pick("paymentMethod"),
            cardType = pick("cardType"),
            accountNumber = pick("accountNumber"),
            verificationMethod = pick("verificationMethod"),
            aid = pick("aid"),
            bankId = pick("bankId"),
            transactionTypeId = pick("transactionTypeId"),
            transactionEventId = pick("transactionEventId"),
            surchargeAmount = pick("surchargeAmount"),
            customerTrns = pick("customerTrns"),
            status = status,
            action = pick("action"),
            providerPayload = raw.filterValues { it.isNotBlank() }
        )
        Log.i(
            TAG,
            "parse result status=$resolvedStatus approved=$approved clientTxn=$clientTransactionId providerRef=$providerRef externalTxn=$externalTxnId errorCode=${result.errorCode}"
        )
        return result
    }
}
