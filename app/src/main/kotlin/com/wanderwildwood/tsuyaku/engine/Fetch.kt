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

    suspend fun download(url: String, dest: File, size: Long, sha256: String?, gzipped: Boolean, onBytes: (Long) -> Unit) {
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
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
            if (code != HttpURLConnection.HTTP_OK) throw FetchFailed(Why.Server, "HTTP $code for $url")
            conn.inputStream.use { save(it, dest, size, sha256, gzipped, onBytes) }
        } finally {
            conn.disconnect()
        }
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
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        written += n
                        if (written > size) throw FetchFailed(Why.Damaged, "${dest.name} is longer than $size bytes")
                    }
                }
            } catch (e: FetchFailed) {
                throw e
            } catch (e: IOException) {
                // Out of room shows up here as an IOException too; the caller checked the room
                // before starting, so a write failing now is far more likely a dropped connection.
                throw FetchFailed(Why.Offline, e.toString())
            }
            if (written != size) throw FetchFailed(Why.Damaged, "${dest.name}: $written bytes, expected $size")
            if (sha256 != null) {
                val got = digest.digest().joinToString("") { "%02x".format(it) }
                if (!got.equals(sha256, ignoreCase = true)) throw FetchFailed(Why.Damaged, "${dest.name}: SHA-256 $got, expected $sha256")
            }
            if (!part.renameTo(dest)) throw FetchFailed(Why.Space, "could not move ${part.name} into place")
            done = true
        } finally {
            if (!done) part.delete()
        }
    }
}
