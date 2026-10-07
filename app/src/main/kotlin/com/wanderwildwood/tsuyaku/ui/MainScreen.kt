package com.wanderwildwood.tsuyaku.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.tsuyaku.R
import com.wanderwildwood.tsuyaku.engine.ENGLISH
import com.wanderwildwood.tsuyaku.engine.Outcome
import com.wanderwildwood.tsuyaku.engine.Pieces
import com.wanderwildwood.tsuyaku.engine.languageName
import kotlinx.coroutines.delay

/** The bar every screen has: a title, Back where there is somewhere to go back to, and its own actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit) {
    TopAppBarMMD(
        title = { TextMMD(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
        actions = { actions() },
    )
}

/**
 * The home screen. The way at the top, from and into, with the swap between them; the text
 * in the upper half and its translation in the lower, each with what can be done to it beneath.
 */
@Composable
fun MainScreen(
    desk: Desk,
    installed: Set<String>,
    notes: Boolean,
    onLanguages: () -> Unit,
    onGet: (String) -> Unit,
    onAbout: () -> Unit,
    onCamera: () -> Unit,
    onPicture: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onNotes: (String) -> Unit,
) {
    var picking by remember { mutableStateOf<Picking?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(stringResource(R.string.app_name), null) {
                BarButton(Icons.Languages, stringResource(R.string.cd_languages), onLanguages)
                BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (installed.size <= 1) {
                NoneYet(onLanguages)
                return@Column
            }
            WayRow(desk, onFrom = { picking = Picking.From }, onTo = { picking = Picking.To })
            HorizontalDividerMMD()

            if (desk.fromPicture) {
                TextMMD(
                    text = stringResource(R.string.from_picture),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            TextField(
                value = desk.input,
                onValueChange = desk::edit,
                modifier = Modifier.fillMaxWidth().weight(1f).textActions(desk.input, desk::edit),
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { TextMMD(text = stringResource(R.string.input_hint), style = MaterialTheme.typography.bodyLarge) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                // Not TextFieldMMD: its rule beneath belongs under a field, and this is half the page.
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                BarButton(Icons.Camera, stringResource(R.string.cd_camera), onCamera)
                BarButton(Icons.Picture, stringResource(R.string.cd_picture), onPicture)
                Spacer(Modifier.weight(1f))
                if (desk.input.text.isNotEmpty()) ArmedText(stringResource(R.string.clear), stringResource(R.string.clear_armed), desk::clear)
            }
            HorizontalDividerMMD()

            Box(Modifier.fillMaxWidth().weight(1f)) {
                Output(desk, onGet)
            }
            val done = desk.outcome as? Outcome.Done
            if (done != null && done.text.isNotBlank() && desk.input.text.isNotBlank()) {
                Actions(done.text, notes, onCopy, onShare, onNotes)
            }
        }
    }

    when (picking) {
        Picking.From -> LanguagePicker(
            title = stringResource(R.string.pick_from),
            codes = installed.filter { it == ENGLISH || desk.app.packs.catalog.lang(it)?.toEnglish != null },
            current = desk.way.from,
            onPick = { picking = null; desk.choose(from = it) },
            onMore = { picking = null; onLanguages() },
            onDismiss = { picking = null },
        )
        Picking.To -> LanguagePicker(
            title = stringResource(R.string.pick_to),
            codes = installed.filter { it == ENGLISH || desk.app.packs.catalog.lang(it)?.fromEnglish != null },
            current = desk.way.to,
            onPick = { picking = null; desk.choose(to = it) },
            onMore = { picking = null; onLanguages() },
            onDismiss = { picking = null },
        )
        null -> Unit
    }
}

private enum class Picking { From, To }

@Composable
private fun WayRow(desk: Desk, onFrom: () -> Unit, onTo: () -> Unit) {
    val from = languageName(desk.way.from)
    val to = languageName(desk.way.to)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
    ) {
        LanguageName(from, stringResource(R.string.cd_from, from), TextAlign.Start, Modifier.weight(1f), onFrom)
        BarButton(Icons.Swap, stringResource(R.string.cd_swap), desk::swap)
        LanguageName(to, stringResource(R.string.cd_to, to), TextAlign.End, Modifier.weight(1f), onTo)
    }
}

@Composable
private fun LanguageName(name: String, description: String, align: TextAlign, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = if (align == TextAlign.Start) Alignment.CenterStart else Alignment.CenterEnd,
        modifier = modifier.height(56.dp).clickable(onClickLabel = description, onClick = onClick).padding(horizontal = 12.dp),
    ) {
        TextMMD(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The lower half: the translation, or what stands in its way. */
@Composable
private fun Output(desk: Desk, onGet: (String) -> Unit) {
    val names = { code: String -> languageName(code) }
    Column(Modifier.fillMaxSize()) {
        val status = when {
            desk.reading -> stringResource(R.string.reading_picture)
            desk.working -> stringResource(R.string.translating)
            else -> desk.notice?.let { stringResource(it) }
        }
        if (status != null) {
            TextMMD(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        if (desk.input.text.isBlank()) return@Column
        when (val o = desk.outcome) {
            is Outcome.Done -> SelectionContainer(Modifier.fillMaxSize().textActions()) {
                LazyColumnMMD(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp)) {
                    Pieces.of(o.text).forEachIndexed { i, piece ->
                        item(key = "p$i") {
                            TextMMD(
                                text = piece.text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = if (piece.opensParagraph && i > 0) 14.dp else 0.dp),
                            )
                        }
                    }
                    if (o.romanised != null) {
                        item(key = "romanised") {
                            TextMMD(
                                text = o.romanised,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
            }
            is Outcome.Missing -> Blocked(stringResource(R.string.missing, names(o.code)), stringResource(R.string.get, names(o.code))) { onGet(o.code) }
            is Outcome.NoModel -> Blocked(
                stringResource(
                    if (desk.way.to == o.code) R.string.no_model_from_english else R.string.no_model_to_english,
                    names(o.code),
                ),
                null,
                null,
            )
            is Outcome.Failed -> Blocked(stringResource(R.string.failed, o.message), null, null)
            null -> Unit
        }
    }
}

@Composable
private fun Blocked(message: String, button: String?, onClick: (() -> Unit)?) {
    Column(Modifier.padding(16.dp)) {
        TextMMD(text = message, style = MaterialTheme.typography.bodyMedium)
        if (button != null && onClick != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButtonMMD(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                TextMMD(text = button, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Actions(text: String, notes: Boolean, onCopy: (String) -> Unit, onShare: (String) -> Unit, onNotes: (String) -> Unit) {
    var copied by remember(text) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Small(stringResource(if (copied) R.string.copied else R.string.copy), Modifier.weight(1f)) {
            onCopy(text)
            copied = true
        }
        Small(stringResource(R.string.share), Modifier.weight(1f)) { onShare(text) }
        if (notes) Small(stringResource(R.string.to_notes), Modifier.weight(1f)) { onNotes(text) }
    }
}

@Composable
fun Small(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButtonMMD(onClick = onClick, modifier = modifier.height(44.dp)) {
        TextMMD(text = label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

/** First run: nothing to translate with yet, and what getting something costs. */
@Composable
private fun NoneYet(onLanguages: () -> Unit) {
    Column(Modifier.padding(20.dp)) {
        TextMMD(text = stringResource(R.string.none_yet), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedButtonMMD(onClick = onLanguages, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            TextMMD(text = stringResource(R.string.choose_language), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * A press that asks first, by changing its own words: "Clear — tap again". It disarms itself
 * after four seconds, so a stray tap leaves nothing live behind.
 */
@Composable
fun ArmedText(label: String, armedLabel: String, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4_000)
            armed = false
        }
    }
    TextMMD(
        text = if (armed) armedLabel else label,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clickable {
                if (armed) {
                    armed = false
                    onConfirm()
                } else {
                    armed = true
                }
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
    )
}
