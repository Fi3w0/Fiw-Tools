package com.fiw.tools.ability

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

/**
 * A tiny shared scheduler for time-based ability effects — delayed impacts (ground_slam) and
 * pulsing zones (gravity_well, healing_totem). One server-tick hook drives them all so each ability
 * doesn't register its own loop, and everything is dropped on server stop to avoid leaking into a
 * freshly loaded world.
 */
object ZoneEffects {
    private class Task(
        var remaining: Int,
        val period: Int,
        var sincePeriod: Int,
        val action: () -> Unit
    )

    private val tasks = ArrayList<Task>()

    fun init() {
        ServerTickEvents.END_SERVER_TICK.register { _ -> tickAll() }
    }

    fun clear() {
        synchronized(tasks) { tasks.clear() }
    }

    /** Run [action] once after [delayTicks] ticks. */
    fun schedule(delayTicks: Int, action: () -> Unit) {
        synchronized(tasks) { tasks.add(Task(delayTicks.coerceAtLeast(1), Int.MAX_VALUE, 0, action)) }
    }

    /** Run [action] every [periodTicks] for [durationTicks] total. */
    fun repeating(durationTicks: Int, periodTicks: Int, action: () -> Unit) {
        synchronized(tasks) { tasks.add(Task(durationTicks.coerceAtLeast(1), periodTicks.coerceAtLeast(1), 0, action)) }
    }

    private fun tickAll() {
        synchronized(tasks) {
            val it = tasks.iterator()
            while (it.hasNext()) {
                val task = it.next()
                if (task.period == Int.MAX_VALUE) {
                    // One-shot: fire when the delay elapses.
                    if (--task.remaining <= 0) {
                        runSafely(task.action)
                        it.remove()
                    }
                } else {
                    if (++task.sincePeriod >= task.period) {
                        task.sincePeriod = 0
                        runSafely(task.action)
                    }
                    if (--task.remaining <= 0) it.remove()
                }
            }
        }
    }

    private fun runSafely(action: () -> Unit) {
        try {
            action()
        } catch (_: Exception) {
            // An effect throwing must not kill the scheduler or the server tick.
        }
    }
}
