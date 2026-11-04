package com.wanderwildwood.tsuyaku.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.tsuyaku.R

/** The address of the app's own source code. */
const val SOURCE_URL = "https://github.com/wanderwildwood/tsuyaku"

/** Fetched on request, not in the APK. */
val LICENCE_DOWNLOADED: List<LicenceComponent> = listOf(
    LicenceComponent("Translation models (Firefox Translations)", "Mozilla", "MPL-2.0", "https://github.com/mozilla/translations"),
    LicenceComponent("Tesseract tessdata_fast language files", "the Tesseract project", "Apache-2.0", "https://github.com/tesseract-ocr/tessdata_fast"),
)

/**
 * Each distinct licence family text once: family id and the raw resource. MIT and BSD have none:
 * every such library carries its own text with its copyright line (config/licenses).
 */
val LICENCE_RAW: List<Pair<String, Int>> = listOf(
    "gpl" to R.raw.gpl_3_0,
    "apache" to R.raw.apache_2_0,
    "mpl" to R.raw.mpl_2_0,
    "ofl" to R.raw.ofl_1_1,
)

@Composable
fun rememberLicencesData(): LicencesData = LicencesData(
    appName = "Translate",
    sourceUrl = SOURCE_URL,
    licenceLine = stringResource(R.string.licences_licence),
    // One row per artifact in the APK, listed by the build (config/libraries adds what Gradle cannot see).
    components = rememberGeneratedComponents(R.raw.aboutlibraries),
    texts = LICENCE_RAW.map { (id, raw) ->
        LicenceText(id, stringResource(when (id) {
            "gpl" -> R.string.licences_full_gpl
            "apache" -> R.string.licences_full_apache
            "mpl" -> R.string.licences_full_mpl
            "ofl" -> R.string.licences_full_ofl
            else -> R.string.licences_full_head
        }), raw)
    },
    extraGroups = listOf(ComponentGroup(stringResource(R.string.licences_models_head), stringResource(R.string.licences_models), LICENCE_DOWNLOADED)),
)

@Composable
fun rememberLicencesLabels(): LicencesLabels {
    val none = stringResource(R.string.about_no_browser_address, "@@")
    val noText = stringResource(R.string.licences_no_text, "@@")
    return LicencesLabels(
        title = stringResource(R.string.licences_title),
        back = stringResource(R.string.licences_back),
        sourceHeading = stringResource(R.string.licences_source_head),
        noText = { licence -> noText.replace("@@", licence) },
        noBrowser = { address -> none.replace("@@", address) },
    )
}
