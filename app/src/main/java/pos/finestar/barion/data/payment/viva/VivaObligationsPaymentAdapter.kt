package pos.finestar.barion.data.payment.viva

import android.util.Log
import com.google.gson.JsonObject
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import pos.finestar.barion.BuildConfig

internal object VivaObligationsMapper {
    fun buildRequest(
        saleRequest: VivaSaleRequest,
        sourceCode: String,
        merchantId: String,
        personId: String,
        walletId: String
    ): JsonObject {
        val resolvedPersonId = personId.ifBlank { merchantId }
        return JsonObject().apply {
            addProperty("amount", saleRequest.amountDecimal())
            if (resolvedPersonId.isNotBlank()) addProperty("personId", resolvedPersonId)
            addProperty("walletId", walletId)
            addProperty("description", "clientTxn:${saleRequest.clientTransactionId}")
            addProperty("currencyCode", saleRequest.currency.currencyCode())
            if (sourceCode.isNotBlank()) addProperty("sourceCode", sourceCode)
        }
    }

    fun parseResult(responseBody: JsonObject?, clientTransactionId: String): VivaSaleResult {
        val status = responseBody.string("status", "state")?.uppercase()
        val approved = responseBody.boolean("approved")
            ?: when (status) {
                "APPROVED", "SUCCESS", "COMPLETED", "ACCEPTED" -> true
                "DECLINED", "FAILED", "REJECTED", "CANCELLED" -> false
                else -> true
            }
        val providerRef = responseBody.string("providerRef", "rrn", "reference", "obligationId", "id")
            ?: "viva-obligation-$clientTransactionId"
        val externalTxn = responseBody.string("externalTransactionId", "transactionId", "orderCode", "obligationId", "id")
        return VivaSaleResult(
            approved = approved,
            providerRef = providerRef,
            externalTransactionId = externalTxn,
            errorCode = responseBody.string("errorCode", "code"),
            errorMessage = responseBody.string("errorMessage", "message"),
            clientTransactionId = clientTransactionId
        )
    }
}

@Singleton
class VivaObligationsPaymentAdapter @Inject constructor(
    private val api: VivaObligationsApi
) : VivaPaymentAdapter {
    private companion object {
        const val TAG = "VivaObligationsAdapter"
    }

    override suspend fun sale(request: VivaSaleRequest): VivaSaleResult {
        val payload = VivaObligationsMapper.buildRequest(
            saleRequest = request,
            sourceCode = BuildConfig.VIVA_OBLIGATIONS_SOURCE_CODE,
            merchantId = BuildConfig.VIVA_OBLIGATIONS_MERCHANT_ID,
            personId = BuildConfig.VIVA_OBLIGATIONS_PERSON_ID,
            walletId = BuildConfig.VIVA_OBLIGATIONS_WALLET_ID
        )
        val headers = buildHeaders()
        Log.i(
            TAG,
            "sale start clientTxn=${request.clientTransactionId} amount=${request.amountDecimal()} currency=${request.currency} currencyCode=${request.currency.currencyCode()} env=${BuildConfig.VIVA_ENV} baseUrl=${BuildConfig.VIVA_OBLIGATIONS_BASE_URL}"
        )
        val response = runCatching {
            api.createObligation(headers = headers, request = payload)
        }.getOrElse { error ->
            Log.e(
                TAG,
                "obligation call failed clientTxn=${request.clientTransactionId} message=${error.message}",
                error
            )
            throw IllegalStateException("Viva obligations call failed: ${error.message}", error)
        }
        Log.i(
            TAG,
            "obligation response clientTxn=${request.clientTransactionId} http=${response.code()} successful=${response.isSuccessful}"
        )
        if (!response.isSuccessful) {
            val bodyMessage = response.errorBody()?.string()?.take(500)
            throw IllegalStateException(
                "Viva obligations HTTP ${response.code()} ${response.message()} ${bodyMessage ?: ""}".trim()
            )
        }
        val result = VivaObligationsMapper.parseResult(response.body(), request.clientTransactionId)
        Log.i(
            TAG,
            "parsed result clientTxn=${request.clientTransactionId} approved=${result.approved} providerRef=${result.providerRef} externalTxn=${result.externalTransactionId}"
        )
        return result
    }

    private fun buildHeaders(): Map<String, String> {
        val headers = linkedMapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json"
        )
        val bearer = BuildConfig.VIVA_OBLIGATIONS_BEARER_TOKEN.trim()
        if (bearer.isBlank()) {
            throw IllegalStateException("Missing VIVA_OBLIGATIONS_BEARER_TOKEN for obligations flow.")
        }
        headers["Authorization"] = "Bearer $bearer"
        return headers
    }
}

private fun JsonObject?.string(vararg keys: String): String? {
    if (this == null) return null
    for (key in keys) {
        if (has(key) && !get(key).isJsonNull) {
            return get(key).asString
        }
    }
    return null
}

private fun JsonObject?.boolean(key: String): Boolean? {
    if (this == null || !has(key) || get(key).isJsonNull) return null
    val value = get(key)
    return when {
        value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> value.asBoolean
        value.isJsonPrimitive && value.asJsonPrimitive.isString -> {
            when (value.asString.trim().uppercase()) {
                "TRUE", "1", "YES", "APPROVED", "SUCCESS" -> true
                "FALSE", "0", "NO", "DECLINED", "FAILED" -> false
                else -> null
            }
        }
        else -> null
    }
}

private fun VivaSaleRequest.amountDecimal(): String {
    return BigDecimal.valueOf(amountCents).movePointLeft(2).stripTrailingZeros().toPlainString()
}

private fun String.currencyCode(): Int {
    return when (trim().uppercase()) {
        "EUR" -> 978
        "USD" -> 840
        "GBP" -> 826
        else -> 978
    }
}
