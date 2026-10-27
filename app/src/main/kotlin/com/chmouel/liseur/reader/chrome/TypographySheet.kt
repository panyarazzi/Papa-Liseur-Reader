package com.chmouel.liseur.reader.chrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chmouel.liseur.R
import com.chmouel.liseur.data.settings.fonts.UserFont
import com.chmouel.liseur.data.settings.ReadingFont
import com.chmouel.liseur.data.settings.ReaderPrefs
import com.chmouel.liseur.data.settings.ReaderTheme
import com.chmouel.liseur.data.settings.ReaderThemeChoice
import com.chmouel.liseur.data.settings.ReadingCss
import com.chmouel.liseur.ui.LiseurModalBottomSheet
import com.chmouel.liseur.ui.contentWidthCap
import com.chmouel.liseur.ui.reading.FixedLayoutNotice
import com.chmouel.liseur.ui.reading.ReadingBrightnessSlider
import com.chmouel.liseur.ui.reading.ReadingFontDropdown
import com.chmouel.liseur.ui.reading.rememberFontLibrary
import com.chmouel.liseur.ui.reading.ReadingFontSizeSlider
import com.chmouel.liseur.ui.reading.ReadingThemeRow
import com.chmouel.liseur.ui.windowWidth
import com.chmouel.liseur.reader.fonts.EpubFontFamily
import com.chmouel.liseur.reader.fonts.EpubFontInspector
import org.readium.r2.shared.publication.Publication

/**
 * Kindle-style "Aa" sheet: reading theme, font, size and brightness,
 * applied live as they change.
 *
 * The controls themselves live in `ui/reading`, because Settings shows
 * the same ones on a screen of its own. This sheet is the quick path to
 * them with a book open, and adds the answer that only makes sense with
 * one: whether the screen stays awake for it.
 *
 * It is deliberately short. Everything a reader sets once, if ever, is a
 * tap further in, behind the Advanced row at the bottom — see
 * `docs/adr/0001-advanced-reading-menu.md`.
 *
 * [readingCss] is what this book can honour. A fixed-layout book greys
 * the size and the face, because Readium gates them on a reflowable
 * publication, and the line at the top says so once for both. The theme
 * and the brightness stay live:
 * the brightness is the screen's, and Liseur passes its own
 * `backgroundColor`, which Readium paints the pager with whatever the
 * layout — so the swatches still change the surround of a fixed page.
 * See `docs/adr/0020-fixed-layout-reading-settings.md`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypographySheet(
    prefs: ReaderPrefs,
    publication: Publication,
    importedFonts: List<UserFont>,
    fontReplacements: Map<String, String>,
    onFontReplacementChanged: (String, String?) -> Unit,
    fontScales: Map<String, Double>,
    onFontScaleChanged: (String, Double) -> Unit,
    readingTheme: ReaderTheme,
    readingCss: ReadingCss,
    onFontSelected: (ReadingFont) -> Unit,
    onFontSizeChanged: (Double) -> Unit,
    onThemeSelected: (ReaderThemeChoice) -> Unit,
    onBrightnessChanged: (Float?) -> Unit,
    onOpenAdvanced: () -> Unit,
    onDismiss: () -> Unit,
) {
    LiseurModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .align(Alignment.CenterHorizontally)
                .widthIn(max = contentWidthCap(windowWidth()))
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val reshapeable = readingCss.honoursAnything
            if (!reshapeable) FixedLayoutNotice()
            ReadingThemeRow(
                selected = prefs.themeChoice,
                resolved = readingTheme,
                onSelected = onThemeSelected,
            )
            ReadingFontSizeSlider(
                value = prefs.fontSize,
                enabled = reshapeable,
                onChanged = onFontSizeChanged,
            )
            ReadingBrightnessSlider(value = prefs.brightness, onChanged = onBrightnessChanged)
            val fontLibrary = rememberFontLibrary(onSelected = onFontSelected)
            ReadingFontDropdown(
                selected = prefs.font,
                imported = fontLibrary.fonts,
                enabled = reshapeable,
                onSelected = onFontSelected,
                onImport = fontLibrary.pick,
                onRemove = fontLibrary.remove,
            )
            FontReplacementControls(
                publication = publication,
                importedFonts = importedFonts,
                replacements = fontReplacements,
                scales = fontScales,
                enabled = reshapeable,
                onReplacementChanged = onFontReplacementChanged,
                onScaleChanged = onFontScaleChanged,
            )
            // Last, and always. The sheet behind it has rows for any
            // book, read either way, so it can no longer open empty.
            AdvancedRow(onClick = onOpenAdvanced)
        }
    }
}


/** Per-EPUB family-to-font mapping, kept separate from the whole-book font picker. */
@Composable
private fun FontReplacementControls(
    publication: Publication,
    importedFonts: List<UserFont>,
    replacements: Map<String, String>,
    scales: Map<String, Double>,
    enabled: Boolean,
    onReplacementChanged: (String, String?) -> Unit,
    onScaleChanged: (String, Double) -> Unit,
) {
    var expanded by remember(publication) { mutableStateOf(false) }
    var loading by remember(publication) { mutableStateOf(false) }
    var families by remember(publication) { mutableStateOf<List<EpubFontFamily>>(emptyList()) }
    var failed by remember(publication) { mutableStateOf(false) }
    var failureReason by remember(publication) { mutableStateOf<String?>(null) }

    LaunchedEffect(publication, expanded) {
        if (expanded && families.isEmpty() && !loading) {
            loading = true
            val result = runCatching { EpubFontInspector.inspect(publication) }
            families = result.getOrDefault(emptyList())
            failed = result.isFailure
            failureReason = result.exceptionOrNull()?.let { error ->
                error::class.simpleName + ": " + (error.message ?: "no details")
            }
            loading = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Replace fonts in this book", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Choose a font and tune its size independently for each EPUB family",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            when {
                loading -> Text("Scanning this EPUB for font families…", style = MaterialTheme.typography.bodyMedium)
                failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Could not inspect this EPUB's stylesheets.", color = MaterialTheme.colorScheme.error)
                    failureReason?.let { reason ->
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                families.isEmpty() -> Text("No explicit font families were found in the EPUB CSS.", style = MaterialTheme.typography.bodyMedium)
                else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    families.filter { it.referencedByStyles }.forEach { family ->
                        FontReplacementItem(
                            family = family,
                            importedFonts = importedFonts,
                            selectedId = replacements[family.name],
                            selectedScale = scales[family.name] ?: 1.0,
                            onSelected = { onReplacementChanged(family.name, it) },
                            onScaleChanged = { onScaleChanged(family.name, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FontReplacementItem(
    family: EpubFontFamily,
    importedFonts: List<UserFont>,
    selectedId: String?,
    selectedScale: Double,
    onSelected: (String?) -> Unit,
    onScaleChanged: (Double) -> Unit,
) {
    var menuExpanded by remember(family.name) { mutableStateOf(false) }
    val scale = remember(family.name, selectedScale) { mutableStateOf(selectedScale.toFloat()) }
    val selectedName = importedFonts.firstOrNull { it.id == selectedId }?.displayName ?: "Original font"
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { menuExpanded = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(family.name, style = MaterialTheme.typography.bodyMedium)
                    Text(selectedName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Original font") },
                    onClick = { onSelected(null); menuExpanded = false },
                )
                importedFonts.forEach { font ->
                    DropdownMenuItem(
                        text = { Text(font.displayName) },
                        onClick = { onSelected(font.id); menuExpanded = false },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Font scale", style = MaterialTheme.typography.bodySmall)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text("${(scale.value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = scale.value,
            onValueChange = { scale.value = it },
            valueRange = 0.5f..2.0f,
            steps = 29,
            onValueChangeFinished = { onScaleChanged(scale.value.toDouble()) },
        )
    }
}

/**
 * The way through to the settings a reader changes once, if ever.
 *
 * One row, at the bottom, and the only row this sheet gains however many
 * reading features arrive: that is the whole bargain of
 * `docs/adr/0001-advanced-reading-menu.md`. What it opens closes back to
 * here, and here closes back to the book.
 */
@Composable
private fun AdvancedRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = stringResource(R.string.reader_advanced),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

