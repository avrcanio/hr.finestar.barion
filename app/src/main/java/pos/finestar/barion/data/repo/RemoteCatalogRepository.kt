package pos.finestar.barion.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton
import pos.finestar.barion.api.PosApi
import pos.finestar.barion.domain.model.CatalogProduct
import pos.finestar.barion.domain.model.DrinkCategory
import pos.finestar.barion.domain.model.DrinkCategoryDisplay
import pos.finestar.barion.domain.repo.CatalogRepository

@Singleton
class RemoteCatalogRepository @Inject constructor(
    private val api: PosApi
) : CatalogRepository {

    override suspend fun getDrinkCategories(includeInactive: Boolean): List<DrinkCategory> {
        val payload = api.getDrinkCategories(includeInactive = if (includeInactive) 1 else null)
        val rawList = payload.getArray("results")
            ?: payload.getArray("categories")
            ?: payload.getArray("items")
            ?: JsonArray()

        return rawList.mapNotNull { it.asJsonObjectOrNull()?.toDrinkCategory() }
            .sortedWith(compareBy<DrinkCategory> { it.sortOrder }.thenBy { it.name })
    }

    override suspend fun getDrinkCategoryDisplay(rootId: Long): DrinkCategoryDisplay {
        val payload = api.getDrinkCategoryDisplay(rootId)
        val categories = (payload.getArray("categories") ?: JsonArray())
            .mapNotNull { it.asJsonObjectOrNull()?.toDrinkCategory() }
            .sortedWith(compareBy<DrinkCategory> { it.sortOrder }.thenBy { it.name })

        return DrinkCategoryDisplay(
            rootId = payload.getLong("root_id") ?: rootId,
            displayLevel = payload.getInt("display_level") ?: 1,
            categories = categories
        )
    }

    override suspend fun searchProducts(query: String?, drinkCategoryId: Long?): List<CatalogProduct> {
        val payload = api.searchProducts(
            query = query?.takeIf { it.isNotBlank() },
            drinkCategoryId = drinkCategoryId
        )
        val rawList = payload.getArray("results")
            ?: payload.getArray("products")
            ?: payload.getArray("items")
            ?: JsonArray()

        return rawList.mapNotNull { element ->
            val node = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val id = node.getLong("id") ?: return@mapNotNull null
            val name = node.getString("name") ?: node.getString("product_name") ?: "Artikl #$id"
            val price = node.getDouble("price")
                ?: node.getDouble("unit_price")
                ?: node.getDouble("price_with_tax")
                ?: node.getObject("active_price")?.getDouble("price")
                ?: 0.0
            CatalogProduct(id = id, name = name, price = price)
        }.sortedBy { it.name }
    }

    private fun JsonObject.toDrinkCategory(): DrinkCategory? {
        val id = getLong("id") ?: return null
        return DrinkCategory(
            id = id,
            name = getString("name") ?: "Kategorija #$id",
            parentId = getLong("parent_id"),
            sortOrder = getInt("sort_order") ?: 0
        )
    }

    private fun JsonObject.getObject(name: String): JsonObject? {
        val element = get(name) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    private fun JsonObject.getArray(name: String): JsonArray? {
        val element = get(name) ?: return null
        return if (element.isJsonArray) element.asJsonArray else null
    }

    private fun JsonObject.getString(name: String): String? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else element.asString
    }

    private fun JsonObject.getLong(name: String): Long? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else element.asLong
    }

    private fun JsonObject.getInt(name: String): Int? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else element.asInt
    }

    private fun JsonObject.getDouble(name: String): Double? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else element.asDouble
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }
}
