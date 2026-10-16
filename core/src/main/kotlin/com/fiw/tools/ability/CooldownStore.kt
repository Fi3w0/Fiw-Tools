package com.fiw.tools.ability

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/** UUID-keyed remaining ticks; offline time never consumes a cooldown. */
class CooldownStore(private val directory: Path) {
    private val gson = Gson()

    fun load(player: UUID): Map<String, Long> {
        val file = directory.resolve("$player.json")
        if (!Files.exists(file)) return emptyMap()
        val root = gson.fromJson(Files.readString(file), JsonObject::class.java)
        return root.entrySet().associate { it.key to it.value.asLong }.filterValues { it > 0 }
    }

    fun save(player: UUID, remaining: Map<String, Long>) {
        Files.createDirectories(directory)
        val file = directory.resolve("$player.json")
        val temp = Files.createTempFile(directory, "$player-", ".tmp")
        try {
            Files.writeString(temp, gson.toJson(remaining.filterValues { it > 0 }))
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    companion object {
        fun remaining(expiries: Map<String, Long>, now: Long): Map<String, Long> =
            expiries.filterValues { it > now }.mapValues { it.value - now }

        fun restore(remaining: Map<String, Long>, now: Long): Map<String, Long> =
            remaining.filterValues { it > 0 }.mapValues { now + it.value.coerceAtMost(Long.MAX_VALUE - now) }
    }
}
