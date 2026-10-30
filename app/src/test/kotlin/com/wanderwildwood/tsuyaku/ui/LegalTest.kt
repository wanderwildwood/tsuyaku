package com.wanderwildwood.tsuyaku.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalTest {
    @Test fun everyLibraryHasNameLicenceAndHttpsLink() {
        for (l in LIBRARIES + DOWNLOADED) {
            assertTrue(l.name, l.name.isNotBlank() && l.by.isNotBlank() && l.licence.isNotBlank())
            assertTrue(l.link, l.link.startsWith("https://"))
        }
        assertTrue(LIBRARIES.size >= 15)
    }

    @Test fun everyLicenceNamedHasAText() {
        for (l in LIBRARIES + DOWNLOADED) {
            val names = l.licence.split(" and ")
            for (n in names) assertTrue("${l.name}: $n", n in LICENCE_TEXTS)
        }
    }

    @Test fun modelsAreListedAsDownloaded() {
        assertTrue(DOWNLOADED.any { it.licence == "MPL-2.0" })
        assertTrue(SOURCE_URL.startsWith("https://github.com/"))
    }

    @Test fun paragraphsJoinWrappedLines() {
        assertEquals(listOf("one two", "three"), licenceParagraphs("one\n two\n\n\nthree\n"))
    }
}
