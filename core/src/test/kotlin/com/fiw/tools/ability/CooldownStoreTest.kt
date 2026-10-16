package com.fiw.tools.ability

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class CooldownStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun restoresSlotsAndGroupsAgainstANewClock() {
        val player = UUID.randomUUID()
        val store = CooldownStore(directory)
        val remaining = CooldownStore.remaining(mapOf("wand#0" to 150L, "group:blink" to 180L, "expired#1" to 90L), 100)
        store.save(player, remaining)
        assertEquals(mapOf("wand#0" to 60L, "group:blink" to 90L), CooldownStore.restore(CooldownStore(directory).load(player), 10))
        assertEquals(emptyMap(), store.load(UUID.randomUUID()))
    }

    @Test
    fun latestSnapshotDoesNotResurrectResetOrReducedGroups() {
        val player = UUID.randomUUID()
        val store = CooldownStore(directory)
        store.save(player, mapOf("group:dash" to 100L, "wand#1" to 80L))
        // Recharge reset removes the group; reduction leaves just the current remaining ticks.
        store.save(player, CooldownStore.remaining(mapOf("wand#1" to 125L), 100))
        assertEquals(mapOf("wand#1" to 25L), store.load(player))
        store.save(player, emptyMap())
        assertEquals(emptyMap(), store.load(player))
    }
}
