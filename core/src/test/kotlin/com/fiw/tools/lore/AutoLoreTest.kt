package com.fiw.tools.lore

import com.fiw.tools.config.ItemConfigParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoLoreTest {
    @Test
    fun linesForActiveAndPassiveAbilities() {
        val def = ItemConfigParser.parse(
            """
            {
              "id": "storm", "base": "minecraft:diamond_sword", "autoLore": true, "bonusVsBosses": 1.5,
              "abilities": [
                { "type": "lightning_rain", "trigger": "on_right_click", "cooldownTicks": 120 },
                { "type": "ignite", "trigger": "on_attack", "chance": 0.25, "cooldownTicks": 90 },
                { "type": "passive_buff", "trigger": "while_held", "cooldownTicks": 20, "params": { "loreName": "Haste" } },
                { "type": "secret", "trigger": "on_kill", "params": { "hideInLore": true } },
                { "type": "fiw:mega-blast", "trigger": "on_boss_phase", "cooldownTicks": 1500 }
              ]
            }
            """.trimIndent()
        )
        assertEquals(true, def.autoLore)
        assertEquals(1.5f, def.bonusVsBosses)
        assertEquals(1f, def.bonusVsMinions)
        assertEquals(
            listOf(
                "&7Right-click: &fLightning Rain &8(6s cooldown)",
                "&7On hit: &fIgnite &8(4.5s cooldown, 25% chance)",
                "&7While held: &fHaste",
                "&7Boss phase change: &fMega Blast &8(1m 15s cooldown)"
            ),
            AutoLore.lines(def)
        )
    }

    @Test
    fun autoLoreDefaultsToUnset() {
        val def = ItemConfigParser.parse("""{ "id": "a", "base": "minecraft:stick" }""")
        assertNull(def.autoLore)
        assertEquals("1m", AutoLore.formatTicks(1200))
    }
}
