package com.wanderwildwood.tsuyaku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PiecesTest {
    @Test fun shortSentencesStayTogether() =
        assertEquals(listOf(Piece("Open daily. Closed on Sundays.", true)), Pieces.of("Open daily. Closed on Sundays.", Locale.ENGLISH))

    @Test fun paragraphsOpenRows() {
        val p = Pieces.of("First one.\n\nSecond one.", Locale.ENGLISH)
        assertEquals(listOf(Piece("First one.", true), Piece("Second one.", true)), p)
    }

    @Test fun aLongParagraphIsCutBetweenSentences() {
        val sentence = "The trail climbs through a forest of oaks and chestnut trees. "
        val p = Pieces.of(sentence.repeat(10).trim(), Locale.ENGLISH, most = 140)
        assertTrue(p.size > 3)
        assertTrue(p.first().opensParagraph)
        assertTrue(p.drop(1).none { it.opensParagraph })
        assertTrue(p.all { it.text.length <= 140 })
        assertEquals(sentence.repeat(10).trim(), p.joinToString(" ") { it.text })
    }

    @Test fun aSentenceLongerThanARowIsKeptWhole() {
        val long = "word ".repeat(100).trim() + "."
        assertEquals(listOf(Piece(long, true)), Pieces.of(long, Locale.ENGLISH, most = 50))
    }
}
