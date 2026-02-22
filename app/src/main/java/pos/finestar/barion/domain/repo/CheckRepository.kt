package pos.finestar.barion.domain.repo

import pos.finestar.barion.domain.model.CheckSession

interface CheckRepository {
    suspend fun createCheck(tableId: Long): CheckSession
    suspend fun getOpenCheckByTable(tableId: Long): CheckSession
    suspend fun getCheck(checkId: Long): CheckSession?
    suspend fun addItem(checkId: Long, productId: Long, qty: Int): CheckSession
    suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession
    suspend fun removeItem(checkId: Long, itemId: Long): CheckSession
}
