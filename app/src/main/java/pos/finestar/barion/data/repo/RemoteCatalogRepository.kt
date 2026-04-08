package pos.finestar.barion.data.repo

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Gson
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import pos.finestar.barion.api.PosApi
import pos.finestar.barion.api.model.BundlePriceRequestDto
import pos.finestar.barion.api.model.CatalogBootstrapDto
import pos.finestar.barion.api.model.CatalogCategoryDto
import pos.finestar.barion.api.model.CatalogLayoutSnapshotDto
import pos.finestar.barion.api.model.CatalogProductDto
import pos.finestar.barion.api.model.CheckItemModifierInputDto
import pos.finestar.barion.data.local.ApiCacheDao
import pos.finestar.barion.data.local.ApiCacheEntity
import pos.finestar.barion.data.local.CatalogCategoryEntity
import pos.finestar.barion.data.local.CatalogLayoutSnapshotEntity
import pos.finestar.barion.data.local.CatalogProductEntity
import pos.finestar.barion.data.local.CatalogSyncDao
import pos.finestar.barion.data.local.CatalogSyncMetadataEntity
import pos.finestar.barion.data.local.ProductModifierCacheEntity
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
import pos.finestar.barion.sync.CatalogSyncGate

@Singleton
class RemoteCatalogRepository @Inject constructor(
    private val api: PosApi,
    private val apiCacheDao: ApiCacheDao,
    private val catalogSyncDao: CatalogSyncDao,
    private val gson: Gson
) : CatalogRepository {
    private data class CacheEntry<T>(
        val value: T,
        val savedAtMillis: Long
    )

    private data class BootstrapMeta(
        val catalogVersion: Long,
        val activeMode: String,
        val rootId: Long,
        val displayLevel: Int,
        val selectedCategoryId: Long?
    )

    private val displayCache = ConcurrentHashMap<Long, CacheEntry<CategoryDisplay>>()
    private val syncGate = CatalogSyncGate()
    private val cacheTtlMillis = 60_000L

    override suspend fun syncCatalog(forceBootstrap: Boolean) {
        val before = syncGate.state()
        Log.d(
            TAG,
            "syncCatalog requested forceBootstrap=$forceBootstrap gateRunning=${before.isSyncRunning} pending=${before.pendingRefresh}"
        )
        var requestedForceBootstrap = forceBootstrap
        syncGate.requestSync {
            Log.d(
                TAG,
                "syncCatalog entered gate forceBootstrap=$requestedForceBootstrap"
            )
            if (requestedForceBootstrap) {
                Log.d(TAG, "syncCatalog path=bootstrap (forced)")
                runBootstrapSync()
                requestedForceBootstrap = false
                return@requestSync
            }
            val metadata = catalogSyncDao.getMetadata()
            if (metadata == null || metadata.lastCatalogVersion <= 0L) {
                Log.d(
                    TAG,
                    "syncCatalog path=bootstrap (missing/invalid metadata) lastCatalogVersion=${metadata?.lastCatalogVersion}"
                )
                runBootstrapSync()
            } else {
                Log.d(TAG, "syncCatalog path=delta startVersion=${metadata.lastCatalogVersion}")
                runDeltaSync(startVersion = metadata.lastCatalogVersion)
            }
            requestedForceBootstrap = false
        }
        val after = syncGate.state()
        Log.d(
            TAG,
            "syncCatalog finished gateRunning=${after.isSyncRunning} pending=${after.pendingRefresh}"
        )
    }

    internal fun syncStateForDebug(): CatalogSyncGate.State = syncGate.state()

    override suspend fun getCatalogBootstrap(
        includeProducts: Boolean,
        forceRefresh: Boolean
    ): CatalogBootstrap {
        if (forceRefresh) {
            syncCatalog(forceBootstrap = false)
        }

        val categories = catalogSyncDao.getCategories().map { it.toDomain() }
        val products = if (includeProducts) {
            catalogSyncDao.getProductsByCategory(categoryId = null).map { it.toDomain() }
        } else {
            emptyList()
        }

        if (categories.isEmpty() && products.isEmpty()) {
            syncCatalog(forceBootstrap = true)
            return getCatalogBootstrap(includeProducts = includeProducts, forceRefresh = false)
        }

        val meta = readBootstrapMeta()
        val selectedCategoryId = meta?.selectedCategoryId
            ?.takeIf { selectedId -> categories.any { it.id == selectedId } }
            ?: categories.firstOrNull()?.id

        return CatalogBootstrap(
            catalogVersion = meta?.catalogVersion ?: (catalogSyncDao.getMetadata()?.lastCatalogVersion ?: 0L),
            activeMode = meta?.activeMode ?: "unknown",
            rootId = meta?.rootId ?: 0L,
            displayLevel = meta?.displayLevel ?: 1,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            products = products
        )
    }

    override suspend fun getCategoryDisplay(rootId: Long): CategoryDisplay {
        val cached = displayCache[rootId]
        if (cached != null && isFresh(cached.savedAtMillis)) return cached.value

        val categories = catalogSyncDao.getCategories().map { it.toDomain() }
        if (categories.isNotEmpty()) {
            return CategoryDisplay(
                rootId = rootId,
                displayLevel = 1,
                categories = categories.sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.name })
            ).also { fresh ->
                displayCache[rootId] = CacheEntry(value = fresh, savedAtMillis = System.currentTimeMillis())
            }
        }

        return runCatching {
            val payload = api.getCategoryDisplay(rootId)
            val payloadObject = payload.asJsonObjectOrNull()
            val parsed = (payloadObject?.getArray("categories") ?: JsonArray())
                .mapNotNull { it.asJsonObjectOrNull()?.toCategory() }
                .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.name })

            CategoryDisplay(
                rootId = payloadObject?.getLong("root_id") ?: rootId,
                displayLevel = payloadObject?.getInt("display_level") ?: 1,
                categories = parsed
            )
        }.onSuccess { fresh ->
            displayCache[rootId] = CacheEntry(value = fresh, savedAtMillis = System.currentTimeMillis())
        }.getOrElse {
            cached?.value ?: throw it
        }
    }

    override suspend fun searchProducts(
        query: String?,
        categoryId: Long?,
        forceRefresh: Boolean
    ): List<CatalogProduct> {
        if (forceRefresh) {
            syncCatalog(forceBootstrap = false)
        }

        val normalizedQuery = query?.trim().orEmpty()
        val local = if (normalizedQuery.isBlank()) {
            catalogSyncDao.getProductsByCategory(categoryId = categoryId)
        } else {
            catalogSyncDao.searchProducts(pattern = "%${escapeLike(normalizedQuery)}%")
                .filter { categoryId == null || it.categoryId == categoryId }
        }
        if (local.isNotEmpty()) {
            Log.d(
                TAG,
                "searchProducts local-hit query='${normalizedQuery}' categoryId=$categoryId count=${local.size} forceRefresh=$forceRefresh"
            )
            return local.map { it.toDomain() }
        }

        Log.d(
            TAG,
            "searchProducts local-miss query='${normalizedQuery}' categoryId=$categoryId -> remote /products/search fallback"
        )
        val remote = fetchProductsFromSearchApi(
            query = normalizedQuery.takeIf { it.isNotBlank() },
            categoryId = categoryId
        )
        if (remote.isNotEmpty()) {
            catalogSyncDao.upsertProducts(remote)
            Log.d(
                TAG,
                "searchProducts remote-hit query='${normalizedQuery}' categoryId=$categoryId upserted=${remote.size}"
            )
        } else {
            Log.d(
                TAG,
                "searchProducts remote-empty query='${normalizedQuery}' categoryId=$categoryId (no bootstrap fallback)"
            )
        }

        return remote.map { it.toDomain() }
    }

    private suspend fun fetchProductsFromSearchApi(
        query: String?,
        categoryId: Long?
    ): List<CatalogProductEntity> {
        val payload = api.searchProducts(
            query = query,
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

        return rawList.mapNotNull { element ->
            element.asJsonObjectOrNull()?.toCatalogProductEntityOrNull()
        }
    }

    private fun JsonObject.toCatalogProductEntityOrNull(): CatalogProductEntity? {
        val id = getLong("id")
            ?: getLong("artikl_id")
            ?: getLong("rm_id")
            ?: return null
        val nameResolved = getString("name")
            ?: getString("product_name")
            ?: "Artikl #$id"
        val priceResolved = sequenceOf(
            getString("unit_price"),
            getString("price"),
            getString("price_with_tax"),
            getObject("active_price")?.getString("price")
        ).mapNotNull { it?.toDoubleOrNull() }.firstOrNull() ?: 0.0

        return CatalogProductEntity(
            id = id,
            rmId = getLong("rm_id"),
            name = nameResolved,
            code = getString("code"),
            image = getString("image"),
            image46x75 = getString("image_46x75"),
            image125x200 = getString("image_125x200"),
            thumbnailUrl = getString("thumbnail_url"),
            imageUrl = getString("image_url"),
            imageVersion = getLong("image_version"),
            modifierVersion = getLong("modifier_version"),
            categoryId = getLong("category_id"),
            categoryName = getString("category_name"),
            isSellable = getBoolean("is_sellable") ?: true,
            isStockItem = getBoolean("is_stock_item") ?: true,
            price = priceResolved,
            taxRate = getString("tax_rate")?.toDoubleOrNull(),
            popularityScore = getString("popularity_score")?.toDoubleOrNull()
        )
    }

    override suspend fun getProductModifiers(
        productId: Long,
        expectedModifierVersion: Long?,
        forceRefresh: Boolean
    ): ProductModifiersConfig {
        val localCache = catalogSyncDao.getModifierCache(productId)
        if (!forceRefresh && localCache != null && localCache.isCompatibleWith(expectedModifierVersion)) {
            return runCatching {
                gson.fromJson(localCache.payloadJson, ProductModifiersConfig::class.java)
            }.getOrNull() ?: ProductModifiersConfig(artiklId = productId, modifierVersion = expectedModifierVersion)
        }

        return try {
            val payload = api.getProductModifiers(artiklId = productId)
            val parsed = payload.toProductModifiersConfig(
                productId = productId,
                fallbackModifierVersion = expectedModifierVersion
            )
            catalogSyncDao.upsertModifierCache(
                ProductModifierCacheEntity(
                    productId = productId,
                    modifierVersion = parsed.modifierVersion,
                    payloadJson = gson.toJson(parsed),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            parsed
        } catch (t: Throwable) {
            if (localCache != null) {
                runCatching { gson.fromJson(localCache.payloadJson, ProductModifiersConfig::class.java) }
                    .getOrNull()
                    ?.copy(modifierVersion = localCache.modifierVersion)
                    ?: ProductModifiersConfig(artiklId = productId, modifierVersion = localCache.modifierVersion)
            } else {
                ProductModifiersConfig(artiklId = productId, modifierVersion = expectedModifierVersion)
            }
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

    private suspend fun runBootstrapSync() {
        Log.d(TAG, "runBootstrapSync start includeProducts=1")
        val payload = api.getCatalogBootstrap(rootId = null, includeProducts = 1)
        val categories = payload.categories.map { it.toEntity() }
        val products = payload.products.mapNotNull { it.toEntityOrNull() }
        Log.d(
            TAG,
            "runBootstrapSync response catalogVersion=${payload.catalogVersion} categories=${categories.size} products=${products.size}"
        )

        catalogSyncDao.clearCategories()
        catalogSyncDao.clearProducts()
        if (categories.isNotEmpty()) catalogSyncDao.upsertCategories(categories)
        if (products.isNotEmpty()) catalogSyncDao.upsertProducts(products)

        val metadata = CatalogSyncMetadataEntity(
            id = CatalogSyncMetadataEntity.SINGLETON_ID,
            lastCatalogVersion = payload.catalogVersion,
            activeModeRaw = normalizeRuntimeModeRaw(payload.activeMode),
            inFlightTargetVersion = null,
            inFlightAppliedThroughVersion = null,
            updatedAtMillis = System.currentTimeMillis()
        )
        catalogSyncDao.upsertMetadata(metadata)
        writeBootstrapMeta(payload)
        Log.d(
            TAG,
            "runBootstrapSync metadata committed lastCatalogVersion=${metadata.lastCatalogVersion}"
        )

        // Prime full layout/category/product state from canonical delta source.
        Log.d(TAG, "runBootstrapSync followup delta from afterVersion=0")
        runDeltaSync(startVersion = 0L)
    }

    private suspend fun runDeltaSync(startVersion: Long) {
        var afterVersion = startVersion
        var targetVersion: Long? = null
        var loopGuard = 0
        val persistedModeRaw = catalogSyncDao.getMetadata()?.activeModeRaw
        Log.d(TAG, "runDeltaSync start startVersion=$startVersion")

        while (loopGuard < 100) {
            loopGuard += 1
            Log.d(
                TAG,
                "runDeltaSync page request page=$loopGuard afterVersion=$afterVersion targetVersion=$targetVersion limit=$DELTA_LIMIT"
            )
            val response = api.getCatalogChanges(
                afterVersion = afterVersion,
                targetVersion = targetVersion,
                limit = DELTA_LIMIT
            )
            Log.d(
                TAG,
                "runDeltaSync page response page=$loopGuard requiresFullSync=${response.requiresFullSync} base=${response.baseVersion} applied=${response.appliedThroughVersion} target=${response.targetVersion} catalog=${response.catalogVersion} hasMore=${response.hasMore} layoutsU=${response.layouts.updated.size} layoutsD=${response.layouts.deleted.size} categoriesU=${response.categories.updated.size} categoriesD=${response.categories.deleted.size} productsU=${response.products.updated.size} productsD=${response.products.deleted.size}"
            )

            if (response.requiresFullSync) {
                if (startVersion == 0L) return
                Log.w(
                    TAG,
                    "runDeltaSync requiresFullSync=true startVersion=$startVersion currentAfter=$afterVersion -> triggering bootstrap fallback"
                )
                runBootstrapSync()
                return
            }

            if (response.layouts.updated.isNotEmpty()) {
                val layouts = response.layouts.updated.map { it.toEntity(gson) }
                catalogSyncDao.upsertLayoutSnapshots(layouts)
            }
            if (response.layouts.deleted.isNotEmpty()) {
                catalogSyncDao.deleteLayoutSnapshots(response.layouts.deleted)
            }

            if (response.categories.updated.isNotEmpty()) {
                catalogSyncDao.upsertCategories(response.categories.updated.map { it.toEntity() })
            }
            if (response.categories.deleted.isNotEmpty()) {
                catalogSyncDao.deleteCategories(response.categories.deleted)
            }

            if (response.products.updated.isNotEmpty()) {
                val mapped = response.products.updated.mapNotNull { it.toEntityOrNull() }
                if (mapped.isNotEmpty()) catalogSyncDao.upsertProducts(mapped)
                response.products.updated.forEach { dto ->
                    catalogSyncDao.deleteModifierCache(dto.id)
                }
                Log.d(
                    TAG,
                    "runDeltaSync invalidated modifier cache for updated products count=${response.products.updated.size}"
                )
            }
            if (response.products.deleted.isNotEmpty()) {
                catalogSyncDao.deleteProducts(response.products.deleted)
                response.products.deleted.forEach { productId ->
                    catalogSyncDao.deleteModifierCache(productId)
                }
                Log.d(
                    TAG,
                    "runDeltaSync deleted products and modifier cache ids=${response.products.deleted}"
                )
            }

            val nextLastCatalogVersion = if (response.hasMore) {
                afterVersion
            } else {
                response.targetVersion.takeIf { it > 0L } ?: response.catalogVersion
            }
            catalogSyncDao.upsertMetadata(
                CatalogSyncMetadataEntity(
                    id = CatalogSyncMetadataEntity.SINGLETON_ID,
                    lastCatalogVersion = nextLastCatalogVersion,
                    activeModeRaw = persistedModeRaw,
                    inFlightTargetVersion = if (response.hasMore) response.targetVersion else null,
                    inFlightAppliedThroughVersion = if (response.hasMore) response.appliedThroughVersion else null,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            Log.d(
                TAG,
                "runDeltaSync metadata update page=$loopGuard nextLastCatalogVersion=$nextLastCatalogVersion inFlightTarget=${if (response.hasMore) response.targetVersion else null} inFlightApplied=${if (response.hasMore) response.appliedThroughVersion else null}"
            )

            if (!response.hasMore) {
                Log.d(TAG, "runDeltaSync complete no-more-pages finalLastCatalogVersion=$nextLastCatalogVersion")
                return
            }
            if (response.appliedThroughVersion <= afterVersion) {
                Log.w(
                    TAG,
                    "runDeltaSync abort non-advancing cursor appliedThroughVersion=${response.appliedThroughVersion} afterVersion=$afterVersion"
                )
                return
            }

            targetVersion = response.targetVersion
            afterVersion = response.appliedThroughVersion
        }
        Log.w(TAG, "runDeltaSync abort loopGuard reached=$loopGuard startVersion=$startVersion")
    }

    private fun CatalogCategoryDto.toEntity(): CatalogCategoryEntity {
        return CatalogCategoryEntity(
            id = id,
            name = name ?: "Kategorija #$id",
            parentId = parentId,
            sortOrder = sortOrder ?: 0,
            popularityScore = popularityScore?.toDoubleOrNull()
        )
    }

    private fun CatalogProductDto.toEntityOrNull(): CatalogProductEntity? {
        val nameResolved = name?.takeIf { it.isNotBlank() } ?: "Artikl #$id"
        val priceResolved = sequenceOf(unitPrice, price, priceWithTax)
            .mapNotNull { it?.toDoubleOrNull() }
            .firstOrNull()
            ?: 0.0

        return CatalogProductEntity(
            id = id,
            rmId = rmId,
            name = nameResolved,
            code = code,
            image = image,
            image46x75 = image46x75,
            image125x200 = image125x200,
            thumbnailUrl = thumbnailUrl,
            imageUrl = imageUrl,
            imageVersion = imageVersion,
            modifierVersion = modifierVersion,
            categoryId = categoryId,
            categoryName = categoryName,
            isSellable = isSellable ?: true,
            isStockItem = isStockItem ?: true,
            price = priceResolved,
            taxRate = taxRate?.toDoubleOrNull(),
            popularityScore = popularityScore?.toDoubleOrNull()
        )
    }

    private fun CatalogLayoutSnapshotDto.toEntity(gson: Gson): CatalogLayoutSnapshotEntity {
        return CatalogLayoutSnapshotEntity(
            id = id,
            name = name ?: "Layout #$id",
            isActive = isActive ?: true,
            updatedAt = updatedAt,
            zonesJson = gson.toJson(zones),
            tablesJson = gson.toJson(tables)
        )
    }

    private fun CatalogCategoryEntity.toDomain(): Category {
        return Category(
            id = id,
            name = name,
            parentId = parentId,
            sortOrder = sortOrder
        )
    }

    private fun CatalogProductEntity.toDomain(): CatalogProduct {
        return CatalogProduct(
            id = id,
            rmId = rmId,
            name = name,
            code = code,
            image = image,
            image46x75 = image46x75,
            image125x200 = image125x200,
            thumbnailUrl = thumbnailUrl,
            imageUrl = imageUrl,
            imageVersion = imageVersion,
            modifierVersion = modifierVersion,
            categoryId = categoryId,
            categoryName = categoryName,
            isSellable = isSellable,
            isStockItem = isStockItem,
            price = price,
            taxRate = taxRate,
            popularityScore = popularityScore
        )
    }

    private fun ProductModifierCacheEntity.isCompatibleWith(expectedModifierVersion: Long?): Boolean {
        if (expectedModifierVersion == null) return true
        return modifierVersion == expectedModifierVersion
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

    private fun JsonObject.toProductModifiersConfig(
        productId: Long,
        fallbackModifierVersion: Long?
    ): ProductModifiersConfig {
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
            modifierVersion = getLong("modifier_version") ?: fallbackModifierVersion,
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
        return if (element.isJsonNull) null else runCatching { element.asLong }.getOrNull()
    }

    private fun JsonObject.getInt(name: String): Int? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else runCatching { element.asInt }.getOrNull()
    }

    private fun JsonObject.getDouble(name: String): Double? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else runCatching { element.asDouble }.getOrNull()
    }

    private fun JsonObject.getBoolean(name: String): Boolean? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else runCatching { element.asBoolean }.getOrNull()
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }

    private fun isFresh(savedAtMillis: Long): Boolean {
        return System.currentTimeMillis() - savedAtMillis <= cacheTtlMillis
    }

    private suspend fun writeBootstrapMeta(bootstrap: CatalogBootstrapDto) {
        val meta = BootstrapMeta(
            catalogVersion = bootstrap.catalogVersion,
            activeMode = bootstrap.activeMode ?: "unknown",
            rootId = bootstrap.rootId ?: 0L,
            displayLevel = bootstrap.displayLevel ?: 1,
            selectedCategoryId = bootstrap.selectedCategoryId
        )
        apiCacheDao.upsert(
            ApiCacheEntity(
                key = BOOTSTRAP_META_KEY,
                payload = gson.toJson(meta),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    private suspend fun readBootstrapMeta(): BootstrapMeta? {
        val entry = apiCacheDao.get(BOOTSTRAP_META_KEY) ?: return null
        return runCatching {
            gson.fromJson(entry.payload, BootstrapMeta::class.java)
        }.getOrNull()
    }

    private fun escapeLike(input: String): String {
        return input.replace("%", "\\%").replace("_", "\\_")
    }

    private fun normalizeRuntimeModeRaw(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            "day" -> "day"
            "night" -> "night"
            else -> "unknown"
        }
    }

    companion object {
        private const val TAG: String = "CatalogSyncRepo"
        private const val BOOTSTRAP_META_KEY: String = "catalog_bootstrap_meta"
        private const val DELTA_LIMIT: Int = 200
    }
}
