package hr.finestar.barion.data.payment.viva

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VivaRequestMapperTest {

    private val mapper = VivaRequestMapper()

    @Test
    fun `maps decimals to cents with half-up rounding`() {
        val mapped = mapper.map(
            checkId = 118L,
            partId = 145L,
            amount = 1.805,
            tipAmount = 0.105,
            clientTransactionId = "txn-1"
        )

        assertEquals(181L, mapped.amountCents)
        assertEquals(11L, mapped.tipAmountCents)
        assertEquals("txn-1", mapped.clientTransactionId)
        assertEquals("EUR", mapped.currency)
    }

    @Test
    fun `clamps negative amounts to zero`() {
        val mapped = mapper.map(
            checkId = 118L,
            partId = 145L,
            amount = -1.0,
            tipAmount = -0.5,
            clientTransactionId = "txn-2"
        )

        assertEquals(0L, mapped.amountCents)
        assertEquals(0L, mapped.tipAmountCents)
    }

    @Test
    fun `generates client transaction id when not provided`() {
        val mapped = mapper.map(
            checkId = 118L,
            partId = 145L,
            amount = 1.8,
            tipAmount = 0.0,
            clientTransactionId = "  "
        )

        assertTrue(mapped.clientTransactionId.startsWith("check-118-part-145-"))
    }
}

