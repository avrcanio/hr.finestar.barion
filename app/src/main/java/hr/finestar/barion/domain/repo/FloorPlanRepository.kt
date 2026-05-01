package hr.finestar.barion.domain.repo

import hr.finestar.barion.domain.model.AllowedLayout
import hr.finestar.barion.domain.model.FloorPlanData
import hr.finestar.barion.domain.model.RuntimeMode

interface FloorPlanRepository {
    suspend fun getTables(layoutId: Long? = null, forceRefresh: Boolean = false): FloorPlanData
    suspend fun getAllowedLayouts(forceRefresh: Boolean = false): List<AllowedLayout>
    suspend fun getRuntimeMode(forceRefresh: Boolean = false): RuntimeMode
}
