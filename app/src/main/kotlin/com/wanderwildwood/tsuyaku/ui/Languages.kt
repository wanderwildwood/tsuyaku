package com.wanderwildwood.tsuyaku.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tsuyaku.R
import com.wanderwildwood.tsuyaku.engine.Catalog
import com.wanderwildwood.tsuyaku.engine.Lang
import com.wanderwildwood.tsuyaku.engine.PackState
import com.wanderwildwood.tsuyaku.engine.Why
import com.wanderwildwood.tsuyaku.engine.languageName
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Choosing a language to translate from or into, among those on the phone. The one in use is
 * drawn with a border; the last row leads to the rest.
 */
@Composable
fun LanguagePicker(
    title: String,
    codes: Collection<String>,
    current: String,
    onPick: (String) -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(codes) { codes.sortedBy { languageName(it) } }
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        val row: @Composable (String) -> Unit = { code ->
            val chosen = code == current
            TextMMD(
                text = languageName(code),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .then(if (chosen) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), RoundedCornerShape(8.dp)) else Modifier)
                    .clickable { onPick(code) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        // A few fit as they are; MMD's list takes all the height it is given, so it is kept for many.
        if (sorted.size <= FEW) {
            Column { for (code in sorted) row(code) }
        } else {
            val most = minOf(360.dp, LocalConfiguration.current.screenHeightDp.dp * 0.5f)
            LazyColumnMMD(modifier = Modifier.height(most)) {
                for (code in sorted) item(key = code) { row(code) }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButtonMMD(onClick = onMore, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            TextMMD(text = stringResource(R.string.more_languages), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Every language there is to fetch: those on the phone first, then the rest by name. A row
 * says what it costs before anything is pressed, and what it is doing while it does it.
 * Pressing fetches; pressing again stops; pressing one on the phone asks, then deletes it.
 */
@Composable
fun LanguagesScreen(
    catalog: Catalog,
    states: Map<String, PackState>,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    onAbout: () -> Unit,
) {
    val byName = remember(catalog) { catalog.languages.sortedBy { languageName(it.code) } }
    val here = byName.filter { states[it.code] is PackState.Here }
    val rest = byName.filter { states[it.code] !is PackState.Here }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(stringResource(R.string.languages), onBack) {
                BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout)
            }
        },
    ) { padding ->
        LazyColumnMMD(modifier = Modifier.padding(padding).fillMaxSize()) {
            item(key = "note") {
                TextMMD(
                    text = stringResource(R.string.languages_note),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            if (here.isNotEmpty()) {
                item(key = "here") { Heading(stringResource(R.string.section_here)) }
                for (lang in here) item(key = "h:${lang.code}") { PackRow(lang, states[lang.code], onDownload, onStop, onDelete) }
            }
            if (rest.isNotEmpty()) {
                item(key = "get") { Heading(stringResource(R.string.section_get)) }
                for (lang in rest) item(key = "g:${lang.code}") { PackRow(lang, states[lang.code], onDownload, onStop, onDelete) }
            }
            item(key = "foot") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Heading(text: String) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PackRow(
    lang: Lang,
    state: PackState?,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val name = languageName(lang.code)
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4_000)
            armed = false
        }
    }
    val line = when (state) {
        is PackState.Here -> stringResource(if (state.onCard) R.string.pack_here_card else R.string.pack_here, megabytes(state.bytes))
        is PackState.Getting -> stringResource(R.string.pack_getting, megabytes(state.done, unit = false), megabytes(state.total))
        is PackState.Stopped -> when (state.why) {
            Why.Offline -> stringResource(R.string.pack_stopped_offline)
            Why.Server -> stringResource(R.string.pack_stopped_server)
            Why.Space -> stringResource(R.string.pack_stopped_space, megabytes(lang.directions.sumOf { d -> d.files.sumOf { it.size } }))
            Why.Damaged -> stringResource(R.string.pack_stopped_damaged)
        }
        is PackState.Absent -> stringResource(R.string.pack_absent, megabytes(state.toFetch))
        null -> ""
    }
    val notes = buildList {
        if (lang.toEnglish == null) add(stringResource(R.string.pack_one_way_from_english))
        if (lang.fromEnglish == null) add(stringResource(R.string.pack_one_way_to_english))
        if (lang.trial) add(stringResource(R.string.pack_trial))
        if (lang.tess == null) add(stringResource(R.string.pack_no_pictures))
    }
    val joiner = stringResource(R.string.joiner)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable {
                when (state) {
                    is PackState.Here -> if (armed) { armed = false; onDelete(lang.code) } else armed = true
                    is PackState.Getting -> onStop(lang.code)
                    is PackState.Absent, is PackState.Stopped -> onDownload(lang.code)
                    null -> Unit
                }
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        TextMMD(
            text = if (armed) stringResource(R.string.pack_delete_armed, name) else name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal,
        )
        TextMMD(text = (listOf(line) + notes).filter { it.isNotEmpty() }.joinToString(joiner), style = MaterialTheme.typography.labelSmall)
    }
}

/** A size in megabytes as the panel shows it: whole numbers, one decimal under ten. */
@Composable
private fun megabytes(bytes: Long, unit: Boolean = true): String {
    val mb = bytes / 1_000_000.0
    val number = if (mb < 10) String.format(Locale.getDefault(), "%.1f", mb) else String.format(Locale.getDefault(), "%.0f", mb)
    return if (unit) stringResource(R.string.megabytes, number) else number
}

private const val FEW = 5
