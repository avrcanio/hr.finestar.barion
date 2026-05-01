package hr.finestar.barion.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import hr.finestar.barion.data.repo.FakeCheckRepository

class OpenOrCreateCheckFlowTest {

    @Test
    fun `create check then fetch same open check by table`() {
        runBlocking {
            val repository = FakeCheckRepository()
            val createCheck = CreateCheckForTableUseCase(repository)
            val getOpenCheck = GetOpenCheckForTableUseCase(repository)

            val created = createCheck(7L)
            val opened = getOpenCheck(7L)

            assertTrue(created.checkId > 3000L)
            assertEquals(created.checkId, opened.checkId)
            assertEquals(7L, opened.tableId)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `get open check fails when table has no check`() {
        runBlocking {
            val repository = FakeCheckRepository()
            val getOpenCheck = GetOpenCheckForTableUseCase(repository)

            getOpenCheck(999L)
        }
    }
}
