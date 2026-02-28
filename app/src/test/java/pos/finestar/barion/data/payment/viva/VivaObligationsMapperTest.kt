package pos.finestar.barion.data.payment.viva

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VivaObligationsMapperTest {

    @Test
    fun `buildRequest includes required fields`() {
        val request = VivaObligationsMapper.buildRequest(
            saleRequest = VivaSaleRequest(
                amountCents = 10500,
                tipAmountCents = 0,
                clientTransactionId = "check-1-part-1"
            ),
            sourceCode = "1234",
            merchantId = "merchant-1",
            personId = "person-1",
            walletId = "wallet-1"
        )

        assertEquals("105", request.get("amount").asString)
        assertEquals("person-1", request.get("personId").asString)
        assertEquals("wallet-1", request.get("walletId").asString)
        assertEquals("clientTxn:check-1-part-1", request.get("description").asString)
        assertEquals(978, request.get("currencyCode").asInt)
        assertEquals("1234", request.get("sourceCode").asString)
    }

    @Test
    fun `parseResult maps approved response`() {
        val body = JsonObject().apply {
            addProperty("status", "APPROVED")
            addProperty("rrn", "rrn-123")
            addProperty("transactionId", "txn-321")
        }

        val result = VivaObligationsMapper.parseResult(
            responseBody = body,
            clientTransactionId = "check-1-part-1"
        )

        assertTrue(result.approved)
        assertEquals("rrn-123", result.providerRef)
        assertEquals("txn-321", result.externalTransactionId)
    }

    @Test
    fun `parseResult maps declined response`() {
        val body = JsonObject().apply {
            addProperty("approved", false)
            addProperty("errorCode", "DECLINED")
            addProperty("message", "Card declined")
        }

        val result = VivaObligationsMapper.parseResult(
            responseBody = body,
            clientTransactionId = "check-1-part-1"
        )

        assertFalse(result.approved)
        assertEquals("DECLINED", result.errorCode)
        assertEquals("Card declined", result.errorMessage)
        assertEquals("viva-obligation-check-1-part-1", result.providerRef)
    }
}
