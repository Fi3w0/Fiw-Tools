package com.fiw.tools.config

import com.fiw.tools.FiwToolsPlatform
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory
import java.nio.file.Files

/** Server-wide defaults from config/fiw_tools/settings.json (created on first load). */
object ToolsSettings {
    private val logger = LoggerFactory.getLogger("fiw-tools")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** Default for items that don't set `autoLore` themselves. */
    @Volatile var autoLore: Boolean = false
        private set

    /** Audit destination for run_command: off, console, or file. */
    @Volatile var logCommands: String = "off"
        private set

    fun load() {
        val file = FiwToolsPlatform.configRoot().parent.resolve("settings.json")
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.parent)
                val defaults = JsonObject().apply {
                    addProperty("autoLore", false)
                    addProperty("logCommands", "off")
                }
                Files.writeString(file, gson.toJson(defaults))
            }
            val root = gson.fromJson(Files.readString(file), JsonObject::class.java) ?: JsonObject()
            val audit = root.get("logCommands")?.asString ?: "off"
            logCommands = audit.takeIf { it in setOf("off", "console", "file") } ?: "off"
            if (audit != logCommands) logger.warn("Unknown logCommands value: $audit — using off")
            autoLore = root.get("autoLore")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        } catch (e: Exception) {
            logger.warn("Could not read ${file.fileName}: ${e.message} — using defaults")
            autoLore = false
            logCommands = "off"
        }
    }
}
