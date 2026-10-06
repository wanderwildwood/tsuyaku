package com.wanderwildwood.tsuyaku

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.tsuyaku.engine.Outcome
import com.wanderwildwood.tsuyaku.engine.Pairing
import com.wanderwildwood.tsuyaku.engine.Way
import com.wanderwildwood.tsuyaku.ui.Desk
import com.wanderwildwood.tsuyaku.ui.Overlay
import com.wanderwildwood.tsuyaku.ui.monochrome

/**
 * "Translate" in the menu other apps show over selected text, and in Android's own translate
 * action. A small panel over the app it came from, with the translation and what to do with
 * it; Back or a press outside it goes straight back.
 *
 * The way is guessed from the text: something in the language last translated from goes into
 * the one last translated into, and something already in that one goes back the other way,
 * since it is most likely a reply being written. When the text came from a field that can be
 * written in, the translation can be put in its place.
 *
 * Nothing here needs saving if Android closes it: the text comes with the request, which is
 * delivered again.
 */
class TranslateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = (intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT) ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
            ?.toString()
        if (text.isNullOrBlank()) {
            finish()
            return
        }
        val writable = intent.action == Intent.ACTION_PROCESS_TEXT &&
            !intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false) &&
            callingActivity != null

        setContent {
            ThemeMMD(colorScheme = monochrome) {
                val packs = app.packs
                var way by remember { mutableStateOf<Way?>(null) }
                var outcome by remember { mutableStateOf<Outcome?>(null) }
                var working by remember { mutableStateOf(true) }

                val states by packs.states.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    val installed = packs.installedNow()
                    val last = app.memory.way ?: Pairing.first(installed, Desk.phoneLanguage())
                    val detected = runCatching { app.detector.detectLanguage(text, last.from) }.getOrNull()
                    way = Pairing.choose(detected, last, installed)
                }
                LaunchedEffect(way) {
                    val w = way ?: return@LaunchedEffect
                    working = true
                    outcome = app.translator.translate(w.from, w.to, text)
                    working = false
                }

                Overlay(
                    text = text,
                    way = way,
                    outcome = outcome,
                    working = working,
                    installed = packs.installed(states),
                    catalog = packs.catalog,
                    writable = writable,
                    onWay = { w ->
                        way = w
                        app.memory.way = w
                    },
                    onReplace = { replaceWith(it) },
                    onCopy = { copy(it) },
                    onShare = { share(it) },
                    onOpen = { open(text, way) },
                    onGet = { code -> getLanguage(code) },
                    onDismiss = ::finish,
                )
            }
        }
    }

    private fun replaceWith(translation: String) {
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, translation))
        finish()
    }

    private fun copy(translation: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), translation))
    }

    private fun share(translation: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, translation)
        runCatching { startActivity(Intent.createChooser(send, null)) }
        finish()
    }

    private fun open(text: String, way: Way?) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_TEXT)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (way != null) intent.putExtra(MainActivity.EXTRA_FROM, way.from).putExtra(MainActivity.EXTRA_TO, way.to)
        startActivity(intent)
        finish()
    }

    /** The languages screen, fetching [code] if one is named. */
    private fun getLanguage(code: String?) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_GET_LANGUAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (code != null) intent.putExtra(MainActivity.EXTRA_LANGUAGE, code)
        startActivity(intent)
        finish()
    }
}
