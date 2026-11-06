package com.wanderwildwood.tsuyaku

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.tsuyaku.ui.AboutDialog
import com.wanderwildwood.tsuyaku.ui.Desk
import com.wanderwildwood.tsuyaku.ui.LanguagesScreen
import com.wanderwildwood.tsuyaku.ui.MainScreen
import com.wanderwildwood.tsuyaku.ui.houseTypography
import com.wanderwildwood.tsuyaku.ui.monochrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.wanderwildwood.tsuyaku.ui.ArmedScope

/**
 * The home-screen entry, and where text and pictures shared from other apps arrive.
 *
 * A shared picture is copied into the app's own storage before anything else happens: the
 * permission to read the sender's copy lasts only as long as this activity, and Android may
 * close it to free memory while Tesseract is still reading.
 */
class MainActivity : ComponentActivity() {
    private lateinit var desk: Desk
    private var showLanguages by mutableStateOf(false)

    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val shot = cameraFile()
        if (saved && shot.length() > 0) {
            shot.renameTo(app.memory.picture)
            desk.readPendingPicture()
        } else {
            shot.delete()
        }
    }

    private val gallery = registerForActivityResult(FilesGetContent()) { uri ->
        if (uri != null) takePicture(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        desk = Desk(app, lifecycleScope)
        if (savedInstanceState == null) take(intent)

        setContent {
            ThemeMMD(colorScheme = monochrome, typography = houseTypography) {
                ArmedScope {
                    // A blinking caret redraws the panel twice a second for as long as the field has focus.
                    CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                    val states by app.packs.states.collectAsStateWithLifecycle()
                    val installed = app.packs.installed(states)
                    LaunchedEffect(installed) { desk.settle(installed) }
                    var about by rememberSaveable { mutableStateOf(false) }

                    BackHandler(enabled = showLanguages) { showLanguages = false }
                    if (showLanguages) {
                        LanguagesScreen(
                            catalog = app.packs.catalog,
                            states = states,
                            onDownload = app.packs::download,
                            onStop = app.packs::stop,
                            onDelete = app.packs::delete,
                            onBack = { showLanguages = false },
                            onAbout = { about = true },
                        )
                    } else {
                        MainScreen(
                            desk = desk,
                            installed = installed,
                            onLanguages = { showLanguages = true },
                            onGet = { code ->
                                app.packs.download(code)
                                showLanguages = true
                            },
                            onAbout = { about = true },
                            onCamera = if (cameraAvailable()) ({ openCamera() }) else null,
                            onPicture = { runCatching { gallery.launch("image/*") }.onFailure { say(R.string.nothing_to_share) } },
                            onCopy = ::copy,
                            onShare = ::share,
                        )
                    }
                    if (about) AboutDialog(onDismiss = { about = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
    }

    override fun onResume() {
        super.onResume()
        // The card may have gone in or out while the app was away.
        app.packs.refresh()
    }

    override fun onPause() {
        super.onPause()
        desk.flush()
    }

    /** Text or a picture handed over by another app, or a language asked for by the overlay. */
    private fun take(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = intent.streamUri()
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                if (intent.type?.startsWith("image/") == true && stream != null) {
                    showLanguages = false
                    takePicture(stream)
                } else if (!text.isNullOrBlank()) {
                    showLanguages = false
                    desk.replace(text)
                }
            }
            ACTION_GET_LANGUAGE -> {
                intent.getStringExtra(EXTRA_LANGUAGE)?.let { app.packs.download(it) }
                showLanguages = true
            }
            ACTION_OPEN_TEXT -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { desk.replace(it) }
                val from = intent.getStringExtra(EXTRA_FROM)
                val to = intent.getStringExtra(EXTRA_TO)
                if (from != null && to != null) desk.choose(from, to)
                showLanguages = false
            }
        }
    }

    private fun takePicture(uri: Uri) {
        lifecycleScope.launch {
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val part = File(app.memory.picture.path + ".part")
                        part.outputStream().use { input.copyTo(it) }
                        part.renameTo(app.memory.picture)
                    } ?: false
                }.getOrDefault(false)
            }
            if (copied) desk.readPendingPicture() else say(R.string.picture_unreadable)
        }
    }

    private fun cameraFile() = File(cacheDir, "camera/shot.jpg")

    // Android 11+ shows only what <queries> names; the manifest lists IMAGE_CAPTURE. The button
    // is offered only when a camera app really answers it.
    private fun cameraAvailable(): Boolean = runCatching {
        packageManager.resolveActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE), PackageManager.MATCH_DEFAULT_ONLY) != null
    }.getOrDefault(false)

    private fun openCamera() {
        val file = cameraFile()
        file.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        runCatching { camera.launch(uri) }.onFailure { say(R.string.no_camera) }
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { startActivity(Intent.createChooser(send, null)) }.onFailure { say(R.string.nothing_to_share) }
    }

    private fun say(text: Int) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object {
        const val ACTION_GET_LANGUAGE = "com.wanderwildwood.tsuyaku.GET_LANGUAGE"
        const val ACTION_OPEN_TEXT = "com.wanderwildwood.tsuyaku.OPEN_TEXT"
        const val EXTRA_LANGUAGE = "language"
        const val EXTRA_FROM = "from"
        const val EXTRA_TO = "to"
    }
}

@Suppress("DEPRECATION")
private fun Intent.streamUri(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
