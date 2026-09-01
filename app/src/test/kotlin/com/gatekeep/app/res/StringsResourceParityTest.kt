package com.gatekeep.app.res

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringsResourceParityTest {

    @Test
    fun `localized strings contain all base keys`() {
        val resDir = locateResDir()
        val baseKeys = readStringKeys(File(resDir, "values/strings.xml"))
        listOf("values-en-rGB", "values-de-rAT", "values-es-rES").forEach { localeDir ->
            val localeKeys = readStringKeys(File(resDir, "$localeDir/strings.xml"))
            val missing = baseKeys - localeKeys
            assertTrue("Missing keys in $localeDir: $missing", missing.isEmpty())
        }
    }

    private fun locateResDir(): File {
        var dir = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "app/src/main/res")
            if (candidate.isDirectory) return candidate.absoluteFile
            dir = dir.parentFile
        }
        error("Could not locate app/src/main/res from ${System.getProperty("user.dir")}")
    }

    private fun readStringKeys(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        val keys = mutableSetOf<String>()
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as Element
            keys += element.getAttribute("name")
        }
        return keys
    }
}
