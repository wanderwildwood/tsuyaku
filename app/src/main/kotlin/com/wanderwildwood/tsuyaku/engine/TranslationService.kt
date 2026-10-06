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

import android.util.Log
import dev.davidv.bergamot.NativeLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.measureTimeMillis

/** What came of asking for a translation. */
sealed interface Outcome {
    data class Done(val text: String, val romanised: String?) : Outcome

    /** A pack the pair needs is not on the phone ([code] is the language that is missing). */
    data class Missing(val code: String) : Outcome

    /** No model goes that way yet: Mozilla has a model into English for the language, but not out of it, or the reverse. */
    data class NoModel(val code: String) : Outcome

    data class Failed(val message: String) : Outcome
}

/**
 * Bergamot, the engine Firefox translates pages with, run on the phone.
 *
 * Every model goes to or from English, so a pair with English on neither side is two models
 * in a row, the English between them never shown. Bergamot keeps each model it loads in
 * memory; on this phone that is the difference between a translation in a second and one in
 * five, and also most of the app's memory. So at most the two models one pair needs are kept,
 * and [release] lets them go when Android asks for memory back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TranslationService(private val packs: Packs) {
    // One translation at a time, always on the same thread.
    private val engine = Dispatchers.IO.limitedParallelism(1)

    private var native: NativeLib? = null
    private val loaded = LinkedHashSet<String>()

    suspend fun translate(from: String, to: String, text: String): Outcome =
        withContext(engine) {
            if (text.isBlank()) return@withContext Outcome.Done("", null)
            if (from == to) return@withContext Outcome.Done(text, null)
            // Numbers don't translate.
            if (text.trim().toFloatOrNull() != null) return@withContext Outcome.Done(text, null)

            val steps = steps(from, to)
            val configs = mutableListOf<Pair<String, String>>()
            for ((a, b) in steps) {
                val code = if (a == ENGLISH) b else a
                val lang = packs.catalog.lang(code) ?: return@withContext Outcome.NoModel(code)
                val direction = (if (a == ENGLISH) lang.fromEnglish else lang.toEnglish)
                    ?: return@withContext Outcome.NoModel(code)
                val dir = packs.modelDir(code) ?: return@withContext Outcome.Missing(code)
                configs += "$a$b" to generateConfig(dir, direction)
            }

            try {
                val lib = load(configs)
                val result: String
                val elapsed = measureTimeMillis {
                    result = if (configs.size == 1) {
                        lib.translateMultiple(arrayOf(text), configs[0].first)[0]
                    } else {
                        lib.pivotMultiple(configs[0].first, configs[1].first, arrayOf(text))[0]
                    }
                }
                Log.d(TAG, "${text.length} characters $from to $to in ${elapsed}ms")
                Outcome.Done(result, TransliterationService.transliterate(result, to))
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed", e)
                // A model that failed half way in is not one to trust with the next request.
                release()
                Outcome.Failed(e.message ?: e.toString())
            }
        }

    /** Let go of every loaded model. The next translation loads what it needs again. */
    suspend fun release() =
        withContext(engine) {
            native?.cleanup()
            native = null
            loaded.clear()
        }

    private fun load(configs: List<Pair<String, String>>): NativeLib {
        val keys = configs.map { it.first }
        if (!loaded.containsAll(keys)) {
            // Only what this pair needs stays: a third model would be memory the phone does not have.
            native?.cleanup()
            native = null
            loaded.clear()
        }
        val lib = native ?: NativeLib().also { native = it }
        for ((key, config) in configs) {
            if (key in loaded) continue
            val ms = measureTimeMillis { lib.loadModelIntoCache(config, key) }
            Log.d(TAG, "Loaded model $key in ${ms}ms")
            loaded += key
        }
        return lib
    }

    private fun steps(from: String, to: String): List<Pair<String, String>> =
        when {
            from == ENGLISH -> listOf(from to to)
            to == ENGLISH -> listOf(from to to)
            else -> listOf(from to ENGLISH, ENGLISH to to) // Pivot through English
        }

    private fun generateConfig(dir: File, d: Direction): String =
        """
models:
  - ${File(dir, d.model.name)}
vocabs:
  - ${File(dir, d.srcVocab.name)}
  - ${File(dir, d.trgVocab.name)}
shortlist:
    - ${File(dir, d.lex.name)}
    - false
beam-size: 1
normalize: 1.0
word-penalty: 0
max-length-break: 128
mini-batch-words: 1024
workspace: 128
max-length-factor: 2.0
skip-cost: true
cpu-threads: 0
quiet: false
quiet-translation: false
gemm-precision: int8shiftAlphaAll
alignment: soft
"""

    private companion object {
        const val TAG = "TranslationService"
    }
}
