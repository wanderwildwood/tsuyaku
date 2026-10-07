package com.wanderwildwood.tsuyaku.engine

import java.text.BreakIterator
import java.util.Locale

/** A run of whole sentences to show as one row of a list, and whether a paragraph starts with it. */
data class Piece(val text: String, val opensParagraph: Boolean)

/**
 * A translation cut into rows for a list that moves a row at a time.
 *
 * A row taller than the screen would be stepped past half read, and a long paragraph is easily
 * that, so a paragraph is cut between sentences into rows of at most about [most] letters.
 * Short sentences stay together, so the text still reads as a paragraph.
 */
object Pieces {
    fun of(text: String, locale: Locale = Locale.getDefault(), most: Int = 280): List<Piece> {
        val out = mutableListOf<Piece>()
        var first = true
        for (paragraph in text.split('\n')) {
            if (paragraph.isBlank()) {
                first = true
                continue
            }
            val opens = first || out.isEmpty()
            first = false
            var row = StringBuilder()
            var opensRow = opens
            for (sentence in sentences(paragraph, locale)) {
                if (row.isNotEmpty() && row.length + sentence.length > most) {
                    out += Piece(row.toString().trimEnd(), opensRow)
                    row = StringBuilder()
                    opensRow = false
                }
                row.append(sentence)
            }
            if (row.isNotBlank()) out += Piece(row.toString().trimEnd(), opensRow)
        }
        return out
    }

    private fun sentences(paragraph: String, locale: Locale): List<String> {
        val it = BreakIterator.getSentenceInstance(locale)
        it.setText(paragraph)
        val out = mutableListOf<String>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            out += paragraph.substring(start, end)
            start = end
            end = it.next()
        }
        return out
    }
}
