package pos.finestar.barion.data.repo

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import pos.finestar.barion.api.PosApi
import pos.finestar.barion.api.model.CheckDto
import pos.finestar.barion.api.model.CreateCheckRequestDto
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.model.TableStatus
import pos.finestar.barion.domain.repo.CheckRepository

@Singleton
class RemoteCheckRepository @Inject constructor(
    private val api: PosApi
) : CheckRepository {

    private val checkCache = ConcurrentHashMap<Long, CheckSession>()

    override suspend fun createCheck(tableId: Long): CheckSession {
        val response = api.createCheck(CreateCheckRequestDto(tableId))
        return response.check.toDomain().also { cache(it) }
    }

    override suspend fun getOpenCheckByTable(tableId: Long): CheckSession {
        val check = api.getOpenCheckByTable(tableId).toDomain()
        cache(check)
        return check
    }

    override suspend fun getCheck(checkId: Long): CheckSession? {
        return runCatching {
            val payload = api.getCheckItems(checkId)
            CheckItemsMapper.toCheckSession(payload)
        }.onSuccess { cache(it) }
            .getOrElse { checkCache[checkId] }
    }

    private fun cache(check: CheckSession) {
        checkCache[check.checkId] = check
    }

    private fun CheckDto.toDomain(): CheckSession {
        val mappedStatus = if (status == "FREE") TableStatus.FREE else TableStatus.OPEN
        return CheckSession(
            checkId = id,
            tableId = tableId,
            tableName = "Sto $tableId",
            status = mappedStatus,
            items = emptyList()
        )
    }
}
