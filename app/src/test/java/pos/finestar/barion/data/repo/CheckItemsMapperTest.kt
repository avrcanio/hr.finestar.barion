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
}
