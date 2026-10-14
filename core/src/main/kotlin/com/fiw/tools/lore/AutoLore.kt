package com.fiw.tools.lore

import com.fiw.tools.config.ItemDefinition
import kotlin.math.roundToInt

/**
 * Generated tooltip lines for an item's abilities, e.g. `&7Right-click: &fLightning Rain &8(6s cooldown)`.
 * Ability names come from the type id ("lightning_rain" → "Lightning Rain") unless the ability's
 * params set `loreName`; `hideInLore: true` leaves an ability out.
 */
object AutoLore {

    private val TRIGGERS = mapOf(
        "on_right_click" to "Right-click",
        "on_shift_right_click" to "Sneak + right-click",
        "on_attack" to "On hit",
        "on_kill" to "On kill",
        "on_boss_kill" to "On boss kill",
        "on_boss_phase" to "Boss phase change",
        "on_hurt" to "When hurt",
        "on_block_break" to "On block break",
        "on_consume" to "On use",
        "on_pickup" to "On pickup",
        "on_drop" to "On drop",
        "on_death" to "On death",
        "on_respawn" to "On respawn",
        "on_enter_dimension" to "Entering a dimension",
        "on_leave_dimension" to "Leaving a dimension",
        "on_equip" to "On equip",
        "on_unequip" to "On unequip",
        "while_held" to "While held",
        "while_worn" to "While worn",
        "while_sneaking" to "While sneaking",
        "resonance" to "Set bonus"
    )

    fun lines(def: ItemDefinition): List<String> = lines(def.abilities)

    fun lines(abilities: List<ItemDefinition.AbilityDef>): List<String> = abilities.mapNotNull { ab ->
        if (ab.params.get("hideInLore")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) return@mapNotNull null
        val trigger = ab.trigger.lowercase()
        val label = TRIGGERS[trigger] ?: humanize(trigger.removePrefix("on_"))
        val name = ab.params.get("loreName")?.takeIf { it.isJsonPrimitive }?.asString ?: humanize(ab.type)
        val extras = ArrayList<String>()
        // Passive cooldowns are tick intervals, not something a player waits on.
        if (ab.cooldownTicks > 0 && !trigger.startsWith("while_") && trigger != "resonance") {
            extras.add(formatTicks(ab.cooldownTicks) + " cooldown")
        }
        if (ab.chance < 1f) extras.add("${(ab.chance * 100).roundToInt()}% chance")
        val suffix = if (extras.isEmpty()) "" else " &8(" + extras.joinToString(", ") + ")"
        "&7$label: &f$name$suffix"
    }

    /** "lightning_rain" → "Lightning Rain". */
    fun humanize(id: String): String =
        id.substringAfter(':').split('_', '-').filter { it.isNotEmpty() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

    /** 120 → "6s", 90 → "4.5s", 1200 → "1m", 1500 → "1m 15s". */
    fun formatTicks(ticks: Int): String {
        val seconds = ticks / 20.0
        if (seconds < 60) {
            val rounded = (seconds * 10).roundToInt() / 10.0
            return if (rounded % 1.0 == 0.0) "${rounded.toInt()}s" else "${rounded}s"
        }
        val total = seconds.roundToInt()
        val m = total / 60
        val s = total % 60
        return if (s == 0) "${m}m" else "${m}m ${s}s"
    }
}
