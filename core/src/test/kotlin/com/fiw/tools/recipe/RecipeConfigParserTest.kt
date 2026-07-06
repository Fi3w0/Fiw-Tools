package com.fiw.tools.recipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeConfigParserTest {
    @Test
    fun parsesShapedRecipe() {
        val recipes = RecipeConfigParser.parseAll(
            """
            {
              "id": "storm_blade_craft",
              "type": "shaped",
              "pattern": ["NDN", "DSD", "NDN"],
              "key": {
                "N": "minecraft:netherite_ingot",
                "D": "minecraft:diamond_block",
                "S": "fiw:storm_blade"
              },
              "result": { "item": "fiw:storm_blade_awakened", "count": 1 }
            }
            """.trimIndent(),
            "file"
        )
        assertEquals(1, recipes.size)
        val r = recipes[0]
        assertEquals("storm_blade_craft", r.id)
        assertTrue(r.shaped)
        assertEquals(3, r.width)
        assertEquals(3, r.height)
        assertEquals("fiw:storm_blade", r.ingredientAt(1, 1))
        assertEquals("minecraft:netherite_ingot", r.ingredientAt(0, 0))
        assertEquals("fiw:storm_blade_awakened", r.resultItem)
        assertEquals(1, r.resultCount)
    }

    @Test
    fun parsesMultipleRecipesFromWrapperAndArray() {
        val wrapped = RecipeConfigParser.parseAll(
            """
            { "recipes": [
              { "type": "shapeless", "ingredients": ["fiw:soul_gem", "minecraft:stick"],
                "result": { "item": "minecraft:nether_star", "count": 2 } },
              { "pattern": ["X"], "key": { "X": "minecraft:dirt" },
                "result": { "item": "fiw:dirt_wand" } }
            ] }
            """.trimIndent(),
            "myfile"
        )
        assertEquals(2, wrapped.size)
        assertEquals("myfile#0", wrapped[0].id)
        assertFalse(wrapped[0].shaped)
        assertEquals(2, wrapped[0].resultCount)
        assertTrue(wrapped[1].shaped)

        val bareArray = RecipeConfigParser.parseAll(
            """
            [ { "type": "shapeless", "ingredients": ["minecraft:apple"],
                "result": { "item": "fiw:golden_apple_plus" } } ]
            """.trimIndent(),
            "arr"
        )
        assertEquals(1, bareArray.size)
    }

    @Test
    fun shapedPatternWithSpacesLeavesEmptySlots() {
        val r = RecipeConfigParser.parseAll(
            """
            { "pattern": [" A ", "ABA", " A "], "key": { "A": "minecraft:gold_ingot", "B": "fiw:core" },
              "result": { "item": "fiw:charm" } }
            """.trimIndent(),
            "f"
        )[0]
        assertNull(r.ingredientAt(0, 0))
        assertEquals("minecraft:gold_ingot", r.ingredientAt(1, 0))
        assertEquals("fiw:core", r.ingredientAt(1, 1))
    }

    @Test
    fun parsesEnchantmentTransferBlock() {
        val r = RecipeConfigParser.parseAll(
            """
            { "type": "shapeless", "ingredients": ["fiw:old_chest", "minecraft:netherite_ingot"],
              "result": { "item": "fiw:new_chest" },
              "enchantments": {
                "mode": "merge", "conflict": "anvil", "from": ["fiw:old_chest"],
                "only": ["protection", "minecraft:unbreaking"], "exclude": ["thorns"],
                "transferCurses": false, "respectMaxLevel": true, "maxLevel": 10,
                "onlyCompatible": true, "checkConflicts": true
              } }
            """.trimIndent(),
            "f"
        )[0]
        val e = r.enchantments!!
        assertEquals(RecipeEnchantmentsDef.MODE_MERGE, e.mode)
        assertEquals(RecipeEnchantmentsDef.CONFLICT_COMBINE, e.conflict)
        assertEquals(listOf("fiw:old_chest"), e.from)
        assertEquals(listOf("minecraft:protection", "minecraft:unbreaking"), e.only)
        assertEquals(listOf("minecraft:thorns"), e.exclude)
        assertFalse(e.transferCurses)
        assertTrue(e.respectMaxLevel)
        assertEquals(10, e.maxLevel)
        assertTrue(e.onlyCompatible)
        assertTrue(e.checkConflicts)
    }

    @Test
    fun enchantmentShorthandAndAliases() {
        val shorthand = RecipeConfigParser.parseAll(
            """{ "type": "shapeless", "ingredients": ["minecraft:diamond"],
                 "result": { "item": "fiw:gem" }, "enchantments": true }""", "f"
        )[0].enchantments!!
        assertEquals(RecipeEnchantmentsDef.MODE_MERGE, shorthand.mode)
        assertEquals(RecipeEnchantmentsDef.CONFLICT_HIGHEST, shorthand.conflict)

        assertNull(
            RecipeConfigParser.parseAll(
                """{ "type": "shapeless", "ingredients": ["minecraft:diamond"],
                     "result": { "item": "fiw:gem" }, "enchantments": false }""", "f"
            )[0].enchantments
        )

        val old = RecipeConfigParser.parseAll(
            """{ "type": "shapeless", "ingredients": ["minecraft:diamond"],
                 "result": { "item": "fiw:gem" },
                 "enchantments": { "mode": "old", "conflict": "sum" } }""", "f"
        )[0].enchantments!!
        assertEquals(RecipeEnchantmentsDef.MODE_KEEP_OLD, old.mode)
        assertEquals(RecipeEnchantmentsDef.CONFLICT_ADD, old.conflict)
    }

    @Test
    fun rejectsInvalidEnchantmentBlocks() {
        assertFailsWith<IllegalArgumentException> { // unknown mode
            RecipeConfigParser.parseAll(
                """{ "type": "shapeless", "ingredients": ["minecraft:diamond"],
                     "result": { "item": "fiw:gem" }, "enchantments": { "mode": "sideways" } }""", "f"
            )
        }
        assertFailsWith<IllegalArgumentException> { // unknown conflict
            RecipeConfigParser.parseAll(
                """{ "type": "shapeless", "ingredients": ["minecraft:diamond"],
                     "result": { "item": "fiw:gem" }, "enchantments": { "conflict": "coinflip" } }""", "f"
            )
        }
        assertFailsWith<IllegalArgumentException> { // maxLevel out of range
            RecipeConfigParser.parseAll(
                """{ "type": "shapeless", "ingredients": ["minecraft:diamond"],
                     "result": { "item": "fiw:gem" }, "enchantments": { "maxLevel": 500 } }""", "f"
            )
        }
        assertFailsWith<IllegalArgumentException> { // transfer onto count > 1 needs explicit opt-in
            RecipeConfigParser.parseAll(
                """{ "type": "shapeless", "ingredients": ["minecraft:diamond"],
                     "result": { "item": "fiw:gem", "count": 2 }, "enchantments": true }""", "f"
            )
        }
        // ...and works once opted in.
        val multiplied = RecipeConfigParser.parseAll(
            """{ "type": "shapeless", "ingredients": ["minecraft:diamond"],
                 "result": { "item": "fiw:gem", "count": 2 },
                 "enchantments": { "allowMultiplied": true } }""", "f"
        )[0].enchantments!!
        assertTrue(multiplied.allowMultiplied)
    }

    @Test
    fun rejectsInvalidRecipes() {
        assertFailsWith<IllegalArgumentException> { // missing result
            RecipeConfigParser.parseAll("""{ "type": "shapeless", "ingredients": ["minecraft:dirt"] }""", "f")
        }
        assertFailsWith<IllegalArgumentException> { // symbol not in key
            RecipeConfigParser.parseAll(
                """{ "pattern": ["AB"], "key": { "A": "minecraft:dirt" }, "result": { "item": "minecraft:stone" } }""",
                "f"
            )
        }
        assertFailsWith<IllegalArgumentException> { // ragged pattern rows
            RecipeConfigParser.parseAll(
                """{ "pattern": ["AA", "A"], "key": { "A": "minecraft:dirt" }, "result": { "item": "minecraft:stone" } }""",
                "f"
            )
        }
        assertFailsWith<IllegalArgumentException> { // too many ingredients
            val ten = (1..10).joinToString(",") { "\"minecraft:dirt\"" }
            RecipeConfigParser.parseAll(
                """{ "type": "shapeless", "ingredients": [$ten], "result": { "item": "minecraft:stone" } }""",
                "f"
            )
        }
    }
}
