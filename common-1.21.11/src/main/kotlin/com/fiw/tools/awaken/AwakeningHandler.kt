package com.fiw.tools.awaken

import com.fiw.tools.build.FiwItems
import com.fiw.tools.build.ItemBuilder
import com.fiw.tools.config.ItemDefinition
import com.fiw.tools.config.ItemRegistry
import com.fiw.tools.util.TextParser
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import org.slf4j.LoggerFactory

/**
 * Artifact awakening. An item with an `awakening` block tracks per-stack progress toward its
 * condition; when met, the stack transforms into the `upgradeTo` item. Chains are built by giving
 * the upgraded item its own `awakening` block, and craft-based awakening falls out of the custom
 * recipe system (`result: fiw:<awakened id>`) with no extra machinery.
 *
 * Triggers: `kill_entity` (any entity id — bosses included), `kill_player` (optionally one
 * specific player), `deal_damage` (total damage dealt with the item held), `visit_dimension`.
 * Kill/damage progress counts the main-hand stack; dimension visits check the whole inventory.
 */
object AwakeningHandler {
    /** Per-stack custom-data key holding accumulated progress (kills or damage dealt). */
    const val PROGRESS_KEY = "fiw_awaken_progress"

    private val logger = LoggerFactory.getLogger("fiw-tools/awaken")

    private const val SWEEP_TICKS = 20
    private var counter = 0

    fun onKill(player: ServerPlayer, stack: ItemStack, victim: LivingEntity) {
        val (def, aw) = awakeningFor(stack) ?: return
        when (aw.trigger) {
            "kill_entity" -> {
                val typeId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.type).toString()
                if (typeId == aw.entity) progress(player, stack, def, aw, 1.0)
            }
            "kill_player" -> {
                val victimPlayer = victim as? ServerPlayer ?: return
                if (victimPlayer === player) return
                val filter = aw.playerName
                if (filter == null
                    || filter.equals(victimPlayer.gameProfile.name, ignoreCase = true)
                    || filter.equals(victimPlayer.uuid.toString(), ignoreCase = true)
                ) {
                    progress(player, stack, def, aw, 1.0)
                }
            }
        }
    }

    fun onDamageDealt(player: ServerPlayer, stack: ItemStack, amount: Float) {
        if (amount <= 0f) return
        val (def, aw) = awakeningFor(stack) ?: return
        if (aw.trigger == "deal_damage") progress(player, stack, def, aw, amount.toDouble())
    }

    /** Dimension-visit sweep, once a second. */
    fun tick(server: MinecraftServer) {
        if (++counter < SWEEP_TICKS) return
        counter = 0
        for (player in server.playerList.players) {
            val dimId = player.level().dimension().identifier().toString()
            val inv = player.inventory
            for (slot in 0 until inv.containerSize) {
                val stack = inv.getItem(slot)
                if (stack.isEmpty) continue
                val (def, aw) = awakeningFor(stack) ?: continue
                if (aw.trigger == "visit_dimension" && aw.dimension == dimId) {
                    awaken(player, stack, def, aw)
                }
            }
        }
    }

    private fun awakeningFor(stack: ItemStack): Pair<ItemDefinition, ItemDefinition.AwakeningDef>? {
        val id = FiwItems.fiwId(stack) ?: return null
        val def = ItemRegistry.byId(id) ?: return null
        val aw = def.awakening ?: return null
        return def to aw
    }

    private fun progress(
        player: ServerPlayer,
        stack: ItemStack,
        def: ItemDefinition,
        aw: ItemDefinition.AwakeningDef,
        delta: Double
    ) {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return
        val tag = data.copyTag()
        val total = tag.getDoubleOr(PROGRESS_KEY, 0.0) + delta
        if (total >= aw.count) {
            awaken(player, stack, def, aw)
            return
        }
        tag.putDouble(PROGRESS_KEY, total)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        if (aw.showProgress) {
            val text = "&5Awakening &d${total.toLong()}&5/&d${aw.count.toLong()}"
            player.displayClientMessage(TextParser.parse(text), true)
        }
    }

    private fun awaken(
        player: ServerPlayer,
        stack: ItemStack,
        def: ItemDefinition,
        aw: ItemDefinition.AwakeningDef
    ) {
        val upgradeDef = ItemRegistry.byId(aw.upgradeTo) ?: run {
            logger.warn("Item '${def.id}': awakening upgradeTo '${aw.upgradeTo}' is not a loaded item")
            return
        }
        val upgraded = ItemBuilder.build(upgradeDef, stack.count, player.level().registryAccess()) ?: return

        val inv = player.inventory
        var replaced = false
        for (slot in 0 until inv.containerSize) {
            if (inv.getItem(slot) === stack) {
                inv.setItem(slot, upgraded)
                replaced = true
                break
            }
        }
        if (!replaced) return

        val message = aw.message ?: "&5&lYour ${def.id} has awakened!"
        val component: Component = TextParser.parse(message)
        if (aw.broadcast) {
            player.level().server.playerList.broadcastSystemMessage(component, false)
        } else {
            player.sendSystemMessage(component)
        }
        playSound(player, aw.sound)
    }

    private fun playSound(player: ServerPlayer, id: String?) {
        val sid = Identifier.tryParse(id ?: return) ?: return
        val holder = BuiltInRegistries.SOUND_EVENT.get(sid).orElse(null) ?: return
        val event = com.fiw.tools.util.HolderAccess.value(holder)
        player.level().playSound(null, player.x, player.y, player.z, event, SoundSource.PLAYERS, 0.9f, 1.0f)
    }
}
