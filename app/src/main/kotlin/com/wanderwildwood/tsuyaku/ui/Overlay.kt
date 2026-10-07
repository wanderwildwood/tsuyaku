package com.wanderwildwood.tsuyaku.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tsuyaku.R
import com.wanderwildwood.tsuyaku.engine.Catalog
import com.wanderwildwood.tsuyaku.engine.ENGLISH
import com.wanderwildwood.tsuyaku.engine.Outcome
import com.wanderwildwood.tsuyaku.engine.Pieces
import com.wanderwildwood.tsuyaku.engine.Way
import com.wanderwildwood.tsuyaku.engine.languageName
import kotlinx.coroutines.delay

/**
 * The translation of text selected in another app, in a panel over it: the way at the top,
 * each language pressable to change it, then the translation, then what to do with it.
 * "Replace with translation" is there only when the text came from a field that can take it.
 */
@Composable
fun Overlay(
    text: String,
    way: Way?,
    outcome: Outcome?,
    working: Boolean,
    installed: Set<String>,
    catalog: Catalog,
    writable: Boolean,
    onWay: (Way) -> Unit,
    onReplace: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onOpen: () -> Unit,
    onGet: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = from, false = into
    EInkDialog(onDismiss = onDismiss) {
        if (way != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                WayName(languageName(way.from), Modifier.weight(1f, fill = false)) { picking = true }
                TextMMD(text = "→", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp))
                WayName(languageName(way.to), Modifier.weight(1f, fill = false)) { picking = false }
            }
        }
        Spacer(Modifier.height(8.dp))

        val done = outcome as? Outcome.Done
        when {
            working || way == null -> Line(stringResource(R.string.translating))
            done != null -> SelectionContainer(Modifier.textActions()) {
                Paged(done.text.split('\n'), done.romanised)
            }
            outcome is Outcome.Missing -> {
                val name = languageName(outcome.code)
                Line(stringResource(R.string.missing, name))
                Spacer(Modifier.height(12.dp))
                Wide(stringResource(R.string.get, name)) { onGet(outcome.code) }
            }
            outcome is Outcome.NoModel -> Line(
                stringResource(
                    if (way.to == outcome.code) R.string.no_model_from_english else R.string.no_model_to_english,
                    languageName(outcome.code),
                ),
            )
            outcome is Outcome.Failed -> Line(stringResource(R.string.failed, outcome.message))
        }

        if (done != null && !working) {
            Spacer(Modifier.height(16.dp))
            if (writable && done.text.isNotBlank() && done.text != text) {
                Wide(stringResource(R.string.replace)) { onReplace(done.text) }
                Spacer(Modifier.height(8.dp))
            }
            var copied by remember(done) { mutableStateOf(false) }
            LaunchedEffect(copied) {
                if (copied) {
                    delay(2_000)
                    copied = false
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Small(stringResource(if (copied) R.string.copied else R.string.copy), Modifier.weight(1f)) {
                    onCopy(done.text)
                    copied = true
                }
                Small(stringResource(R.string.share), Modifier.weight(1f)) { onShare(done.text) }
                Small(stringResource(R.string.open), Modifier.weight(1f), onOpen)
            }
        }
    }

    picking?.let { from ->
        val current = way ?: return@let
        LanguagePicker(
            title = stringResource(if (from) R.string.pick_from else R.string.pick_to),
            codes = installed.filter { code ->
                code == ENGLISH || catalog.lang(code)?.let { if (from) it.toEnglish != null else it.fromEnglish != null } == true
            },
            current = if (from) current.from else current.to,
            onPick = { code ->
                picking = null
                onWay(if (from) Way(code, current.to) else Way(current.from, code))
            },
            onMore = { picking = null; onGet(null) },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun WayName(name: String, modifier: Modifier, onClick: () -> Unit) {
    TextMMD(
        text = name,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}

@Composable
private fun Line(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Wide(label: String, onClick: () -> Unit) {
    OutlinedButtonMMD(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        TextMMD(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * The translation, as tall as it is and no taller, up to a share of the panel; past that it pages
 * in a list. A list of MMD's fills all the height it is given, so a short translation in one would
 * float in an empty box and, held sideways, push the buttons below off the screen.
 */
@Composable
private fun Paged(paragraphs: List<String>, romanised: String?) {
    val measurer = rememberTextMeasurer()
    val body = MaterialTheme.typography.bodyLarge
    val small = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val screen = LocalConfiguration.current.screenHeightDp.dp
    val most = minOf(300.dp, screen * 0.4f)
    BoxWithConstraints {
        val width = constraints.maxWidth
        val needed = remember(paragraphs, romanised, width) {
            val text = paragraphs.sumOf { measurer.measure(it, body, constraints = Constraints(maxWidth = width)).size.height }
            val extra = romanised?.let { measurer.measure(it, small, constraints = Constraints(maxWidth = width)).size.height + with(density) { 10.dp.roundToPx() } } ?: 0
            with(density) { (text + extra).toDp() }
        }
        if (needed <= most) {
            Column {
                for (p in paragraphs) TextMMD(text = p, style = body, modifier = Modifier.fillMaxWidth())
                if (romanised != null) TextMMD(text = romanised, style = small, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        } else {
            LazyColumnMMD(modifier = Modifier.height(most), scrollStep = 1) {
                Pieces.of(paragraphs.joinToString("\n")).forEachIndexed { i, piece ->
                    item(key = "p$i") {
                        TextMMD(
                            text = piece.text,
                            style = body,
                            modifier = Modifier.fillMaxWidth().padding(top = if (piece.opensParagraph && i > 0) 12.dp else 0.dp),
                        )
                    }
                }
                if (romanised != null) {
                    item(key = "romanised") {
                        TextMMD(text = romanised, style = small, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    }
                }
            }
        }
    }
}
