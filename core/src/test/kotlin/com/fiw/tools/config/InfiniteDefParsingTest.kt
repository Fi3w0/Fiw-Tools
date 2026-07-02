package com.fiw.tools.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InfiniteDefParsingTest {
    private fun item(extra: String) = ItemConfigParser.parse(
        """{ "id": "x", "base": "minecraft:apple"$extra }"""
    )

    @Test
    fun absentMeansVanillaConsumption() {
        assertNull(item("").infinite)
    }

    @Test
    fun stringShorthandAndNormalAlias() {
        assertEquals("keep", item(""", "infinite": "keep"""").infinite?.mode)
        assertEquals("keep", item(""", "infinite": "normal"""").infinite?.mode)
    }

    @Test
    fun objectFormParsesAllModes() {
        val dmg = item(""", "infinite": { "mode": "damage", "damagePerUse": 5 }""").infinite
        assertNotNull(dmg)
        assertEquals("damage", dmg.mode)
        assertEquals(5, dmg.damagePerUse)

        val rep = item(""", "infinite": { "mode": "replace", "replaceWith": "minecraft:bowl", "replaceCount": 2 }""").infinite
        assertNotNull(rep)
        assertEquals("replace", rep.mode)
        assertEquals("minecraft:bowl", rep.replaceWith)
        assertEquals(2, rep.replaceCount)
    }

    @Test
    fun rejectsBadConfigs() {
        assertFailsWith<IllegalArgumentException> { item(""", "infinite": "forever"""") }
        assertFailsWith<IllegalArgumentException> { item(""", "infinite": { "mode": "replace" }""") }
    }
}
