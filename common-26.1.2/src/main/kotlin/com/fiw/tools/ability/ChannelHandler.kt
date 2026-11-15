package com.fiw.tools.ability

import com.fiw.tools.util.TextParser
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * The channel primitive that makes ritual abilities feel earned. A channel is a hold-to-cast: the
 * player must keep a condition (sneaking, staying near where they started) for [Channel.durationTicks]
 * without taking damage. Progress is shown as an action-bar bar plus a shrinking particle ring — no
 * client mod, no HUD. Each tick calls [Channel.onProgress]; finishing calls [Channel.onComplete];
 * breaking early (moved, un-sneaked, or hit) calls [Channel.onInterrupt]. One channel per player.
 *
 * Abilities capture their own state/params in the callbacks — channels are transient (dropped on
 * disconnect and server stop), so lambdas are fine and nothing is serialized.
 */
object ChannelHandler {
    class Channel(
        val startTick: Long,
        val durationTicks: Int,
        val requireSneak: Boolean,
        val maxMoveSq: Double,
        val anchor: Vec3,
        val label: String,
        val particle: ParticleOptions,
        val onProgress: (ServerPlayer, Int) -> Unit,
        val onComplete: (ServerPlayer) -> Unit,
        val onInterrupt: (ServerPlayer) -> Unit
    )

    private val channels = ConcurrentHashMap<UUID, Channel>()

    fun isChanneling(id: UUID): Boolean = channels.containsKey(id)

    /** Begin a channel for [player]. Returns false (does nothing) if they're already channeling. */
    fun start(
        player: ServerPlayer,
        durationTicks: Int,
        requireSneak: Boolean,
        maxMove: Double,
        label: String,
        particle: ParticleOptions,
        onProgress: (ServerPlayer, Int) -> Unit = { _, _ -> },
        onInterrupt: (ServerPlayer) -> Unit = { },
        onComplete: (ServerPlayer) -> Unit
    ): Boolean {
        if (channels.containsKey(player.uuid)) return false
        channels[player.uuid] = Channel(
            player.level().gameTime, durationTicks.coerceAtLeast(1), requireSneak,
            maxMove * maxMove, player.position(), label, particle, onProgress, onComplete, onInterrupt
        )
        return true
    }

    fun cancel(id: UUID) {
        channels.remove(id)
    }

    fun clear() {
        channels.clear()
    }

    fun tick(server: MinecraftServer) {
        if (channels.isEmpty()) return
        val it = channels.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val player = server.playerList.getPlayer(entry.key)
            val ch = entry.value
            if (player == null || !player.isAlive) { it.remove(); continue }
            val now = player.level().gameTime
            val elapsed = (now - ch.startTick).toInt()

            val brokeSneak = ch.requireSneak && !player.isShiftKeyDown
            val moved = player.position().distanceToSqr(ch.anchor) > ch.maxMoveSq
            val hit = AbilityState.lastDamageTickOf(entry.key) > ch.startTick
            if (brokeSneak || moved || hit) {
                it.remove()
                runSafe { ch.onInterrupt(player) }
                player.sendOverlayMessage(Component.literal("§8Channel broken."))
                player.level().playSound(null, player.x, player.y, player.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 0.8f)
                continue
            }

            runSafe { ch.onProgress(player, elapsed) }
            val frac = (elapsed.toDouble() / ch.durationTicks).coerceIn(0.0, 1.0)
            player.sendOverlayMessage(bar(ch.label, frac))
            spawnRing(player.level() as ServerLevel, player.position(), 1.3 * (1.0 - frac) + 0.3, ch.particle)

            if (elapsed >= ch.durationTicks) {
                it.remove()
                runSafe { ch.onComplete(player) }
            }
        }
    }

    private fun bar(label: String, frac: Double): Component {
        val filled = (frac * 10).toInt().coerceIn(0, 10)
        val pct = (frac * 100).toInt()
        val fill = "■".repeat(filled)
        val empty = "■".repeat(10 - filled)
        return TextParser.parse("$label &8[&c$fill&7$empty&8] &f$pct%")
    }

    private fun spawnRing(world: ServerLevel, center: Vec3, radius: Double, particle: ParticleOptions) {
        val points = 12
        for (i in 0 until points) {
            val a = i / points.toDouble() * Math.PI * 2
            world.sendParticles(particle, center.x + cos(a) * radius, center.y + 0.15, center.z + sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private inline fun runSafe(block: () -> Unit) {
        try { block() } catch (_: Exception) { }
    }
}