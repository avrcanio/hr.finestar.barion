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

    private val tables = listOf(
        FloorTable(1, "Sto 1", 80f, 120f, 180f, 140f, TableStatus.FREE),
        FloorTable(2, "Sto 2", 320f, 120f, 180f, 140f, TableStatus.OPEN),
        FloorTable(3, "Sto 3", 560f, 120f, 180f, 140f, TableStatus.FREE),
        FloorTable(4, "Sto 4", 80f, 340f, 220f, 160f, TableStatus.OPEN),
        FloorTable(5, "Sto 5", 360f, 360f, 220f, 160f, TableStatus.FREE),
        FloorTable(6, "Sto 6", 660f, 360f, 220f, 160f, TableStatus.FREE)
    )

    override suspend fun getTables(): List<FloorTable> = tables

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
