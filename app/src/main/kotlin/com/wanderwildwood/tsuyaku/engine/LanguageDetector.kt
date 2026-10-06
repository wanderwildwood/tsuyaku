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

import dev.davidv.bergamot.LangDetect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LanguageDetector {
  private val langDetect = LangDetect()

  suspend fun detectLanguage(
    text: String,
    fromLang: Language?,
  ): Language? =
    withContext(Dispatchers.IO) {
      if (text.isBlank()) {
        return@withContext null
      }

      val detected = langDetect.detectLanguage(text, fromLang?.code)
      if (detected.isReliable) {
        Language.entries.firstOrNull { it.code == detected.language }
      } else {
        null
      }
    }
}
