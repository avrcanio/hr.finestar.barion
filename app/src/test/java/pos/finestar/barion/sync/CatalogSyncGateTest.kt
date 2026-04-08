package pos.finestar.barion.sync

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSyncGateTest {
    @Test
    fun `concurrent requests do not run in parallel and schedule one follow-up`() = runTest {
        val gate = CatalogSyncGate()
        val runs = AtomicInteger(0)
        val entered = CompletableDeferred<Unit>()
        val unblock = CompletableDeferred<Unit>()

        val first = async {
            gate.requestSync {
                runs.incrementAndGet()
                entered.complete(Unit)
                unblock.await()
            }
        }
        val second = async {
            gate.requestSync {
                runs.incrementAndGet()
            }
        }
        val third = async {
            gate.requestSync {
                runs.incrementAndGet()
            }
        }

        entered.await()
        val stateDuringFirst = gate.state()
        assertTrue(stateDuringFirst.isSyncRunning)

        unblock.complete(Unit)
        awaitAll(first, second, third)

        assertEquals(2, runs.get())
        val finalState = gate.state()
        assertFalse(finalState.isSyncRunning)
        assertFalse(finalState.pendingRefresh)
    }
}
