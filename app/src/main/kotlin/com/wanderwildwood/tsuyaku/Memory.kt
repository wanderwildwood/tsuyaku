package com.wanderwildwood.tsuyaku

import android.content.Context
import com.wanderwildwood.tsuyaku.engine.ENGLISH
import com.wanderwildwood.tsuyaku.engine.Way
import java.io.File

/**
 * What the app remembers between one use and the next: the last way it translated, and the
 * text being written on its own screen.
 *
 * The text is written to a file as it is typed, not held for Android's saved state alone.
 * Android may close the app to free memory whenever it is out of sight, and on this phone it
 * often is: a paragraph typed, a look at a message to check a word, and the paragraph could
 * be gone. The file outlives that, and a restart of the phone besides.
 */
class Memory(context: Context) {
    private val prefs = context.getSharedPreferences("memory", Context.MODE_PRIVATE)
    private val draftFile = File(context.filesDir, "draft.txt")
    private val pictureFile = File(context.filesDir, "picture-pending")

    var way: Way?
        get() {
            val from = prefs.getString("from", null) ?: return null
            val to = prefs.getString("to", null) ?: return null
            return Way(from, to)
        }
        set(value) {
            prefs.edit().putString("from", value?.from ?: ENGLISH).putString("to", value?.to ?: ENGLISH).apply()
        }

    var draft: String
        get() = runCatching { draftFile.readText() }.getOrDefault("")
        set(value) {
            // Written beside and moved over, so a kill mid-write leaves the last whole draft.
            val tmp = File(draftFile.path + ".tmp")
            runCatching {
                tmp.writeText(value)
                tmp.renameTo(draftFile)
            }
        }

    /** True when the draft was read from a picture, until it is edited or replaced. */
    var draftFromPicture: Boolean
        get() = prefs.getBoolean("from_picture", false)
        set(value) = prefs.edit().putBoolean("from_picture", value).apply()

    /**
     * A shared picture waiting to be read. It is copied here the moment it arrives, because the
     * permission to read the sender's copy does not outlive this app being closed, and reading
     * one takes long enough for that to happen.
     */
    val picture: File get() = pictureFile
}
