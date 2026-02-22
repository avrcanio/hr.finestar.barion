package pos.finestar.barion.domain.model

data class CheckSession(
    val checkId: Long,
    val tableId: Long,
    val tableName: String,
    val status: TableStatus,
    val items: List<CheckItem>
)
