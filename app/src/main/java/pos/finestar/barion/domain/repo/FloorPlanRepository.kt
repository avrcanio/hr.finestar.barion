package pos.finestar.barion.domain.repo

import pos.finestar.barion.domain.model.AllowedLayout
import pos.finestar.barion.domain.model.FloorPlanData

interface FloorPlanRepository {
    suspend fun getTables(layoutId: Long? = null): FloorPlanData
    suspend fun getAllowedLayouts(): List<AllowedLayout>
}
