package com.fiw.tools.config

import com.fiw.tools.recipe.RecipeConfigParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/** Uses the production parsers, with no Minecraft runtime or registry required. */
class ExampleConfigsTest {
    @Test
    fun parsesEveryExampleItemAndRecipe() {
        val working = Path.of(System.getProperty("user.dir"))
        val root = (if (working.fileName.toString() == "core") working.parent else working).resolve("examples")
        val files = Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }
                .filter { it.parent.fileName.toString() in setOf("items", "recipes") }
                .sorted().toList()
        }
        assertTrue(files.isNotEmpty(), "No example configs found at $root")
        val errors = mutableListOf<String>()
        for (file in files) {
            try {
                if (file.parent.fileName.toString() == "recipes") {
                    assertTrue(RecipeConfigParser.parseAll(file.readText(), file.fileName.toString()).isNotEmpty())
                } else {
                    val item = ItemConfigParser.parse(file.readText())
                    assertTrue(item.id.isNotBlank() && item.base.isNotBlank())
                }
            } catch (e: Exception) {
                errors.add("${root.relativize(file)}: ${e.message}")
            } catch (e: AssertionError) {
                errors.add("${root.relativize(file)}: ${e.message}")
            }
        }
        assertTrue(errors.isEmpty(), errors.joinToString("\n"))
    }
}
