package com.fiw.tools.ability

import java.util.concurrent.ThreadLocalRandom

/**
 * Resolves which of a trigger's eligible abilities actually fire. Abilities with no `procGroup` roll
 * their own `chance` independently — the original behaviour. Abilities sharing a `procGroup` are
 * mutually exclusive: a single weighted roll picks at most one of them, each weighted by its
 * `chance`. When a group's chances sum below 1.0 the shortfall is the probability that none fire;
 * once they reach or exceed 1.0 exactly one always fires (weights are used as relative odds).
 *
 * Callers gate abilities through every non-random check first (trigger, stance, cooldown, cost,
 * conditions), then hand the survivors here so the chance/proc roll is the last word.
 */
object ProcGroups {
    data class Candidate(val index: Int, val chance: Float, val procGroup: String?)

    /** Returns the indices (as given on each [Candidate]) that should execute. */
    fun resolve(candidates: List<Candidate>): Set<Int> {
        if (candidates.isEmpty()) return emptySet()
        // Fast path: nothing shares a proc group, so every ability just rolls its own chance.
        if (candidates.none { !it.procGroup.isNullOrEmpty() }) {
            val winners = HashSet<Int>()
            for (c in candidates) if (rollsThrough(c.chance)) winners.add(c.index)
            return winners
        }
        val winners = HashSet<Int>()
        val groups = HashMap<String, MutableList<Candidate>>()
        for (c in candidates) {
            val g = c.procGroup
            if (g.isNullOrEmpty()) {
                if (rollsThrough(c.chance)) winners.add(c.index)
            } else {
                groups.getOrPut(g.lowercase()) { mutableListOf() }.add(c)
            }
        }
        for (members in groups.values) pickOne(members)?.let { winners.add(it) }
        return winners
    }

    private fun rollsThrough(chance: Float): Boolean =
        chance >= 1f || ThreadLocalRandom.current().nextFloat() < chance

    private fun pickOne(members: List<Candidate>): Int? {
        if (members.size == 1) return if (rollsThrough(members[0].chance)) members[0].index else null
        val total = members.sumOf { it.chance.toDouble() }
        val span = maxOf(1.0, total)  // when the group's chances sum below 1, the gap = "nothing fires"
        var roll = ThreadLocalRandom.current().nextDouble(span)
        for (c in members) {
            if (roll < c.chance) return c.index
            roll -= c.chance
        }
        return null
    }
}
