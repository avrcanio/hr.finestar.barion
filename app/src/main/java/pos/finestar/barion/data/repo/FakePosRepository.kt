package pos.finestar.barion.data.repo

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import pos.finestar.barion.domain.model.CheckItem
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.model.FloorTable
import pos.finestar.barion.domain.model.TableStatus
import pos.finestar.barion.domain.repo.CheckRepository
import pos.finestar.barion.domain.repo.FloorPlanRepository

@Singleton
class FakePosRepository @Inject constructor() : FloorPlanRepository, CheckRepository {
    private val checkIdGenerator = AtomicLong(3000L)

    private val tables = mutableListOf(
        FloorTable(1, "Sto 1", 80f, 120f, 180f, 140f, TableStatus.FREE),
        FloorTable(2, "Sto 2", 320f, 120f, 180f, 140f, TableStatus.OPEN, openCheckId = 2001L),
        FloorTable(3, "Sto 3", 560f, 120f, 180f, 140f, TableStatus.FREE),
        FloorTable(4, "Sto 4", 80f, 340f, 220f, 160f, TableStatus.OPEN, openCheckId = 2002L),
        FloorTable(5, "Sto 5", 360f, 360f, 220f, 160f, TableStatus.FREE),
        FloorTable(6, "Sto 6", 660f, 360f, 220f, 160f, TableStatus.FREE)
    )

    private val checksById = mutableMapOf(
        2001L to CheckSession(
            checkId = 2001L,
            tableId = 2,
            tableName = "Sto 2",
            status = TableStatus.OPEN,
            items = listOf(
                CheckItem("Espresso", 2, 2.50),
                CheckItem("Voda", 1, 1.20)
            )
        ),
        2002L to CheckSession(
            checkId = 2002L,
            tableId = 4,
            tableName = "Sto 4",
            status = TableStatus.OPEN,
            items = listOf(
                CheckItem("Gin tonic", 3, 7.50)
            )
        )
    )

    override suspend fun getTables(): List<FloorTable> = tables.toList()

    override suspend fun openOrCreateCheck(tableId: Long): CheckSession {
        val index = tables.indexOfFirst { it.id == tableId }
        require(index >= 0) { "Table $tableId not found" }

        val table = tables[index]
        val existingCheckId = table.openCheckId

        if (existingCheckId != null) {
            return checksById.getValue(existingCheckId)
        }

        val newCheckId = checkIdGenerator.incrementAndGet()
        val newCheck = CheckSession(
            checkId = newCheckId,
            tableId = table.id,
            tableName = table.name,
            status = TableStatus.OPEN,
            items = listOf(
                CheckItem("Welcome item", 1, 0.00)
            )
        )

        checksById[newCheckId] = newCheck
        tables[index] = table.copy(status = TableStatus.OPEN, openCheckId = newCheckId)
        return newCheck
    }

    override suspend fun getCheck(checkId: Long): CheckSession? = checksById[checkId]
}
