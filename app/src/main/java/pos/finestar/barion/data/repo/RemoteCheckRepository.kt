package pos.finestar.barion.data.repo

import com.google.gson.Gson
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.ResponseBody
import pos.finestar.barion.api.PosApi
import pos.finestar.barion.api.model.AddCheckItemRequestDto
import pos.finestar.barion.api.model.ApiErrorDto
import pos.finestar.barion.api.model.CheckDto
import pos.finestar.barion.api.model.CreateCheckRequestDto
import pos.finestar.barion.api.model.IssueReceiptRequestDto
import pos.finestar.barion.api.model.UpdateCheckItemQtyRequestDto
import pos.finestar.barion.domain.model.CheckItem
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.model.TableStatus
import pos.finestar.barion.domain.repo.CheckRepository
import retrofit2.HttpException

@Singleton
class RemoteCheckRepository @Inject constructor(
    private val api: PosApi,
    private val gson: Gson
) : CheckRepository {

    private val checkCache = ConcurrentHashMap<Long, CheckSession>()

    override suspend fun createCheck(tableId: Long): CheckSession {
        val response = try {
            api.createCheck(CreateCheckRequestDto(tableId = tableId))
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Kreiranje racuna nije uspjelo.")
        }
        return response.check.toDomain().also { cache(it) }
    }

    override suspend fun getOpenCheckByTable(tableId: Long): CheckSession {
        val check = try {
            api.getOpenCheckByTable(tableId).toDomain()
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Otvaranje postojeceg racuna nije uspjelo.")
        }
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

    override suspend fun addItem(checkId: Long, productId: Long, qty: Int): CheckSession {
        api.addCheckItem(checkId = checkId, request = AddCheckItemRequestDto(productId = productId, qty = qty))
        return requireNotNull(getCheck(checkId)) { "Check $checkId not found after add item." }
    }

    override suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession {
        api.updateCheckItemQty(itemId = itemId, request = UpdateCheckItemQtyRequestDto(qty = qty))
        return requireNotNull(getCheck(checkId)) { "Check $checkId not found after update item." }
    }

    override suspend fun removeItem(checkId: Long, itemId: Long): CheckSession {
        val response = api.deleteCheckItem(itemId = itemId)
        if (!response.isSuccessful) {
            throw IllegalStateException("Delete item failed with code ${response.code()}")
        }
        return requireNotNull(getCheck(checkId)) { "Check $checkId not found after remove item." }
    }

    override suspend fun issueReceipt(checkId: Long, fiscalize: Boolean): CheckSession? {
        val payload = api.issueReceipt(
            checkId = checkId,
            request = IssueReceiptRequestDto(fiscalize = fiscalize)
        )
        val mapped = runCatching { CheckItemsMapper.toCheckSession(payload) }.getOrNull()
        if (mapped != null) {
            cache(mapped)
            return mapped
        }
        return getCheck(checkId)
    }

    private fun cache(check: CheckSession) {
        checkCache[check.checkId] = check
    }

    private fun mapHttpException(httpException: HttpException, defaultMessage: String): IllegalStateException {
        val detail = parseErrorDetail(httpException.response()?.errorBody())
        val message = detail ?: when (val code = httpException.code()) {
            in 400..499 -> "Zahtjev je odbijen (HTTP $code)."
            in 500..599 -> "Serverska greska (HTTP $code)."
            else -> defaultMessage
        }
        return IllegalStateException(message)
    }

    private fun parseErrorDetail(errorBody: ResponseBody?): String? {
        val raw = errorBody?.string().orEmpty()
        if (raw.isBlank()) return null

        return runCatching {
            gson.fromJson(raw, ApiErrorDto::class.java).detail
        }.getOrNull()
    }

    private fun CheckDto.toDomain(): CheckSession {
        val mappedStatus = if (status == "FREE") TableStatus.FREE else TableStatus.OPEN
        return CheckSession(
            checkId = id,
            tableId = tableId,
            tableName = "Sto $tableId",
            status = mappedStatus,
            items = listOf(
                CheckItem(name = "Dummy stavka", qty = 1, price = 5.0)
            )
        )
    }
}
