package pos.finestar.barion.domain.model

data class CheckItem(
    val itemId: Long? = null,
    val productId: Long? = null,
    val name: String,
    val imageUrl: String? = null,
    val qty: Int,
    val price: Double,
    val lineType: String = "NORMAL",
    val note: String? = null,
    val displayLines: List<String> = emptyList(),
    val roundNumber: Int? = null,
    val sentToBar: Boolean = false,
    val sentAt: String? = null
)
