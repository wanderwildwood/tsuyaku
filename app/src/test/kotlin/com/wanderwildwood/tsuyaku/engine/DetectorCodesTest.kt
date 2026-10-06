package com.wanderwildwood.tsuyaku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorCodesTest {
    @Test fun cld2CodesBecomeTheApps() {
        assertEquals("he", LanguageDetector.fromCld2("iw"))
        assertEquals("zh_hant", LanguageDetector.fromCld2("zh-Hant"))
        assertEquals("zh", LanguageDetector.fromCld2("zh"))
        assertEquals("es", LanguageDetector.fromCld2("es"))
        assertNull(LanguageDetector.fromCld2("un"))
    }
}
