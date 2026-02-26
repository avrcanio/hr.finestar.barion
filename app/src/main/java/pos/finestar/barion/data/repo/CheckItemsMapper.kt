package pos.finestar.barion.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import pos.finestar.barion.BuildConfig
import pos.finestar.barion.domain.model.CheckItem
import pos.finestar.barion.domain.model.CheckSession
import pos.finestar.barion.domain.model.TableStatus

object CheckItemsMapper {

    fun toCheckSession(payload: JsonObject): CheckSession {
        val checkNode = payload.getObject("check") ?: payload
        val totalsNode = payload.getObject("totals")

        val checkId = checkNode.getLong("id") ?: payload.getLong("check_id") ?: 0L
        val tableId = checkNode.getLong("table_id") ?: payload.getLong("table_id") ?: 0L
        val statusRaw = (checkNode.getString("status") ?: payload.getString("status") ?: "OPEN")
        val status = if (statusRaw == "FREE") TableStatus.FREE else TableStatus.OPEN

        val items = (payload.getArray("items") ?: payload.getArray("check_items") ?: JsonArray())
            .mapNotNull { it.asJsonObjectOrNull() }
            .map { item ->
                val itemId = item.getLong("id") ?: item.getLong("item_id")
                val productId = item.getLong("artikl_id")
                    ?: item.getLong("product_id")
                    ?: item.getObject("product")?.getLong("id")
                val name = item.getString("product_name")
                    ?: item.getString("artikl_name")
                    ?: item.getString("name")
                    ?: item.getObject("product")?.getString("name")
                    ?: "Item"
                val qty = item.getInt("qty")
                    ?: item.getInt("quantity")
                    ?: item.getDouble("quantity")?.toInt()
                    ?: 1
                val imageUrl = normalizeImageUrl(
                    item.getString("image_46x75")
                        ?: item.getString("image")
                        ?: item.getString("image_url")
                        ?: item.getObject("product")?.getString("image_46x75")
                        ?: item.getObject("product")?.getString("image")
                )
                val price = item.getDouble("unit_price")
                    ?: item.getDouble("price")
                    ?: item.getDouble("line_total")
                    ?: 0.0
                val roundNumber = item.getInt("round_number")
                val sentAt = item.getString("sent_at")
                val lineType = item.getString("line_type") ?: "NORMAL"
                val note = item.getString("note")
                // STORNO/GRATIS/OTPIS lines are operational/accounting lines and should not be sent to bar printer.
                val sentToBar = if (lineType.uppercase() != "NORMAL") {
                    true
                } else {
                    item.getBoolean("sent_to_bar") ?: !sentAt.isNullOrBlank()
                }
                CheckItem(
                    itemId = itemId,
                    productId = productId,
                    name = name,
                    imageUrl = imageUrl,
                    qty = qty,
                    price = price,
                    lineType = lineType,
                    note = note,
                    roundNumber = roundNumber,
                    sentToBar = sentToBar,
                    sentAt = sentAt
                )
            }

        val subtotal = totalsNode?.getDouble("net_amount")
            ?: totalsNode?.getDouble("subtotal")
            ?: payload.getDouble("net_amount")
            ?: payload.getDouble("subtotal")
            ?: 0.0
        val tax = totalsNode?.getDouble("vat_amount")
            ?: totalsNode?.getDouble("tax")
            ?: payload.getDouble("vat_amount")
            ?: payload.getDouble("tax")
            ?: 0.0
        val total = totalsNode?.getDouble("total_amount")
            ?: totalsNode?.getDouble("total")
            ?: payload.getDouble("total_amount")
            ?: payload.getDouble("total")
            ?: 0.0

        return CheckSession(
            checkId = checkId,
            tableId = tableId,
            tableName = "Sto $tableId",
            status = status,
            items = items,
            subtotal = subtotal,
            tax = tax,
            total = total
        )
    }

    private fun JsonObject.getObject(name: String): JsonObject? {
        val element = this.get(name) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    private fun JsonObject.getArray(name: String): JsonArray? {
        val element = this.get(name) ?: return null
        return if (element.isJsonArray) element.asJsonArray else null
    }

    private fun JsonObject.getString(name: String): String? {
        val element = this.get(name) ?: return null
        return if (element.isJsonNull) null else element.asString
    }

    private fun JsonObject.getLong(name: String): Long? {
        val element = this.get(name) ?: return null
        return element.toLongOrNull()
    }

    private fun JsonObject.getInt(name: String): Int? {
        val element = this.get(name) ?: return null
        return element.toIntOrNull()
    }

    private fun JsonObject.getDouble(name: String): Double? {
        val element = this.get(name) ?: return null
        return element.toDoubleOrNull()
    }

    private fun JsonObject.getBoolean(name: String): Boolean? {
        val element = this.get(name) ?: return null
        return element.toBooleanOrNull()
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }

    private fun JsonElement.toLongOrNull(): Long? = runCatching {
        when {
            isJsonPrimitive && asJsonPrimitive.isNumber -> asLong
            isJsonPrimitive && asJsonPrimitive.isString -> asString.toLong()
            else -> null
        }
    }.getOrNull()

    private fun JsonElement.toIntOrNull(): Int? = runCatching {
        when {
            isJsonPrimitive && asJsonPrimitive.isNumber -> asInt
            isJsonPrimitive && asJsonPrimitive.isString -> asString.toInt()
            else -> null
        }
    }.getOrNull()

    private fun JsonElement.toDoubleOrNull(): Double? = runCatching {
        when {
            isJsonPrimitive && asJsonPrimitive.isNumber -> asDouble
            isJsonPrimitive && asJsonPrimitive.isString -> asString.toDouble()
            else -> null
        }
    }.getOrNull()

    private fun JsonElement.toBooleanOrNull(): Boolean? = runCatching {
        when {
            isJsonPrimitive && asJsonPrimitive.isBoolean -> asBoolean
            isJsonPrimitive && asJsonPrimitive.isString -> asString.toBooleanStrictOrNull()
            else -> null
        }
    }.getOrNull()

    private fun normalizeImageUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return raw
        }
        val base = BuildConfig.BARION_API_BASE_URL.trimEnd('/')
        return if (raw.startsWith("/")) "$base$raw" else "$base/$raw"
    }
}
