package com.ebooksplayer.shelf.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebooksplayer.shelf.data.db.entity.Book
import com.ebooksplayer.shelf.data.db.entity.Collection
import com.ebooksplayer.shelf.data.drive.DriveDownloadOutcome
import com.ebooksplayer.shelf.data.drive.DriveSyncRepository
import com.ebooksplayer.shelf.data.repository.BookRepository
import com.ebooksplayer.shelf.data.repository.CollectionRepository
import com.ebooksplayer.shelf.ui.navigation.ARG_BOOK_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val repository: BookRepository,
    private val driveSyncRepository: DriveSyncRepository,
    private val collectionRepository: CollectionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[ARG_BOOK_ID])

    val book: StateFlow<Book?> = repository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val collections: StateFlow<List<Collection>> = collectionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookCollectionIds: StateFlow<Set<Long>> = collectionRepository
        .observeCollectionIdsForBook(bookId)
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    fun download() {
        if (_isDownloading.value) return
        _isDownloading.value = true
        viewModelScope.launch {
            try {
                when (val outcome = driveSyncRepository.downloadBook(bookId)) {
                    is DriveDownloadOutcome.Success -> Unit
                    is DriveDownloadOutcome.AuthorizationFailed ->
                        _downloadError.value = "Couldn't get access to Google Drive."
                    is DriveDownloadOutcome.Failure ->
                        _downloadError.value = "Download failed: ${outcome.reason}"
                }
            } catch (e: Exception) {
                _downloadError.value = "Download failed: ${e.message ?: "unknown error"}"
            } finally {
                _isDownloading.value = false
            }
        }
    }

    fun clearDownloadError() {
        _downloadError.value = null
    }

    fun toggleCollection(collectionId: Long) {
        viewModelScope.launch {
            if (collectionId in bookCollectionIds.value) {
                collectionRepository.removeBookFromCollection(bookId, collectionId)
            } else {
                collectionRepository.addBookToCollection(bookId, collectionId)
            }
        }
    }
}
