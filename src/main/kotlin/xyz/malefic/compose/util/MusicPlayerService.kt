package xyz.malefic.compose.util

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import javazoom.jlgui.basicplayer.BasicController
import javazoom.jlgui.basicplayer.BasicPlayer
import javazoom.jlgui.basicplayer.BasicPlayerEvent
import javazoom.jlgui.basicplayer.BasicPlayerListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

class MusicPlayerService : BasicPlayerListener {
    private val player = BasicPlayer()

    // State variables for UI updates
    private var _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    private var _currentPosition = mutableStateOf(0L)
    val currentPosition: State<Long> = _currentPosition

    private var _duration = mutableStateOf(0L)
    val duration: State<Long> = _duration

    private var _currentTrackInfo = mutableStateOf<Map<String, Any>>(emptyMap())
    val currentTrackInfo: State<Map<String, Any>> = _currentTrackInfo

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

    init {
        player.addBasicPlayerListener(this)
    }

    suspend fun play(track: Track) =
        withContext(Dispatchers.IO) {
            try {
                val file = File(track.filePath)
                if (file.exists()) {
                    _currentTrack.value = track
                    player.open(file)
                    player.play()
                }
            } catch (e: Exception) {
                println("Error playing track: ${e.message}")
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
            player.pause()
        } catch (e: Exception) {
            println("Error pausing: ${e.message}")
        }
    }

    fun resume() {
        try {
            player.resume()
        } catch (e: Exception) {
            println("Error resuming: ${e.message}")
        }
    }

    fun stop() {
        try {
            player.stop()
            _currentTrack.value = null
        } catch (e: Exception) {
            println("Error stopping: ${e.message}")
        }
    }

    fun setVolume(volume: Float) {
        try {
            val clampedVolume = volume.coerceIn(0f, 1f)
            _volume.value = clampedVolume
            val gain = clampedVolume.toDouble()
            player.setGain(gain)
        } catch (e: Exception) {
            println("Error setting volume: ${e.message}")
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

    fun seek(positionSeconds: Long) {
        try {
            // BasicPlayer doesn't support seeking directly
            // This is a limitation of the current audio library
            println("Seek functionality not supported by BasicPlayer")
        } catch (e: Exception) {
            println("Error seeking: ${e.message}")
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

    // BasicPlayerListener implementation
    override fun opened(
        stream: Any,
        properties: MutableMap<Any?, Any?>,
    ) {
        println("Track opened: $stream")
        properties.let { props ->
            _currentTrackInfo.value = props.toMap() as Map<String, Any>
            (props["duration"] as? Long)?.let { dur ->
                _duration.value = dur / 1000
            }
            val bitrate = props["bitrate"] ?: "Unknown"
            val channels = props["audio.channels"] ?: "Unknown"
            val sampleRate = props["audio.samplerate.hz"] ?: "Unknown"
            println("Audio info - Bitrate: $bitrate, Channels: $channels, Sample Rate: $sampleRate")
        }
    }

    override fun progress(
        bytesread: Int,
        microseconds: Long,
        pcmdata: ByteArray,
        properties: MutableMap<Any?, Any?>,
    ) {
        _currentPosition.value = microseconds / 1000000
    }

    override fun stateUpdated(event: BasicPlayerEvent?) {
        event?.let { e ->
            when (e.code) {
                BasicPlayerEvent.OPENED -> println("Player opened")
                BasicPlayerEvent.PLAYING -> {
                    _isPlaying.value = true
                    println("Player started playing")
                }
                BasicPlayerEvent.PAUSED -> {
                    _isPlaying.value = false
                    println("Player paused")
                }
                BasicPlayerEvent.RESUMED -> {
                    _isPlaying.value = true
                    println("Player resumed")
                }
                BasicPlayerEvent.STOPPED -> {
                    _isPlaying.value = false
                    _currentPosition.value = 0L
                    println("Player stopped")
                }
                BasicPlayerEvent.EOM -> {
                    _isPlaying.value = false
                    println("End of media reached - playing next track")
                    // Auto-play next track if not in repeat one mode
                    if (_repeatMode.value != RepeatMode.ONE) {
                        val nextTrack = nextTrack()
                        nextTrack?.let { track ->
                            GlobalScope.launch {
                                play(track)
                            }
                        }
                    } else {
                        // Repeat current track
                        _currentTrack.value?.let { track ->
                            GlobalScope.launch {
                                play(track)
                            }
                        }
                    }
                }
                BasicPlayerEvent.UNKNOWN -> println("Unknown player event")
            }
        }
    }

    override fun setController(controller: BasicController?) {
        println("Controller set: $controller")
    }
}
