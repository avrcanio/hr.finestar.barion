package hr.finestar.barion.domain.model

data class FloorTable(
    val id: Long,
    val name: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val status: TableStatus,
    val openCheckId: Long? = null,
    val itemCount: Int? = null
)
