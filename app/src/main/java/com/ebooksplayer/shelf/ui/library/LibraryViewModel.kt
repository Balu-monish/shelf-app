package com.ebooksplayer.shelf.ui.library

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebooksplayer.shelf.data.db.entity.Book
import com.ebooksplayer.shelf.data.db.entity.BookFormat
import com.ebooksplayer.shelf.data.db.entity.Collection
import com.ebooksplayer.shelf.data.drive.DriveAuthManager
import com.ebooksplayer.shelf.data.drive.DriveSyncOutcome
import com.ebooksplayer.shelf.data.drive.DriveSyncRepository
import com.ebooksplayer.shelf.data.importer.ImportResult
import com.ebooksplayer.shelf.data.repository.BookRepository
import com.ebooksplayer.shelf.data.repository.CollectionRepository
import com.ebooksplayer.shelf.data.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FormatFilter { ALL, EBOOK, AUDIOBOOK }
enum class SortMode(val label: String) {
    RECENTLY_OPENED("Recently opened"),
    RECENTLY_ADDED("Recently added"),
    TITLE("Title"),
    AUTHOR("Author"),
    SERIES("Series"),
}

data class LibraryBookItem(
    val book: Book,
    /** 0f..1f, or null if the book has never been opened. */
    val progressFraction: Float?,
    val lastActivityAt: Long?,
)

data class LibraryUiState(
    val items: List<LibraryBookItem> = emptyList(),
    val continueItems: List<LibraryBookItem> = emptyList(),
    val query: String = "",
    val filter: FormatFilter = FormatFilter.ALL,
    val sort: SortMode = SortMode.RECENTLY_ADDED,
    val collections: List<Collection> = emptyList(),
    val selectedCollectionId: Long? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository,
    private val progressRepository: ProgressRepository,
    private val driveSyncRepository: DriveSyncRepository,
    private val driveAuthManager: DriveAuthManager,
    private val collectionRepository: CollectionRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val formatFilter = MutableStateFlow(FormatFilter.ALL)
    private val sortMode = MutableStateFlow(SortMode.RECENTLY_ADDED)
    private val selectedCollectionId = MutableStateFlow<Long?>(null)

    private val libraryItems = combine(
        repository.observeBooks(),
        progressRepository.observeAllEbookProgress(),
        progressRepository.observeAllAudiobookProgress(),
    ) { books, ebookProgress, audiobookProgress ->
        val ebookByBookId = ebookProgress.associateBy { it.bookId }
        val audiobookByBookId = audiobookProgress.associateBy { it.bookId }
        books.map { book ->
            when (book.format) {
                BookFormat.EPUB -> {
                    val progress = ebookByBookId[book.id]
                    LibraryBookItem(book, progress?.percentage, progress?.lastReadAt)
                }
                BookFormat.M4B -> {
                    val progress = audiobookByBookId[book.id]
                    val duration = book.durationMs
                    val fraction = if (progress != null && duration != null && duration > 0) {
                        (progress.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    LibraryBookItem(book, fraction, progress?.lastPlayedAt)
                }
            }
        }
    }

    /** Ids of books in the selected collection, or null when no collection filter is active. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val collectionFilterBookIds: Flow<Set<Long>?> =
        selectedCollectionId.flatMapLatest { id ->
            if (id == null) {
                flowOf<Set<Long>?>(null)
            } else {
                collectionRepository.observeBookIdsInCollection(id).map { it.toSet() }
            }
        }

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryItems,
        searchQuery,
        formatFilter,
        sortMode,
        selectedCollectionId,
        collectionFilterBookIds,
        collectionRepository.observeAll(),
    ) { values ->
        @Suppress("UNCHECKED_CAST") val items = values[0] as List<LibraryBookItem>
        val query = values[1] as String
        val filter = values[2] as FormatFilter
        val sort = values[3] as SortMode
        val selectedId = values[4] as Long?
        @Suppress("UNCHECKED_CAST") val collectionBookIds = values[5] as Set<Long>?
        @Suppress("UNCHECKED_CAST") val collections = values[6] as List<Collection>

        val filtered = items.filter { item ->
            matchesFilter(item.book.format, filter) &&
                matchesQuery(item.book, query) &&
                (collectionBookIds == null || item.book.id in collectionBookIds)
        }

        val innerComparator: Comparator<LibraryBookItem> = when (sort) {
            SortMode.TITLE -> compareBy<LibraryBookItem> { it.book.title.lowercase() }
            SortMode.RECENTLY_ADDED -> compareByDescending<LibraryBookItem> { it.book.dateAdded }
            SortMode.RECENTLY_OPENED -> compareByDescending<LibraryBookItem> { it.lastActivityAt ?: -1L }
            SortMode.AUTHOR -> compareBy<LibraryBookItem, String?>(nullsLast()) { it.book.author?.lowercase() }
            // Books without series data (all audiobooks, and any EPUB Calibre
            // didn't tag) fall back to sorting by title in this same list -
            // there's no separate grouped/standalone section.
            SortMode.SERIES -> compareBy<LibraryBookItem>(
                { (it.book.series ?: it.book.title).lowercase() },
                { it.book.seriesIndex ?: 0f },
                { it.book.title.lowercase() },
            )
        }
        // Downloaded books always sort ahead of not-yet-downloaded stubs,
        // regardless of which mode is selected.
        val sorted = filtered.sortedWith(
            compareByDescending<LibraryBookItem> { it.book.isDownloaded }.then(innerComparator),
        )

        val continueItems = items
            .filter { it.lastActivityAt != null }
            .sortedByDescending { it.lastActivityAt }
            .take(10)

        LibraryUiState(
            items = sorted,
            continueItems = continueItems,
            query = query,
            filter = filter,
            sort = sort,
            collections = collections,
            selectedCollectionId = selectedId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _driveMessage = MutableStateFlow<String?>(null)
    val driveMessage: StateFlow<String?> = _driveMessage.asStateFlow()

    val driveConsentRequest: StateFlow<IntentSender?> = driveAuthManager.consentRequest

    fun setQuery(query: String) {
        searchQuery.value = query
    }

    fun setFilter(filter: FormatFilter) {
        formatFilter.value = filter
    }

    fun setSort(sort: SortMode) {
        sortMode.value = sort
    }

    fun selectCollection(id: Long?) {
        selectedCollectionId.value = id
    }

    fun createCollection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { collectionRepository.createCollection(trimmed) }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            when (val result = repository.importBook(uri)) {
                is ImportResult.Success -> Unit
                is ImportResult.UnsupportedFormat ->
                    _importError.value = "\"${result.fileName}\" isn't an .epub or .m4b file"
                is ImportResult.Failure ->
                    _importError.value = "Couldn't import that file: ${result.reason}"
            }
        }
    }

    fun clearImportError() {
        _importError.value = null
    }

    fun clearDriveMessage() {
        _driveMessage.value = null
    }

    fun onDriveConsentResult(intent: Intent?) {
        driveAuthManager.onConsentResult(intent)
    }

    fun syncFromDrive() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        viewModelScope.launch {
            try {
                _driveMessage.value = when (val outcome = driveSyncRepository.sync()) {
                    is DriveSyncOutcome.AuthorizationFailed ->
                        "Couldn't get access to Google Drive."
                    is DriveSyncOutcome.Failure ->
                        "Drive sync failed: ${outcome.reason}"
                    is DriveSyncOutcome.Completed -> {
                        val summary = outcome.summary
                        when {
                            summary.imported == 0 && summary.failed == 0 -> "Drive library is already up to date."
                            summary.failed == 0 -> "Imported ${summary.imported} new item(s) from Drive."
                            else -> "Imported ${summary.imported}, ${summary.failed} failed. Check your connection and try again."
                        }
                    }
                }
            } catch (e: Exception) {
                _driveMessage.value = "Drive sync failed: ${e.message ?: "unknown error"}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private fun matchesFilter(format: BookFormat, filter: FormatFilter): Boolean = when (filter) {
        FormatFilter.ALL -> true
        FormatFilter.EBOOK -> format == BookFormat.EPUB
        FormatFilter.AUDIOBOOK -> format == BookFormat.M4B
    }

    private fun matchesQuery(book: Book, query: String): Boolean {
        if (query.isBlank()) return true
        return book.title.contains(query, ignoreCase = true) ||
            book.author?.contains(query, ignoreCase = true) == true
    }
}
