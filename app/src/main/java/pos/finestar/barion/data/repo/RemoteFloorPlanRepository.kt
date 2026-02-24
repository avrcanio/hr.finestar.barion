package pos.finestar.barion.data.repo

import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.ResponseBody
import pos.finestar.barion.api.PosApi
import pos.finestar.barion.api.model.ApiErrorDto
import pos.finestar.barion.domain.model.AllowedLayout
import pos.finestar.barion.domain.model.FloorTable
import pos.finestar.barion.domain.model.FloorPlanData
import pos.finestar.barion.domain.model.TableStatus
import pos.finestar.barion.domain.repo.FloorPlanRepository
import retrofit2.HttpException

@Singleton
class RemoteFloorPlanRepository @Inject constructor(
    private val api: PosApi,
    private val gson: Gson
) : FloorPlanRepository {

    override suspend fun getTables(layoutId: Long?): FloorPlanData {
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
            )
        } catch (httpException: HttpException) {
            if (httpException.code() == 404) {
                val detail = parseErrorDetail(httpException.response()?.errorBody())
                throw IllegalStateException(detail ?: "Aktivni layout nije postavljen.")
            }
            throw httpException
        }
    }

    override suspend fun getAllowedLayouts(): List<AllowedLayout> {
        return api.getAllowedLayouts().layouts.map { layout ->
            AllowedLayout(
                id = layout.id,
                name = layout.name,
                isDefault = layout.isDefault
            )
        }
    }

    private fun parseErrorDetail(errorBody: ResponseBody?): String? {
        val raw = errorBody?.string().orEmpty()
        if (raw.isBlank()) return null

        return runCatching {
            gson.fromJson(raw, ApiErrorDto::class.java).detail
        }.getOrNull()
    }

    private fun pos.finestar.barion.api.model.TableStatusDto?.toDomainStatus(): TableStatus {
        val raw = this?.status.orEmpty()
        return if (raw == "FREE") TableStatus.FREE else TableStatus.OPEN
    }
}
