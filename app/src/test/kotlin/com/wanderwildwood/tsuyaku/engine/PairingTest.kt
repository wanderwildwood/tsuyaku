package com.wanderwildwood.tsuyaku.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingTest {
    private val installed = setOf("en", "es", "fr")
    private val last = Way("es", "en")

    @Test fun textInTheLanguageReadGoesIntoTheOtherOne() =
        assertEquals(Way("es", "en"), Pairing.choose("es", last, installed))

    @Test fun textAlreadyInTheTargetGoesBackTheOtherWay() =
        assertEquals(Way("en", "es"), Pairing.choose("en", last, installed))

    @Test fun anotherLanguageOnThePhoneBecomesTheSource() =
        assertEquals(Way("fr", "en"), Pairing.choose("fr", last, installed))

    @Test fun aLanguageNotOnThePhoneLeavesTheWayAlone() =
        assertEquals(last, Pairing.choose("de", last, installed))

    @Test fun noGuessLeavesTheWayAlone() =
        assertEquals(last, Pairing.choose(null, last, installed))

    @Test fun firstWayIsIntoThePhonesLanguage() =
        assertEquals(Way("es", "en"), Pairing.first(setOf("en", "es"), "en"))

    @Test fun firstWayIsOutOfEnglishWhenThePhonesLanguageIsFetched() =
        assertEquals(Way("en", "de"), Pairing.first(setOf("en", "de"), "de"))

    @Test fun firstWayIntoEnglishWhenThePhonesLanguageIsMissing() =
        assertEquals(Way("fr", "en"), Pairing.first(setOf("en", "fr"), "pl"))

    @Test fun nothingFetchedIsEnglishBothWays() =
        assertEquals(Way("en", "en"), Pairing.first(setOf("en"), "en"))

    @Test fun textInAnotherLanguageIsWarnedAbout() =
        assertEquals("fr", Pairing.mismatch("fr", last))

    @Test fun textInTheSourceLanguageIsNotWarnedAbout() =
        assertEquals(null, Pairing.mismatch("es", last))

    @Test fun noGuessIsNotWarnedAbout() =
        assertEquals(null, Pairing.mismatch(null, last))

    @Test fun theTwoChineseScriptsAreOneLanguageForTheWarning() =
        assertEquals(null, Pairing.mismatch("zh", Way("zh_hant", "en")))
}
