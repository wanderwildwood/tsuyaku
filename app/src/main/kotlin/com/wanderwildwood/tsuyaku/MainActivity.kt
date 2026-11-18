package com.wanderwildwood.tsuyaku

import com.wanderwildwood.tsuyaku.ui.DarkSelection
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.net.Uri
import android.os.Bundle
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
        if (savedInstanceState == null) {
            // Left by an earlier run cut short: a copy half made, a picture whose read never
            // finished, a camera shot whose answer never came back. Cleared before the desk is
            // made, because the desk at once moves a waiting picture to the ".reading" name.
            File(app.memory.picture.path + ".part").delete()
            File(app.memory.picture.path + ".reading").delete()
            cameraFile().delete()
        }
        desk = Desk(app, lifecycleScope)
        // Reopened from Recents, Android hands back the intent the task first started with: text
        // or a picture shared long ago would take the place of the draft written since.
        val fromRecents = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromRecents) take(intent)

        setContent {
            ThemeMMD(colorScheme = monochrome, typography = houseTypography) { DarkSelection {
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
            } }
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
                // Only the overlay, in this same process, may start a download this way; any
                // other app sending the action just opens the languages screen.
                val ours = intent.getStringExtra(EXTRA_TOKEN) == TOKEN
                if (ours) intent.getStringExtra(EXTRA_LANGUAGE)?.let { app.packs.download(it) }
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
                val part = File(app.memory.picture.path + ".part")
                try {
                    runCatching {
                        contentResolver.openInputStream(uri)?.use { input ->
                            part.outputStream().use { out ->
                                // No photo comes near this; stop rather than fill the phone.
                                val buffer = ByteArray(64 * 1024)
                                var total = 0L
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    check(total <= MAX_PICTURE_BYTES) { "too big" }
                                    out.write(buffer, 0, n)
                                }
                            }
                            part.renameTo(app.memory.picture)
                        } ?: false
                    }.getOrDefault(false)
                } finally {
                    part.delete()
                }
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

    // Said under the text, where it stays until the text changes: a toast is gone in a few
    // seconds, before it can be read on this panel.
    private fun say(text: Int) = desk.tell(text)

    companion object {
        const val ACTION_GET_LANGUAGE = "com.wanderwildwood.tsuyaku.GET_LANGUAGE"
        const val ACTION_OPEN_TEXT = "com.wanderwildwood.tsuyaku.OPEN_TEXT"
        const val EXTRA_LANGUAGE = "language"
        const val EXTRA_TOKEN = "token"
        private const val MAX_PICTURE_BYTES = 50L * 1024 * 1024

        /** Made fresh each time the app starts, and known only inside it. */
        val TOKEN: String = java.util.UUID.randomUUID().toString()
        const val EXTRA_FROM = "from"
        const val EXTRA_TO = "to"
    }
}

/**
 * The shared picture, when it is handed over as content: a file:// address from another app
 * could name a file of this app's own, which it would then read as if it had been shared.
 */
@Suppress("DEPRECATION")
private fun Intent.streamUri(): Uri? =
    (getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
