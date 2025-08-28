package xyz.malefic.compose.util

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent
import java.io.File
import kotlin.random.Random

class ModernMusicPlayerService {
    private var audioPlayer: AudioPlayerComponent? = null
    private var mediaPlayer: MediaPlayer? = null

    // State variables for UI updates
    private var _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    private var _currentPosition = mutableStateOf(0L)
    val currentPosition: State<Long> = _currentPosition

    private var _duration = mutableStateOf(0L)
    val duration: State<Long> = _duration

    private var _volume = mutableStateOf(1.0f)
    val volume: State<Float> = _volume

    private var _isShuffleEnabled = mutableStateOf(false)
    val isShuffleEnabled: State<Boolean> = _isShuffleEnabled

    private var _repeatMode = mutableStateOf(RepeatMode.OFF)
    val repeatMode: State<RepeatMode> = _repeatMode

    private var _currentTrack = mutableStateOf<Track?>(null)
    val currentTrack: State<Track?> = _currentTrack

    private var shuffledPlaylist: List<Track> = emptyList()
    private var currentPlaylist: List<Track> = emptyList()
    private var isDisposed = false

    init {
        try {
            initializeVlcPlayer()
            startPositionMonitoring()
        } catch (e: Exception) {
            println("Error initializing VLCJ player: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun initializeVlcPlayer() {
        audioPlayer = AudioPlayerComponent()
        mediaPlayer = audioPlayer?.mediaPlayer()

        mediaPlayer?.events()?.addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    _isPlaying.value = true
                    // Get duration when media starts playing
                    val durationMs = mediaPlayer.status().length()
                    _duration.value = durationMs / 1000
                    println("Playing: ${_currentTrack.value?.title} - Duration: ${_duration.value}s")
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    _isPlaying.value = false
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    _isPlaying.value = false
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    _isPlaying.value = false
                    handleEndOfMedia()
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    _isPlaying.value = false
                    println("VLC Media Player error occurred")
                }

                override fun timeChanged(
                    mediaPlayer: MediaPlayer,
                    newTime: Long,
                ) {
                    _currentPosition.value = newTime / 1000
                }
            },
        )
    }

    private fun startPositionMonitoring() {
        GlobalScope.launch {
            monitorPosition()
        }
    }

    private suspend fun monitorPosition() {
        while (!isDisposed) {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.status().isPlaying) {
                        val timeMs = mp.status().time()
                        _currentPosition.value = timeMs / 1000
                    }
                }
            } catch (e: Exception) {
                // Ignore errors during monitoring
            }
            delay(100) // Update every 100ms
        }
    }

    suspend fun play(track: Track) =
        withContext(Dispatchers.IO) {
            try {
                val file = File(track.filePath)
                if (file.exists()) {
                    stop() // Stop current playback

                    _currentTrack.value = track
                    _duration.value = track.duration

                    // Use VLCJ to play the media file
                    val success = mediaPlayer?.media()?.play(file.absolutePath) ?: false
                    if (success) {
                        // Set initial volume
                        setVolumeInternal(_volume.value)
                        println("Successfully loaded: ${track.title} by ${track.artist}")
                        println("File format: ${file.extension} - Full path: ${file.absolutePath}")
                    } else {
                        println("Failed to load media file: ${file.absolutePath}")
                    }
                } else {
                    println("File does not exist: ${track.filePath}")
                }
            } catch (e: Exception) {
                println("Error playing track: ${e.message}")
                e.printStackTrace()
            }
        }

    suspend fun playWithPlaylist(
        track: Track,
        playlist: List<Track>,
    ) = withContext(Dispatchers.IO) {
        try {
            currentPlaylist = playlist
            updateShuffledPlaylist()
            play(track)
        } catch (e: Exception) {
            println("Error playing track with playlist: ${e.message}")
        }
    }

    fun pause() {
        try {
            mediaPlayer?.controls()?.pause()
        } catch (e: Exception) {
            println("Error pausing: ${e.message}")
        }
    }

    fun resume() {
        try {
            mediaPlayer?.controls()?.play()
        } catch (e: Exception) {
            println("Error resuming: ${e.message}")
        }
    }

    fun stop() {
        try {
            mediaPlayer?.controls()?.stop()
            _isPlaying.value = false
            _currentPosition.value = 0L
        } catch (e: Exception) {
            println("Error stopping: ${e.message}")
        }
    }

    fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0f, 1f)
        setVolumeInternal(_volume.value)
    }

    private fun setVolumeInternal(volume: Float) {
        try {
            val vlcVolume = (volume * 100).toInt().coerceIn(0, 100)
            mediaPlayer?.audio()?.setVolume(vlcVolume)
        } catch (e: Exception) {
            println("Error setting volume: ${e.message}")
        }
    }

    fun seek(positionSeconds: Long) {
        try {
            val positionMs = positionSeconds * 1000
            mediaPlayer?.controls()?.setTime(positionMs)
            _currentPosition.value = positionSeconds
        } catch (e: Exception) {
            println("Error seeking: ${e.message}")
        }
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        updateShuffledPlaylist()
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
    }

    fun toggleRepeatMode() {
        _repeatMode.value =
            when (_repeatMode.value) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
    }

    private fun updateShuffledPlaylist() {
        shuffledPlaylist =
            if (_isShuffleEnabled.value) {
                currentPlaylist.shuffled(Random.Default)
            } else {
                currentPlaylist
            }
    }

    fun nextTrack(): Track? =
        when (_repeatMode.value) {
            RepeatMode.ONE -> _currentTrack.value
            RepeatMode.ALL, RepeatMode.OFF -> {
                val playlist = if (_isShuffleEnabled.value) shuffledPlaylist else currentPlaylist
                getNextTrack(playlist, _currentTrack.value)
            }
        }

    fun previousTrack(): Track? =
        when (_repeatMode.value) {
            RepeatMode.ONE -> _currentTrack.value
            RepeatMode.ALL, RepeatMode.OFF -> {
                val playlist = if (_isShuffleEnabled.value) shuffledPlaylist else currentPlaylist
                getPreviousTrack(playlist, _currentTrack.value)
            }
        }

    private fun getNextTrack(
        playlist: List<Track>,
        current: Track?,
    ): Track? {
        if (playlist.isEmpty()) return null
        val currentIndex = playlist.indexOf(current)
        return if (currentIndex >= 0 && currentIndex < playlist.size - 1) {
            playlist[currentIndex + 1]
        } else if (_repeatMode.value == RepeatMode.ALL) {
            playlist.firstOrNull()
        } else {
            null
        }
    }

    private fun getPreviousTrack(
        playlist: List<Track>,
        current: Track?,
    ): Track? {
        if (playlist.isEmpty()) return null
        val currentIndex = playlist.indexOf(current)
        return if (currentIndex > 0) {
            playlist[currentIndex - 1]
        } else if (_repeatMode.value == RepeatMode.ALL) {
            playlist.lastOrNull()
        } else {
            null
        }
    }

    private fun handleEndOfMedia() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                // Repeat current track
                _currentTrack.value?.let { track ->
                    GlobalScope.launch {
                        play(track)
                    }
                }
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                // Play next track
                val nextTrack = nextTrack()
                if (nextTrack != null) {
                    _currentTrack.value = nextTrack // Update the current track state
                    GlobalScope.launch {
                        play(nextTrack)
                    }
                } else if (_repeatMode.value == RepeatMode.ALL && currentPlaylist.isNotEmpty()) {
                    // Restart playlist
                    val firstTrack = currentPlaylist.first()
                    _currentTrack.value = firstTrack // Update the current track state
                    GlobalScope.launch {
                        play(firstTrack)
                    }
                }
            }
        }
    }

    fun dispose() {
        isDisposed = true
        try {
            stop()
            mediaPlayer?.release()
            audioPlayer?.release()
        } catch (e: Exception) {
            println("Error disposing player: ${e.message}")
        }
    }
}
