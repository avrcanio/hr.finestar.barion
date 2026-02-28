package pos.finestar.barion.data.di

import org.junit.Assert.assertSame
import org.junit.Test
import pos.finestar.barion.data.payment.viva.VivaPaymentAdapter
import pos.finestar.barion.data.payment.viva.VivaSaleRequest
import pos.finestar.barion.data.payment.viva.VivaSaleResult

class PaymentModuleTest {

    private val app2App = FakeAdapter("app2app")
    private val obligations = FakeAdapter("obligations")
    private val test = FakeAdapter("test")

    @Test
    fun `selectVivaAdapter chooses APP2APP`() {
        val selected = PaymentModule.selectVivaAdapter(
            mode = "APP2APP",
            app2AppAdapter = app2App,
            obligationsAdapter = obligations,
            testAdapter = test
        )
        assertSame(app2App, selected)
    }

    @Test
    fun `selectVivaAdapter chooses OBLIGATIONS`() {
        val selected = PaymentModule.selectVivaAdapter(
            mode = "OBLIGATIONS",
            app2AppAdapter = app2App,
            obligationsAdapter = obligations,
            testAdapter = test
        )
        assertSame(obligations, selected)
    }

    @Test
    fun `selectVivaAdapter falls back to TEST`() {
        val selected = PaymentModule.selectVivaAdapter(
            mode = "UNKNOWN",
            app2AppAdapter = app2App,
            obligationsAdapter = obligations,
            testAdapter = test
        )
        assertSame(test, selected)
    }

    private class FakeAdapter(private val name: String) : VivaPaymentAdapter {
        override suspend fun sale(request: VivaSaleRequest): VivaSaleResult {
            return VivaSaleResult(approved = true, providerRef = name)
        }
    }
}
