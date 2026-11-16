@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.ebooksplayer.shelf.ui.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark as BookmarkIcon
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ebooksplayer.shelf.data.db.entity.Bookmark
import com.ebooksplayer.shelf.data.db.entity.Highlight
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val highlights by viewModel.highlights.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    var showToc by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showHighlights by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingNoteLocator by remember { mutableStateOf<Locator?>(null) }
    var viewingHighlight by remember { mutableStateOf<Highlight?>(null) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? ReaderUiState.Ready)?.book?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state is ReaderUiState.Ready) {
                        IconButton(onClick = { showToc = true }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Contents")
                        }
                        IconButton(onClick = { showBookmarks = true }) {
                            Icon(Icons.Filled.BookmarkIcon, contentDescription = "Bookmarks")
                        }
                        IconButton(onClick = { showHighlights = true }) {
                            Icon(Icons.Filled.Highlight, contentDescription = "Highlights")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.FormatSize, contentDescription = "Display settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            is ReaderUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is ReaderUiState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Couldn't open this book.", textAlign = TextAlign.Center) }

            is ReaderUiState.Ready -> EpubNavigatorHost(
                modifier = Modifier.fillMaxSize().padding(padding),
                publication = current.publication,
                initialLocator = current.initialLocator,
                preferences = preferences,
                highlights = highlights,
                onLocatorChanged = viewModel::saveProgress,
                onHighlight = { locator -> viewModel.addHighlight(locator) },
                onNote = { locator -> pendingNoteLocator = locator },
                onDictionary = { text -> launchDictionaryLookup(context, text) },
                onSearchWeb = { text -> launchWebSearch(context, text) },
                onHighlightTapped = { highlight -> viewingHighlight = highlight },
                onNavigatorReady = { navigator = it },
            )
        }

        val readyState = state as? ReaderUiState.Ready
        if (showToc && readyState != null) {
            ModalBottomSheet(onDismissRequest = { showToc = false }) {
                TableOfContentsList(
                    links = readyState.publication.tableOfContents,
                    onSelect = { link ->
                        showToc = false
                        readyState.publication.locatorFromLink(link)?.let { locator ->
                            navigator?.go(locator)
                        }
                    },
                )
            }
        }

        if (showBookmarks) {
            ModalBottomSheet(onDismissRequest = { showBookmarks = false }) {
                BookmarkList(
                    bookmarks = bookmarks,
                    onAdd = viewModel::addBookmark,
                    onSelect = { bookmark ->
                        showBookmarks = false
                        viewModel.locatorFromBookmark(bookmark)?.let { locator -> navigator?.go(locator) }
                    },
                    onDelete = viewModel::deleteBookmark,
                )
            }
        }

        if (showHighlights) {
            ModalBottomSheet(onDismissRequest = { showHighlights = false }) {
                HighlightList(
                    highlights = highlights,
                    onSelect = { highlight ->
                        showHighlights = false
                        viewModel.locatorFromHighlight(highlight)?.let { locator -> navigator?.go(locator) }
                    },
                    onDelete = viewModel::deleteHighlight,
                )
            }
        }

        if (showSettings) {
            DisplaySettingsDialog(
                preferences = preferences,
                onPreferencesChange = viewModel::updatePreferences,
                onDismiss = { showSettings = false },
            )
        }

        pendingNoteLocator?.let { locator ->
            NoteDialog(
                onConfirm = { note ->
                    viewModel.addHighlight(locator, note.ifBlank { null })
                    pendingNoteLocator = null
                },
                onDismiss = { pendingNoteLocator = null },
            )
        }

        viewingHighlight?.let { highlight ->
            AlertDialog(
                onDismissRequest = { viewingHighlight = null },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteHighlight(highlight)
                        viewingHighlight = null
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { viewingHighlight = null }) { Text("Close") } },
                title = { Text("Highlight") },
                text = {
                    Text(highlight.note?.takeIf { it.isNotBlank() } ?: "No note on this highlight.")
                },
            )
        }
    }
}

@Composable
private fun BookmarkList(
    bookmarks: List<Bookmark>,
    onAdd: () -> Unit,
    onSelect: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Bookmarks", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Bookmark this page")
            }
        }
        if (bookmarks.isEmpty()) {
            Text(
                "No bookmarks yet. Tap + to bookmark the current page.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(bookmark) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Bookmark", style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { onDelete(bookmark) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightList(
    highlights: List<Highlight>,
    onSelect: (Highlight) -> Unit,
    onDelete: (Highlight) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            "Highlights",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        if (highlights.isEmpty()) {
            Text(
                "No highlights yet. Select some text while reading to highlight it.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn {
                items(highlights, key = { it.id }) { highlight ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(highlight) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            highlight.note?.takeIf { it.isNotBlank() } ?: "Highlight",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onDelete(highlight) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete highlight")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Note") },
                keyboardOptions = KeyboardOptions.Default,
            )
        },
    )
}

private fun launchDictionaryLookup(context: Context, text: String) {
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Look up")) }
}

private fun launchWebSearch(context: Context, text: String) {
    if (text.isBlank()) return
    val url = "https://www.google.com/search?q=" + Uri.encode(text)
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun TableOfContentsList(links: List<Link>, onSelect: (Link) -> Unit) {
    LazyColumn {
        items(links) { link ->
            Text(
                text = link.title ?: link.href.toString(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(link) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun DisplaySettingsDialog(
    preferences: EpubPreferences,
    onPreferencesChange: (EpubPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Display") },
        text = {
            ReadingPreferencesControls(
                preferences = preferences,
                onPreferencesChange = onPreferencesChange,
            )
        },
    )
}
