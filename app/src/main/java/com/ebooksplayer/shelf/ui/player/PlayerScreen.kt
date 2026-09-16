package com.ebooksplayer.shelf.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark as BookmarkIcon
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List as ChaptersIcon
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ebooksplayer.shelf.data.db.entity.Bookmark
import com.ebooksplayer.shelf.player.PlayerChapter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    bookId: Long,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    var showChapters by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(playbackError) {
        playbackError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearPlaybackError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showBookmarks = true }) {
                        Icon(Icons.Filled.BookmarkIcon, contentDescription = "Bookmarks")
                    }
                },
            )
        },
    ) { padding ->
        val book = state.book
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                if (book?.coverUri != null) {
                    AsyncImage(
                        model = book.coverUri,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Text(
                text = book?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp),
            )
            book?.author?.let {
                Text(text = it, style = MaterialTheme.typography.bodyLarge)
            }
            state.chapters.getOrNull(state.currentChapterIndex)?.let { chapter ->
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = viewModel::seekTo,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                IconButton(onClick = viewModel::skipBack) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds")
                }
                FilledIconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.height(64.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(onClick = viewModel::skipForward) {
                    Icon(Icons.Filled.Forward30, contentDescription = "Forward 30 seconds")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box {
                    TextButton(onClick = { showSpeedMenu = true }) {
                        Text("${state.speed}x")
                    }
                    DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                        SPEED_OPTIONS.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}x") },
                                onClick = {
                                    viewModel.setSpeed(speed)
                                    showSpeedMenu = false
                                },
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { showSleepMenu = true }) {
                        Icon(Icons.Filled.Bedtime, contentDescription = "Sleep timer")
                    }
                    DropdownMenu(expanded = showSleepMenu, onDismissRequest = { showSleepMenu = false }) {
                        SLEEP_TIMER_OPTIONS_MINUTES.forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text("$minutes min") },
                                onClick = {
                                    viewModel.startSleepTimer(minutes)
                                    showSleepMenu = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Off") },
                            onClick = {
                                viewModel.cancelSleepTimer()
                                showSleepMenu = false
                            },
                        )
                    }
                }

                if (state.chapters.isNotEmpty()) {
                    IconButton(onClick = { showChapters = true }) {
                        Icon(Icons.Filled.ChaptersIcon, contentDescription = "Chapters")
                    }
                }
            }

            state.sleepTimerRemainingMs?.let { remaining ->
                Text(
                    text = "Sleeping in ${formatDuration(remaining)}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    if (showChapters) {
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { showChapters = false },
            sheetState = sheetState,
        ) {
            ChapterList(
                chapters = state.chapters,
                currentIndex = state.currentChapterIndex,
                onSelect = { chapter ->
                    viewModel.seekToChapter(chapter)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showChapters = false }
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
                    viewModel.seekToBookmark(bookmark)
                    showBookmarks = false
                },
                onDelete = viewModel::deleteBookmark,
            )
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragPositionMs by remember { mutableStateOf<Long?>(null) }
    val displayedPosition = dragPositionMs ?: positionMs
    val duration = durationMs.coerceAtLeast(1L)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = displayedPosition.coerceIn(0L, duration).toFloat(),
            onValueChange = { dragPositionMs = it.roundToInt().toLong() },
            onValueChangeFinished = {
                dragPositionMs?.let(onSeek)
                dragPositionMs = null
            },
            valueRange = 0f..duration.toFloat(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(displayedPosition), style = MaterialTheme.typography.labelMedium)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ChapterList(chapters: List<PlayerChapter>, currentIndex: Int, onSelect: (PlayerChapter) -> Unit) {
    LazyColumn {
        items(chapters, key = { it.index }) { chapter ->
            val isCurrent = chapter.index == currentIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    )
                    .clickable { onSelect(chapter) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(chapter.title, style = MaterialTheme.typography.bodyLarge)
                Text(formatDuration(chapter.startMs), style = MaterialTheme.typography.labelMedium)
            }
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
                Icon(Icons.Filled.Add, contentDescription = "Add bookmark at current position")
            }
        }
        if (bookmarks.isEmpty()) {
            Text(
                "No bookmarks yet. Tap + to save the current position.",
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
                        Text(
                            formatDuration(bookmark.position.toLongOrNull() ?: 0L),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        IconButton(onClick = { onDelete(bookmark) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark")
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
