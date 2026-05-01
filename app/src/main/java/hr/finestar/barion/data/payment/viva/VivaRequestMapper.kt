package hr.finestar.barion.data.payment.viva

import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

data class VivaSaleRequest(
    val amountCents: Long,
    val tipAmountCents: Long,
    val clientTransactionId: String,
    val currency: String = "EUR"
)

@Singleton
class VivaRequestMapper @Inject constructor() {

    fun map(
        checkId: Long,
        partId: Long,
        amount: Double,
        tipAmount: Double,
        clientTransactionId: String?
    ): VivaSaleRequest {
        val amountCents = toCents(amount)
        val tipAmountCents = toCents(tipAmount.coerceAtLeast(0.0))
        val resolvedClientTransactionId = clientTransactionId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "check-$checkId-part-$partId-${System.currentTimeMillis()}"
        return VivaSaleRequest(
            amountCents = amountCents,
            tipAmountCents = tipAmountCents,
            clientTransactionId = resolvedClientTransactionId,
            currency = "EUR"
        )
    }

    private fun toCents(amount: Double): Long {
        return BigDecimal.valueOf(amount.coerceAtLeast(0.0))
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }
}

