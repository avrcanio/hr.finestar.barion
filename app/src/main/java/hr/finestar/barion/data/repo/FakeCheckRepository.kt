package hr.finestar.barion.data.repo

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import hr.finestar.barion.domain.model.CheckItem
import hr.finestar.barion.domain.model.CheckRoundState
import hr.finestar.barion.domain.model.CheckRoundStateItem
import hr.finestar.barion.domain.model.CheckSession
import hr.finestar.barion.domain.model.FiscalizeReceiptResult
import hr.finestar.barion.domain.model.SettlementMethod
import hr.finestar.barion.domain.model.SettlementPart
import hr.finestar.barion.domain.model.SettlementPartStatus
import hr.finestar.barion.domain.model.SettlementPrepareResult
import hr.finestar.barion.domain.model.SettlementReceipt
import hr.finestar.barion.domain.model.SettlementState
import hr.finestar.barion.domain.model.SettlementSelection
import hr.finestar.barion.domain.model.SelectedModifier
import hr.finestar.barion.domain.model.TableStatus
import hr.finestar.barion.domain.repo.CheckRepository

@Singleton
class FakeCheckRepository @Inject constructor() : CheckRepository {
    private val checkIdGenerator = AtomicLong(3000L)
    private val settlementIdGenerator = AtomicLong(10_000L)
    private val checksByTableId = mutableMapOf<Long, CheckSession>()
    private val itemIdGenerator = AtomicLong(1L)
    private val receiptIdGenerator = AtomicLong(50_000L)
    private val settlementPartsByCheck = mutableMapOf<Long, MutableMap<Long, SettlementPart>>()

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
        productName: String?,
        modifiers: List<SelectedModifier>,
        note: String?
    ): CheckSession {
        val current = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")
        val displayLines = buildList {
            modifiers.forEach { modifier ->
                val suffix = modifier.quantity?.takeIf { it > 0 }?.let { " x$it" }.orEmpty()
                add("• ${modifier.type.name.lowercase()} #${modifier.id}$suffix")
            }
            note?.trim()?.takeIf { it.isNotBlank() }?.let { add("• Napomena: $it") }
        }
        val added = CheckItem(
            itemId = itemIdGenerator.getAndIncrement(),
            productId = productId,
            name = productName ?: "Product $productId",
            qty = qty,
            price = unitPrice,
            note = note,
            displayLines = displayLines
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

    override suspend fun prepareSettlementPart(
        checkId: Long,
        selections: List<SettlementSelection>,
        amount: Double?,
        method: SettlementMethod,
        tipAmount: Double,
        remainingTotal: Double?
    ): SettlementPrepareResult {
        val current = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")
        require(selections.isNotEmpty()) { "Odaberite barem jednu stavku." }
        val selectedByItemId = selections.associateBy { it.checkItemId }
        val calculatedAmount = current.items.asSequence()
            .filter { it.itemId != null && it.itemId in selectedByItemId.keys }
            .sumOf { item ->
                val selectedQty = selectedByItemId[item.itemId]?.qty ?: 0
                item.price * selectedQty
            }
        val resolvedAmount = amount ?: calculatedAmount
        val created = SettlementPart(
            partId = settlementIdGenerator.incrementAndGet(),
            status = SettlementPartStatus.PREPARED,
            amount = resolvedAmount,
            tipAmount = 0.0,
            totalCharged = resolvedAmount
        )
        val parts = settlementPartsByCheck.getOrPut(checkId) { mutableMapOf() }
        parts[created.partId] = created
        return SettlementPrepareResult(part = created)
    }

    override suspend fun paySettlementPartCash(
        checkId: Long,
        partId: Long,
        amount: Double,
        selections: List<SettlementSelection>
    ): SettlementPart {
        val part = settlementPartsByCheck[checkId]?.get(partId)
            ?: throw IllegalArgumentException("Settlement part $partId not found")
        val paid = part.copy(
            method = SettlementMethod.CASH,
            status = SettlementPartStatus.PAID,
            amount = amount,
            tipAmount = 0.0,
            totalCharged = amount,
            issuedReceiptId = receiptIdGenerator.incrementAndGet(),
            receiptPdfUrl = "https://example.local/receipt/${receiptIdGenerator.get()}.pdf"
        )
        settlementPartsByCheck[checkId]?.set(partId, paid)
        return paid
    }

    override suspend fun confirmSettlementPartCard(
        checkId: Long,
        partId: Long,
        amount: Double,
        tipAmount: Double,
        approved: Boolean,
        providerRef: String,
        clientTransactionId: String
    ): SettlementPart {
        val part = settlementPartsByCheck[checkId]?.get(partId)
            ?: throw IllegalArgumentException("Settlement part $partId not found")
        val status = if (approved) SettlementPartStatus.PAID else SettlementPartStatus.FAILED
        val updated = part.copy(
            method = SettlementMethod.CARD,
            status = status,
            amount = amount,
            tipAmount = tipAmount,
            totalCharged = amount + tipAmount,
            providerRef = providerRef
        )
        settlementPartsByCheck[checkId]?.set(partId, updated)
        return updated
    }

    override suspend fun getSettlementState(checkId: Long): SettlementState {
        val check = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")
        val latestReceipt = settlementPartsByCheck[checkId]
            ?.values
            ?.lastOrNull { it.issuedReceiptId != null }
        return SettlementState(
            checkStatus = check.status.name,
            paymentStatus = null,
            settlementStatus = null,
            remainingTotal = null,
            canIssueReceipt = null,
            issuedReceiptId = latestReceipt?.issuedReceiptId,
            receiptPdfUrl = latestReceipt?.receiptPdfUrl
        )
    }

    override suspend fun getCheckRoundState(checkId: Long): CheckRoundState {
        val check = checksByTableId.values.firstOrNull { it.checkId == checkId }
            ?: throw IllegalArgumentException("Check $checkId not found")
        val items = check.items.map { item ->
            CheckRoundStateItem(
                id = item.itemId ?: 0L,
                checkId = checkId,
                artiklId = item.productId,
                name = item.name,
                roundNumber = item.roundNumber,
                sourceQuantity = kotlin.math.abs(item.qty),
                soldQuantity = 0.0,
                stornoQuantity = 0.0,
                gratisQuantity = 0.0,
                otpisQuantity = 0.0,
                remainingQuantity = kotlin.math.abs(item.qty),
                strikeMain = false,
                paidLine = null
            )
        }
        return CheckRoundState(
            checkId = checkId,
            status = check.status.name,
            items = items,
            updatedAt = null
        )
    }

    override suspend fun fiscalizeReceipt(checkId: Long, receiptId: Long): FiscalizeReceiptResult {
        return FiscalizeReceiptResult(
            checkId = checkId,
            receiptId = receiptId,
            action = "fiscalized",
            status = "fiscalized",
            receiptNumber = receiptId.toInt(),
            totalAmount = null,
            zki = "fake-zki",
            jir = "fake-jir",
            qr = null,
            pdfUrl = "https://example.local/receipt/$receiptId.pdf",
            receipts = listOf(
                SettlementReceipt(
                    id = receiptId,
                    receiptNumber = receiptId.toInt(),
                    totalAmount = null,
                    status = "fiscalized",
                    pdfUrl = "https://example.local/receipt/$receiptId.pdf"
                )
            )
        )
    }
}
