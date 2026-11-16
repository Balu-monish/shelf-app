package com.ebooksplayer.shelf.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebooksplayer.shelf.data.db.entity.AudiobookProgress
import com.ebooksplayer.shelf.data.db.entity.Book
import com.ebooksplayer.shelf.data.db.entity.Bookmark
import com.ebooksplayer.shelf.data.repository.BookRepository
import com.ebooksplayer.shelf.data.repository.BookmarkRepository
import com.ebooksplayer.shelf.data.repository.ProgressRepository
import com.ebooksplayer.shelf.player.PlayerChapter
import com.ebooksplayer.shelf.player.PlayerController
import com.ebooksplayer.shelf.ui.navigation.ARG_BOOK_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerUiState(
    val book: Book? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val chapters: List<PlayerChapter> = emptyList(),
    val sleepTimerRemainingMs: Long? = null,
) {
    val currentChapterIndex: Int
        get() = chapters.indexOfLast { positionMs >= it.startMs }.coerceAtLeast(0)
}

val SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
val SLEEP_TIMER_OPTIONS_MINUTES = listOf(5, 15, 30, 45, 60)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val progressRepository: ProgressRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val playerController: PlayerController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[ARG_BOOK_ID])

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.observeForBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackError: StateFlow<String?> = playerController.playbackError

    fun clearPlaybackError() = playerController.clearPlaybackError()

    private val _book = MutableStateFlow<Book?>(null)

    val uiState: StateFlow<PlayerUiState> = combine(
        _book,
        playerController.isPlaying,
        playerController.positionMs,
        playerController.durationMs,
        playerController.speed,
        playerController.chapters,
        playerController.sleepTimerRemainingMs,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        PlayerUiState(
            book = values[0] as Book?,
            isPlaying = values[1] as Boolean,
            positionMs = values[2] as Long,
            durationMs = values[3] as Long,
            speed = values[4] as Float,
            chapters = values[5] as List<PlayerChapter>,
            sleepTimerRemainingMs = values[6] as Long?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerUiState())

    private var progressSaveJob: Job? = null

    init {
        viewModelScope.launch {
            val book = bookRepository.getBook(bookId) ?: return@launch
            val fileUri = book.fileUri ?: return@launch
            _book.value = book
            val progress = progressRepository.getAudiobookProgress(bookId)
            playerController.playBook(
                bookId = bookId,
                fileUri = fileUri,
                title = book.title,
                author = book.author,
                artworkUri = book.coverUri,
                startPositionMs = progress?.positionMs ?: 0L,
                initialSpeed = progress?.playbackSpeed ?: 1f,
            )
        }
        startProgressAutosave()
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun seekTo(ms: Long) = playerController.seekTo(ms)
    fun skipForward() = playerController.skipForward()
    fun skipBack() = playerController.skipBack()
    fun setSpeed(value: Float) = playerController.setSpeed(value)
    fun seekToChapter(chapter: PlayerChapter) = playerController.seekToChapter(chapter)
    fun startSleepTimer(minutes: Int) = playerController.startSleepTimer(minutes * 60_000L)
    fun cancelSleepTimer() = playerController.cancelSleepTimer()

    fun addBookmark() {
        viewModelScope.launch {
            bookmarkRepository.addBookmark(bookId, uiState.value.positionMs.toString())
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkRepository.deleteBookmark(bookmark) }
    }

    fun seekToBookmark(bookmark: Bookmark) {
        bookmark.position.toLongOrNull()?.let(playerController::seekTo)
    }

    private fun startProgressAutosave() {
        progressSaveJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                saveProgress()
            }
        }
    }

    private suspend fun saveProgress() {
        val state = uiState.value
        val book = state.book ?: return
        progressRepository.saveAudiobookProgress(
            AudiobookProgress(
                bookId = book.id,
                chapterIndex = state.currentChapterIndex,
                positionMs = state.positionMs,
                playbackSpeed = state.speed,
                lastPlayedAt = System.currentTimeMillis(),
            ),
        )
    }

    override fun onCleared() {
        progressSaveJob?.cancel()
        viewModelScope.launch { saveProgress() }
        super.onCleared()
    }
}
