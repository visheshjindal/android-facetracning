package com.xim.facetracking

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ArchitectureTest {
    @Test fun productionLayersRespectDependencyDirection() {
        val source = listOf(File("src/main/java/com/xim/facetracking"), File("app/src/main/java/com/xim/facetracking"))
            .first { it.isDirectory }
        val forbidden = mapOf(
            "domain" to listOf("android.", "androidx.", "java.io.", "com.google.", "com.xim.facetracking.presentation.", "com.xim.facetracking.infrastructure.", "com.xim.facetracking.di."),
            "presentation" to listOf("com.xim.facetracking.infrastructure.", "com.xim.facetracking.di.", "androidx.camera.", "com.google.mlkit.", "java.io."),
            "infrastructure" to listOf("com.xim.facetracking.presentation.", "com.xim.facetracking.di.")
        )
        forbidden.forEach { (layer, imports) ->
            val files = source.resolve(layer).walkTopDown().filter { it.extension == "kt" }.toList()
            assertTrue("Missing source layer $layer", files.isNotEmpty())
            files.forEach { file ->
                val lines = file.readLines().filter { it.startsWith("import ") }
                imports.forEach { prefix -> assertFalse("${file.name} imports forbidden $prefix", lines.any { it.startsWith("import $prefix") }) }
            }
        }
        assertFalse(source.walkTopDown().filter { it.extension == "kt" }.any { it.readText().contains("com.xim.facetracking.capture.") })
    }
}
