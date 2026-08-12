package com.fiw.tools

import java.nio.file.Path

object FiwToolsPlatform {
    private var configDirectory: Path? = null

    fun init(configDir: Path) {
        configDirectory = configDir
    }

    fun configRoot(): Path =
        (configDirectory ?: error("Fiw Tools platform config directory was not initialized"))
            .resolve("fiw_tools")
            .resolve("items")

    fun recipesRoot(): Path =
        (configDirectory ?: error("Fiw Tools platform config directory was not initialized"))
            .resolve("fiw_tools")
            .resolve("recipes")
}
