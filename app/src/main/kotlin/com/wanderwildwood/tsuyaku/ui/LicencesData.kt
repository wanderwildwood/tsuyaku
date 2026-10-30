package com.wanderwildwood.tsuyaku.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.tsuyaku.R

/** The address of the app's own source code. */
const val SOURCE_URL = "https://github.com/wanderwildwood/tsuyaku"

/** What the APK carries that is not this app's own code, and the licence each project declares. */
val LICENCE_COMPONENTS: List<LicenceComponent> = listOf(
    LicenceComponent("Offline Translator (the app this began as)", "David Ventura", "GPL-3.0-or-later", "https://github.com/DavidVentura/offline-translator"),
    LicenceComponent("Bergamot translator", "the Bergamot project and David Ventura's fork", "MPL-2.0", "https://github.com/DavidVentura/bergamot-translator"),
    LicenceComponent("Marian", "Marcin Junczys-Dowmunt and the University of Edinburgh", "MIT", "https://github.com/marian-nmt/marian-dev"),
    LicenceComponent("intgemm", "University of Edinburgh and contributors", "MIT", "https://github.com/kpu/intgemm"),
    LicenceComponent("ruy", "Google", "Apache-2.0", "https://github.com/google/ruy"),
    LicenceComponent("SentencePiece", "Google", "Apache-2.0", "https://github.com/google/sentencepiece"),
    LicenceComponent("ssplit-cpp (sentence splitting)", "University of Edinburgh", "Apache-2.0", "https://github.com/ugermann/ssplit-cpp"),
    LicenceComponent("yaml-cpp, spdlog, zstr, cnpy, mio, inside Marian", "their authors", "MIT", "https://github.com/marian-nmt/marian-dev"),
    LicenceComponent("CLI11 and pathie-cpp, inside Marian", "their authors", "BSD", "https://github.com/marian-nmt/marian-dev"),
    LicenceComponent("CLD2 (language guessing)", "Google, CLD2Owners", "Apache-2.0", "https://github.com/CLD2Owners/cld2"),
    LicenceComponent("Tesseract OCR", "the Tesseract project", "Apache-2.0", "https://github.com/tesseract-ocr/tesseract"),
    LicenceComponent("Tesseract4Android, with Leptonica, libjpeg-turbo and libpng inside", "Adaptech and the Leptonica, libjpeg-turbo and libpng authors", "Apache-2.0 and BSD", "https://github.com/adaptech-cz/Tesseract4Android"),
    LicenceComponent("Mudita Mindful Design (MMD)", "Mudita", "Apache-2.0", "https://github.com/mudita/MMD"),
    LicenceComponent("Lato typeface, inside MMD", "tyPoland Lukasz Dziedzic", "OFL-1.1", "https://www.latofonts.com"),
    LicenceComponent("Jetpack Compose, Activity, Lifecycle, Core, ExifInterface", "Google", "Apache-2.0", "https://developer.android.com/jetpack"),
    LicenceComponent("Kotlin standard library and coroutines", "JetBrains", "Apache-2.0", "https://kotlinlang.org"),
    LicenceComponent("Material Symbols icons", "Google", "Apache-2.0", "https://fonts.google.com/icons"),
)

/** Fetched on request, not in the APK. */
val LICENCE_DOWNLOADED: List<LicenceComponent> = listOf(
    LicenceComponent("Translation models (Firefox Translations)", "Mozilla", "MPL-2.0", "https://github.com/mozilla/translations"),
    LicenceComponent("Tesseract tessdata_fast language files", "the Tesseract project", "Apache-2.0", "https://github.com/tesseract-ocr/tessdata_fast"),
)

/** Each distinct licence text once: family id and the raw resource. */
val LICENCE_RAW: List<Pair<String, Int>> = listOf(
    "gpl" to R.raw.gpl_3_0,
    "apache" to R.raw.apache_2_0,
    "mpl" to R.raw.mpl_2_0,
    "mit" to R.raw.mit,
    "bsd" to R.raw.bsd_3_clause,
    "ofl" to R.raw.ofl_1_1,
)

@Composable
fun rememberLicencesData(): LicencesData = LicencesData(
    appName = "Translate",
    sourceUrl = SOURCE_URL,
    licenceLine = stringResource(R.string.licences_licence),
    components = LICENCE_COMPONENTS,
    texts = LICENCE_RAW.map { (id, raw) ->
        LicenceText(id, stringResource(when (id) {
            "gpl" -> R.string.licences_full_gpl
            "apache" -> R.string.licences_full_apache
            "mpl" -> R.string.licences_full_mpl
            "mit" -> R.string.licences_full_mit
            "bsd" -> R.string.licences_full_bsd
            "ofl" -> R.string.licences_full_ofl
            else -> R.string.licences_full_head
        }), raw)
    },
    extraGroups = listOf(ComponentGroup(stringResource(R.string.licences_models_head), stringResource(R.string.licences_models), LICENCE_DOWNLOADED)),
)

@Composable
fun rememberLicencesLabels(): LicencesLabels {
    val none = stringResource(R.string.about_no_browser_address, "@@")
    return LicencesLabels(
        title = stringResource(R.string.licences_title),
        back = stringResource(R.string.licences_back),
        sourceHeading = stringResource(R.string.licences_source_head),
        licenceHeading = stringResource(R.string.licences_licence_head),
        builtWithHeading = stringResource(R.string.licences_built_head),
        fullTextsHeading = stringResource(R.string.licences_full_head),
        noBrowser = { address -> none.replace("@@", address) },
    )
}
