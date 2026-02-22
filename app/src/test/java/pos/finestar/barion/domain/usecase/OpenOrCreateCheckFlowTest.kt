package pos.finestar.barion.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pos.finestar.barion.data.repo.FakePosRepository
import pos.finestar.barion.domain.model.TableStatus

class OpenOrCreateCheckFlowTest {

    @Test
    fun `returns existing open check for table that already has one`() {
        runBlocking {
            val repository = FakePosRepository()
            val openOrCreateCheck = OpenOrCreateCheckForTableUseCase(repository)

            val check = openOrCreateCheck(2L)

            assertEquals(2001L, check.checkId)
            assertEquals(2L, check.tableId)
            assertEquals(TableStatus.OPEN, check.status)
            assertTrue(check.items.isNotEmpty())
        }
    }

    @Test
    fun `creates new check for free table and then reuses same check id`() {
        runBlocking {
            val repository = FakePosRepository()
            val openOrCreateCheck = OpenOrCreateCheckForTableUseCase(repository)
            val getCheckById = GetCheckByIdUseCase(repository)
            val getFloorTables = GetFloorTablesUseCase(repository)

            val created = openOrCreateCheck(1L)
            val openedAgain = openOrCreateCheck(1L)
            val persisted = getCheckById(created.checkId)
            val table = getFloorTables().first { it.id == 1L }

            assertTrue(created.checkId > 3000L)
            assertEquals(created.checkId, openedAgain.checkId)
            assertNotNull(persisted)
            assertEquals(TableStatus.OPEN, table.status)
            assertEquals(created.checkId, table.openCheckId)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws for unknown table`() {
        runBlocking {
            val repository = FakePosRepository()
            val openOrCreateCheck = OpenOrCreateCheckForTableUseCase(repository)

            openOrCreateCheck(999L)
        }
    }
}
