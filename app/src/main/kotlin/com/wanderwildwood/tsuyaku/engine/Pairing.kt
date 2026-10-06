package com.wanderwildwood.tsuyaku.engine

/** A way to translate: from one language into another. */
data class Way(val from: String, val to: String)

/**
 * Which way to translate text that arrived from somewhere else, given the language it looks
 * like it is in.
 *
 * Text selected in another app is usually in the language being read, so it goes into the
 * language last translated into. Text in that language already is usually a reply being
 * written, so it goes the other way, into the language last translated from. When the
 * detector cannot tell, or the language it names is not on the phone, the last way stands.
 */
object Pairing {
    fun choose(detected: String?, last: Way, installed: Set<String>): Way =
        when {
            detected == null -> last
            detected == last.to && last.from != last.to -> Way(last.to, last.from)
            detected == last.from -> last
            detected in installed -> Way(detected, last.to)
            else -> last
        }

    /**
     * The way to start with on a phone that has never translated: from a language fetched
     * into the phone's own (English when the phone's own is not on it), or out of English
     * when the phone's own language is the one fetched.
     */
    fun first(installed: Set<String>, phone: String): Way {
        val to = if (phone in installed) phone else ENGLISH
        val from = installed.filter { it != to }.sortedWith(compareBy({ it == ENGLISH }, { it })).firstOrNull() ?: ENGLISH
        return Way(from, to)
    }
}
