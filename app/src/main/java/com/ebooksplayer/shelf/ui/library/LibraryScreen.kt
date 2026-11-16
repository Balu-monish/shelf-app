package com.ebooksplayer.shelf.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ebooksplayer.shelf.data.db.entity.Book
import com.ebooksplayer.shelf.data.db.entity.BookFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (bookId: Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val driveMessage by viewModel.driveMessage.collectAsState()
    val driveConsentRequest by viewModel.driveConsentRequest.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateCollectionDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBook) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onDriveConsentResult(result.data) }

    LaunchedEffect(driveConsentRequest) {
        driveConsentRequest?.let { sender ->
            driveConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    LaunchedEffect(importError) {
        importError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearImportError()
        }
    }

    LaunchedEffect(driveMessage) {
        driveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearDriveMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shelf") },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp).size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = viewModel::syncFromDrive) {
                            Icon(Icons.Filled.Sync, contentDescription = "Sync from Drive")
                        }
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Filled.SwapVert, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        viewModel.setSort(mode)
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.Add, contentDescription = "Import a book")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search your library") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.filter == FormatFilter.ALL,
                    onClick = { viewModel.setFilter(FormatFilter.ALL) },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = state.filter == FormatFilter.EBOOK,
                    onClick = { viewModel.setFilter(FormatFilter.EBOOK) },
                    label = { Text("Books") },
                )
                FilterChip(
                    selected = state.filter == FormatFilter.AUDIOBOOK,
                    onClick = { viewModel.setFilter(FormatFilter.AUDIOBOOK) },
                    label = { Text("Audiobooks") },
                )
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.selectedCollectionId == null,
                    onClick = { viewModel.selectCollection(null) },
                    label = { Text("All") },
                )
                state.collections.forEach { collection ->
                    FilterChip(
                        selected = state.selectedCollectionId == collection.id,
                        onClick = { viewModel.selectCollection(collection.id) },
                        label = { Text(collection.name) },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { showCreateCollectionDialog = true },
                    label = { Text("+ New") },
                )
            }

            if (state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.query.isBlank()) {
                            "Your library is empty.\nTap + to import an .epub or .m4b file."
                        } else {
                            "No books match \"${state.query}\"."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.continueItems.isNotEmpty() && state.query.isBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ContinueSection(items = state.continueItems, onOpenBook = onOpenBook)
                        }
                    }
                    items(state.items, key = { it.book.id }) { item ->
                        BookCover(item = item, onClick = { onOpenBook(item.book.id) })
                    }
                }
            }
        }
    }

    if (showCreateCollectionDialog) {
        CreateCollectionDialog(
            onCreate = { name ->
                viewModel.createCollection(name)
                showCreateCollectionDialog = false
            },
            onDismiss = { showCreateCollectionDialog = false },
        )
    }
}

@Composable
private fun CreateCollectionDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New collection") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Collection name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ContinueSection(items: List<LibraryBookItem>, onOpenBook: (Long) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = "Continue",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { "continue-${it.book.id}" }) { item ->
                Box(modifier = Modifier.width(110.dp)) {
                    BookCover(item = item, onClick = { onOpenBook(item.book.id) })
                }
            }
        }
    }
}

@Composable
private fun BookCover(item: LibraryBookItem, onClick: () -> Unit) {
    val book = item.book
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp)),
            onClick = onClick,
        ) {
            Box {
                if (book.coverUri != null) {
                    AsyncImage(
                        model = book.coverUri,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = book.title.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }
                FormatBadge(
                    format = book.format,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
                if (!book.isDownloaded) {
                    NotDownloadedBadge(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    )
                }
                item.progressFraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    )
                }
            }
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun NotDownloadedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Icon(
            Icons.Filled.CloudDownload,
            contentDescription = "Not downloaded",
            modifier = Modifier.padding(3.dp).size(14.dp),
        )
    }
}

@Composable
private fun FormatBadge(format: BookFormat, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = if (format == BookFormat.EPUB) "BOOK" else "AUDIO",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
