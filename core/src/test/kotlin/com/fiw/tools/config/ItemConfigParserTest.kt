package com.fiw.tools.config

import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path

class ItemConfigParserTest {
    @Test
    fun parsesRepresentativeItem() {
        val def = ItemConfigParser.parse(
            """
            {
              "id": "storm_blade",
              "base": "minecraft:diamond_sword",
              "displayName": "&b&lStorm Blade",
              "lore": ["&7Right-click to call lightning."],
              "enchantments": { "minecraft:sharpness": 7 },
              "attributes": [
                { "type": "minecraft:attack_damage", "slot": "mainhand", "amount": 4.0, "operation": "add_value" }
              ],
              "abilities": [
                {
                  "type": "lightning_strike",
                  "trigger": "on_right_click",
                  "cooldownTicks": 120,
                  "params": { "range": 18, "damage": 10 }
                }
              ],
              "cursed": true,
              "curseWhitelist": ["Fiw"],
              "curseSettings": { "perTick": 2.5, "checksEnderChest": true },
              "imbueLimit": 2,
              "rechargeOnKill": {
                "hand": "off",
                "entity": "minecraft:zombie",
                "cooldownTicks": 80,
                "cooldownPercent": 0.5,
                "cooldownAbilities": ["blink"],
                "cooldownTriggers": ["on_right_click"],
                "repairDamage": 12,
                "repairPercent": 0.1,
                "addMaxDamage": 5,
                "maxDamageCap": 400,
                "unbreakable": true
              }
            }
            """.trimIndent()
        )

        assertEquals("storm_blade", def.id)
        assertEquals("minecraft:diamond_sword", def.base)
        assertEquals("&b&lStorm Blade", def.displayName)
        assertEquals(7, def.enchantments["minecraft:sharpness"])
        assertEquals(1, def.attributes.size)
        assertEquals("lightning_strike", def.abilities.single().type)
        assertEquals(120, def.abilities.single().cooldownTicks)
        assertEquals(18, def.abilities.single().params.get("range").asInt)
        assertTrue(def.cursed)
        assertEquals(listOf("Fiw"), def.curseWhitelist)
        assertEquals(2.5f, def.curseSettings.perTick)
        assertTrue(def.curseSettings.checksEnderChest)
        assertEquals(2, def.imbueLimit)
        assertNotNull(def.rechargeOnKill)
        assertEquals("entities", def.rechargeOnKill.target)
        assertEquals("off", def.rechargeOnKill.hand)
        assertEquals(listOf("minecraft:zombie"), def.rechargeOnKill.entities)
        assertEquals(80, def.rechargeOnKill.cooldownTicks)
        assertEquals(0.5f, def.rechargeOnKill.cooldownPercent)
        assertEquals(listOf("blink"), def.rechargeOnKill.cooldownAbilityTypes)
        assertEquals(listOf("on_right_click"), def.rechargeOnKill.cooldownTriggers)
        assertEquals(12, def.rechargeOnKill.repairDamage)
        assertEquals(5, def.rechargeOnKill.addMaxDamage)
        assertEquals(400, def.rechargeOnKill.maxDamageCap)
        assertTrue(def.rechargeOnKill.unbreakable)
    }

    @Test
    fun parsesRechargeOnKillAnyEntityShortcut() {
        val def = ItemConfigParser.parse(
            """
            {
              "id": "offhand_repairer",
              "base": "minecraft:shield",
              "rechargeOnKill": {
                "slot": "any",
                "entity": "any",
                "repairDamage": 1
              }
            }
            """.trimIndent()
        )

        assertNotNull(def.rechargeOnKill)
        assertEquals("any", def.rechargeOnKill.hand)
        assertEquals("any", def.rechargeOnKill.target)
        assertTrue(def.rechargeOnKill.entities.isEmpty())
        assertEquals(1, def.rechargeOnKill.repairDamage)
    }

    @Test
    fun rejectsRechargeMobsTargetWithPlayersFilter() {
        val error = runCatching {
            ItemConfigParser.parse(
                """
                {
                  "id": "contradiction",
                  "base": "minecraft:iron_sword",
                  "rechargeOnKill": { "target": "mobs", "players": ["Fi3w0"] }
                }
                """.trimIndent()
            )
        }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun rejectsMissingRequiredFields() {
        val error = runCatching {
            ItemConfigParser.parse("""{"id":"missing_base"}""")
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error.message?.contains("base") == true)
    }

    @Test
    fun rejectsInvalidStackStateFields() {
        assertIllegal("""{ "id": "bad", "base": "minecraft:stick", "stackSize": 0 }""")
        assertIllegal("""{ "id": "bad", "base": "minecraft:stick", "stackSize": 100 }""")
        assertIllegal("""{ "id": "bad", "base": "minecraft:stick", "imbueLimit": -2 }""")
        assertIllegal("""{ "id": "bad", "base": "minecraft:stick", "soulCapacity": 0 }""")
        assertIllegal("""{ "id": "bad", "base": "minecraft:stick", "resonanceRequires": 0 }""")
    }

    @Test
    fun parsesAllRootExampleItems() {
        val root = repoRoot().resolve("examples/items")
        val files = Files.walk(root)
            .use { stream -> stream.filter { it.isRegularFile() && it.toString().endsWith(".json") }.toList() }

        assertFalse(files.isEmpty(), "expected root examples/items JSON files")
        for (file in files) {
            val def = ItemConfigParser.parse(file.readText())
            assertTrue(def.id.isNotBlank(), "id should not be blank in ${file.name}")
            assertTrue(def.base.isNotBlank(), "base should not be blank in ${file.name}")
        }
    }

    private fun repoRoot(): Path {
        val userDir = Path.of(System.getProperty("user.dir"))
        return if (userDir.fileName.toString() == "core") userDir.parent else userDir
    }

    private fun assertIllegal(json: String) {
        val error = runCatching { ItemConfigParser.parse(json) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
    }
}
