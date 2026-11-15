package com.fiw.tools.command

import com.fiw.tools.FiwToolsPlatform
import com.fiw.tools.build.FiwItems
import com.fiw.tools.config.ItemRegistry
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

/**
 * `/fiwtools export <id>`: writes the held stack as a new item config. For a Fiw item the copy
 * starts from that item's own JSON (abilities, curses, systems) and takes the stack's current
 * name, lore, enchantments and attributes. Never overwrites a file; reports what it couldn't map.
 */
object ItemExporter {
    data class Result(val file: Path?, val error: String?, val notes: List<String>)

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val ID = Regex("[a-z0-9_]+")

    // Components that map to config fields, or that a Fiw item rebuilds itself.
    private val HANDLED: Set<DataComponentType<*>> = setOf(
        DataComponents.CUSTOM_NAME, DataComponents.LORE, DataComponents.RARITY, DataComponents.ENCHANTMENTS,
        DataComponents.ATTRIBUTE_MODIFIERS, DataComponents.UNBREAKABLE, DataComponents.MAX_DAMAGE,
        DataComponents.DAMAGE, DataComponents.MAX_STACK_SIZE, DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
        DataComponents.DYED_COLOR, DataComponents.ITEM_MODEL, DataComponents.REPAIR_COST
    )

    fun export(stack: ItemStack, id: String): Result {
        if (!ID.matches(id)) return Result(null, "Use lowercase letters, digits and _ for the id.", emptyList())
        val file = FiwToolsPlatform.configRoot().resolve("$id.json")
        if (Files.exists(file) || ItemRegistry.byId(id) != null) {
            return Result(null, "An item '$id' already exists — pick another id.", emptyList())
        }
        val notes = ArrayList<String>()
        val sourceId = FiwItems.fiwId(stack)
        val json = sourceId?.let { ItemRegistry.sourceOf(it) }?.let { JsonParser.parseString(it).asJsonObject } ?: JsonObject()
        if (sourceId != null) notes.add("Copied abilities and settings from '$sourceId'.")

        json.addProperty("id", id)
        json.addProperty("base", BuiltInRegistries.ITEM.getKey(stack.item).toString())
        stack.get(DataComponents.CUSTOM_NAME)?.let { json.addProperty("displayName", toCodes(it, notes)) }
        stack.get(DataComponents.LORE)?.let { lore ->
            // A Fiw item's lore may contain generated lines (auto lore, dynamic lore, imbues);
            // keep its configured lore and let the copy regenerate the rest.
            if (sourceId == null) json.add("lore", JsonArray().apply { lore.lines().forEach { add(toCodes(it, notes)) } })
        }
        stack.get(DataComponents.RARITY)?.let { json.addProperty("rarity", it.serializedName) }
        stack.get(DataComponents.ENCHANTMENTS)?.let { ench ->
            val o = JsonObject()
            for (entry in ench.entrySet()) {
                entry.key.unwrapKey().ifPresent { k -> o.addProperty(k.identifier().toString(), entry.intValue) }
            }
            if (o.size() > 0) json.add("enchantments", o) else json.remove("enchantments")
        }
        stack.get(DataComponents.ATTRIBUTE_MODIFIERS)?.let { mods ->
            val arr = JsonArray()
            for (e in mods.modifiers()) {
                val type = e.attribute().unwrapKey().map { it.identifier().toString() }.orElse(null) ?: continue
                arr.add(JsonObject().apply {
                    addProperty("type", type)
                    addProperty("slot", e.slot().serializedName)
                    addProperty("amount", e.modifier().amount())
                    addProperty("operation", e.modifier().operation().serializedName)
                })
            }
            if (arr.size() > 0) json.add("attributes", arr) else json.remove("attributes")
        }
        if (stack.has(DataComponents.UNBREAKABLE)) json.addProperty("unbreakable", true)
        stack.get(DataComponents.MAX_DAMAGE)?.let { json.addProperty("maxDamage", it) }
        stack.get(DataComponents.DAMAGE)?.takeIf { it > 0 }?.let { json.addProperty("damage", it) }
        stack.get(DataComponents.MAX_STACK_SIZE)?.takeIf { it != stack.item.defaultMaxStackSize }
            ?.let { json.addProperty("stackSize", it) }
        stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)?.let { json.addProperty("enchantmentGlint", it) }
        stack.get(DataComponents.DYED_COLOR)?.let { json.addProperty("dyedColor", String.format("#%06X", it.rgb() and 0xFFFFFF)) }
        stack.get(DataComponents.ITEM_MODEL)?.let { json.addProperty("itemModel", it.toString()) }
        stack.get(DataComponents.REPAIR_COST)?.takeIf { it > 0 }?.let { json.addProperty("repairCost", it) }

        for (entry in stack.componentsPatch.entrySet()) {
            val type = entry.key
            if (type in HANDLED || !entry.value.isPresent) continue
            if (type == DataComponents.CUSTOM_DATA && sourceId != null) continue
            notes.add("Not exported: ${BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type)}")
        }

        Files.createDirectories(file.parent)
        Files.writeString(file, gson.toJson(json))
        return Result(file, null, notes)
    }

    /** Component text back to `&` codes. Hex colors become the nearest legacy color. */
    fun toCodes(component: Component, notes: MutableList<String>): String {
        val sb = StringBuilder()
        var approximated = false
        component.visit({ style: Style, text: String ->
            if (text.isNotEmpty()) {
                val color = style.color
                if (color != null) {
                    val legacy = ChatFormatting.entries.firstOrNull { it.isColor && it.color == color.value }
                        ?: nearest(color.value).also { approximated = true }
                    sb.append('&').append(legacy.char)
                } else {
                    sb.append("&r")
                }
                if (style.isBold) sb.append("&l")
                if (style.isItalic) sb.append("&o")
                if (style.isUnderlined) sb.append("&n")
                if (style.isStrikethrough) sb.append("&m")
                if (style.isObfuscated) sb.append("&k")
                sb.append(text)
            }
            Optional.empty<Unit>()
        }, Style.EMPTY)
        if (approximated) notes.add("A hex color was replaced with the nearest & color.")
        return sb.toString().removePrefix("&r")
    }

    private fun nearest(rgb: Int): ChatFormatting = ChatFormatting.entries.filter { it.isColor && it.color != null }
        .minBy { f ->
            val c = f.color!!
            val dr = ((c shr 16) and 0xFF) - ((rgb shr 16) and 0xFF)
            val dg = ((c shr 8) and 0xFF) - ((rgb shr 8) and 0xFF)
            val db = (c and 0xFF) - (rgb and 0xFF)
            dr * dr + dg * dg + db * db
        }
}
