package com.ebooksplayer.shelf.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.Chapter
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Wraps a MediaController connected to [PlaybackService] so the UI never
 * talks to ExoPlayer directly. This is the standard Media3 session pattern:
 * the player lives in the service, the app talks to it through a
 * controller that can survive the app process changing configuration.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Playback operation failed", throwable)
        _playbackError.value = throwable.message ?: "Couldn't play this audiobook"
    }
    // MediaController requires every call to happen on the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)

    private var controller: MediaController? = null

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _chapters = MutableStateFlow<List<PlayerChapter>>(emptyList())
    val chapters: StateFlow<List<PlayerChapter>> = _chapters.asStateFlow()

    private val _currentBookId = MutableStateFlow<Long?>(null)
    val currentBookId: StateFlow<Long?> = _currentBookId.asStateFlow()

    private var sleepTimerJob: Job? = null
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    private var positionPollJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlayingNow: Boolean) {
            _isPlaying.value = isPlayingNow
            if (isPlayingNow) startPositionPolling() else stopPositionPolling()
        }

        override fun onTracksChanged(tracks: Tracks) {
            _chapters.value = extractChapters(tracks)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _speed.value = playbackParameters.speed
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _chapters.value = emptyList()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            val duration = player.duration
            if (duration != androidx.media3.common.C.TIME_UNSET) {
                _durationMs.value = duration
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error", error)
            _playbackError.value = error.message ?: "Couldn't play this audiobook"
        }
    }

    private suspend fun ensureConnected(): MediaController {
        controller?.let { return it }
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val newController = MediaController.Builder(context, sessionToken).buildAsync().await()
        newController.addListener(listener)
        controller = newController
        return newController
    }

    fun playBook(
        bookId: Long,
        fileUri: String,
        title: String,
        author: String?,
        artworkUri: String?,
        startPositionMs: Long,
        initialSpeed: Float,
    ) {
        scope.launch {
            val mc = ensureConnected()
            if (_currentBookId.value == bookId) {
                if (!mc.isPlaying) mc.play()
                return@launch
            }

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(title)
                .apply { author?.let { setArtist(it) } }
                .apply { artworkUri?.let { setArtworkUri(Uri.parse(it)) } }
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(bookId.toString())
                .setUri(Uri.parse(fileUri))
                .setMediaMetadata(mediaMetadata)
                .build()

            mc.setMediaItem(mediaItem, startPositionMs)
            mc.playbackParameters = PlaybackParameters(initialSpeed)
            mc.prepare()
            mc.play()
            _currentBookId.value = bookId
        }
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun skipForward() {
        controller?.seekForward()
    }

    fun skipBack() {
        controller?.seekBack()
    }

    fun setSpeed(value: Float) {
        controller?.setPlaybackSpeed(value)
    }

    fun seekToChapter(chapter: PlayerChapter) {
        controller?.seekTo(chapter.startMs)
    }

    fun currentPositionMs(): Long = controller?.currentPosition ?: 0L

    fun startSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        sleepTimerJob = scope.launch {
            var remaining = durationMs
            _sleepTimerRemainingMs.value = remaining
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _sleepTimerRemainingMs.value = remaining.coerceAtLeast(0)
            }
            controller?.pause()
            _sleepTimerRemainingMs.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMs.value = null
    }

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = scope.launch {
            while (isActive) {
                _positionMs.value = controller?.currentPosition ?: 0L
                delay(500)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
        _positionMs.value = controller?.currentPosition ?: _positionMs.value
    }

    @OptIn(UnstableApi::class)
    private fun extractChapters(tracks: Tracks): List<PlayerChapter> {
        val result = mutableListOf<PlayerChapter>()
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val metadata = group.getTrackFormat(i).metadata ?: continue
                for (j in 0 until metadata.length()) {
                    val entry = metadata.get(j)
                    if (entry is Chapter) {
                        result += PlayerChapter(
                            index = result.size,
                            title = entry.title?.value ?: "Chapter ${result.size + 1}",
                            startMs = entry.startTimeMs,
                            endMs = entry.endTimeMs,
                        )
                    }
                }
            }
        }
        return result.sortedBy { it.startMs }
    }

    companion object {
        private const val TAG = "PlayerController"
    }
}
