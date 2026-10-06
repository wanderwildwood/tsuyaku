package com.wanderwildwood.tsuyaku.engine

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** English: every model goes to it or from it, so it is never a pack of its own. */
const val ENGLISH = "en"

/** One file of a model, as Mozilla serves it (gzipped) and as it sits on the phone (not). */
data class ModelFile(
    val role: String,
    val path: String,
    /** Bytes to download. */
    val gz: Long,
    /** Bytes once unpacked: what it takes on the phone, and what a whole file measures. */
    val size: Long,
    /** Mozilla publishes this for the model itself, not for its vocabulary or shortlist. */
    val sha256: String?,
) {
    val name: String get() = path.substringAfterLast('/').removeSuffix(".gz")
}

/** A model one way: a language to English, or English to it. */
data class Direction(val arch: String, val released: Boolean, val files: List<ModelFile>) {
    private fun role(vararg r: String) = files.first { it.role in r }
    val model get() = role("model")
    val srcVocab get() = role("srcVocab", "vocab")
    val trgVocab get() = role("trgVocab", "vocab")
    val lex get() = role("lex")
}

/**
 * A language the app can fetch: its two models, and Tesseract's data for reading it in a
 * picture where Tesseract has any. Either model may be missing; Norwegian Nynorsk, for one,
 * has only a model into English so far.
 */
data class Lang(
    val code: String,
    val toEnglish: Direction?,
    val fromEnglish: Direction?,
    val tess: String?,
    val tessSize: Long,
) {
    val directions: List<Direction> get() = listOfNotNull(toEnglish, fromEnglish)

    /** Mozilla has not released every model it serves; such a model is offered, and called a trial. */
    val trial: Boolean get() = directions.any { !it.released }
}

/** What there is to download, from the list written by data/build-catalog.py. */
class Catalog(
    val modelBase: String,
    val tessBase: String,
    val englishTessSize: Long,
    val languages: List<Lang>,
) {
    private val byCode = languages.associateBy { it.code }

    fun lang(code: String): Lang? = byCode[code]

    companion object {
        fun load(context: Context): Catalog =
            parse(context.assets.open("catalog.json").bufferedReader().use { it.readText() })

        fun parse(json: String): Catalog {
            val root = JSONObject(json)
            val langs = root.getJSONArray("languages")
            return Catalog(
                modelBase = root.getString("modelBase"),
                tessBase = root.getString("tessBase"),
                englishTessSize = root.getJSONObject("english").getLong("tessSize"),
                languages = (0 until langs.length()).map { i ->
                    val l = langs.getJSONObject(i)
                    Lang(
                        code = l.getString("code"),
                        toEnglish = direction(l.optJSONObject("toEnglish")),
                        fromEnglish = direction(l.optJSONObject("fromEnglish")),
                        tess = if (l.has("tess")) l.getString("tess") else null,
                        tessSize = l.optLong("tessSize", 0),
                    )
                },
            )
        }

        private fun direction(d: JSONObject?): Direction? {
            if (d == null) return null
            val files = d.getJSONArray("files")
            return Direction(
                arch = d.getString("arch"),
                released = d.getBoolean("released"),
                files = (0 until files.length()).map { i ->
                    val f = files.getJSONObject(i)
                    ModelFile(
                        role = f.getString("role"),
                        path = f.getString("path"),
                        gz = f.getLong("gz"),
                        size = f.getLong("size"),
                        sha256 = if (f.has("sha256")) f.getString("sha256") else null,
                    )
                },
            )
        }
    }
}

/**
 * A language's name in the phone's own language, from the platform rather than a list of
 * our own, so it follows whatever language the phone is set to.
 */
fun languageName(code: String, inLocale: Locale = Locale.getDefault()): String {
    val tag = when (code) {
        "zh" -> "zh-Hans"
        "zh_hant" -> "zh-Hant"
        else -> code.replace('_', '-')
    }
    val name = Locale.forLanguageTag(tag).getDisplayName(inLocale)
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(inLocale) else it.toString() }
}
