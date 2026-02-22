package pos.finestar.barion.domain.model

data class CheckSession(
    val checkId: Long,
    val tableId: Long,
    val tableName: String,
    val status: TableStatus,
    val items: List<CheckItem>,
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0
)
