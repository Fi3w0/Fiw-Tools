package com.fiw.tools.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChargesAndCostParsingTest {
    private fun item(extra: String) = ItemConfigParser.parse(
        """{ "id": "x", "base": "minecraft:blaze_rod"$extra }"""
    )

    @Test
    fun parsesChargesShorthandAndObject() {
        assertNull(item("").charges)

        val short = item(""", "charges": 5""").charges
        assertNotNull(short)
        assertEquals(5, short.max)
        assertEquals(5, short.effectiveStart)

        val full = item(""", "charges": { "max": 10, "start": 3, "regenTicks": 100, "regenAmount": 2 }""").charges
        assertNotNull(full)
        assertEquals(10, full.max)
        assertEquals(3, full.effectiveStart)
        assertEquals(100, full.regenTicks)
        assertEquals(2, full.regenAmount)
    }

    @Test
    fun parsesAbilityCostAndCooldownGroup() {
        val def = item(
            """, "abilities": [
                { "type": "blink", "cooldownGroup": "Mobility",
                  "cost": { "charges": 2, "durability": 1, "health": 2.0, "hunger": 1, "xpLevels": 1,
                            "item": "minecraft:ender_pearl", "itemCount": 2 } }
            ]"""
        ).abilities[0]
        assertEquals("Mobility", def.cooldownGroup)
        val cost = def.cost
        assertNotNull(cost)
        assertEquals(2, cost.charges)
        assertEquals(1, cost.durability)
        assertEquals(2.0f, cost.health)
        assertEquals(1, cost.hunger)
        assertEquals(1, cost.xpLevels)
        assertEquals("minecraft:ender_pearl", cost.item)
        assertEquals(2, cost.itemCount)
    }

    @Test
    fun emptyCostBecomesNull() {
        val def = item(""", "abilities": [ { "type": "blink", "cost": {} } ]""").abilities[0]
        assertNull(def.cost)
    }

    @Test
    fun parsesDynamicLoreAndRechargeCharges() {
        val def = item(
            """, "dynamicLore": ["&8Charges: &c{charges}/{max_charges}"],
                 "charges": 4,
                 "rechargeOnKill": { "charges": 2 }"""
        )
        assertEquals(1, def.dynamicLore.size)
        assertEquals(2, def.rechargeOnKill?.charges)
    }

    @Test
    fun rejectsInvalidCharges() {
        assertFailsWith<IllegalArgumentException> { item(""", "charges": { "max": 0 }""") }
        assertFailsWith<IllegalArgumentException> { item(""", "charges": { "max": 5, "start": 9 }""") }
        assertFailsWith<IllegalArgumentException> {
            item(""", "abilities": [ { "type": "blink", "cost": { "charges": -1 } } ]""")
        }
    }
}
