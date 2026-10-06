package com.wanderwildwood.tsuyaku.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureTextTest {
    @Test fun linesOfABlockRunTogether() =
        assertEquals("The trail closes at dusk.", PictureText.join(listOf(listOf("The trail closes", "at dusk."))))

    @Test fun blocksBecomeParagraphs() =
        assertEquals("Open\n\nClosed on Sundays", PictureText.join(listOf(listOf("Open"), listOf("Closed on", "Sundays"))))

    @Test fun aWordBrokenByAHyphenIsJoined() =
        assertEquals("Please keep dogs on a lead", PictureText.join(listOf(listOf("Please keep dogs on a le-", "ad"))))

    @Test fun aHyphenBeforeACapitalStays() =
        assertEquals("Nord- Süd", PictureText.join(listOf(listOf("Nord-", "Süd"))))

    @Test fun emptyBlocksAndLinesGo() =
        assertEquals("Exit", PictureText.join(listOf(listOf("  ", ""), listOf(" Exit "))))
}
