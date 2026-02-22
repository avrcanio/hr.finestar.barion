package pos.finestar.barion.data.repo

import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.ResponseBody
import pos.finestar.barion.api.PosApi
import pos.finestar.barion.api.model.ApiErrorDto
import pos.finestar.barion.domain.model.FloorTable
import pos.finestar.barion.domain.model.TableStatus
import pos.finestar.barion.domain.repo.FloorPlanRepository
import retrofit2.HttpException

@Singleton
class RemoteFloorPlanRepository @Inject constructor(
    private val api: PosApi,
    private val gson: Gson
) : FloorPlanRepository {

    override suspend fun getTables(): List<FloorTable> {
        return try {
            val response = api.getActiveLayout()
            response.tables.map { table ->
                FloorTable(
                    id = table.tableId,
                    name = table.label,
                    x = table.x,
                    y = table.y,
                    width = table.w,
                    height = table.h,
                    status = TableStatus.FREE
                )
            }
        } catch (httpException: HttpException) {
            if (httpException.code() == 404) {
                val detail = parseErrorDetail(httpException.response()?.errorBody())
                throw IllegalStateException(detail ?: "Aktivni layout nije postavljen.")
            }
            throw httpException
        }
    }

    private fun parseErrorDetail(errorBody: ResponseBody?): String? {
        val raw = errorBody?.string().orEmpty()
        if (raw.isBlank()) return null

        return runCatching {
            gson.fromJson(raw, ApiErrorDto::class.java).detail
        }.getOrNull()
    }
}
