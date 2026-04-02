package pos.finestar.barion.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import pos.finestar.barion.api.PosApi
import pos.finestar.barion.api.model.BundlePriceRequestDto
import pos.finestar.barion.api.model.CheckItemModifierInputDto
import pos.finestar.barion.data.local.ApiCacheDao
import pos.finestar.barion.data.local.ApiCacheEntity
import pos.finestar.barion.domain.model.BundlePricePreview
import pos.finestar.barion.domain.model.CatalogBootstrap
import pos.finestar.barion.domain.model.CatalogProduct
import pos.finestar.barion.domain.model.Category
import pos.finestar.barion.domain.model.CategoryDisplay
import pos.finestar.barion.domain.model.ModifierType
import pos.finestar.barion.domain.model.ProductModifierGroup
import pos.finestar.barion.domain.model.ProductModifierOption
import pos.finestar.barion.domain.model.ProductModifiersConfig
import pos.finestar.barion.domain.model.SelectedModifier
import pos.finestar.barion.domain.model.SelectionMode
import pos.finestar.barion.domain.repo.CatalogRepository

@Singleton
class RemoteCatalogRepository @Inject constructor(
    private val api: PosApi,
    private val apiCacheDao: ApiCacheDao,
    private val gson: Gson
) : CatalogRepository {
    private data class CacheEntry<T>(
        val value: T,
        val savedAtMillis: Long
    )

    private val bootstrapCache = ConcurrentHashMap<String, CacheEntry<CatalogBootstrap>>()
    private val displayCache = ConcurrentHashMap<Long, CacheEntry<CategoryDisplay>>()
    private val productsCache = ConcurrentHashMap<String, CacheEntry<List<CatalogProduct>>>()
    private val modifiersCache = ConcurrentHashMap<Long, CacheEntry<ProductModifiersConfig>>()

    private val cacheTtlMillis = 60_000L
    private val productsTtlMillis = 2 * 60 * 1000L

    override suspend fun getCatalogBootstrap(
        includeProducts: Boolean,
        forceRefresh: Boolean
    ): CatalogBootstrap {
        val key = "include_products_${if (includeProducts) 1 else 0}"
        val cached = bootstrapCache[key]
        if (!forceRefresh && cached != null && isFresh(cached.savedAtMillis)) return cached.value

        val roomCacheKey = "catalog_bootstrap:$key"
        if (!forceRefresh) {
            val roomCached = readCacheEntry<CatalogBootstrap>(
                key = roomCacheKey,
                ttlMillis = productsTtlMillis,
                type = CatalogBootstrap::class.java
            )
            if (roomCached != null) {
                bootstrapCache[key] = CacheEntry(
                    value = roomCached,
                    savedAtMillis = System.currentTimeMillis()
                )
                return roomCached
            }
        }

        return runCatching {
            val payload = api.getCatalogBootstrap(
                rootId = null,
                includeProducts = if (includeProducts) 1 else null
            )
            val categories = (payload.getArray("categories") ?: JsonArray())
                .mapNotNull { it.asJsonObjectOrNull()?.toCategory() }
            val selectedCategoryId = payload.getLong("selected_category_id")
                ?.takeIf { selectedId -> categories.any { it.id == selectedId } }
                ?: categories.firstOrNull()?.id
            val products = (payload.getArray("products") ?: JsonArray())
                .mapNotNull { it.asJsonObjectOrNull()?.toCatalogProductOrNull() }
            CatalogBootstrap(
                activeMode = payload.getString("active_mode") ?: "unknown",
                rootId = payload.getLong("root_id") ?: 0L,
                displayLevel = payload.getInt("display_level") ?: 1,
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                products = products
            )
        }.onSuccess { fresh ->
            writeCacheEntry(roomCacheKey, fresh)
            bootstrapCache[key] = CacheEntry(
                value = fresh,
                savedAtMillis = System.currentTimeMillis()
            )
        }.getOrElse { throwable ->
            cached?.value ?: readCacheEntry<CatalogBootstrap>(
                key = roomCacheKey,
                ttlMillis = Long.MAX_VALUE,
                type = CatalogBootstrap::class.java
            ) ?: throw throwable
        }
    }

    override suspend fun getCategoryDisplay(rootId: Long): CategoryDisplay {
        val cached = displayCache[rootId]
        if (cached != null && isFresh(cached.savedAtMillis)) return cached.value

        return runCatching {
            val payload = api.getCategoryDisplay(rootId)
            val payloadObject = payload.asJsonObjectOrNull()
            val categories = (payloadObject?.getArray("categories") ?: JsonArray())
                .mapNotNull { it.asJsonObjectOrNull()?.toCategory() }
                .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.name })

            CategoryDisplay(
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

    override suspend fun searchProducts(
        query: String?,
        categoryId: Long?,
        forceRefresh: Boolean
    ): List<CatalogProduct> {
        val normalizedQuery = query?.trim().orEmpty()
        val key = "${categoryId ?: 0L}|${normalizedQuery.lowercase()}"
        val cached = productsCache[key]
        if (!forceRefresh && cached != null && isFresh(cached.savedAtMillis)) return cached.value

        val roomCacheKey = "products:$key"
        if (!forceRefresh) {
            val roomCached = readCacheEntry<List<CatalogProduct>>(
                key = roomCacheKey,
                ttlMillis = productsTtlMillis,
                type = object : TypeToken<List<CatalogProduct>>() {}.type
            )
            if (roomCached != null) {
                productsCache[key] = CacheEntry(
                    value = roomCached,
                    savedAtMillis = System.currentTimeMillis()
                )
                return roomCached
            }
        }

        try {
            val payload = api.searchProducts(
                query = normalizedQuery.takeIf { it.isNotBlank() },
                categoryId = categoryId,
                sort = "popular"
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

            val fresh = rawList.mapNotNull { element ->
                element.asJsonObjectOrNull()?.toCatalogProductOrNull()
            }

            writeCacheEntry(roomCacheKey, fresh)
            productsCache[key] = CacheEntry(
                value = fresh,
                savedAtMillis = System.currentTimeMillis()
            )
            return fresh
        } catch (t: Throwable) {
            return cached?.value ?: readCacheEntry<List<CatalogProduct>>(
                key = roomCacheKey,
                ttlMillis = Long.MAX_VALUE,
                type = object : TypeToken<List<CatalogProduct>>() {}.type
            ) ?: throw t
        }
    }

    override suspend fun getProductModifiers(
        productId: Long,
        forceRefresh: Boolean
    ): ProductModifiersConfig {
        val cached = modifiersCache[productId]
        if (!forceRefresh && cached != null && isFresh(cached.savedAtMillis)) return cached.value

        val roomCacheKey = "product_modifiers:$productId"
        if (!forceRefresh) {
            val roomCached = readCacheEntry<ProductModifiersConfig>(
                key = roomCacheKey,
                ttlMillis = productsTtlMillis,
                type = ProductModifiersConfig::class.java
            )
            if (roomCached != null) {
                modifiersCache[productId] = CacheEntry(
                    value = roomCached,
                    savedAtMillis = System.currentTimeMillis()
                )
                return roomCached
            }
        }

        try {
            val payload = api.getProductModifiers(artiklId = productId)
            val parsed = payload.toProductModifiersConfig(productId = productId)
            writeCacheEntry(roomCacheKey, parsed)
            modifiersCache[productId] = CacheEntry(
                value = parsed,
                savedAtMillis = System.currentTimeMillis()
            )
            return parsed
        } catch (t: Throwable) {
            return cached?.value ?: readCacheEntry<ProductModifiersConfig>(
                key = roomCacheKey,
                ttlMillis = Long.MAX_VALUE,
                type = ProductModifiersConfig::class.java
            ) ?: ProductModifiersConfig(artiklId = productId)
        }
    }

    override suspend fun previewBundlePrice(
        productId: Long,
        modifiers: List<SelectedModifier>
    ): BundlePricePreview {
        val payload = api.previewBundlePrice(
            artiklId = productId,
            request = BundlePriceRequestDto(
                modifiers = modifiers.map { selected ->
                    CheckItemModifierInputDto(
                        type = selected.type.name.lowercase(),
                        id = selected.id,
                        quantity = selected.quantity
                    )
                }
            )
        )
        return BundlePricePreview(
            artiklId = payload.getLong("artikl_id") ?: productId,
            baseUnitPrice = payload.getDouble("base_unit_price") ?: 0.0,
            mixersDelta = payload.getDouble("mixers_delta") ?: 0.0,
            finalUnitPrice = payload.getDouble("final_unit_price") ?: 0.0
        )
    }

    private fun JsonObject.toCategory(): Category? {
        val id = getLong("id") ?: return null
        return Category(
            id = id,
            name = getString("name") ?: "Kategorija #$id",
            parentId = getLong("parent_id"),
            sortOrder = getInt("sort_order") ?: 0
        )
    }

    private fun JsonObject.toCatalogProductOrNull(): CatalogProduct? {
        val id = getLong("id")
            ?: getLong("artikl_id")
            ?: getLong("rm_id")
            ?: return null
        val name = getString("name") ?: getString("product_name") ?: "Artikl #$id"
        val price = getDouble("unit_price")
            ?: getDouble("price")
            ?: getDouble("price_with_tax")
            ?: getObject("active_price")?.getDouble("price")
            ?: 0.0
        return CatalogProduct(
            id = id,
            name = name,
            code = getString("code"),
            image = getString("image"),
            image46x75 = getString("image_46x75"),
            image125x200 = getString("image_125x200"),
            categoryId = getLong("category_id"),
            categoryName = getString("category_name"),
            isSellable = getBoolean("is_sellable") ?: true,
            isStockItem = getBoolean("is_stock_item") ?: true,
            price = price
        )
    }

    private fun JsonObject.toProductModifiersConfig(productId: Long): ProductModifiersConfig {
        val groupsNode = getArray("groups")
            ?: getArray("modifier_groups")
            ?: getArray("results")
            ?: JsonArray()
        val parsedGroups = groupsNode.mapNotNull { groupElement ->
            val group = groupElement.asJsonObjectOrNull() ?: return@mapNotNull null
            if (group.getBoolean("is_active") == false) return@mapNotNull null
            val groupId = group.getLong("id")
                ?: group.getLong("group_id")
                ?: return@mapNotNull null
            val groupType = ModifierType.fromRaw(group.getString("type"))
            val rawOptions = sequenceOf(
                group.getArray("options"),
                group.getArray("modifier_options"),
                group.getArray("bundle_options")
            ).filterNotNull().flatMap { it.asSequence() }.toList()

            val options = rawOptions.mapNotNull optionMap@{ optionElement ->
                val option = optionElement.asJsonObjectOrNull() ?: return@optionMap null
                if (option.getBoolean("is_active") == false) return@optionMap null
                val optionId = option.getLong("id")
                    ?: option.getLong("option_id")
                    ?: return@optionMap null
                val optionType = ModifierType.fromRaw(option.getString("type") ?: group.getString("type"))
                ProductModifierOption(
                    id = optionId,
                    name = option.getString("name")
                        ?: option.getString("option_name")
                        ?: option.getString("artikl_name")
                        ?: "Opcija #$optionId",
                    code = option.getString("code") ?: option.getString("option_code"),
                    type = optionType,
                    artiklId = option.getLong("artikl_id"),
                    artiklName = option.getString("artikl_name"),
                    priceDelta = option.getDouble("price_delta") ?: 0.0
                )
            }.distinctBy { "${it.type.name}:${it.id}" }

            ProductModifierGroup(
                id = groupId,
                name = group.getString("name") ?: group.getString("group_name") ?: "Grupa #$groupId",
                code = group.getString("code") ?: group.getString("group_code"),
                type = groupType,
                selectionMode = SelectionMode.fromRaw(group.getString("selection_mode")),
                minSelect = group.getInt("min_select_override")
                    ?: group.getInt("min_select")
                    ?: if (group.getBoolean("is_required") == true) 1 else 0,
                maxSelect = group.getInt("max_select_override")
                    ?: group.getInt("max_select"),
                allowNote = group.getBoolean("allow_note") ?: true,
                isRequired = group.getBoolean("is_required") ?: false,
                options = options
            )
        }

        return ProductModifiersConfig(
            artiklId = getLong("artikl_id") ?: productId,
            groups = parsedGroups,
            allowNote = getBoolean("allow_note") ?: true
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

    private suspend fun writeCacheEntry(key: String, value: Any) {
        apiCacheDao.upsert(
            ApiCacheEntity(
                key = key,
                payload = gson.toJson(value),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    private suspend fun <T> readCacheEntry(
        key: String,
        ttlMillis: Long,
        type: java.lang.reflect.Type
    ): T? {
        val entry = apiCacheDao.get(key) ?: return null
        val isValid = System.currentTimeMillis() - entry.updatedAtMillis <= ttlMillis
        if (!isValid) return null
        return runCatching { gson.fromJson<T>(entry.payload, type) }.getOrNull()
    }
}
