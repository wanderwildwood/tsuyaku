package com.wanderwildwood.tsuyaku.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tsuyaku.R

/** The three pages About leads to, each a full screen of its own. */
enum class LegalPage { Privacy, Terms, Licences }

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
            LegalPage.Licences ->
                LicencesScreen(
                    data = rememberLicencesData(),
                    labels = rememberLicencesLabels(),
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 8.dp),
                    showBackButton = false,
                    showTitle = false,
                )
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
private fun Paragraph(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.bodySmall)
}
