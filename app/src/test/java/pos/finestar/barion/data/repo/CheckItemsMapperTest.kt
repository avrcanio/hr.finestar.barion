package pos.finestar.barion.data.repo

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckItemsMapperTest {

    @Test
    fun `maps check items payload into domain session`() {
        val json = JsonParser.parseString(
            """
            {
              "check": { "id": 21, "table_id": 7, "status": "OPEN" },
              "items": [
                { "product_name": "Espresso", "qty": 2, "unit_price": 2.5 },
                { "product_name": "Water", "qty": 1, "unit_price": 1.2 }
              ],
              "totals": { "subtotal": 6.2, "tax": 1.24, "total": 7.44 }
            }
            """.trimIndent()
        ).asJsonObject

        val mapped = CheckItemsMapper.toCheckSession(json)

        assertEquals(21L, mapped.checkId)
        assertEquals(7L, mapped.tableId)
        assertEquals(2, mapped.items.size)
        assertEquals("Espresso", mapped.items.first().name)
        assertEquals(6.2, mapped.subtotal, 0.0001)
        assertEquals(7.44, mapped.total, 0.0001)
    }

    @Test
    fun `maps stale payload variant with check_items and quantity price keys`() {
        val json = JsonParser.parseString(
            """
            {
              "id": 30,
              "table_id": 9,
              "status": "OPEN",
              "check_items": [
                { "item_id": 501, "product_id": 33, "name": "Tonic", "quantity": 3, "price": 3.5 }
              ],
              "subtotal": 10.5,
              "tax": 2.1,
              "total": 12.6
            }
            """.trimIndent()
        ).asJsonObject

        val mapped = CheckItemsMapper.toCheckSession(json)

        assertEquals(30L, mapped.checkId)
        assertEquals(9L, mapped.tableId)
        assertEquals(1, mapped.items.size)
        assertEquals(501L, mapped.items.first().itemId)
        assertEquals(33L, mapped.items.first().productId)
        assertEquals(3, mapped.items.first().qty)
        assertEquals(12.6, mapped.total, 0.0001)
    }
}
