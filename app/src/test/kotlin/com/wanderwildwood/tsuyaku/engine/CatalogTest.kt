package com.wanderwildwood.tsuyaku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/** The list the app ships, read the way the app reads it. */
class CatalogTest {
    private val catalog = Catalog.parse(File("src/main/assets/catalog.json").readText())

    @Test fun everyLanguageHasAtLeastOneModel() {
        assertTrue(catalog.languages.size > 40)
        for (l in catalog.languages) assertTrue(l.code, l.directions.isNotEmpty())
    }

    @Test fun everyModelHasItsFourParts() {
        for (l in catalog.languages) for (d in l.directions) {
            assertTrue(d.model.name.endsWith(".bin"))
            assertTrue(d.srcVocab.name.endsWith(".spm"))
            assertTrue(d.trgVocab.name.endsWith(".spm"))
            assertTrue(d.lex.name.endsWith(".bin"))
            assertNotNull("${l.code} model hash", d.model.sha256)
            for (f in d.files) assertTrue(f.size > 0 && f.gz > 0)
        }
    }

    @Test fun spanishBothWaysWithPictures() {
        val es = catalog.lang("es")!!
        assertNotNull(es.toEnglish)
        assertNotNull(es.fromEnglish)
        assertEquals("spa", es.tess)
        assertTrue(es.tessSize > 0)
    }

    @Test fun englishIsNotAPack() = assertNull(catalog.lang(ENGLISH))

    @Test fun addressesArePinned() {
        assertTrue(catalog.modelBase.startsWith("https://storage.googleapis.com/"))
        assertTrue(catalog.tessBase.matches(Regex("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/[0-9a-f]{40}")))
    }

    @Test fun namesComeFromThePlatform() {
        assertEquals("Spanish", languageName("es", Locale.ENGLISH))
        assertEquals("Chinese (Traditional)", languageName("zh_hant", Locale.ENGLISH))
        assertEquals("Español", languageName("es", Locale.forLanguageTag("es")))
    }
}
