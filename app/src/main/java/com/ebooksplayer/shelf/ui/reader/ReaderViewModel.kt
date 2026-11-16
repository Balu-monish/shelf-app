@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.ebooksplayer.shelf.ui.reader

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebooksplayer.shelf.data.db.entity.Book
import com.ebooksplayer.shelf.data.db.entity.Bookmark
import com.ebooksplayer.shelf.data.db.entity.EbookProgress
import com.ebooksplayer.shelf.data.db.entity.Highlight
import com.ebooksplayer.shelf.data.repository.BookRepository
import com.ebooksplayer.shelf.data.repository.BookmarkRepository
import com.ebooksplayer.shelf.data.repository.HighlightRepository
import com.ebooksplayer.shelf.data.repository.ProgressRepository
import com.ebooksplayer.shelf.data.settings.ReaderPreferencesRepository
import com.ebooksplayer.shelf.ui.navigation.ARG_BOOK_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener

private const val HIGHLIGHT_COLOR_PLAIN = 0xFFFFF59D.toInt() // yellow
private const val HIGHLIGHT_COLOR_WITH_NOTE = 0xFFFFAB91.toInt() // orange, marks a note is attached

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data object Error : ReaderUiState
    data class Ready(
        val book: Book,
        val publication: Publication,
        val initialLocator: Locator?,
    ) : ReaderUiState
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val progressRepository: ProgressRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val highlightRepository: HighlightRepository,
    private val readerPreferencesRepository: ReaderPreferencesRepository,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[ARG_BOOK_ID])

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.observeForBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val highlights: StateFlow<List<Highlight>> = highlightRepository.observeForBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<EpubPreferences> = readerPreferencesRepository.preferences

    /** The most recent locator reported by the navigator, used as the bookmark target. */
    private var lastLocator: Locator? = null

    init {
        viewModelScope.launch {
            _state.value = openBook()
        }
    }

    private suspend fun openBook(): ReaderUiState {
        val book = bookRepository.getBook(bookId) ?: return ReaderUiState.Error
        val fileUri = book.fileUri ?: return ReaderUiState.Error
        val url = Uri.parse(fileUri).toAbsoluteUrl() ?: return ReaderUiState.Error

        val asset = assetRetriever.retrieve(url).getOrElse { return ReaderUiState.Error }
        val publication = publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse { return ReaderUiState.Error }

        val initialLocator = progressRepository.getEbookProgress(bookId)
            ?.locatorJson
            ?.let { json -> runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull() }

        return ReaderUiState.Ready(book = book, publication = publication, initialLocator = initialLocator)
    }

    fun saveProgress(locator: Locator) {
        lastLocator = locator
        viewModelScope.launch {
            progressRepository.saveEbookProgress(
                EbookProgress(
                    bookId = bookId,
                    locatorJson = locator.toJSON().toString(),
                    percentage = (locator.locations.progression ?: 0.0).toFloat(),
                    lastReadAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun addBookmark() {
        val locator = lastLocator ?: (state.value as? ReaderUiState.Ready)?.initialLocator ?: return
        viewModelScope.launch {
            bookmarkRepository.addBookmark(bookId, locator.toJSON().toString())
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkRepository.deleteBookmark(bookmark) }
    }

    fun locatorFromBookmark(bookmark: Bookmark): Locator? =
        runCatching { Locator.fromJSON(JSONObject(bookmark.position)) }.getOrNull()

    fun updatePreferences(preferences: EpubPreferences) {
        readerPreferencesRepository.update(preferences)
    }

    fun addHighlight(locator: Locator, note: String? = null) {
        val color = if (note != null) HIGHLIGHT_COLOR_WITH_NOTE else HIGHLIGHT_COLOR_PLAIN
        viewModelScope.launch {
            highlightRepository.addHighlight(bookId, locator.toJSON().toString(), color, note)
        }
    }

    fun deleteHighlight(highlight: Highlight) {
        viewModelScope.launch { highlightRepository.deleteHighlight(highlight) }
    }

    fun locatorFromHighlight(highlight: Highlight): Locator? =
        runCatching { Locator.fromJSON(JSONObject(highlight.locatorJson)) }.getOrNull()

    override fun onCleared() {
        (_state.value as? ReaderUiState.Ready)?.publication?.close()
        super.onCleared()
    }
}
