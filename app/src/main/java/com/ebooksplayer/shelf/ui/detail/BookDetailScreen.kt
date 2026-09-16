package com.ebooksplayer.shelf.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ebooksplayer.shelf.data.db.entity.BookFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Long,
    onRead: () -> Unit,
    onListen: () -> Unit,
    onBack: () -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val book by viewModel.book.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val bookCollectionIds by viewModel.bookCollectionIds.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(downloadError) {
        downloadError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearDownloadError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        val current = book
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    if (current.coverUri != null) {
                        AsyncImage(
                            model = current.coverUri,
                            contentDescription = current.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(current.title.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                if (!current.isDownloaded) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Icon(
                            Icons.Filled.CloudDownload,
                            contentDescription = "Not downloaded",
                            modifier = Modifier.padding(4.dp).size(20.dp),
                        )
                    }
                }
            }

            Text(
                text = current.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            current.author?.let {
                Text(text = it, style = MaterialTheme.typography.bodyLarge)
            }

            if (current.isDownloaded) {
                Button(
                    onClick = if (current.format == BookFormat.EPUB) onRead else onListen,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Text(if (current.format == BookFormat.EPUB) "Read" else "Listen")
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Button(onClick = viewModel::download, enabled = !isDownloading) {
                        Text(downloadLabel(current.driveSizeBytes))
                    }
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(start = 16.dp).size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            Text(
                "Collections",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
            if (collections.isEmpty()) {
                Text(
                    "Create one from the library screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    collections.forEach { collection ->
                        FilterChip(
                            selected = collection.id in bookCollectionIds,
                            onClick = { viewModel.toggleCollection(collection.id) },
                            label = { Text(collection.name) },
                        )
                    }
                }
            }
        }
    }
}

private fun downloadLabel(sizeBytes: Long?): String {
    if (sizeBytes == null) return "Download"
    val mb = sizeBytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        "Download (%.1f GB)".format(mb / 1024.0)
    } else {
        "Download (%.0f MB)".format(mb)
    }
}
