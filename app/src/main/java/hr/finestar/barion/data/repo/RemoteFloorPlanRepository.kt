package hr.finestar.barion.data.repo

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.ResponseBody
import hr.finestar.barion.api.PosApi
import hr.finestar.barion.api.model.ApiErrorDto
import hr.finestar.barion.data.local.ApiCacheDao
import hr.finestar.barion.data.local.ApiCacheEntity
import hr.finestar.barion.data.local.CatalogSyncDao
import hr.finestar.barion.data.local.CatalogSyncMetadataEntity
import hr.finestar.barion.domain.model.AllowedLayout
import hr.finestar.barion.domain.model.FloorTable
import hr.finestar.barion.domain.model.FloorPlanData
import hr.finestar.barion.domain.model.RuntimeMode
import hr.finestar.barion.domain.model.TableStatus
import hr.finestar.barion.domain.repo.FloorPlanRepository
import retrofit2.HttpException

@Singleton
class RemoteFloorPlanRepository @Inject constructor(
    private val api: PosApi,
    private val gson: Gson,
    private val apiCacheDao: ApiCacheDao,
    private val catalogSyncDao: CatalogSyncDao
) : FloorPlanRepository {

    private val activeLayoutTtlMillis = 60_000L
    private val allowedLayoutsTtlMillis = 5 * 60_000L
    private val runtimeModeTtlMillis = 30_000L

    override suspend fun getTables(layoutId: Long?, forceRefresh: Boolean): FloorPlanData {
        val cacheKey = "active_layout:${layoutId ?: 0L}"
        if (!forceRefresh) {
            val cached = readCacheEntry<FloorPlanData>(
                key = cacheKey,
                ttlMillis = activeLayoutTtlMillis,
                type = FloorPlanData::class.java
            )
            if (cached != null) return cached
        }

        return try {
            val activeLayout = api.getActiveLayout(
                layoutId = layoutId,
                includeAllowed = 1
            )
            val tableStatuses = api.getTableStatuses(activeLayout.layout.id)
                .associateBy { it.tableId }

            val tables = activeLayout.tables.map { table ->
                val statusDto = tableStatuses[table.tableId]
                FloorTable(
                    id = table.tableId,
                    name = table.label,
                    x = table.x,
                    y = table.y,
                    width = table.w,
                    height = table.h,
                    status = statusDto.toDomainStatus(),
                    openCheckId = statusDto?.openCheckId,
                    itemCount = statusDto?.itemCount
                )
            }
            FloorPlanData(
                layoutId = activeLayout.layout.id,
                layoutName = activeLayout.layout.name,
                resolvedBy = activeLayout.resolvedBy,
                allowedLayouts = activeLayout.allowedLayouts.map {
                    AllowedLayout(
                        id = it.id,
                        name = it.name,
                        isDefault = it.isDefault
                    )
                },
                tables = tables
            ).also { fresh -> writeCacheEntry(cacheKey, fresh) }
        } catch (httpException: HttpException) {
            val stale = readCacheEntry<FloorPlanData>(
                key = cacheKey,
                ttlMillis = Long.MAX_VALUE,
                type = FloorPlanData::class.java
            )
            if (stale != null) return stale
            if (httpException.code() == 404) {
                val detail = parseErrorDetail(httpException.response()?.errorBody())
                throw IllegalStateException(detail ?: "Aktivni layout nije postavljen.")
            }
            throw httpException
        }
    }

    override suspend fun getAllowedLayouts(forceRefresh: Boolean): List<AllowedLayout> {
        val cacheKey = "allowed_layouts"
        if (!forceRefresh) {
            val cached = readCacheEntry<List<AllowedLayout>>(
                key = cacheKey,
                ttlMillis = allowedLayoutsTtlMillis,
                type = object : TypeToken<List<AllowedLayout>>() {}.type
            )
            if (cached != null) return cached
        }

        try {
            val fresh = api.getAllowedLayouts().layouts.map { layout ->
                AllowedLayout(
                    id = layout.id,
                    name = layout.name,
                    isDefault = layout.isDefault
                )
            }

            writeCacheEntry(cacheKey, fresh)
            return fresh
        } catch (t: Throwable) {
            return readCacheEntry<List<AllowedLayout>>(
                key = cacheKey,
                ttlMillis = Long.MAX_VALUE,
                type = object : TypeToken<List<AllowedLayout>>() {}.type
            ) ?: throw t
        }
    }

    override suspend fun getRuntimeMode(forceRefresh: Boolean): RuntimeMode {
        val cacheKey = "runtime_mode"
        if (!forceRefresh) {
            val cached = readCacheEntry<RuntimeMode>(
                key = cacheKey,
                ttlMillis = runtimeModeTtlMillis,
                type = RuntimeMode::class.java
            )
            if (cached != null) return cached
        }

        return try {
            val fresh = RuntimeMode.fromRaw(api.getRuntimeMode().activeMode)
            val metadata = catalogSyncDao.getMetadata()
            catalogSyncDao.upsertMetadata(
                (metadata ?: CatalogSyncMetadataEntity()).copy(
                    activeModeRaw = fresh.name.lowercase(),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            writeCacheEntry(cacheKey, fresh)
            fresh
        } catch (t: Throwable) {
            readCacheEntry<RuntimeMode>(
                key = cacheKey,
                ttlMillis = Long.MAX_VALUE,
                type = RuntimeMode::class.java
            ) ?: RuntimeMode.UNKNOWN
        }
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

    private fun parseErrorDetail(errorBody: ResponseBody?): String? {
        val raw = errorBody?.string().orEmpty()
        if (raw.isBlank()) return null

        return runCatching {
            gson.fromJson(raw, ApiErrorDto::class.java).detail
        }.getOrNull()
    }

    private fun hr.finestar.barion.api.model.TableStatusDto?.toDomainStatus(): TableStatus {
        val raw = this?.status.orEmpty()
        return if (raw == "FREE") TableStatus.FREE else TableStatus.OPEN
    }
}
