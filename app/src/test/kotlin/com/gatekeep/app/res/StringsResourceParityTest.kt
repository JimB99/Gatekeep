package com.gatekeep.app.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringsResourceParityTest {

    @Test
    fun `localized strings contain all base keys`() {
        val baseStrings = readStrings(File(locateResDir(), "values/strings.xml"))
        LOCALE_DIRS.forEach { localeDir ->
            val localeStrings = readStrings(File(locateResDir(), "$localeDir/strings.xml"))
            val missing = baseStrings.keys - localeStrings.keys
            assertTrue("Missing keys in $localeDir: $missing", missing.isEmpty())
        }
    }

    @Test
    fun sessionContinuityGraceCopyIsTranslatedAndFormats() {
        val base = readStrings(File(locateResDir(), "values/strings.xml"))
        val sampleDuration = "15s"
        LOCALE_DIRS.forEach { localeDir ->
            val localeStrings = readStrings(File(locateResDir(), "$localeDir/strings.xml"))
            GRACE_FORMAT_KEYS.forEach { key ->
                val formatted = String.format(localeStrings.getValue(key), sampleDuration)
                assertTrue(
                    "$key in $localeDir did not insert the duration: $formatted",
                    formatted.contains(sampleDuration),
                )
                assertTrue(
                    "$key in $localeDir still has a placeholder: $formatted",
                    !formatted.contains("%"),
                )
            }
        }
        assertTrue(
            "de-AT still uses the English settings label",
            readStrings(File(locateResDir(), "values-de-rAT/strings.xml"))
                .getValue("session_continuity_grace") != base.getValue("session_continuity_grace"),
        )
        assertTrue(
            "es-ES still uses the English settings label",
            readStrings(File(locateResDir(), "values-es-rES/strings.xml"))
                .getValue("session_continuity_grace") != base.getValue("session_continuity_grace"),
        )
    }

    @Test
    fun `localized strings use the same format placeholders as the base locale`() {
        val baseStrings = readStrings(File(locateResDir(), "values/strings.xml"))
        LOCALE_DIRS.forEach { localeDir ->
            val localeStrings = readStrings(File(locateResDir(), "$localeDir/strings.xml"))
            localeStrings.forEach { (key, value) ->
                val baseValue = baseStrings[key] ?: return@forEach
                assertEquals(
                    "Placeholder mismatch for '$key' in $localeDir",
                    placeholdersOf(baseValue).toSet(),
                    placeholdersOf(value).toSet(),
                )
            }
        }
    }

    private fun placeholdersOf(value: String): List<String> =
        PLACEHOLDER_REGEX.findAll(value).map { it.value }.sorted().toList()

    private fun locateResDir(): File {
        val workingDir = System.getProperty("user.dir") ?: "."
        var dir: File? = File(workingDir)
        while (dir != null) {
            val candidate = File(dir, "app/src/main/res")
            if (candidate.isDirectory) return candidate.absoluteFile
            dir = dir.parentFile
        }
        error("Could not locate app/src/main/res from $workingDir")
    }

    private fun readStrings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                put(element.getAttribute("name"), element.textContent.orEmpty())
            }
        }
    }

    private companion object {
        val LOCALE_DIRS = listOf("values-en-rGB", "values-de-rAT", "values-es-rES")
        val PLACEHOLDER_REGEX = Regex("""%\d+\$[sdfx]|%[sdfx]""")
        val GRACE_FORMAT_KEYS = listOf("session_hard_block_hint", "block_session_hard_block")
    }
}
