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

package dev.davidv.translator

import android.icu.text.Transliterator
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

object TransliterationService {
  private val transliterators = mutableMapOf<String, Transliterator?>()

  private val scriptComponents =
    mapOf(
      "Arabic" to listOf("Arabic"),
      "Cyrillic" to listOf("Cyrillic"),
      "Greek" to listOf("Greek"),
      "Han" to listOf("Han"),
      "Japanese" to listOf("Hiragana", "Katakana", "Han"),
      "Hangul" to listOf("Hangul"),
      "Devanagari" to listOf("Devanagari"),
      "Hebrew" to listOf("Hebrew"),
      "Bengali" to listOf("Bengali"),
      "Gujarati" to listOf("Gujarati"),
      "Kannada" to listOf("Kannada"),
      "Malayalam" to listOf("Malayalam"),
      "Tamil" to listOf("Tamil"),
      "Telugu" to listOf("Telugu"),
    )

  private fun shouldTransliterate(
    language: Language,
    targetScript: String = "Latin",
  ): Boolean = language.script != targetScript && scriptComponents.containsKey(language.script)

  private fun getTransliterationRule(
    fromScript: String,
    toScript: String = "Latin",
  ): String? {
    val components = scriptComponents[fromScript] ?: return null
    return components.joinToString("; ") { "$it-$toScript" }
  }

  fun transliterate(
    text: String,
    language: Language,
    targetScript: String = "Latin",
  ): String? {
    if (!shouldTransliterate(language, targetScript) || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return null
    }

    val rule = getTransliterationRule(language.script, targetScript) ?: return null

    return try {
      val transliterator = getTransliterator(rule)
      transliterator?.transliterate(text)
    } catch (e: Exception) {
      Log.w("TransliterationService", "Failed to transliterate text for $language", e)
      null
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun getTransliterator(rule: String): Transliterator? =
    transliterators.getOrPut(rule) {
      try {
        Transliterator.getInstance(rule)
      } catch (e: Exception) {
        Log.w("TransliterationService", "Failed to create transliterator for rule: $rule", e)
        null
      }
    }
}
