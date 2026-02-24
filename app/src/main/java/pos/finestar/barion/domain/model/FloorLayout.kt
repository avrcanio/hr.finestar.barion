package pos.finestar.barion.domain.model

data class AllowedLayout(
    val id: Long,
    val name: String,
    val isDefault: Boolean
)

data class FloorPlanData(
    val layoutId: Long,
    val layoutName: String,
    val resolvedBy: String? = null,
    val allowedLayouts: List<AllowedLayout> = emptyList(),
    val tables: List<FloorTable> = emptyList()
)
