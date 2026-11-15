package com.chmouel.liseur.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chmouel.liseur.BuildConfig
import com.chmouel.liseur.diagnostics.DiagnosticHistory
import com.chmouel.liseur.diagnostics.DiagnosticExport
import com.chmouel.liseur.R
import com.chmouel.liseur.ui.LocalEInk
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LiteraryQuote(val text: String, val author: String)

/**
 * Words about reading, chosen to match what Liseur is for: leisure,
 * solitude, a long book, and nothing between you and the page.
 *
 * One is picked when the screen opens and stays for the visit — no
 * carousel, no shuffle while you are looking.
 */
private val LiteraryQuotes = listOf(
    LiteraryQuote(
        text = "You can never get a cup of tea large enough or a book long enough to suit me.",
        author = "C. S. Lewis",
    ),
    LiteraryQuote(
        text = "A great book should leave you with many experiences, and slightly exhausted at the end. You live several lives while reading.",
        author = "William Styron",
    ),
    LiteraryQuote(
        text = "There are perhaps no days of our childhood we lived so fully as those we believe we left without having lived them, those we spent with a favourite book.",
        author = "Marcel Proust",
    ),
    LiteraryQuote(
        text = "The only advice, indeed, that one can give another about reading is to take no advice, to follow your own instincts, to use your own reason, to come to your own conclusions.",
        author = "Virginia Woolf",
    ),
    LiteraryQuote(
        text = "Books must be read as deliberately and reservedly as they were written.",
        author = "Henry David Thoreau",
    ),
    LiteraryQuote(
        text = "Many a book is like a key to unknown chambers within the castle of one\u2019s own self.",
        author = "Franz Kafka",
    ),
    LiteraryQuote(
        text = "I have always imagined that Paradise will be a kind of library.",
        author = "Jorge Luis Borges",
    ),
    LiteraryQuote(
        text = "Reading gives us someplace to go when we have to stay where we are.",
        author = "Mason Cooley",
    ),
)

/** The inside cover — quiet, warm, unhurried. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenSponsor: () -> Unit,
    onOpenLicences: () -> Unit,
    diagnostics: DiagnosticHistory,
    modifier: Modifier = Modifier,
) {
    val quote = remember { LiteraryQuotes.random() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnosticParts by remember { mutableStateOf(emptyList<DiagnosticHistory.Part>()) }
    var diagnosticPartIndex by remember { mutableStateOf(0) }
    var preparingDiagnostics by remember { mutableStateOf(false) }
    var exportingDiagnostics by remember { mutableStateOf(false) }
    val exportFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                exportingDiagnostics = true
                val result = runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    val parts = diagnostics.exportParts()
                    diagnosticParts = parts
                    diagnosticPartIndex = 0
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        DiagnosticExport.writeToTree(context, uri, parts)
                    }
                }
                exportingDiagnostics = false
                result.onSuccess {
                    Toast.makeText(context, "Saved ${it.filesWritten} files in ${it.folderName}", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(context, "Diagnostic export failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val colors = MaterialTheme.colorScheme

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = { PaneBackButton(onBack) },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LogoBlock(Modifier.padding(top = 48.dp))

                AnimatedVisibility(
                    visibleState = remember { MutableTransitionState(false) }
                        .apply { targetState = true },
                    // A fade is a run of whole-screen repaints on electronic
                    // paper, so the quote is simply there when the screen is.
                    enter = if (LocalEInk.current) {
                        EnterTransition.None
                    } else {
                        fadeIn(animationSpec = tween(durationMillis = 300))
                    },
                    modifier = Modifier.padding(top = 48.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = quote.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontStyle = FontStyle.Italic,
                            ),
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.about_quote_attribution, quote.author),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                AuthorBlock(modifier = Modifier.padding(top = 40.dp))

                OutlinedButton(
                    onClick = onOpenSponsor,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    modifier = Modifier
                        .padding(top = 32.dp)
                        .widthIn(max = 280.dp),
                ) {
                    Icon(
                        Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                        tint = colors.primary,
                    )
                    Text(
                        text = stringResource(R.string.about_sponsor),
                        color = colors.primary,
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onOpenSource) {
                        Text(
                            text = stringResource(R.string.about_source),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "\u00b7",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onOpenLicences) {
                        Text(
                            text = stringResource(R.string.about_licences),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.about_star_prompt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 16.dp, bottom = 48.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.about_version,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ) + " (${BuildConfig.PAPA_BUILD_LABEL})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.about_licence_line),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, bottom = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Diagnostic history", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Local only. Each copied part is limited to 12,000 characters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        OutlinedButton(
                            enabled = !preparingDiagnostics,
                            onClick = {
                                preparingDiagnostics = true
                                scope.launch {
                                    diagnosticParts = runCatching { diagnostics.exportParts() }.getOrElse { emptyList() }
                                    diagnosticPartIndex = 0
                                    preparingDiagnostics = false
                                }
                            },
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text(if (preparingDiagnostics) "Preparing…" else "Prepare / refresh export")
                        }
                        Text(
                            "Choose the Documents folder in the system picker. Liseur will create Papa Liseur Diagnostics and a separate folder for this APK build.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        OutlinedButton(
                            enabled = !preparingDiagnostics && !exportingDiagnostics,
                            onClick = { exportFolderPicker.launch(null) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(if (exportingDiagnostics) "Exporting…" else "Export to Documents folder")
                        }
                        if (diagnosticParts.isNotEmpty()) {
                            val part = diagnosticParts[diagnosticPartIndex]
                            Text(
                                "Part ${part.index} of ${part.count} · ${part.characterCount} characters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(enabled = diagnosticPartIndex > 0, onClick = { diagnosticPartIndex-- }) {
                                    Text("Previous")
                                }
                                TextButton(onClick = { clipboard.setText(AnnotatedString(part.text)) }) {
                                    Text("Copy this part")
                                }
                                TextButton(
                                    enabled = diagnosticPartIndex < diagnosticParts.lastIndex,
                                    onClick = { diagnosticPartIndex++ },
                                ) { Text("Next") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogoBlock(modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(
                if (isDark) {
                    R.drawable.ic_brand_emblem_night_transparent
                } else {
                    R.drawable.ic_brand_emblem_transparent
                },
            ),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AuthorBlock(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_author_avatar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        )
        Text(
            text = stringResource(R.string.about_author),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
