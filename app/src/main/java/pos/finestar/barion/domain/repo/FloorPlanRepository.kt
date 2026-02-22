package pos.finestar.barion.domain.repo

import pos.finestar.barion.domain.model.FloorTable

interface FloorPlanRepository {
    suspend fun getTables(): List<FloorTable>
}
