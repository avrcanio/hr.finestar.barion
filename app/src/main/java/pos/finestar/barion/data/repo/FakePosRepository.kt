package pos.finestar.barion.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.model.FloorTable
import pos.finestar.barion.domain.model.TableStatus
import pos.finestar.barion.domain.repo.CheckRepository
import pos.finestar.barion.domain.repo.FloorPlanRepository

@Singleton
class FakePosRepository @Inject constructor() : FloorPlanRepository, CheckRepository {

    override suspend fun getTables(): List<FloorTable> = emptyList()

    override suspend fun openOrCreateCheck(tableId: Long): CheckSession {
        return CheckSession(
            checkId = 0L,
            tableId = tableId,
            tableName = "Table $tableId",
            status = TableStatus.OPEN,
            items = emptyList()
        )
    }

    override suspend fun getCheck(checkId: Long): CheckSession? = null
}
