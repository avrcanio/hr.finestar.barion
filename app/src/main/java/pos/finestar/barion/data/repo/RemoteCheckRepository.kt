package pos.finestar.barion.data.repo

import com.google.gson.Gson
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.ResponseBody
import pos.finestar.barion.api.PosApi
import pos.finestar.barion.api.model.AddCheckItemRequestDto
import pos.finestar.barion.api.model.ApiErrorDto
import pos.finestar.barion.api.model.CheckDto
import pos.finestar.barion.api.model.CheckItemActionRequestDto
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

    override suspend fun addItem(
        checkId: Long,
        productId: Long,
        qty: Int,
        unitPrice: Double,
        productName: String?
    ): CheckSession {
        try {
            api.addCheckItem(
                checkId = checkId,
                request = AddCheckItemRequestDto(
                    artiklId = productId,
                    artiklName = productName,
                    productId = productId,
                    productName = productName,
                    quantity = formatDecimal(qty.toDouble()),
                    unitPrice = formatDecimal(unitPrice)
                )
            )
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Dodavanje stavke nije uspjelo.")
        }
        return requireNotNull(getCheck(checkId)) { "Check $checkId not found after add item." }
    }

    override suspend fun updateItem(checkId: Long, itemId: Long, qty: Int): CheckSession {
        try {
            api.updateCheckItemQty(itemId = itemId, request = UpdateCheckItemQtyRequestDto(qty = qty))
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Azuriranje stavke nije uspjelo.")
        }
        return requireNotNull(getCheck(checkId)) { "Check $checkId not found after update item." }
    }

    override suspend fun removeItem(checkId: Long, itemId: Long): CheckSession {
        val response = api.deleteCheckItem(itemId = itemId)
        if (!response.isSuccessful) {
            throw IllegalStateException("Delete item failed with code ${response.code()}")
        }
        return requireNotNull(getCheck(checkId)) { "Check $checkId not found after remove item." }
    }

    override suspend fun stornoItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession {
        try {
            api.stornoCheckItem(
                itemId = itemId,
                request = CheckItemActionRequestDto(
                    reason = reason?.takeIf { it.isNotBlank() },
                    quantity = qty?.let { formatDecimal(it.toDouble()) }
                )
            )
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Storno nije uspio.")
        }
        return requireNotNull(getCheck(checkId)) { "Check $checkId not found after storno." }
    }

    override suspend fun gratisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession {
        try {
            api.gratisCheckItem(
                itemId = itemId,
                request = CheckItemActionRequestDto(
                    reason = reason?.takeIf { it.isNotBlank() },
                    quantity = qty?.let { formatDecimal(it.toDouble()) }
                )
            )
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Gratis nije uspio.")
        }
        return requireNotNull(getCheck(checkId)) { "Check $checkId not found after gratis." }
    }

    override suspend fun otpisItem(checkId: Long, itemId: Long, reason: String?, qty: Int?): CheckSession {
        try {
            api.otpisCheckItem(
                itemId = itemId,
                request = CheckItemActionRequestDto(
                    reason = reason?.takeIf { it.isNotBlank() },
                    quantity = qty?.let { formatDecimal(it.toDouble()) }
                )
            )
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Otpis nije uspio.")
        }
        return requireNotNull(getCheck(checkId)) { "Check $checkId not found after otpis." }
    }

    override suspend fun sendToBar(checkId: Long): CheckSession {
        val payload = try {
            api.sendToBar(checkId = checkId)
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Slanje na sank nije uspjelo.")
        }

        val mapped = runCatching { CheckItemsMapper.toCheckSession(payload) }.getOrNull()
        if (mapped != null) {
            cache(mapped)
            return mapped
        }
        return requireNotNull(getCheck(checkId)) { "Check $checkId not found after send-to-bar." }
    }

    override suspend fun closeCheck(checkId: Long): CheckSession? {
        val payload = try {
            api.closeCheck(checkId = checkId)
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Zatvaranje checka nije uspjelo.")
        }
        val mapped = runCatching { CheckItemsMapper.toCheckSession(payload) }.getOrNull()
        if (mapped != null) {
            cache(mapped)
            return mapped
        }
        return getCheck(checkId)
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

        runCatching {
            val payload = gson.fromJson(raw, Map::class.java)
            val detail = payload["detail"] as? String
            if (!detail.isNullOrBlank()) return detail
            payload.entries.firstOrNull()?.let { (key, value) ->
                when (value) {
                    is List<*> -> {
                        val first = value.firstOrNull()?.toString().orEmpty()
                        if (first.isNotBlank()) return "$key: $first"
                    }
                    is String -> {
                        if (value.isNotBlank()) return "$key: $value"
                    }
                }
            }
        }

        return runCatching {
            gson.fromJson(raw, ApiErrorDto::class.java).detail
        }.getOrNull()
    }

    private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.4f", value)

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
