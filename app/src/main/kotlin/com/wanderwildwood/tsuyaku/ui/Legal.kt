package com.wanderwildwood.tsuyaku.ui

import android.content.Context
import androidx.annotation.RawRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tsuyaku.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The three pages About leads to, each a full screen of its own. */
enum class LegalPage { Privacy, Terms, Licences }

/** One piece of other people's work the app carries or fetches, and the licence it comes under. */
data class Library(
    val name: String,
    val by: String,
    val licence: String,
    val link: String,
)

/** The address of the app's own source code. */
const val SOURCE_URL = "https://github.com/wanderwildwood/tsuyaku"

/**
 * What is inside the APK (the engine, the picture reader, the language guesser, the UI
 * libraries) and, last, what is fetched on request. Licences are those each project declares.
 */
val LIBRARIES: List<Library> =
    listOf(
        Library("Offline Translator (the app this began as)", "David Ventura", "GPL-3.0-or-later", "https://github.com/DavidVentura/offline-translator"),
        Library("Bergamot translator", "the Bergamot project and David Ventura's fork", "MPL-2.0", "https://github.com/DavidVentura/bergamot-translator"),
        Library("Marian", "Marcin Junczys-Dowmunt and the University of Edinburgh", "MIT", "https://github.com/marian-nmt/marian-dev"),
        Library("intgemm", "University of Edinburgh and contributors", "MIT", "https://github.com/kpu/intgemm"),
        Library("ruy", "Google", "Apache-2.0", "https://github.com/google/ruy"),
        Library("SentencePiece", "Google", "Apache-2.0", "https://github.com/google/sentencepiece"),
        Library("ssplit-cpp (sentence splitting)", "University of Edinburgh", "Apache-2.0", "https://github.com/ugermann/ssplit-cpp"),
        Library("yaml-cpp, spdlog, zstr, cnpy, mio, inside Marian", "their authors", "MIT", "https://github.com/marian-nmt/marian-dev"),
        Library("CLI11 and pathie-cpp, inside Marian", "their authors", "BSD", "https://github.com/marian-nmt/marian-dev"),
        Library("CLD2 (language guessing)", "Google, CLD2Owners", "Apache-2.0", "https://github.com/CLD2Owners/cld2"),
        Library("Tesseract OCR", "the Tesseract project", "Apache-2.0", "https://github.com/tesseract-ocr/tesseract"),
        Library("Tesseract4Android, with Leptonica, libjpeg-turbo and libpng inside", "Adaptech and the Leptonica, libjpeg-turbo and libpng authors", "Apache-2.0 and BSD", "https://github.com/adaptech-cz/Tesseract4Android"),
        Library("Mudita Mindful Design (MMD)", "Mudita", "Apache-2.0", "https://github.com/mudita/MMD"),
        Library("Lato typeface, inside MMD", "tyPoland Lukasz Dziedzic", "OFL-1.1", "https://www.latofonts.com"),
        Library("Jetpack Compose, Activity, Lifecycle, Core, ExifInterface", "Google", "Apache-2.0", "https://developer.android.com/jetpack"),
        Library("Kotlin standard library and coroutines", "JetBrains", "Apache-2.0", "https://kotlinlang.org"),
        Library("Material Symbols icons", "Google", "Apache-2.0", "https://fonts.google.com/icons"),
    )

/** The downloaded files, listed apart because they are not in the APK. */
val DOWNLOADED: List<Library> =
    listOf(
        Library("Translation models (Firefox Translations)", "Mozilla", "MPL-2.0", "https://github.com/mozilla/translations"),
        Library("Tesseract tessdata_fast language files", "the Tesseract project", "Apache-2.0", "https://github.com/tesseract-ocr/tessdata_fast"),
    )

/** The licence names the app prints a full text for; every name in the lists above is one of these or a combination. */
val LICENCE_TEXTS = setOf("GPL-3.0-or-later", "MPL-2.0", "MIT", "Apache-2.0", "BSD", "OFL-1.1")

/** A licence text as the paragraphs a reader turns pages by: blank lines split rows, wrapped lines are joined. */
fun licenceParagraphs(text: String): List<String> =
    text
        .replace("\r\n", "\n")
        .split(Regex("\n[ \t]*\n"))
        .map { block -> block.lines().joinToString(" ") { it.trim() }.trim() }
        .filter { it.isNotEmpty() }

@Composable
fun LegalScreen(page: LegalPage, onBack: () -> Unit) {
    val title =
        stringResource(
            when (page) {
                LegalPage.Privacy -> R.string.about_row_privacy
                LegalPage.Terms -> R.string.about_row_terms
                LegalPage.Licences -> R.string.about_row_licences
            },
        )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { Bar(title, onBack) {} },
    ) { padding ->
        when (page) {
            LegalPage.Privacy -> TextPage(stringResource(R.string.about_privacy), Modifier.padding(padding))
            LegalPage.Terms -> TextPage(stringResource(R.string.about_terms), Modifier.padding(padding))
            LegalPage.Licences -> LicencesPage(Modifier.padding(padding))
        }
    }
}

@Composable
private fun TextPage(text: String, modifier: Modifier) {
    PagedColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        text.split("\n\n").forEach { item { Paragraph(it) } }
    }
}

@Composable
private fun LicencesPage(modifier: Modifier) {
    val context = LocalContext.current
    val gpl by produceTexts(context, R.raw.gpl_3_0)
    val mpl by produceTexts(context, R.raw.mpl_2_0)
    val apache by produceTexts(context, R.raw.apache_2_0)
    val mit by produceTexts(context, R.raw.mit)
    val bsd by produceTexts(context, R.raw.bsd_3_clause)
    val ofl by produceTexts(context, R.raw.ofl_1_1)
    PagedColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Heading(stringResource(R.string.licences_source_heading)) }
        item { Link(stringResource(R.string.licences_source, SOURCE_URL.removePrefix("https://")), SOURCE_URL) }
        item { Heading(stringResource(R.string.licences_licence_heading)) }
        item { Paragraph(stringResource(R.string.licences_licence)) }
        item { Heading(stringResource(R.string.licences_built_heading)) }
        LIBRARIES.forEach { item { Link("${it.name}, ${it.by}: ${it.licence}", it.link) } }
        item { Heading(stringResource(R.string.licences_models_heading)) }
        item { Paragraph(stringResource(R.string.licences_models)) }
        DOWNLOADED.forEach { item { Link("${it.name}, ${it.by}: ${it.licence}", it.link) } }
        item { Heading(stringResource(R.string.licences_gpl)) }
        gpl.forEach { item { Paragraph(it) } }
        item { Heading(stringResource(R.string.licences_mpl)) }
        mpl.forEach { item { Paragraph(it) } }
        item { Heading(stringResource(R.string.licences_apache)) }
        apache.forEach { item { Paragraph(it) } }
        item { Heading(stringResource(R.string.licences_mit)) }
        mit.forEach { item { Paragraph(it) } }
        item { Heading(stringResource(R.string.licences_bsd)) }
        bsd.forEach { item { Paragraph(it) } }
        item { Heading(stringResource(R.string.licences_ofl)) }
        ofl.forEach { item { Paragraph(it) } }
    }
}

@Composable
private fun produceTexts(context: Context, @RawRes resource: Int): State<List<String>> =
    produceState(initialValue = emptyList<String>(), resource) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    licenceParagraphs(context.resources.openRawResource(resource).bufferedReader().use { it.readText() })
                }.getOrDefault(emptyList())
            }
    }

@Composable
private fun Heading(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Paragraph(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.bodySmall)
}

/** A line that opens an address in a real browser; a phone with none says so. */
@Composable
private fun Link(text: String, address: String) {
    val context = LocalContext.current
    val none = stringResource(R.string.about_no_browser_address, address.removePrefix("https://"))
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        textDecoration = TextDecoration.Underline,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { openLink(context, address, none) }
                .wrapContentHeight(Alignment.CenterVertically),
    )
}
