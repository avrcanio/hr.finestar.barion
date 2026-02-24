package pos.finestar.barion.domain.repo

import pos.finestar.barion.domain.model.CheckSession

interface CheckRepository {
    suspend fun createCheck(tableId: Long): CheckSession
    suspend fun getOpenCheckByTable(tableId: Long): CheckSession
    suspend fun getCheck(checkId: Long): CheckSession?
    suspend fun addItem(
        checkId: Long,
        productId: Long,
        qty: Int,
        unitPrice: Double,
        productName: String? = null
    ): CheckSession
    suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession
    suspend fun removeItem(checkId: Long, itemId: Long): CheckSession
    suspend fun stornoItem(checkId: Long, itemId: Long, reason: String? = null, qty: Int? = null): CheckSession
    suspend fun gratisItem(checkId: Long, itemId: Long, reason: String? = null, qty: Int? = null): CheckSession
    suspend fun otpisItem(checkId: Long, itemId: Long, reason: String? = null, qty: Int? = null): CheckSession
    suspend fun sendToBar(checkId: Long): CheckSession
    suspend fun closeCheck(checkId: Long): CheckSession?
    suspend fun issueReceipt(checkId: Long, fiscalize: Boolean = true): CheckSession?
}
