package com.wanderwildwood.tsuyaku.engine

import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Where a language pack stands. */
sealed interface PackState {
    /** Not on the phone; [toFetch] bytes to download, fewer when part of it already came. */
    data class Absent(val toFetch: Long) : PackState

    data class Getting(val done: Long, val total: Long) : PackState

    /** A download that stopped, and why. Pressing the row tries again. */
    data class Stopped(val why: Why, val toFetch: Long) : PackState

    data class Here(val bytes: Long, val onCard: Boolean) : PackState
}

/**
 * The language packs: which are on the phone, fetching one, and taking one away.
 *
 * Nothing about a download is written down while it runs. Its progress lives in memory only,
 * so if the app is closed or killed part way there is no "downloading" left behind that
 * nothing will ever finish: the next look at the disk finds the files that came whole, and
 * the pack asks for only the rest.
 *
 * The network is used here and nowhere else in the app, and only when a pack is asked for.
 */
class Packs(val catalog: Catalog, private val storage: Storage, private val scope: CoroutineScope) {
    private val _states = MutableStateFlow<Map<String, PackState>>(emptyMap())
    val states: StateFlow<Map<String, PackState>> = _states.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

    /** Look at the disk again; the card may have come or gone. Downloads under way keep their state. */
    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val found = catalog.languages.associate { it.code to scan(it) }
            _states.update { now -> found.mapValues { (code, s) -> if (jobs.containsKey(code)) now[code] ?: s else s } }
        }
    }

    /** Language codes that can be translated now, English always among them. */
    fun installed(states: Map<String, PackState> = _states.value): Set<String> =
        states.filterValues { it is PackState.Here }.keys + ENGLISH

    /** As [installed], read from the disk now rather than from the last look at it. */
    suspend fun installedNow(): Set<String> =
        withContext(Dispatchers.IO) { catalog.languages.filter { scan(it) is PackState.Here }.map { it.code }.toSet() + ENGLISH }

    /** The folder holding [code]'s models, or null when they are not all there. */
    fun modelDir(code: String): File? {
        val lang = catalog.lang(code) ?: return null
        val files = lang.directions.flatMap { it.files }
        return Layout.whereComplete(storage.roots(), code, files)?.let { Layout.dir(it, code) }
    }

    /** Tesseract names of the languages whose picture data is whole on the phone. */
    fun tessReady(): Set<String> {
        val names = catalog.languages.mapNotNull { l -> l.tess?.let { it to l.tessSize } } + ("eng" to catalog.englishTessSize)
        return names.filter { (name, size) -> tessFile(name).length() == size }.map { it.first }.toSet()
    }

    fun tessRoot(): File = storage.tessRoot

    private fun tessFile(name: String) = File(storage.tessData, "$name.traineddata")

    private fun scan(lang: Lang): PackState {
        val files = lang.directions.flatMap { it.files }
        val roots = storage.roots()
        val root = Layout.whereComplete(roots, lang.code, files)
        val tessOk = lang.tess == null || tessFile(lang.tess).length() == lang.tessSize
        val engOk = tessFile("eng").length() == catalog.englishTessSize
        return if (root != null && tessOk && engOk) {
            PackState.Here(files.sumOf { it.size } + (if (lang.tess != null) lang.tessSize else 0), storage.isOnCard(root))
        } else {
            PackState.Absent(toFetch(lang))
        }
    }

    private fun toFetch(lang: Lang): Long {
        val files = lang.directions.flatMap { it.files }
        val root = Layout.resumeIn(storage.roots(), storage.home(), lang.code, files)
        var bytes = Layout.missing(root, lang.code, files).sumOf { it.gz }
        if (lang.tess != null && tessFile(lang.tess).length() != lang.tessSize) bytes += lang.tessSize
        if (tessFile("eng").length() != catalog.englishTessSize) bytes += catalog.englishTessSize
        return bytes
    }

    fun download(code: String) {
        val lang = catalog.lang(code) ?: return
        if (jobs.containsKey(code)) return
        val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val files = lang.directions.flatMap { it.files }
            val root = Layout.resumeIn(storage.roots(), storage.home(), code, files)
            val missing = Layout.missing(root, code, files)
            val tess = buildList {
                if (lang.tess != null && tessFile(lang.tess).length() != lang.tessSize) add(lang.tess to lang.tessSize)
                if (tessFile("eng").length() != catalog.englishTessSize) add("eng" to catalog.englishTessSize)
            }
            val total = missing.sumOf { it.gz } + tess.sumOf { it.second }
            val room = missing.sumOf { it.size } + ROOM_TO_SPARE
            try {
                Layout.dir(root, code).mkdirs()
                storage.tessData.mkdirs()
                if (StatFs(Layout.dir(root, code).path).availableBytes < room) throw FetchFailed(Why.Space, "needs $room bytes")
                var before = 0L
                var shown = 0L
                set(code, PackState.Getting(0, total))
                val progress = { n: Long ->
                    // The panel redraws in full for every change, so the count moves every
                    // couple of seconds rather than with every packet.
                    val now = SystemClock.elapsedRealtime()
                    if (now - shown > SHOW_EVERY_MS) {
                        shown = now
                        set(code, PackState.Getting(before + n, total))
                    }
                }
                for (f in missing) {
                    Fetch.download("${catalog.modelBase}/${f.path}", Layout.file(root, code, f), f.size, f.sha256, gzipped = true, onBytes = progress)
                    before += f.gz
                }
                for ((name, size) in tess) {
                    Fetch.download("${catalog.tessBase}/$name.traineddata", tessFile(name), size, null, gzipped = false, onBytes = progress)
                    before += size
                }
                set(code, scan(lang))
                // English's picture file came with the first pack, and the others' sizes drop by it.
                refresh()
            } catch (e: CancellationException) {
                set(code, scan(lang))
                throw e
            } catch (e: FetchFailed) {
                Log.w(TAG, "Pack $code stopped: ${e.why}: ${e.message}")
                set(code, PackState.Stopped(e.why, toFetch(lang)))
            } catch (e: Exception) {
                Log.w(TAG, "Pack $code stopped", e)
                set(code, PackState.Stopped(Why.Offline, toFetch(lang)))
            }
        }
        jobs[code] = job
        job.invokeOnCompletion { jobs.remove(code, job) }
        job.start()
    }

    /** Stop a download. What came whole stays, so starting again picks up after it. */
    fun stop(code: String) {
        jobs[code]?.cancel()
    }

    /**
     * Take a language off the phone, from wherever it is. Its picture data goes too unless
     * another language still on the phone reads with it (Norwegian's three share one file),
     * and English's goes with the last pack.
     */
    fun delete(code: String) {
        val lang = catalog.lang(code) ?: return
        scope.launch(Dispatchers.IO) {
            jobs[code]?.let { it.cancel(); it.join() }
            for (root in storage.roots()) Layout.dir(root, code).deleteRecursively()
            val others = catalog.languages.filter { it.code != code && scan(it) is PackState.Here }
            if (lang.tess != null && others.none { it.tess == lang.tess }) tessFile(lang.tess).delete()
            if (others.isEmpty()) tessFile("eng").delete()
            refresh()
        }
    }

    private fun set(code: String, state: PackState) {
        _states.update { it + (code to state) }
    }

    private companion object {
        const val TAG = "Packs"
        const val SHOW_EVERY_MS = 2_000L
        const val ROOM_TO_SPARE = 20L * 1024 * 1024
    }
}
