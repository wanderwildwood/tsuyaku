package com.wanderwildwood.tsuyaku.engine

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Why a download stopped, in terms of what can be done about it. */
enum class Why {
    /** No connection, or it dropped. Trying again later may work. */
    Offline,

    /** The server answered, but not with the file. */
    Server,

    /** Not enough room where the pack was going. */
    Space,

    /** A file arrived, but not as the list describes it. Nothing of it was kept. */
    Damaged,
}

class FetchFailed(val why: Why, message: String) : IOException(message)

/**
 * Downloading one file, written beside its final name and moved there only once it has been
 * checked: its full length, and its SHA-256 where Mozilla gives one. A file that fails either
 * check, or is cut off part way, is deleted, so a file under its final name is always whole.
 */
object Fetch {
    private const val TIMEOUT_MS = 30_000

    /**
     * The only hosts a pack is fetched from: Mozilla's translation models and Tesseract's
     * reading data, as catalog.json names them. Anything else is refused before a connection,
     * and a redirect anywhere else is refused before the file is read.
     */
    private val HOSTS = setOf("storage.googleapis.com", "raw.githubusercontent.com")

    suspend fun download(url: String, dest: File, size: Long, sha256: String?, gzipped: Boolean, onBytes: (Long) -> Unit) {
        val parsed = try { URL(url) } catch (e: IOException) { throw FetchFailed(Why.Server, e.toString()) }
        if (parsed.protocol != "https" || parsed.host !in HOSTS) throw FetchFailed(Why.Server, "not a pack address: $url")
        val conn = try {
            (parsed.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                // A plain name, not Android's default, which carries the phone's model and build.
                setRequestProperty("User-Agent", "Translate")
            }
        } catch (e: IOException) {
            throw FetchFailed(Why.Offline, e.toString())
        }
        try {
            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                throw FetchFailed(Why.Offline, e.toString())
            }
            // Redirects are followed, but only to the same hosts over https: the check above is
            // made again on wherever the file really came from.
            val landed = conn.url
            if (landed.protocol != "https" || landed.host !in HOSTS) throw FetchFailed(Why.Server, "sent on to another address for $url")
            if (code != HttpURLConnection.HTTP_OK) throw FetchFailed(Why.Server, "HTTP $code for $url")
            conn.inputStream.use { save(it, dest, size, sha256, gzipped, onBytes) }
        } finally {
            conn.disconnect()
        }
    }

    /** Under this much room left, a write that fails is put down to the room. */
    private const val LOW_ROOM = 1024L * 1024

    /**
     * Whether a failed write ran out of room: the room was checked before the download began,
     * but another app may fill the storage meanwhile. Android says ENOSPC in the message; the
     * room left in [dir] is the fallback.
     */
    internal fun outOfRoom(e: IOException, dir: File?): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            val message = cause.message.orEmpty()
            if ("ENOSPC" in message || "No space left" in message) return true
            cause = cause.cause
        }
        return dir != null && dir.exists() && dir.usableSpace < LOW_ROOM
    }

    /** Where the hash a file was checked against is noted, beside the file. */
    private fun mark(file: File) = File(file.path + ".sha256")

    /**
     * Whether [file] matches [sha256]: at once if it was checked against that hash before, else by
     * hashing it now and noting the match. A file that does not match is deleted, with its note.
     * No hash to check against means nothing to check.
     */
    fun checked(file: File, sha256: String?): Boolean {
        if (sha256 == null) return file.exists()
        val want = sha256.lowercase()
        val note = mark(file)
        if (runCatching { note.readText() }.getOrNull() == want) return true
        val got = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
        if (got == want) {
            runCatching { note.writeText(want) }
            return true
        }
        file.delete()
        note.delete()
        return false
    }

    /** The checking half of [download], on any stream; [onBytes] counts bytes as they came. */
    suspend fun save(raw: InputStream, dest: File, size: Long, sha256: String?, gzipped: Boolean, onBytes: (Long) -> Unit) {
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        var done = false
        try {
            var counted = 0L
            val counting = object : FilterInputStream(raw) {
                override fun read(): Int =
                    super.read().also { if (it >= 0) { counted++; onBytes(counted) } }

                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, len).also { if (it > 0) { counted += it; onBytes(counted) } }
            }
            val input = if (gzipped) GZIPInputStream(counting, 64 * 1024) else counting
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            val buffer = ByteArray(64 * 1024)
            try {
                part.outputStream().use { out ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        try {
                            out.write(buffer, 0, n)
                        } catch (e: IOException) {
                            throw FetchFailed(if (outOfRoom(e, part.parentFile)) Why.Space else Why.Offline, e.toString())
                        }
                        digest.update(buffer, 0, n)
                        written += n
                        if (written > size) throw FetchFailed(Why.Damaged, "${dest.name} is longer than $size bytes")
                    }
                }
            } catch (e: FetchFailed) {
                throw e
            } catch (e: IOException) {
                // A write that runs out of room is told apart above; what is left is the
                // connection dropping or the data arriving broken.
                throw FetchFailed(Why.Offline, e.toString())
            }
            if (written != size) throw FetchFailed(Why.Damaged, "${dest.name}: $written bytes, expected $size")
            if (sha256 != null) {
                val got = digest.digest().joinToString("") { "%02x".format(it) }
                if (!got.equals(sha256, ignoreCase = true)) throw FetchFailed(Why.Damaged, "${dest.name}: SHA-256 $got, expected $sha256")
            }
            if (!part.renameTo(dest)) throw FetchFailed(Why.Space, "could not move ${part.name} into place")
            if (sha256 != null) runCatching { mark(dest).writeText(sha256.lowercase()) }
            done = true
        } finally {
            if (!done) part.delete()
        }
    }
}
