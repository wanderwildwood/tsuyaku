package com.wanderwildwood.tsuyaku

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.net.Uri
import com.wanderwildwood.tsuyaku.engine.Catalog
import com.wanderwildwood.tsuyaku.engine.ENGLISH
import com.wanderwildwood.tsuyaku.engine.ImageProcessor
import com.wanderwildwood.tsuyaku.engine.LanguageDetector
import com.wanderwildwood.tsuyaku.engine.OCRService
import com.wanderwildwood.tsuyaku.engine.Packs
import com.wanderwildwood.tsuyaku.engine.PictureText
import com.wanderwildwood.tsuyaku.engine.Storage
import com.wanderwildwood.tsuyaku.engine.TranslationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The parts every screen shares: the packs, the engine, the detector and the picture reader. */
class TsuyakuApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var packs: Packs
        private set
    lateinit var translator: TranslationService
        private set
    lateinit var memory: Memory
        private set
    val detector by lazy { LanguageDetector() }

    override fun onCreate() {
        super.onCreate()
        memory = Memory(this)
        packs = Packs(Catalog.load(this), Storage(this), scope)
        translator = TranslationService(packs)
        packs.refresh()
    }

    /**
     * The loaded models are most of the app's memory: about 200 MB for one, twice that for a
     * pair through English. They go as soon as nothing of the app is on screen, the panel over
     * another app closing included, rather than sitting in a phone with little to spare until
     * Android has to ask. The next translation loads them again, well under a second.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            scope.launch { translator.release() }
        }
    }

    /**
     * The words in a picture, as text to translate, read in the languages of the way being
     * translated plus English. Empty when there are none, or the picture could not be opened.
     */
    suspend fun readPicture(file: File, from: String, to: String): String? =
        withContext(Dispatchers.IO) {
            val images = ImageProcessor(this@TsuyakuApp)
            val uri = Uri.fromFile(file)
            val bitmap = runCatching {
                images.downscaleImage(images.correctImageOrientation(uri, images.loadBitmapFromUri(uri, MAX_PICTURE)), MAX_PICTURE)
            }.getOrNull() ?: return@withContext null
            val ready = packs.tessReady()
            val wanted = listOf(from, to, ENGLISH).mapNotNull { code ->
                if (code == ENGLISH) "eng" else packs.catalog.lang(code)?.tess
            }.distinct().filter { it in ready }
            val blocks = OCRService(packs.tessRoot()).extractText(bitmap, wanted)
            bitmap.recycle()
            PictureText.join(blocks.map { b -> b.lines.map { it.text } })
        }

    private companion object {
        // Offline Translator's default: enough for print on a photographed page.
        const val MAX_PICTURE = 1500
    }
}

val Context.app: TsuyakuApp get() = applicationContext as TsuyakuApp
