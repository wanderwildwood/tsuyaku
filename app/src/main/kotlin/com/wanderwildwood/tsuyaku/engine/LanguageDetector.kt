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

import dev.davidv.bergamot.LangDetect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which language a text is in, by CLD2, when it is sure enough to say. */
class LanguageDetector {
  init {
    // The detector lives in the same library as the translator; load it either way first.
    System.loadLibrary("bergamot-sys")
  }

  private val langDetect = LangDetect()

  suspend fun detectLanguage(
    text: String,
    hint: String?,
  ): String? =
    withContext(Dispatchers.IO) {
      if (text.isBlank()) {
        return@withContext null
      }

      val detected = langDetect.detectLanguage(text, hint)
      if (detected.isReliable) fromCld2(detected.language) else null
    }

  companion object {
    /** CLD2's codes, in this app's terms. CLD2 still writes Hebrew as "iw". */
    fun fromCld2(code: String): String? =
      when (code) {
        "un", "xx", "" -> null
        "iw" -> "he"
        "zh-Hant", "zh-TW" -> "zh_hant"
        "zh-Hans" -> "zh"
        "in" -> "id"
        else -> code.substringBefore('-')
      }
  }
}
