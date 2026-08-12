package com.fiw.tools.ability.impl

import com.fiw.tools.ability.Ability
import com.fiw.tools.ability.AbilityContext
import com.fiw.tools.ability.AffectScope
import com.fiw.tools.ability.collectTargets
import com.fiw.tools.ability.optD
import com.fiw.tools.ability.optF
import com.fiw.tools.ability.optI
import com.fiw.tools.ability.scope
import com.fiw.tools.util.TextParser
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * `runic_sequence` — a spell you have to *draw*. Each right-click records where you're looking
 * (`up` / `down` / `level`); enter the configured `sequence` of looks in order, each within
 * `stepTimeout` ticks of the last, and the rune fires an AoE burst. A wrong look self-corrects to the
 * longest matching tail rather than hard-resetting, and the action bar shows your progress. No client
 * mod — the "inputs" are pitch buckets on your clicks.
 */
object RunicSequenceAbility : Ability {
    private class Progress(val tokens: MutableList<String>, var lastTick: Long)
    private val progress = ConcurrentHashMap<UUID, Progress>()

    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world
        val seq = p.getAsJsonArray("sequence")?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString?.lowercase() }
            ?: return false
        if (seq.isEmpty()) return false
        val now = world.gameTime

        val stepTimeout = p.optI("stepTimeout", 30).coerceAtLeast(4)
        val prog = progress.getOrPut(player.uuid) { Progress(mutableListOf(), now) }
        if (now - prog.lastTick > stepTimeout) prog.tokens.clear()
        prog.lastTick = now
        prog.tokens.add(lookToken(player))
        trimToPrefix(prog.tokens, seq)

        if (prog.tokens.size == seq.size) {
            progress.remove(player.uuid)
            cast(ctx)
            return true  // the cast is the only "acted" result — this is what arms `cooldownTicks`
        }

        // Intermediate input: recorded and shown, but return false so it doesn't arm the cooldown
        // (otherwise any cooldownTicks would gate the very clicks needed to finish the sequence).
        player.displayClientMessage(bar(prog.tokens, seq), true)
        world.playSound(null, player.x, player.y, player.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.7f, 0.7f + prog.tokens.size * 0.15f)
        return false
    }

    private fun cast(ctx: AbilityContext) {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world
        val radius = p.optD("radius", 5.0)
        val damage = p.optF("damage", 12f)
        val scope = p.scope("affects", AffectScope.MOBS)
        if (damage > 0f) {
            val src = world.damageSources().indirectMagic(player, player)
            for (e in collectTargets(world, player.position(), radius, player, scope)) e.hurtServer(world, src, damage)
        }
        world.sendParticles(ParticleTypes.ENCHANT, player.x, player.y + 1.0, player.z, 80, radius * 0.5, 1.0, radius * 0.5, 0.5)
        world.sendParticles(ParticleTypes.END_ROD, player.x, player.y + 0.5, player.z, 30, radius * 0.4, 0.3, radius * 0.4, 0.1)
        world.playSound(null, player.x, player.y, player.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.5f)
        player.displayClientMessage(TextParser.parse("&d&lRUNE COMPLETE"), true)
    }

    private fun lookToken(player: ServerPlayer): String {
        val pitch = player.xRot
        return when {
            pitch < -50f -> "up"
            pitch > 50f -> "down"
            else -> "level"
        }
    }

    /** Drop leading tokens until the buffer is a prefix of [seq], so a wrong start recovers gracefully. */
    private fun trimToPrefix(tokens: MutableList<String>, seq: List<String>) {
        while (tokens.isNotEmpty()) {
            var ok = true
            for (i in tokens.indices) {
                if (i >= seq.size || tokens[i] != seq[i]) { ok = false; break }
            }
            if (ok) return
            tokens.removeAt(0)
        }
    }

    private fun bar(tokens: List<String>, seq: List<String>): net.minecraft.network.chat.Component {
        val sb = StringBuilder("&dRune ")
        for (i in seq.indices) {
            sb.append(if (i < tokens.size) "&b" else "&8").append(symbol(seq[i])).append(' ')
        }
        return TextParser.parse(sb.toString())
    }

    private fun symbol(token: String): String = when (token) {
        "up" -> "▲"
        "down" -> "▼"
        else -> "◆"
    }
}

/**
 * `heartbeat_cast` — a rhythm spell. There's a steady beat every `beatInterval` ticks; right-click
 * *on* the beat (within `tolerance`) and the burst hits for `onBeatMultiplier`× and heals you, off-beat
 * and it's a weak tap. The action bar tells you your offset after each cast, so you learn the cadence
 * by ear and feel. The skill is internalising the ~1s pulse and clicking to it.
 */
object HeartbeatCastAbility : Ability {
    override fun execute(ctx: AbilityContext): Boolean {
        val p = ctx.params
        val player = ctx.player
        val world = ctx.world
        val interval = p.optI("beatInterval", 20).coerceAtLeast(4)
        val tolerance = p.optI("tolerance", 3).coerceAtLeast(0)
        val phase = (world.gameTime % interval).toInt()
        val offset = minOf(phase, interval - phase)
        val onBeat = offset <= tolerance

        val base = p.optF("damage", 6f)
        val mult = p.optF("onBeatMultiplier", 2.5f)
        val radius = p.optD("radius", 4.0)
        val scope = p.scope("affects", AffectScope.MOBS)
        val dmg = if (onBeat) base * mult else base
        if (dmg > 0f) {
            val src = world.damageSources().indirectMagic(player, player)
            for (e in collectTargets(world, player.position(), radius, player, scope)) e.hurtServer(world, src, dmg)
        }

        if (onBeat) {
            val heal = p.optF("onBeatHeal", 2f)
            if (heal > 0f) player.heal(heal)
            player.displayClientMessage(TextParser.parse("&a&l♪ ON BEAT! &7×%.1f".format(mult)), true)
            world.sendParticles(ParticleTypes.NOTE, player.x, player.y + 1.2, player.z, 16, 0.6, 0.4, 0.6, 1.0)
            world.playSound(null, player.x, player.y, player.z, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 2.0f)
        } else {
            player.displayClientMessage(TextParser.parse("&7off-beat (±${offset}t)"), true)
            world.sendParticles(ParticleTypes.NOTE, player.x, player.y + 1.0, player.z, 4, 0.3, 0.2, 0.3, 0.2)
            world.playSound(null, player.x, player.y, player.z, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.7f, 0.6f)
        }
        return true
    }
}
