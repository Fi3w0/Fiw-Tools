package com.fiw.tools.api

import net.minecraft.world.entity.Entity
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

/**
 * Optional reflection-only link to Fiw Bosses' public `FiwBossesAPI` (phase numbers are
 * 1-based). Nothing is imported from Fiw Bosses; when it's absent every call is a no-op.
 */
internal object FiwBossesBridge {
    private val logger = LoggerFactory.getLogger("fiw-tools/bosses")

    private class Api(
        val isBoss: Method,
        val getPhase: Method,
        val getPhaseCount: Method,
        val setPhase: Method
    )

    private val api: Api? by lazy {
        try {
            val cls = Class.forName("com.fiw.fiw_bosses.api.FiwBossesAPI")
            Api(
                cls.getMethod("isBoss", Entity::class.java),
                cls.getMethod("getPhase", Entity::class.java),
                cls.getMethod("getPhaseCount", Entity::class.java),
                cls.getMethod("setPhase", Entity::class.java, Int::class.javaPrimitiveType)
            )
        } catch (_: ClassNotFoundException) {
            null
        } catch (e: Exception) {
            logger.warn("Fiw Bosses found but its phase API is missing or incompatible: {}", e.message)
            null
        }
    }

    val isPresent: Boolean get() = api != null

    fun isBoss(entity: Entity): Boolean = call { it.isBoss.invoke(null, entity) as Boolean } ?: false

    fun getPhase(entity: Entity): Int = call { it.getPhase.invoke(null, entity) as Int } ?: 0

    fun getPhaseCount(entity: Entity): Int = call { it.getPhaseCount.invoke(null, entity) as Int } ?: 0

    fun setPhase(entity: Entity, phase: Int): Boolean = call { it.setPhase.invoke(null, entity, phase) as Boolean } ?: false

    private fun <T> call(block: (Api) -> T): T? {
        val a = api ?: return null
        return try {
            block(a)
        } catch (e: Exception) {
            logger.warn("Fiw Bosses phase API call failed: {}", e.message)
            null
        }
    }
}
