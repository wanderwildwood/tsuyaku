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
}
