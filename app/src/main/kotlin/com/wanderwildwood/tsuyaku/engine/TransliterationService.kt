/*
 * Copyright (C) 2024 David V
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.wanderwildwood.tsuyaku.engine

import android.icu.text.Transliterator
import android.util.Log

/**
 * The sound of a translation in Latin letters, under it, when it is in a script a reader of
 * the Latin alphabet may not read: Greek, Cyrillic, Arabic, Japanese and the rest. ICU, which
 * Android carries, does the work.
 */
object TransliterationService {
  private val transliterators = mutableMapOf<String, Transliterator?>()

  // ICU's names for the scripts each language is written in.
  private val scripts =
    mapOf(
      "ar" to listOf("Arabic"),
      "fa" to listOf("Arabic"),
      "ur" to listOf("Arabic"),
      "ug" to listOf("Arabic"),
      "be" to listOf("Cyrillic"),
      "bg" to listOf("Cyrillic"),
      "ru" to listOf("Cyrillic"),
      "sr" to listOf("Cyrillic"),
      "uk" to listOf("Cyrillic"),
      "el" to listOf("Greek"),
      "he" to listOf("Hebrew"),
      "hi" to listOf("Devanagari"),
      "mr" to listOf("Devanagari"),
      "bn" to listOf("Bengali"),
      "gu" to listOf("Gujarati"),
      "kn" to listOf("Kannada"),
      "ml" to listOf("Malayalam"),
      "ta" to listOf("Tamil"),
      "te" to listOf("Telugu"),
      "th" to listOf("Thai"),
      "ko" to listOf("Hangul"),
      "ja" to listOf("Hiragana", "Katakana", "Han"),
      "zh" to listOf("Han"),
      "zh_hant" to listOf("Han"),
    )

  fun transliterate(
    text: String,
    code: String,
  ): String? {
    val components = scripts[code] ?: return null
    val rule = components.joinToString("; ") { "$it-Latin" }
    return try {
      getTransliterator(rule)?.transliterate(text)?.takeIf { it != text }
    } catch (e: Exception) {
      Log.w("TransliterationService", "Failed to transliterate text for $code", e)
      null
    }
  }

  private fun getTransliterator(rule: String): Transliterator? =
    synchronized(transliterators) {
      transliterators.getOrPut(rule) {
        try {
          Transliterator.getInstance(rule)
        } catch (e: Exception) {
          Log.w("TransliterationService", "Failed to create transliterator for rule: $rule", e)
          null
        }
      }
    }
}
