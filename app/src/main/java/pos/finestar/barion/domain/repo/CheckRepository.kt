package pos.finestar.barion.domain.repo

import pos.finestar.barion.domain.model.CheckSession

interface CheckRepository {
    suspend fun createCheck(tableId: Long): CheckSession
    suspend fun getOpenCheckByTable(tableId: Long): CheckSession
    suspend fun getCheck(checkId: Long): CheckSession?
}
