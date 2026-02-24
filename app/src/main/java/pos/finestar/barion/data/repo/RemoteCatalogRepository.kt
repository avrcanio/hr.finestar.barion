package pos.finestar.barion.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import pos.finestar.barion.api.PosApi
import pos.finestar.barion.domain.model.CatalogProduct
import pos.finestar.barion.domain.model.DrinkCategory
import pos.finestar.barion.domain.model.DrinkCategoryDisplay
import pos.finestar.barion.domain.repo.CatalogRepository

@Singleton
class RemoteCatalogRepository @Inject constructor(
    private val api: PosApi
) : CatalogRepository {

    private data class CacheEntry<T>(
        val value: T,
        val savedAtMillis: Long
    )

    private val categoriesCache = ConcurrentHashMap<String, CacheEntry<List<DrinkCategory>>>()
    private val displayCache = ConcurrentHashMap<Long, CacheEntry<DrinkCategoryDisplay>>()
    private val productsCache = ConcurrentHashMap<String, CacheEntry<List<CatalogProduct>>>()

    private val cacheTtlMillis = 60_000L

    override suspend fun getDrinkCategories(includeInactive: Boolean, level: Int?): List<DrinkCategory> {
        val key = "include_inactive_${if (includeInactive) 1 else 0}_level_${level ?: 0}"
        val cached = categoriesCache[key]
        if (cached != null && isFresh(cached.savedAtMillis)) return cached.value

        return runCatching {
            val payload = api.getDrinkCategories(
                includeInactive = if (includeInactive) 1 else null,
                level = level
            )
            val payloadObject = payload.asJsonObjectOrNull()
            val rawList = when {
                payload.isJsonArray -> payload.asJsonArray
                payloadObject != null -> payloadObject.getArray("results")
                    ?: payloadObject.getArray("categories")
                    ?: payloadObject.getArray("items")
                    ?: JsonArray()
                else -> JsonArray()
            }

            rawList.mapNotNull { it.asJsonObjectOrNull()?.toDrinkCategory() }
                .sortedWith(compareBy<DrinkCategory> { it.sortOrder }.thenBy { it.name })
        }.onSuccess { fresh ->
            categoriesCache[key] = CacheEntry(
                value = fresh,
                savedAtMillis = System.currentTimeMillis()
            )
        }.getOrElse {
            cached?.value ?: throw it
        }
    }

    override suspend fun getDrinkCategoryDisplay(rootId: Long): DrinkCategoryDisplay {
        val cached = displayCache[rootId]
        if (cached != null && isFresh(cached.savedAtMillis)) return cached.value

        return runCatching {
            val payload = api.getDrinkCategoryDisplay(rootId)
            val payloadObject = payload.asJsonObjectOrNull()
            val categories = (payloadObject?.getArray("categories") ?: JsonArray())
                .mapNotNull { it.asJsonObjectOrNull()?.toDrinkCategory() }
                .sortedWith(compareBy<DrinkCategory> { it.sortOrder }.thenBy { it.name })

            DrinkCategoryDisplay(
                rootId = payloadObject?.getLong("root_id") ?: rootId,
                displayLevel = payloadObject?.getInt("display_level") ?: 1,
                categories = categories
            )
        }.onSuccess { fresh ->
            displayCache[rootId] = CacheEntry(
                value = fresh,
                savedAtMillis = System.currentTimeMillis()
            )
        }.getOrElse {
            cached?.value ?: throw it
        }
    }

    override suspend fun searchProducts(query: String?, drinkCategoryId: Long?): List<CatalogProduct> {
        val normalizedQuery = query?.trim().orEmpty()
        val key = "${drinkCategoryId ?: 0L}|${normalizedQuery.lowercase()}"
        val cached = productsCache[key]
        if (cached != null && isFresh(cached.savedAtMillis)) return cached.value

        return runCatching {
            val payload = api.searchProducts(
                query = normalizedQuery.takeIf { it.isNotBlank() },
                drinkCategoryId = drinkCategoryId
            )
            val payloadObject = payload.asJsonObjectOrNull()
            val rawList = when {
                payload.isJsonArray -> payload.asJsonArray
                payloadObject != null -> payloadObject.getArray("results")
                    ?: payloadObject.getArray("products")
                    ?: payloadObject.getArray("items")
                    ?: JsonArray()
                else -> JsonArray()
            }

            rawList.mapNotNull { element ->
                val node = element.asJsonObjectOrNull() ?: return@mapNotNull null
                val id = node.getLong("id")
                    ?: node.getLong("artikl_id")
                    ?: node.getLong("rm_id")
                    ?: return@mapNotNull null
                val name = node.getString("name") ?: node.getString("product_name") ?: "Artikl #$id"
                val price = node.getDouble("unit_price")
                    ?: node.getDouble("price")
                    ?: node.getDouble("price_with_tax")
                    ?: node.getObject("active_price")?.getDouble("price")
                    ?: 0.0
                CatalogProduct(
                    id = id,
                    name = name,
                    code = node.getString("code"),
                    image = node.getString("image"),
                    image46x75 = node.getString("image_46x75"),
                    image125x200 = node.getString("image_125x200"),
                    drinkCategoryId = node.getLong("drink_category_id"),
                    drinkCategoryName = node.getString("drink_category_name"),
                    isSellable = node.getBoolean("is_sellable") ?: true,
                    isStockItem = node.getBoolean("is_stock_item") ?: true,
                    price = price
                )
            }.sortedBy { it.name }
        }.onSuccess { fresh ->
            productsCache[key] = CacheEntry(
                value = fresh,
                savedAtMillis = System.currentTimeMillis()
            )
        }.getOrElse {
            cached?.value ?: throw it
        }
    }

    private fun JsonObject.toDrinkCategory(): DrinkCategory? {
        val id = getLong("id") ?: return null
        return DrinkCategory(
            id = id,
            name = getString("name") ?: "Kategorija #$id",
            parentId = getLong("parent_id"),
            sortOrder = getInt("sort_order") ?: 0
        )
    }

    private fun JsonObject.getObject(name: String): JsonObject? {
        val element = get(name) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    private fun JsonObject.getArray(name: String): JsonArray? {
        val element = get(name) ?: return null
        return if (element.isJsonArray) element.asJsonArray else null
    }

    private fun JsonObject.getString(name: String): String? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else element.asString
    }

    private fun JsonObject.getLong(name: String): Long? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else element.asLong
    }

    private fun JsonObject.getInt(name: String): Int? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else element.asInt
    }

    private fun JsonObject.getDouble(name: String): Double? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else element.asDouble
    }

    private fun JsonObject.getBoolean(name: String): Boolean? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else element.asBoolean
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }

    private fun isFresh(savedAtMillis: Long): Boolean {
        return System.currentTimeMillis() - savedAtMillis <= cacheTtlMillis
    }
}
