package pos.finestar.barion.data.repo

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.ResponseBody
import pos.finestar.barion.BuildConfig
import pos.finestar.barion.api.PosApi
import pos.finestar.barion.api.model.AddCheckItemRequestDto
import pos.finestar.barion.api.model.ApiErrorDto
import pos.finestar.barion.api.model.CheckDto
import pos.finestar.barion.api.model.CheckItemActionRequestDto
import pos.finestar.barion.api.model.CheckItemModifierInputDto
import pos.finestar.barion.api.model.CreateCheckRequestDto
import pos.finestar.barion.api.model.IssueReceiptRequestDto
import pos.finestar.barion.api.model.SettlementPayCardConfirmRequestDto
import pos.finestar.barion.api.model.SettlementPayCashItemDto
import pos.finestar.barion.api.model.SettlementPayCashRequestDto
import pos.finestar.barion.api.model.SettlementPreparePartDto
import pos.finestar.barion.api.model.SettlementPrepareRequestDto
import pos.finestar.barion.api.model.UpdateCheckItemQtyRequestDto
import pos.finestar.barion.data.local.ApiCacheDao
import pos.finestar.barion.data.local.ApiCacheEntity
import pos.finestar.barion.data.payment.viva.VivaPaymentAdapter
import pos.finestar.barion.data.payment.viva.VivaRequestMapper
import pos.finestar.barion.data.payment.viva.VivaSaleResult
import pos.finestar.barion.domain.model.CheckItem
import pos.finestar.barion.domain.model.CheckRoundState
import pos.finestar.barion.domain.model.CheckRoundStateItem
import pos.finestar.barion.domain.model.CheckRoundStatePaidLine
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.model.FiscalizeReceiptResult
import pos.finestar.barion.domain.model.SettlementMethod
import pos.finestar.barion.domain.model.SettlementPart
import pos.finestar.barion.domain.model.SettlementPartStatus
import pos.finestar.barion.domain.model.SettlementPrepareResult
import pos.finestar.barion.domain.model.SettlementReceipt
import pos.finestar.barion.domain.model.SettlementState
import pos.finestar.barion.domain.model.SettlementStateItem
import pos.finestar.barion.domain.model.SettlementStatePart
import pos.finestar.barion.domain.model.SettlementSelection
import pos.finestar.barion.domain.model.SelectedModifier
import pos.finestar.barion.domain.model.TableStatus
import pos.finestar.barion.domain.repo.CheckRepository
import retrofit2.HttpException

@Singleton
class RemoteCheckRepository @Inject constructor(
    private val api: PosApi,
    private val gson: Gson,
    private val apiCacheDao: ApiCacheDao,
    private val vivaRequestMapper: VivaRequestMapper,
    private val vivaPaymentAdapter: VivaPaymentAdapter
) : CheckRepository {
    companion object {
        private const val TAG = "RemoteCheckRepo"
    }

    private val checkCache = ConcurrentHashMap<Long, CheckSession>()
    private val checkTtlMillis = 15_000L
    private val strictMoneyRegex = Regex("^-?\\d+\\.\\d{2}$")
    private val strictQtyRegex = Regex("^-?\\d+\\.\\d{4}$")

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

    override suspend fun getCheck(checkId: Long, forceRefresh: Boolean): CheckSession? {
        if (!forceRefresh) {
            checkCache[checkId]?.let { return it }
            readCheckCache(checkId, ttlMillis = checkTtlMillis)?.let {
                cache(it)
                return it
            }
        }
        try {
            val payload = api.getCheckItems(checkId)
            val fresh = CheckItemsMapper.toCheckSession(payload)
            cache(fresh)
            writeCheckCache(fresh)
            return fresh
        } catch (_: Throwable) {
            return checkCache[checkId]
                ?: readCheckCache(checkId, ttlMillis = checkTtlMillis)
                ?: readCheckCache(checkId, ttlMillis = Long.MAX_VALUE)
        }
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
        try {
            api.addCheckItem(
                checkId = checkId,
                request = AddCheckItemRequestDto(
                    artiklId = productId,
                    artiklName = productName,
                    productId = productId,
                    productName = productName,
                    quantity = formatDecimal(qty.toDouble()),
                    unitPrice = formatDecimal(unitPrice),
                    modifiers = modifiers.takeIf { it.isNotEmpty() }?.map { modifier ->
                        CheckItemModifierInputDto(
                            type = modifier.type.name.lowercase(Locale.US),
                            id = modifier.id,
                            quantity = modifier.quantity
                        )
                    },
                    note = note?.trim()?.takeIf { it.isNotBlank() }
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
            writeCheckCache(mapped)
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
            writeCheckCache(mapped)
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
            writeCheckCache(mapped)
            return mapped
        }
        return getCheck(checkId)
    }

    override suspend fun prepareSettlementPart(
        checkId: Long,
        selections: List<SettlementSelection>,
        amount: Double?,
        method: SettlementMethod,
        tipAmount: Double,
        remainingTotal: Double?
    ): SettlementPrepareResult {
        Log.d(
            TAG,
            "prepareSettlementPart request checkId=$checkId method=${method.name} amount=${amount?.let { formatMoney(it) }} tip=${formatMoney(tipAmount)} remainingTotal=${remainingTotal?.let { formatMoney(it) }} selections=${selections.joinToString { "${it.checkItemId}:${it.qty}" }}"
        )
        val payload = try {
            val resolvedAmount = amount ?: throw IllegalStateException("Nedostaje amount za prepare-settlement.")
            val parts = buildPrepareParts(
                method = method,
                selectedAmount = resolvedAmount,
                tipAmount = tipAmount,
                remainingTotal = remainingTotal
            )
            api.prepareSettlementPart(
                checkId = checkId,
                request = SettlementPrepareRequestDto(
                    parts = parts
                )
            )
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Priprema naplate nije uspjela.")
        }
        return payload.toPrepareResult().also { result ->
            Log.d(
                TAG,
                "prepareSettlementPart response checkId=$checkId partId=${result.part.partId} amount=${formatMoney(result.part.amount)} tip=${formatMoney(result.part.tipAmount)} status=${result.part.status}"
            )
        }
    }

    override suspend fun paySettlementPartCash(
        checkId: Long,
        partId: Long,
        amount: Double,
        selections: List<SettlementSelection>
    ): SettlementPart {
        Log.d(
            TAG,
            "paySettlementPartCash request checkId=$checkId partId=$partId amount=${formatMoney(amount)} items=${selections.joinToString { "${it.checkItemId}:${it.qty}" }}"
        )
        val payload = try {
            api.paySettlementPartCash(
                checkId = checkId,
                partId = partId,
                request = SettlementPayCashRequestDto(
                    amount = formatMoney(amount),
                    items = selections.takeIf { it.isNotEmpty() }?.map { selection ->
                        SettlementPayCashItemDto(
                            itemId = selection.checkItemId,
                            quantity = selection.qty.toString()
                        )
                    },
                    issueReceipt = false,
                    currency = "EUR"
                )
            )
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Cash naplata nije uspjela.")
        }
        val parsed = payload.toSettlementPart()
        return parsed.copy(
            method = SettlementMethod.CASH,
            status = SettlementPartStatus.PAID,
            amount = if (parsed.amount > 0.0) parsed.amount else amount,
            tipAmount = 0.0,
            totalCharged = if (parsed.totalCharged > 0.0) parsed.totalCharged else amount
        ).also { result ->
            Log.d(
                TAG,
                "paySettlementPartCash response checkId=$checkId requestPartId=$partId resultPartId=${result.partId} amount=${formatMoney(result.amount)} status=${result.status} action=${result.action}"
            )
        }
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
        val confirmPartId = resolveCardConfirmPartId(
            checkId = checkId,
            requestedPartId = partId,
            requestedTipAmount = tipAmount
        )
        if (confirmPartId != partId) {
            Log.w(
                TAG,
                "confirmSettlementPartCard remapped partId checkId=$checkId requestedPartId=$partId resolvedPartId=$confirmPartId"
            )
        }
        val saleRequest = vivaRequestMapper.map(
            checkId = checkId,
            partId = confirmPartId,
            amount = amount,
            tipAmount = tipAmount,
            clientTransactionId = clientTransactionId
        )
        Log.d(
            TAG,
            "confirmSettlementPartCard request checkId=$checkId partId=$confirmPartId approved=$approved amountCents=${saleRequest.amountCents} tipCents=${saleRequest.tipAmountCents} clientTxn=${saleRequest.clientTransactionId}"
        )
        val saleResult = if (approved) {
            Log.i(
                TAG,
                "confirmSettlementPartCard invoking viva adapter checkId=$checkId partId=$confirmPartId clientTxn=${saleRequest.clientTransactionId}"
            )
            runCatching { vivaPaymentAdapter.sale(saleRequest) }
                .getOrElse { error ->
                    Log.e(
                        TAG,
                        "confirmSettlementPartCard viva adapter failed checkId=$checkId partId=$confirmPartId clientTxn=${saleRequest.clientTransactionId} message=${error.message}",
                        error
                    )
                    throw IllegalStateException("Viva sale failed: ${error.message}", error)
                }
        } else {
            VivaSaleResult(
                approved = false,
                providerRef = providerRef.ifBlank { "manual-declined-$checkId-$partId" },
                externalTransactionId = saleRequest.clientTransactionId
            )
        }
        Log.d(
            TAG,
            "confirmSettlementPartCard sale result checkId=$checkId partId=$confirmPartId approved=${saleResult.approved} providerRef=${saleResult.providerRef}"
        )
        Log.i(
            TAG,
            "confirmSettlementPartCard backend confirm request checkId=$checkId partId=$confirmPartId approved=${saleResult.approved} providerRef=${saleResult.providerRef} externalTxn=${saleResult.externalTransactionId ?: saleRequest.clientTransactionId} amount=${formatMoneyFromCents(saleRequest.amountCents)} tip=${formatMoneyFromCents(saleRequest.tipAmountCents)} currency=${saleRequest.currency}"
        )
        val payload = try {
            api.confirmSettlementPartCard(
                checkId = checkId,
                partId = confirmPartId,
                request = SettlementPayCardConfirmRequestDto(
                    approved = saleResult.approved,
                    providerRef = saleResult.providerRef,
                    externalTxnId = saleResult.externalTransactionId ?: saleRequest.clientTransactionId,
                    clientTransactionId = saleRequest.clientTransactionId,
                    amount = formatMoneyFromCents(saleRequest.amountCents),
                    tipAmount = formatMoneyFromCents(saleRequest.tipAmountCents),
                    issueReceipt = false,
                    currency = saleRequest.currency,
                    rrn = saleResult.rrn,
                    referenceNumber = saleResult.referenceNumber,
                    authorisationCode = saleResult.authorisationCode,
                    tid = saleResult.tid,
                    orderCode = saleResult.orderCode,
                    shortOrderCode = saleResult.shortOrderCode,
                    transactionDate = saleResult.transactionDate,
                    paymentMethod = saleResult.paymentMethod,
                    cardType = saleResult.cardType,
                    accountNumber = saleResult.accountNumber,
                    verificationMethod = saleResult.verificationMethod,
                    aid = saleResult.aid,
                    bankId = saleResult.bankId,
                    transactionTypeId = saleResult.transactionTypeId,
                    transactionEventId = saleResult.transactionEventId,
                    surchargeAmount = saleResult.surchargeAmount,
                    customerTrns = saleResult.customerTrns,
                    providerStatus = saleResult.status,
                    providerAction = saleResult.action,
                    providerMessage = saleResult.errorMessage,
                    providerPayload = saleResult.providerPayload.takeIf { it.isNotEmpty() }
                )
            )
        } catch (httpException: HttpException) {
            Log.e(
                TAG,
                "confirmSettlementPartCard backend confirm failed checkId=$checkId partId=$confirmPartId message=${httpException.message()}",
                httpException
            )
            throw mapHttpException(httpException, defaultMessage = "Card potvrda nije uspjela.")
        }
        return payload.toSettlementPart().also { result ->
            Log.d(
                TAG,
                "confirmSettlementPartCard response checkId=$checkId requestPartId=$confirmPartId resultPartId=${result.partId} amount=${formatMoney(result.amount)} status=${result.status} action=${result.action}"
            )
        }
    }

    override suspend fun confirmSettlementCard(
        checkId: Long,
        amount: Double,
        tipAmount: Double,
        approved: Boolean,
        providerRef: String,
        clientTransactionId: String
    ): SettlementPart {
        Log.d(
            TAG,
            "confirmSettlementCard request checkId=$checkId approved=$approved amount=${formatMoney(amount)} tip=${formatMoney(tipAmount)} providerRef=$providerRef"
        )
        val payload = try {
            api.confirmSettlementCard(
                checkId = checkId,
                request = SettlementPayCardConfirmRequestDto(
                    approved = approved,
                    providerRef = providerRef,
                    externalTxnId = clientTransactionId,
                    clientTransactionId = clientTransactionId,
                    amount = formatMoney(amount),
                    tipAmount = formatMoney(tipAmount),
                    issueReceipt = false,
                    currency = "EUR"
                )
            )
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Card potvrda nije uspjela.")
        }
        return payload.toSettlementPart().also { result ->
            Log.d(
                TAG,
                "confirmSettlementCard response checkId=$checkId partId=${result.partId} amount=${formatMoney(result.amount)} status=${result.status} action=${result.action}"
            )
        }
    }

    override suspend fun getSettlementState(checkId: Long): SettlementState {
        Log.d(TAG, "getSettlementState request checkId=$checkId")
        val payload = try {
            api.getSettlementState(checkId = checkId)
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Dohvat settlement stanja nije uspio.")
        }
        return payload.toSettlementState().also { state ->
            Log.d(
                TAG,
                "getSettlementState response checkId=$checkId status=${state.checkStatus} paymentStatus=${state.paymentStatus} remaining=${state.remainingTotal} parts=${state.parts.size} items=${state.items.size}"
            )
        }
    }

    override suspend fun getCheckRoundState(checkId: Long): CheckRoundState {
        Log.d(TAG, "getCheckRoundState request checkId=$checkId")
        val payload = try {
            api.getCheckRoundState(checkId = checkId)
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Dohvat round-state nije uspio.")
        }
        return payload.toCheckRoundState().also { state ->
            Log.d(
                TAG,
                "getCheckRoundState response checkId=$checkId status=${state.status} items=${state.items.size}"
            )
        }
    }

    override suspend fun fiscalizeReceipt(checkId: Long, receiptId: Long): FiscalizeReceiptResult {
        Log.d(TAG, "fiscalizeReceipt request checkId=$checkId receiptId=$receiptId")
        val payload = try {
            api.fiscalizeReceipt(checkId = checkId, receiptId = receiptId)
        } catch (httpException: HttpException) {
            throw mapHttpException(httpException, defaultMessage = "Fiskalizacija računa nije uspjela.")
        }
        return payload.toFiscalizeReceiptResult().also { result ->
            Log.d(
                TAG,
                "fiscalizeReceipt response checkId=$checkId receiptId=$receiptId action=${result.action} status=${result.status}"
            )
        }
    }

    private fun cache(check: CheckSession) {
        checkCache[check.checkId] = check
    }

    private suspend fun writeCheckCache(check: CheckSession) {
        apiCacheDao.upsert(
            ApiCacheEntity(
                key = "check_items:${check.checkId}",
                payload = gson.toJson(check),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    private suspend fun readCheckCache(checkId: Long, ttlMillis: Long): CheckSession? {
        val entry = apiCacheDao.get("check_items:$checkId") ?: return null
        val isValid = System.currentTimeMillis() - entry.updatedAtMillis <= ttlMillis
        if (!isValid) return null
        return runCatching { gson.fromJson(entry.payload, CheckSession::class.java) }.getOrNull()
    }

    private fun mapHttpException(httpException: HttpException, defaultMessage: String): IllegalStateException {
        val detail = parseErrorDetail(httpException.response()?.errorBody())
        val normalizedDetail = detail
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
        val mappedDetail = when {
            normalizedDetail.isNullOrBlank() -> null
            normalizedDetail.contains("tip_amount", ignoreCase = true) &&
                normalizedDetail.contains("mora biti jednak", ignoreCase = true) ->
                "Napojnica nije usklađena s pripremljenim partom. Pokušaj ponovno."
            normalizedDetail.contains("tip", ignoreCase = true) &&
                normalizedDetail.contains("veći", ignoreCase = true) &&
                normalizedDetail.contains("iznos", ignoreCase = true) ->
                "Napojnica ne može biti veća od iznosa naplate."
            else -> normalizedDetail
        }
        val message = mappedDetail ?: when (val code = httpException.code()) {
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

    private suspend fun resolveCardConfirmPartId(
        checkId: Long,
        requestedPartId: Long,
        requestedTipAmount: Double
    ): Long {
        val settlementState = runCatching { getSettlementState(checkId) }
            .getOrElse { error ->
                Log.w(
                    TAG,
                    "resolveCardConfirmPartId settlement-state fetch failed checkId=$checkId requestedPartId=$requestedPartId message=${error.message}"
                )
                return requestedPartId
            }

        val requestedPart = settlementState.parts.firstOrNull { it.partId == requestedPartId }
        if (requestedPart?.isCardConfirmCandidate(requestedTipAmount) == true) {
            return requestedPartId
        }

        val exactCandidate = settlementState.parts.firstOrNull {
            it.isCardConfirmCandidate(requestedTipAmount)
        }?.partId
        if (exactCandidate != null) return exactCandidate

        val methodOnlyCandidate = settlementState.parts.firstOrNull {
            it.isCardConfirmMethodCandidate()
        }?.partId
        if (methodOnlyCandidate != null) return methodOnlyCandidate

        val requestedMethod = requestedPart?.method?.name ?: "UNKNOWN"
        throw IllegalStateException(
            "Nije pronađen pripremljeni CARD part za confirm. Traženi part=$requestedPartId method=$requestedMethod."
        )
    }

    private fun SettlementStatePart.isCardConfirmCandidate(requestedTipAmount: Double): Boolean {
        return isCardConfirmMethodCandidate() && sameMoneyInCents(this.tipAmount ?: 0.0, requestedTipAmount)
    }

    private fun SettlementStatePart.isCardConfirmMethodCandidate(): Boolean {
        if (method != SettlementMethod.CARD) return false
        val normalized = status.uppercase(Locale.US)
        return normalized == "PREPARED" || normalized == "FAILED"
    }

    private fun sameMoneyInCents(left: Double, right: Double): Boolean {
        val leftCents = kotlin.math.round(left * 100.0).toLong()
        val rightCents = kotlin.math.round(right * 100.0).toLong()
        return leftCents == rightCents
    }

    private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.4f", value)
    private fun formatMoney(value: Double): String = String.format(Locale.US, "%.2f", value)
    private fun formatMoneyFromCents(cents: Long): String = formatMoney(cents / 100.0)

    private fun buildPrepareParts(
        method: SettlementMethod,
        selectedAmount: Double,
        tipAmount: Double,
        remainingTotal: Double?
    ): List<SettlementPreparePartDto> {
        val selectedPart = SettlementPreparePartDto(
            method = method.name,
            amount = formatMoney(selectedAmount),
            tipAmount = if (tipAmount == 0.0) null else formatMoney(tipAmount)
        )
        val total = remainingTotal ?: return listOf(selectedPart)
        val remaining = moneySubtract(total, selectedAmount)
        return if (remaining > 0.0) {
            // Keep selected part first so parser resolves expected part_id for immediate payment.
            listOf(
                selectedPart,
                SettlementPreparePartDto(
                    method = SettlementMethod.CASH.name,
                    amount = formatMoney(remaining),
                    tipAmount = null
                )
            )
        } else {
            listOf(selectedPart)
        }
    }

    private fun moneySubtract(a: Double, b: Double): Double {
        val aCents = kotlin.math.round(a * 100.0).toLong()
        val bCents = kotlin.math.round(b * 100.0).toLong()
        val diff = (aCents - bCents).coerceAtLeast(0L)
        return diff / 100.0
    }

    private fun JsonObject.toPrepareResult(): SettlementPrepareResult {
        val remainingNode = (get("remaining") ?: get("remaining_items"))?.takeIf { it.isJsonArray }?.asJsonArray
        val remaining = remainingNode
            ?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            ?.mapNotNull { node ->
                val itemId = node.getLong("check_item_id") ?: node.getLong("item_id")
                val qtyRemaining = node.getInt("qty_remaining") ?: node.getInt("remaining")
                if (itemId == null || qtyRemaining == null) null else itemId to qtyRemaining
            }
            ?.toMap()
            .orEmpty()
        val result = SettlementPrepareResult(
            part = toSettlementPart(),
            remainingByItemId = remaining
        )
        if (result.part.partId <= 0L) {
            Log.w(
                TAG,
                "toPrepareResult resolved invalid partId=${result.part.partId}. Response keys=${keySet().joinToString(",")}"
            )
        }
        return result
    }

    private fun JsonObject.toSettlementPart(): SettlementPart {
        val firstPartsNode = get("parts")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.firstOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject

        val partNode = sequenceOf(
            get("part"),
            get("settlement_part"),
            get("result"),
            get("data"),
            firstPartsNode
        )
            .filterNotNull()
            .firstOrNull { it.isJsonObject }
            ?.asJsonObject
            ?: this

        val nestedPartNode = partNode.getObject("part")
            ?: partNode.getObject("settlement_part")
            ?: partNode.getObject("result")
        val partId = partNode.extractPartId()
            ?: nestedPartNode?.extractPartId()
            ?: extractPartId()
            ?: 0L
        val statusRaw = (partNode.getString("status")
            ?: getString("status")
            ?: "PREPARED").uppercase(Locale.US)
        val methodRaw = (partNode.getString("method") ?: getString("method"))?.uppercase(Locale.US)
        val amount = partNode.getMoneyStrict("amount", "settlement.amount")
            ?: partNode.getMoneyStrict("total", "settlement.total")
            ?: partNode.getMoneyStrict("total_amount", "settlement.total_amount")
            ?: partNode.getMoneyStrict("fiscal_amount", "settlement.fiscal_amount")
            ?: getMoneyStrict("amount", "settlement.amount")
            ?: getMoneyStrict("total", "settlement.total")
            ?: getMoneyStrict("total_amount", "settlement.total_amount")
            ?: 0.0
        val tipAmount = partNode.getMoneyStrict("tip_amount", "settlement.tip_amount")
            ?: getMoneyStrict("tip_amount", "settlement.tip_amount")
            ?: 0.0
        val totalCharged = partNode.getMoneyStrict("total_charged", "settlement.total_charged")
            ?: getMoneyStrict("total_charged", "settlement.total_charged")
            ?: (amount + tipAmount)
        val providerRef = partNode.getString("provider_ref")
            ?: partNode.getString("external_txn_id")
            ?: getString("provider_ref")
            ?: getString("external_txn_id")
        val action = partNode.getString("action") ?: getString("action")
        val issuedReceiptId = partNode.getLong("issued_receipt_id")
            ?: getLong("issued_receipt_id")
            ?: partNode.getObject("receipt")?.getLong("id")
            ?: getObject("receipt")?.getLong("id")
        val receiptPdfUrl = partNode.getString("receipt_pdf_url")
            ?: getString("receipt_pdf_url")
            ?: partNode.getObject("receipt")?.getString("pdf_url")
            ?: getObject("receipt")?.getString("pdf_url")

        return SettlementPart(
            partId = partId,
            method = methodRaw?.toSettlementMethodOrNull(),
            status = statusRaw.toSettlementStatus(),
            amount = amount,
            tipAmount = tipAmount,
            totalCharged = totalCharged,
            providerRef = providerRef,
            action = action,
            issuedReceiptId = issuedReceiptId,
            receiptPdfUrl = receiptPdfUrl
        )
    }

    private fun JsonObject.toSettlementState(): SettlementState {
        val node = getObject("state") ?: this
        val totals = node.getObject("totals") ?: getObject("totals")
        val actions = node.getObject("actions") ?: getObject("actions")
        totals?.getMoneyStrict("check_total", "settlement-state.totals.check_total")
        totals?.getMoneyStrict("confirmed_total", "settlement-state.totals.confirmed_total")
        val items = parseSettlementStateItems(root = this, node = node)
        val parts = parseSettlementStateParts(root = this, node = node)
        val receipts = parseSettlementStateReceipts(root = this, node = node)
        return SettlementState(
            checkStatus = node.getString("check_status")
                ?: node.getString("status")
                ?: getString("check_status")
                ?: "",
            paymentStatus = node.getString("payment_status") ?: getString("payment_status"),
            settlementStatus = node.getString("settlement_status") ?: getString("settlement_status"),
            remainingTotal = totals?.getMoneyStrict("remaining_total", "settlement-state.totals.remaining_total")
                ?: node.getMoneyStrict("remaining_total", "settlement-state.remaining_total")
                ?: getMoneyStrict("remaining_total", "settlement-state.remaining_total"),
            canIssueReceipt = actions?.getBoolean("can_issue_receipt")
                ?: node.getBoolean("can_issue_receipt")
                ?: getBoolean("can_issue_receipt"),
            posReceiptId = node.getLong("pos_receipt_id")
                ?: getLong("pos_receipt_id"),
            posReceiptIds = node.getLongList("pos_receipt_ids")
                ?: getLongList("pos_receipt_ids")
                ?: emptyList(),
            issuedReceiptId = node.getLong("issued_receipt_id")
                ?: getLong("issued_receipt_id")
                ?: node.getObject("receipt")?.getLong("id")
                ?: getObject("receipt")?.getLong("id"),
            receiptPdfUrl = node.getString("receipt_pdf_url")
                ?: getString("receipt_pdf_url")
                ?: node.getObject("receipt")?.getString("pdf_url")
                ?: getObject("receipt")?.getString("pdf_url"),
            receipts = receipts,
            items = items,
            parts = parts
        )
    }

    private fun JsonObject.toCheckRoundState(): CheckRoundState {
        val node = getObject("state") ?: this
        return CheckRoundState(
            checkId = node.getLong("check_id")
                ?: getLong("check_id")
                ?: 0L,
            status = node.getString("status") ?: getString("status"),
            items = parseRoundStateItems(root = this, node = node),
            updatedAt = node.getString("updated_at") ?: getString("updated_at")
        )
    }

    private fun JsonObject.toFiscalizeReceiptResult(): FiscalizeReceiptResult {
        return FiscalizeReceiptResult(
            checkId = getLong("check_id") ?: 0L,
            receiptId = getLong("receipt_id") ?: 0L,
            action = getString("action"),
            status = getString("status"),
            receiptNumber = getInt("receipt_number"),
            totalAmount = getMoneyStrict("total_amount", "fiscalize-receipt.total_amount"),
            zki = getString("zki"),
            jir = getString("jir"),
            qr = getString("qr"),
            pdfUrl = getString("pdf_url"),
            receipts = parseSettlementStateReceipts(root = this, node = this)
        )
    }

    private fun String.toSettlementStatus(): SettlementPartStatus {
        return when (this) {
            "PAID", "CARD_CONFIRMED", "COMPLETE", "SUCCESS", "FISCALIZED" -> SettlementPartStatus.PAID
            "FAILED", "DECLINED" -> SettlementPartStatus.FAILED
            else -> SettlementPartStatus.PREPARED
        }
    }

    private fun String.toSettlementMethodOrNull(): SettlementMethod? {
        return when (this) {
            "CASH" -> SettlementMethod.CASH
            "CARD" -> SettlementMethod.CARD
            else -> null
        }
    }

    private fun JsonObject.getString(name: String): String? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else runCatching { element.asString }.getOrNull()
    }

    private fun JsonObject.getObject(name: String): JsonObject? {
        val element = get(name) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    private fun JsonObject.getLong(name: String): Long? {
        val element = get(name) ?: return null
        return runCatching {
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                element.asString.toLong()
            } else {
                element.asLong
            }
        }.getOrNull()
    }

    private fun JsonObject.getLongList(name: String): List<Long>? {
        val element = get(name) ?: return null
        if (!element.isJsonArray) return null
        return element.asJsonArray
            .mapNotNull { item ->
                runCatching {
                    if (!item.isJsonPrimitive) return@runCatching null
                    val primitive = item.asJsonPrimitive
                    when {
                        primitive.isNumber -> primitive.asLong
                        primitive.isString -> primitive.asString.toLong()
                        else -> null
                    }
                }.getOrNull()
            }
    }

    private fun JsonObject.extractPartId(): Long? {
        return getLong("part_id")
            ?: getLong("id")
            ?: getLong("settlement_part_id")
            ?: getLong("partId")
            ?: getLong("settlementPartId")
    }

    private fun JsonObject.getInt(name: String): Int? {
        val element = get(name) ?: return null
        return runCatching {
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                element.asString.toInt()
            } else {
                element.asInt
            }
        }.getOrNull()
    }

    private fun JsonObject.getDouble(name: String): Double? {
        val element = get(name) ?: return null
        return runCatching {
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                element.asString.toDouble()
            } else {
                element.asDouble
            }
        }.getOrNull()
    }

    private fun JsonObject.getMoneyStrict(name: String, fieldPath: String): Double? {
        val element = get(name) ?: return null
        if (element.isJsonNull) return null
        if (!element.isJsonPrimitive) {
            throw IllegalStateException("Invalid format for $fieldPath: expected money value.")
        }
        val primitive = element.asJsonPrimitive
        if (primitive.isString) {
            val raw = primitive.asString.trim()
            if (!strictMoneyRegex.matches(raw)) {
                throw IllegalStateException("Invalid format for $fieldPath: expected decimal string with 2 decimals.")
            }
            return raw.toDouble()
        }
        if (primitive.isNumber) {
            val numeric = runCatching { primitive.asBigDecimal }.getOrElse {
                throw IllegalStateException("Invalid format for $fieldPath: invalid numeric money value.")
            }
            val normalized = numeric.stripTrailingZeros()
            if (normalized.scale() > 2) {
                throw IllegalStateException("Invalid format for $fieldPath: numeric money has more than 2 decimals.")
            }
            return numeric.toDouble()
        }
        throw IllegalStateException("Invalid format for $fieldPath: expected string or number.")
    }

    private fun JsonObject.getWholeQuantityFrom4Strict(name: String, fieldPath: String): Int? {
        val element = get(name) ?: return null
        if (element.isJsonNull) return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw IllegalStateException("Invalid format for $fieldPath: expected quantity string like 1.0000.")
        }
        val raw = element.asString.trim()
        if (!strictQtyRegex.matches(raw)) {
            throw IllegalStateException("Invalid format for $fieldPath: expected quantity string with 4 decimals.")
        }
        if (!raw.endsWith(".0000")) {
            // Backend contract should send whole quantities for POS item selection.
            // Keep app usable by coercing and logging the exact offending value.
            val parsed = raw.toDoubleOrNull()
                ?: throw IllegalStateException("Invalid value for $fieldPath: quantity is not numeric.")
            val coerced = when {
                parsed <= 0.0 -> 0
                else -> kotlin.math.max(1, kotlin.math.floor(parsed).toInt())
            }
            Log.w(
                TAG,
                "Non-whole quantity for $fieldPath raw=$raw; coerced=$coerced to keep UI operational."
            )
            return coerced
        }
        return raw.substringBefore('.').toIntOrNull()
            ?: throw IllegalStateException("Invalid value for $fieldPath: quantity is out of Int range.")
    }

    private fun JsonObject.getQuantityFrom4StrictAsDouble(name: String, fieldPath: String): Double? {
        val element = get(name) ?: return null
        if (element.isJsonNull) return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw IllegalStateException("Invalid format for $fieldPath: expected quantity string like 1.0000.")
        }
        val raw = element.asString.trim()
        if (!strictQtyRegex.matches(raw)) {
            throw IllegalStateException("Invalid format for $fieldPath: expected quantity string with 4 decimals.")
        }
        return raw.toDouble()
    }

    private fun parseSettlementStateItems(root: JsonObject, node: JsonObject): List<SettlementStateItem> {
        val itemsArray = (node.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: root.get("items")?.takeIf { it.isJsonArray }?.asJsonArray)
            ?: return emptyList()
        return itemsArray
            .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .mapIndexed { index, item ->
                val id = item.getLong("id")
                    ?: throw IllegalStateException("Missing settlement-state.items[$index].id")
                val quantity = item.getWholeQuantityFrom4Strict("quantity", "settlement-state.items[$index].quantity")
                    ?: throw IllegalStateException("Missing settlement-state.items[$index].quantity")
                val paidQuantity = item.getQuantityFrom4StrictAsDouble("paid_quantity", "settlement-state.items[$index].paid_quantity")
                    ?: throw IllegalStateException("Missing settlement-state.items[$index].paid_quantity")
                val remainingQuantity = item.getWholeQuantityFrom4Strict(
                    "remaining_quantity",
                    "settlement-state.items[$index].remaining_quantity"
                ) ?: throw IllegalStateException("Missing settlement-state.items[$index].remaining_quantity")
                val totalAmount = item.getMoneyStrict("total_amount", "settlement-state.items[$index].total_amount")
                    ?: throw IllegalStateException("Missing settlement-state.items[$index].total_amount")
                val paidAmount = item.getMoneyStrict("paid_amount", "settlement-state.items[$index].paid_amount")
                    ?: throw IllegalStateException("Missing settlement-state.items[$index].paid_amount")
                val remainingAmount = item.getMoneyStrict("remaining_amount", "settlement-state.items[$index].remaining_amount")
                    ?: throw IllegalStateException("Missing settlement-state.items[$index].remaining_amount")
                SettlementStateItem(
                    id = id,
                    artiklId = item.getLong("artikl_id") ?: item.getLong("product_id"),
                    name = item.getString("artikl_name")
                        ?: item.getString("product_name")
                        ?: item.getString("name")
                        ?: item.getObject("product")?.getString("name"),
                    imageUrl = normalizeImageUrl(
                        item.getString("image_46x75")
                            ?: item.getString("image")
                            ?: item.getString("image_url")
                            ?: item.getObject("product")?.getString("image_46x75")
                            ?: item.getObject("product")?.getString("image")
                    ),
                    roundNumber = item.getInt("round_number"),
                    quantity = quantity,
                    paidQuantity = paidQuantity,
                    remainingQuantity = remainingQuantity,
                    totalAmount = totalAmount,
                    paidAmount = paidAmount,
                    remainingAmount = remainingAmount
                )
            }
    }

    private fun parseSettlementStateParts(root: JsonObject, node: JsonObject): List<SettlementStatePart> {
        val partsArray = (node.get("parts")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: root.get("parts")?.takeIf { it.isJsonArray }?.asJsonArray)
            ?: return emptyList()
        return partsArray
            .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .mapNotNull { part ->
                val partId = part.extractPartId() ?: return@mapNotNull null
                val status = part.getString("status") ?: return@mapNotNull null
                SettlementStatePart(
                    partId = partId,
                    status = status,
                    method = part.getString("method")
                        ?.uppercase(Locale.US)
                        ?.toSettlementMethodOrNull(),
                    methodDisplay = part.getString("method_display"),
                    amount = part.getMoneyStrict("amount", "settlement-state.parts[].amount")
                        ?: part.getMoneyStrict("total", "settlement-state.parts[].total")
                        ?: part.getMoneyStrict("total_amount", "settlement-state.parts[].total_amount"),
                    tipAmount = part.getMoneyStrict("tip_amount", "settlement-state.parts[].tip_amount"),
                    issuedReceiptId = part.getLong("issued_receipt_id")
                        ?: part.getObject("receipt")?.getLong("id"),
                    cardBrand = part.getString("card_brand"),
                    cardMaskedPan = part.getString("card_masked_pan")
                )
            }
    }

    private fun parseRoundStateItems(root: JsonObject, node: JsonObject): List<CheckRoundStateItem> {
        val itemsArray = (node.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: root.get("items")?.takeIf { it.isJsonArray }?.asJsonArray)
            ?: return emptyList()
        val defaultCheckId = node.getLong("check_id") ?: root.getLong("check_id") ?: 0L
        return itemsArray
            .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .mapIndexed { index, item ->
                val id = item.getLong("item_id")
                    ?: item.getLong("id")
                    ?: throw IllegalStateException("Missing round-state.items[$index].item_id")
                val sourceQuantity = item.getWholeQuantityFrom4Strict(
                    "source_quantity",
                    "round-state.items[$index].source_quantity"
                ) ?: throw IllegalStateException("Missing round-state.items[$index].source_quantity")
                val soldQuantity = item.getQuantityFrom4StrictAsDouble(
                    "sold_quantity",
                    "round-state.items[$index].sold_quantity"
                ) ?: throw IllegalStateException("Missing round-state.items[$index].sold_quantity")
                val stornoQuantity = item.getQuantityFrom4StrictAsDouble(
                    "storno_quantity",
                    "round-state.items[$index].storno_quantity"
                ) ?: throw IllegalStateException("Missing round-state.items[$index].storno_quantity")
                val gratisQuantity = item.getQuantityFrom4StrictAsDouble(
                    "gratis_quantity",
                    "round-state.items[$index].gratis_quantity"
                ) ?: throw IllegalStateException("Missing round-state.items[$index].gratis_quantity")
                val otpisQuantity = item.getQuantityFrom4StrictAsDouble(
                    "otpis_quantity",
                    "round-state.items[$index].otpis_quantity"
                ) ?: throw IllegalStateException("Missing round-state.items[$index].otpis_quantity")
                val remainingQuantity = item.getWholeQuantityFrom4Strict(
                    "remaining_quantity",
                    "round-state.items[$index].remaining_quantity"
                ) ?: throw IllegalStateException("Missing round-state.items[$index].remaining_quantity")
                val paidNode = item.getObject("paid_line")
                CheckRoundStateItem(
                    id = id,
                    checkId = item.getLong("check_id") ?: defaultCheckId,
                    artiklId = item.getLong("artikl_id") ?: item.getLong("product_id"),
                    name = item.getString("artikl_name")
                        ?: item.getString("product_name")
                        ?: item.getString("name"),
                    roundNumber = item.getInt("round_number"),
                    sourceQuantity = sourceQuantity,
                    soldQuantity = soldQuantity,
                    stornoQuantity = stornoQuantity,
                    gratisQuantity = gratisQuantity,
                    otpisQuantity = otpisQuantity,
                    remainingQuantity = remainingQuantity,
                    strikeMain = item.getBoolean("strike_main") ?: false,
                    paidLine = paidNode?.toRoundStatePaidLine(index)
                )
            }
    }

    private fun JsonObject.toRoundStatePaidLine(index: Int): CheckRoundStatePaidLine {
        val lineType = getString("line_type")
            ?: throw IllegalStateException("Missing round-state.items[$index].paid_line.line_type")
        val quantity = getQuantityFrom4StrictAsDouble(
            "quantity",
            "round-state.items[$index].paid_line.quantity"
        ) ?: throw IllegalStateException("Missing round-state.items[$index].paid_line.quantity")
        val unitPrice = getQuantityFrom4StrictAsDouble(
            "unit_price",
            "round-state.items[$index].paid_line.unit_price"
        ) ?: throw IllegalStateException("Missing round-state.items[$index].paid_line.unit_price")
        val totalAmount = getMoneyStrict(
            "total_amount",
            "round-state.items[$index].paid_line.total_amount"
        ) ?: throw IllegalStateException("Missing round-state.items[$index].paid_line.total_amount")
        return CheckRoundStatePaidLine(
            lineType = lineType,
            quantity = quantity,
            unitPrice = unitPrice,
            totalAmount = totalAmount,
            uiColor = getString("ui_color")
        )
    }

    private fun parseSettlementStateReceipts(root: JsonObject, node: JsonObject): List<SettlementReceipt> {
        val receiptsArray = (node.get("receipts")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: root.get("receipts")?.takeIf { it.isJsonArray }?.asJsonArray)
            ?: return emptyList()
        return receiptsArray
            .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .mapNotNull { receipt ->
                val id = receipt.getLong("id") ?: return@mapNotNull null
                SettlementReceipt(
                    id = id,
                    receiptNumber = receipt.getInt("receipt_number"),
                    totalAmount = receipt.getMoneyStrict("total_amount", "settlement-state.receipts[].total_amount")
                        ?: receipt.getMoneyStrict("amount", "settlement-state.receipts[].amount")
                        ?: receipt.getMoneyStrict("total", "settlement-state.receipts[].total"),
                    status = receipt.getString("status"),
                    pdfUrl = receipt.getString("pdf_url"),
                    paymentMethod = receipt.getString("payment_method"),
                    cardBrand = receipt.getString("card_brand"),
                    cardMaskedPan = receipt.getString("card_masked_pan")
                )
            }
    }

    private fun normalizeImageUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return raw
        }
        val base = BuildConfig.BARION_API_BASE_URL.trimEnd('/')
        return if (raw.startsWith("/")) "$base$raw" else "$base/$raw"
    }

    private fun JsonObject.getBoolean(name: String): Boolean? {
        val element = get(name) ?: return null
        return runCatching {
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                element.asString.equals("true", ignoreCase = true)
            } else {
                element.asBoolean
            }
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
