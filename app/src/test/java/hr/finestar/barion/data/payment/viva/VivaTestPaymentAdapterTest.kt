package hr.finestar.barion.data.payment.viva

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VivaTestPaymentAdapterTest {

    @Test
    fun `sale approves when all env-backed values are present`() {
        val adapter = VivaTestPaymentAdapter(
            secretsProvider = object : VivaSecretsProvider {
                override fun merchantId(): String = "merchant"
                override fun apiKey(): String = "api-key"
                override fun posClientId(): String = "client-id"
                override fun posClientSecret(): String = "client-secret"
            }
        )

        val result = kotlinx.coroutines.runBlocking {
            adapter.sale(
                VivaSaleRequest(
                    amountCents = 180,
                    tipAmountCents = 20,
                    clientTransactionId = "check-118-part-145-1"
                )
            )
        }

        assertTrue(result.approved)
        assertTrue(result.providerRef.startsWith("viva-test-"))
        assertEquals("check-118-part-145-1", result.externalTransactionId)
    }

    @Test
    fun `sale fails when env-backed values are missing`() {
        val adapter = VivaTestPaymentAdapter(
            secretsProvider = object : VivaSecretsProvider {
                override fun merchantId(): String? = null
                override fun apiKey(): String? = null
                override fun posClientId(): String? = null
                override fun posClientSecret(): String? = null
            }
        )

        val error = runCatching {
            kotlinx.coroutines.runBlocking {
                adapter.sale(
                    VivaSaleRequest(
                        amountCents = 180,
                        tipAmountCents = 20,
                        clientTransactionId = "check-118-part-145-1"
                    )
                )
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("Missing Viva env vars") == true)
    }
}

