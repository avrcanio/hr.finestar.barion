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
    private val itemIdGenerator = AtomicLong(1L)

    override suspend fun createCheck(tableId: Long): CheckSession {
        checksByTableId[tableId]?.let { return it }

        val newCheck = CheckSession(
            checkId = checkIdGenerator.incrementAndGet(),
            tableId = tableId,
            tableName = "Sto $tableId",
            status = TableStatus.OPEN,
            items = listOf(CheckItem(itemId = itemIdGenerator.getAndIncrement(), productId = 1L, name = "Welcome item", qty = 1, price = 0.0))
        )

        checksByTableId[tableId] = newCheck
        return newCheck
    }

    override suspend fun getOpenCheckByTable(tableId: Long): CheckSession {
        return checksByTableId[tableId]
            ?: throw IllegalArgumentException("Open check for table $tableId not found")
    }

    override suspend fun getCheck(checkId: Long, forceRefresh: Boolean): CheckSession? {
        return checksByTableId.values.firstOrNull { it.checkId == checkId }
    }

    override suspend fun addItem(
        checkId: Long,
        productId: Long,
        qty: Int,
        unitPrice: Double,
        productName: String?
    ): CheckSession {
        val current = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")
        val added = CheckItem(
            itemId = itemIdGenerator.getAndIncrement(),
            productId = productId,
            name = productName ?: "Product $productId",
            qty = qty,
            price = unitPrice
        )
        val updated = current.copy(items = current.items + added)
        checksByTableId[current.tableId] = updated
        return updated
    }

    override suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession {
        val current = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")
        val updatedItems = current.items.map {
            if (it.itemId == itemId) it.copy(qty = qty) else it
        }
        val updated = current.copy(items = updatedItems)
        checksByTableId[current.tableId] = updated
        return updated
    }

    override suspend fun removeItem(checkId: Long, itemId: Long): CheckSession {
        val current = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")
        val updated = current.copy(items = current.items.filterNot { it.itemId == itemId })
        checksByTableId[current.tableId] = updated
        return updated
    }

    override suspend fun stornoItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession {
        val current = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")
        val target = current.items.firstOrNull { it.itemId == itemId }
            ?: throw IllegalArgumentException("Item $itemId not found")
        val requestedQty = (qty ?: kotlin.math.abs(target.qty)).coerceAtLeast(1).coerceAtMost(kotlin.math.abs(target.qty))
        val storno = target.copy(
            itemId = itemIdGenerator.getAndIncrement(),
            qty = -requestedQty,
            lineType = "STORNO",
            note = reason,
            sentToBar = true,
            sentAt = "n/a",
            roundNumber = null
        )
        val updated = current.copy(items = current.items + storno)
        checksByTableId[current.tableId] = updated
        return updated
    }

    override suspend fun gratisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession {
        val current = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")
        val target = current.items.firstOrNull { it.itemId == itemId }
            ?: throw IllegalArgumentException("Item $itemId not found")
        val requestedQty = (qty ?: kotlin.math.abs(target.qty)).coerceAtLeast(1).coerceAtMost(kotlin.math.abs(target.qty))
        val gratisLine = target.copy(
            itemId = itemIdGenerator.getAndIncrement(),
            qty = requestedQty,
            price = 0.0,
            lineType = "GRATIS",
            note = reason,
            sentToBar = true,
            sentAt = "n/a",
            roundNumber = null
        )
        val updated = current.copy(items = current.items + gratisLine)
        checksByTableId[current.tableId] = updated
        return updated
    }

    override suspend fun otpisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession {
        val current = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")
        val target = current.items.firstOrNull { it.itemId == itemId }
            ?: throw IllegalArgumentException("Item $itemId not found")
        val requestedQty = (qty ?: kotlin.math.abs(target.qty)).coerceAtLeast(1).coerceAtMost(kotlin.math.abs(target.qty))
        val otpisLine = target.copy(
            itemId = itemIdGenerator.getAndIncrement(),
            qty = requestedQty,
            price = 0.0,
            lineType = "OTPIS",
            note = reason,
            sentToBar = true,
            sentAt = "n/a",
            roundNumber = null
        )
        val updated = current.copy(items = current.items + otpisLine)
        checksByTableId[current.tableId] = updated
        return updated
    }

    override suspend fun sendToBar(checkId: Long): CheckSession {
        val current = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")

        val nextRound = (current.items.mapNotNull { it.roundNumber }.maxOrNull() ?: 0) + 1
        val updatedItems = current.items.map { item ->
            if (item.sentToBar) item else item.copy(
                sentToBar = true,
                roundNumber = nextRound,
                sentAt = "now"
            )
        }
        val updated = current.copy(items = updatedItems)
        checksByTableId[current.tableId] = updated
        return updated
    }

    override suspend fun closeCheck(checkId: Long): CheckSession? {
        val current = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")
        val closed = current.copy(status = TableStatus.FREE, items = emptyList())
        checksByTableId[current.tableId] = closed
        return closed
    }

    override suspend fun issueReceipt(checkId: Long, fiscalize: Boolean): CheckSession? {
        val current = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")
        val closed = current.copy(status = TableStatus.FREE, items = emptyList())
        checksByTableId[current.tableId] = closed
        return closed
    }
}
