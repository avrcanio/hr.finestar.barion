package pos.finestar.barion.sync

object CatalogChangedPushParser {
    private const val TYPE_KEY = "type"
    private const val VERSION_KEY = "catalogVersion"
    private const val CATALOG_CHANGED = "catalog_changed"

    fun parse(data: Map<String, String>): CatalogChangedPush? {
        val type = data[TYPE_KEY]?.trim().orEmpty()
        if (type != CATALOG_CHANGED) return null
        val version = data[VERSION_KEY]?.trim()?.toLongOrNull()
        return CatalogChangedPush(type = type, catalogVersion = version)
    }

    fun shouldTriggerSync(remoteVersion: Long?, lastCatalogVersion: Long): Boolean {
        if (remoteVersion == null) return false
        return remoteVersion > lastCatalogVersion
    }
}

