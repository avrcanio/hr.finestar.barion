package pos.finestar.barion.data.repo

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import pos.finestar.barion.domain.model.CheckItem
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.model.TableStatus
import pos.finestar.barion.domain.repo.CheckRepository

@Singleton
class FakeCheckRepository @Inject constructor() : CheckRepository {
    private val checkIdGenerator = AtomicLong(3000L)
    private val checksByTableId = mutableMapOf<Long, CheckSession>()

    override suspend fun createCheck(tableId: Long): CheckSession {
        checksByTableId[tableId]?.let { return it }

        val newCheck = CheckSession(
            checkId = checkIdGenerator.incrementAndGet(),
            tableId = tableId,
            tableName = "Sto $tableId",
            status = TableStatus.OPEN,
            items = listOf(CheckItem("Welcome item", 1, 0.0))
        )

        checksByTableId[tableId] = newCheck
        return newCheck
    }

    override suspend fun getOpenCheckByTable(tableId: Long): CheckSession {
        return checksByTableId[tableId]
            ?: throw IllegalArgumentException("Open check for table $tableId not found")
    }

    override suspend fun getCheck(checkId: Long): CheckSession? {
        return checksByTableId.values.firstOrNull { it.checkId == checkId }
    }
}
