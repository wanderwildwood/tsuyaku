package com.wanderwildwood.tsuyaku.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.wanderwildwood.tsuyaku.R
import com.wanderwildwood.tsuyaku.TsuyakuApp
import com.wanderwildwood.tsuyaku.engine.Outcome
import com.wanderwildwood.tsuyaku.engine.Pairing
import com.wanderwildwood.tsuyaku.engine.Way
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The main screen's state: the text being written, the way it is translated, and what came of
 * it. The text goes to [TsuyakuApp.memory] as it changes, so nothing typed is lost if Android
 * closes the app while it is out of sight.
 *
 * Translation starts when typing pauses, not on every letter: each result redraws the panel,
 * and the engine is busy for a moment with each.
 */
class Desk(val app: TsuyakuApp, private val scope: CoroutineScope) {
    private val memory = app.memory

    var input by mutableStateOf(TextFieldValue(memory.draft))
        private set
    var way by mutableStateOf(memory.way ?: Pairing.first(app.packs.installed(), phoneLanguage()))
        private set
    var outcome by mutableStateOf<Outcome?>(null)
        private set
    var working by mutableStateOf(false)
        private set
    var reading by mutableStateOf(false)
        private set
    var fromPicture by mutableStateOf(memory.draftFromPicture)
        private set

    /** A sentence about the last picture, when it gave no text. */
    var notice by mutableStateOf<Int?>(null)
        private set

    // Once the language translated from is picked by hand, the detector stops second-guessing it
    // until the text is replaced.
    private var fromByHand = false
    private var job: Job? = null
    private var saveJob: Job? = null
    private var generation = 0

    init {
        if (input.text.isNotBlank()) translateSoon(0)
        readPendingPicture()
    }

    fun edit(value: TextFieldValue) {
        val changed = value.text != input.text
        input = value
        if (changed) {
            notice = null
            saveSoon()
            translateSoon(PAUSE_MS)
        }
    }

    /** Text from elsewhere — shared, swapped in, or read from a picture — in place of what was there. */
    fun replace(text: String, picture: Boolean = false) {
        input = TextFieldValue(text, TextRange(text.length))
        fromPicture = picture
        memory.draftFromPicture = picture
        fromByHand = false
        notice = null
        save()
        if (text.isBlank()) {
            job?.cancel()
            outcome = null
        } else {
            translateSoon(0)
        }
    }

    fun clear() = replace("")

    fun choose(from: String? = null, to: String? = null) {
        if (from != null) fromByHand = true
        way = Way(from ?: way.from, to ?: way.to)
        memory.way = way
        translateSoon(0)
    }

    /**
     * After the packs change: a way that has nothing to translate with, or uses a language
     * since taken off the phone, gives way to the first sensible one.
     */
    fun settle(installed: Set<String>) {
        if (way.from != way.to && way.from in installed && way.to in installed) return
        val better = Pairing.first(installed, phoneLanguage())
        if (better == way) return
        way = better
        if (better.from != better.to) memory.way = better
        translateSoon(0)
    }

    /** The other way round, the translation becoming the text. */
    fun swap() {
        val translated = (outcome as? Outcome.Done)?.text
        way = Way(way.to, way.from)
        memory.way = way
        if (!translated.isNullOrBlank()) {
            replace(translated)
            fromByHand = true
        } else {
            fromByHand = true
            translateSoon(0)
        }
    }

    /** Reads the picture waiting in [com.wanderwildwood.tsuyaku.Memory.picture], if there is one. */
    fun readPendingPicture() {
        val file = memory.picture
        if (!file.exists() || reading) return
        reading = true
        notice = null
        scope.launch {
            val text = try {
                app.readPicture(file, way.from, way.to)
            } finally {
                // Read or not, the picture is done with: one that cannot be read must not be
                // tried again on every start.
                withContext(Dispatchers.IO) { file.delete() }
                reading = false
            }
            when {
                text == null -> notice = R.string.picture_unreadable
                text.isBlank() -> notice = R.string.no_words
                else -> replace(text, picture = true)
            }
        }
    }

    /** Write the draft now, not in a moment: the app is going out of sight. */
    fun flush() {
        saveJob?.cancel()
        memory.draft = input.text
    }

    private fun save() {
        saveJob?.cancel()
        val text = input.text
        saveJob = scope.launch(Dispatchers.IO) { memory.draft = text }
    }

    private fun saveSoon() {
        saveJob?.cancel()
        val text = input.text
        saveJob = scope.launch(Dispatchers.IO) {
            delay(SAVE_MS)
            memory.draft = text
        }
    }

    fun translateSoon(delayMs: Long) {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            val text = input.text
            if (text.isBlank()) {
                outcome = null
                return@launch
            }
            val mine = ++generation
            working = true
            try {
                if (!fromByHand && text.length >= DETECT_FROM) {
                    val detected = runCatching { app.detector.detectLanguage(text, way.from) }.getOrNull()
                    val better = Pairing.choose(detected, way, app.packs.installed())
                    if (better != way) {
                        way = better
                        memory.way = better
                    }
                }
                val result = app.translator.translate(way.from, way.to, text)
                if (input.text == text) outcome = result
            } finally {
                if (mine == generation) working = false
            }
        }
    }

    companion object {
        private const val PAUSE_MS = 900L
        private const val SAVE_MS = 300L

        // Under this many letters the detector guesses more than it knows.
        private const val DETECT_FROM = 12

        fun phoneLanguage(): String {
            val locale = Locale.getDefault()
            return when (val l = locale.language) {
                "iw" -> "he"
                "in" -> "id"
                "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zh_hant" else "zh"
                else -> l
            }
        }
    }
}
