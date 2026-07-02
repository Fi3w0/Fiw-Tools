package com.fiw.tools.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AwakeningDefParsingTest {
    private fun item(extra: String) = ItemConfigParser.parse(
        """{ "id": "x", "base": "minecraft:netherite_sword"$extra }"""
    )

    @Test
    fun absentMeansNoAwakening() {
        assertNull(item("").awakening)
    }

    @Test
    fun parsesKillEntityAwakening() {
        val a = item(
            """, "awakening": { "trigger": "kill_entity", "entity": "minecraft:wither",
                "count": 3, "upgradeTo": "x_awakened", "message": "&5It awakens!", "broadcast": true }"""
        ).awakening
        assertNotNull(a)
        assertEquals("kill_entity", a.trigger)
        assertEquals("minecraft:wither", a.entity)
        assertEquals(3.0, a.count)
        assertEquals("x_awakened", a.upgradeTo)
        assertEquals(true, a.broadcast)
    }

    @Test
    fun parsesOtherTriggers() {
        assertEquals("deal_damage",
            item(""", "awakening": { "trigger": "deal_damage", "count": 500, "upgradeTo": "y" }""").awakening?.trigger)
        assertEquals("minecraft:the_end",
            item(""", "awakening": { "trigger": "visit_dimension", "dimension": "minecraft:the_end", "upgradeTo": "y" }""").awakening?.dimension)
        assertEquals("Fi3w0",
            item(""", "awakening": { "trigger": "kill_player", "playerName": "Fi3w0", "upgradeTo": "y" }""").awakening?.playerName)
    }

    @Test
    fun rejectsInvalidAwakenings() {
        assertFailsWith<IllegalArgumentException> { // no upgradeTo
            item(""", "awakening": { "trigger": "kill_player" }""")
        }
        assertFailsWith<IllegalArgumentException> { // kill_entity without entity
            item(""", "awakening": { "trigger": "kill_entity", "upgradeTo": "y" }""")
        }
        assertFailsWith<IllegalArgumentException> { // unknown trigger
            item(""", "awakening": { "trigger": "sleep", "upgradeTo": "y" }""")
        }
        assertFailsWith<IllegalArgumentException> { // visit_dimension without dimension
            item(""", "awakening": { "trigger": "visit_dimension", "upgradeTo": "y" }""")
        }
    }
}
