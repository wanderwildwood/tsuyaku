package com.wanderwildwood.tsuyaku.engine

/**
 * The words read from a picture as text to translate: a paragraph per block Tesseract found,
 * its lines run together, since a picture breaks lines where the page ran out of width and a
 * sentence cut in two translates as two broken halves. A word split by a hyphen at a line's
 * end is joined again.
 */
object PictureText {
    fun join(blocks: List<List<String>>): String =
        blocks.map { lines -> joinLines(lines.map { it.trim() }.filter { it.isNotEmpty() }) }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

    private fun joinLines(lines: List<String>): String {
        val out = StringBuilder()
        for (line in lines) {
            if (out.isEmpty()) {
                out.append(line)
            } else if (out.length >= 2 && out.last() == '-' && out[out.length - 2].isLetter() && line.first().isLowerCase()) {
                out.setLength(out.length - 1)
                out.append(line)
            } else {
                out.append(' ').append(line)
            }
        }
        return out.toString()
    }
}
