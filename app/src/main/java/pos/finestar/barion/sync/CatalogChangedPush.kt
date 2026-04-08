package pos.finestar.barion.sync

data class CatalogChangedPush(
    val type: String,
    val catalogVersion: Long?
)

