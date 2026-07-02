package com.fiw.tools.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BindingDefParsingTest {
    private fun item(extra: String) = ItemConfigParser.parse(
        """{ "id": "x", "base": "minecraft:netherite_sword"$extra }"""
    )

    @Test
    fun absentMeansUnbound() {
        assertNull(item("").binding)
    }

    @Test
    fun parsesFullBinding() {
        val b = item(
            """, "binding": { "mode": "first_pickup", "curse": true, "cursePerTick": 2.5,
                "message": "&5It is yours now.", "blockUse": false }"""
        ).binding
        assertNotNull(b)
        assertEquals("first_pickup", b.mode)
        assertEquals(true, b.curse)
        assertEquals(2.5f, b.cursePerTick)
        assertEquals(false, b.blockUse)
    }

    @Test
    fun defaultsAreSafe() {
        val b = item(""", "binding": { "mode": "first_use" }""").binding
        assertNotNull(b)
        assertEquals(false, b.curse)
        assertEquals(true, b.blockUse)
        assertEquals(1.0f, b.cursePerTick)
    }

    @Test
    fun rejectsUnknownMode() {
        assertFailsWith<IllegalArgumentException> {
            item(""", "binding": { "mode": "first_kiss" }""")
        }
    }
}
