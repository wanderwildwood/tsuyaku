package com.wanderwildwood.tsuyaku.engine

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Where language packs live.
 *
 * The models are the bulk of a pack, 30 to 80 MB a language, so they go on the SD card when
 * there is one: the app's own folder there needs no permission and goes when the app does.
 * Without a card, or once it is taken out, they live in the phone's own storage, in a folder
 * Android leaves out of backups, since anything in it can be fetched again.
 *
 * Tesseract's files for reading pictures stay on the phone whatever happens. They are a few
 * megabytes each, and Tesseract wants every language it reads in one folder.
 */
class Storage(private val context: Context) {
    val internal: File get() = File(context.noBackupFilesDir, "packs")

    val tessRoot: File get() = File(context.noBackupFilesDir, "tesseract")
    val tessData: File get() = File(tessRoot, "tessdata")

    /** The app's folder on a removable card that is in and readable, if there is one. */
    fun sdCard(): File? =
        context.getExternalFilesDirs(null).drop(1).firstOrNull { dir ->
            dir != null &&
                runCatching {
                    Environment.isExternalStorageRemovable(dir) &&
                        Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
                }.getOrDefault(false)
        }?.let { File(it, "packs") }

    /** Every place a pack may already be, the card first. */
    fun roots(): List<File> = listOfNotNull(sdCard(), internal)

    /** Where a new pack goes. */
    fun home(): File = sdCard() ?: internal

    fun isOnCard(root: File): Boolean = root != internal
}

/** How a pack's files are laid out under a root: one folder per language. */
object Layout {
    fun dir(root: File, code: String) = File(root, code)

    fun file(root: File, code: String, f: ModelFile) = File(dir(root, code), f.name)

    /** A file counts only whole: one cut short by a lost connection is not there. */
    fun isWhole(root: File, code: String, f: ModelFile): Boolean = file(root, code, f).length() == f.size

    /** The first root holding every one of [files] whole, or null. */
    fun whereComplete(roots: List<File>, code: String, files: List<ModelFile>): File? =
        roots.firstOrNull { root -> files.all { isWhole(root, code, it) } }

    /**
     * Where to carry on fetching a pack: the root already holding part of it, so a download
     * cut off and started again does not leave half a pack in each place; else [home].
     */
    fun resumeIn(roots: List<File>, home: File, code: String, files: List<ModelFile>): File =
        roots.firstOrNull { root -> files.any { isWhole(root, code, it) } } ?: home

    fun missing(root: File, code: String, files: List<ModelFile>): List<ModelFile> =
        files.filterNot { isWhole(root, code, it) }
}
