package com.fiw.tools.ability

import com.fiw.tools.FiwToolsPlatform
import com.fiw.tools.config.ToolsSettings
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Logs the substituted command before dispatch, including commands rejected by Brigadier. */
object CommandAudit {
    private val logger = LoggerFactory.getLogger("fiw-tools/run_command")
    private val context = ThreadLocal<Pair<String, Int>>()
    private var writer = Executors.newSingleThreadExecutor()

    fun <T> during(itemId: String, index: Int, action: () -> T): T {
        val previous = context.get()
        context.set(itemId to index)
        try {
            return action()
        } finally {
            if (previous == null) context.remove() else context.set(previous)
        }
    }

    fun record(ctx: AbilityContext, command: String) {
        val mode = ToolsSettings.logCommands
        if (mode == "off") return
        val origin = context.get()
        val line = JsonObject().apply {
            addProperty("time", Instant.now().toString())
            addProperty("player", ctx.player.gameProfile.name)
            addProperty("uuid", ctx.player.uuid.toString())
            addProperty("item", origin?.first ?: com.fiw.tools.build.FiwItems.fiwId(ctx.stack))
            addProperty("ability", origin?.second ?: -1)
            addProperty("command", command)
        }.toString()
        if (mode == "console") {
            logger.info(line)
        } else {
            val file = FiwToolsPlatform.configRoot().parent.resolve("logs/commands.jsonl")
            if (writer.isShutdown) writer = Executors.newSingleThreadExecutor()
            writer.submit {
                try {
                    Files.createDirectories(file.parent)
                    Files.writeString(file, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                } catch (e: Exception) {
                    logger.error("Could not write command audit log", e)
                }
            }
        }
    }

    fun stop() {
        writer.shutdown()
        writer.awaitTermination(30, TimeUnit.SECONDS)
    }
}
