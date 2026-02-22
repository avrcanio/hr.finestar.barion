package pos.finestar.barion.domain.model

data class CheckItem(
    val itemId: Long? = null,
    val productId: Long? = null,
    val name: String,
    val qty: Int,
    val price: Double
)
