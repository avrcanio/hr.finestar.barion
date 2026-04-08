package pos.finestar.barion.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogChangedPushParserTest {
    @Test
    fun `parse accepts valid catalog_changed payload`() {
        val parsed = CatalogChangedPushParser.parse(
            mapOf(
                "type" to "catalog_changed",
                "catalogVersion" to "12"
            )
        )

        assertNotNull(parsed)
        assertEquals("catalog_changed", parsed?.type)
        assertEquals(12L, parsed?.catalogVersion)
    }

    @Test
    fun `parse ignores unknown type`() {
        val parsed = CatalogChangedPushParser.parse(
            mapOf(
                "type" to "other_event",
                "catalogVersion" to "12"
            )
        )

        assertNull(parsed)
    }

    @Test
    fun `parse keeps null version for malformed version`() {
        val parsed = CatalogChangedPushParser.parse(
            mapOf(
                "type" to "catalog_changed",
                "catalogVersion" to "abc"
            )
        )

        assertNotNull(parsed)
        assertNull(parsed?.catalogVersion)
    }

    @Test
    fun `shouldTriggerSync true only when remote is newer`() {
        assertTrue(CatalogChangedPushParser.shouldTriggerSync(remoteVersion = 13L, lastCatalogVersion = 12L))
        assertFalse(CatalogChangedPushParser.shouldTriggerSync(remoteVersion = 12L, lastCatalogVersion = 12L))
        assertFalse(CatalogChangedPushParser.shouldTriggerSync(remoteVersion = 11L, lastCatalogVersion = 12L))
        assertFalse(CatalogChangedPushParser.shouldTriggerSync(remoteVersion = null, lastCatalogVersion = 12L))
    }
}

