package xyz.malefic.compose.util

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import javazoom.jlgui.basicplayer.BasicController
import javazoom.jlgui.basicplayer.BasicPlayer
import javazoom.jlgui.basicplayer.BasicPlayerEvent
import javazoom.jlgui.basicplayer.BasicPlayerListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

    private var _currentTrack = mutableStateOf<Track?>(null)
    val currentTrack: State<Track?> = _currentTrack

    private var _volume = mutableStateOf(0.8f) // 0.0 to 1.0
    val volume: State<Float> = _volume

    private var _isShuffleEnabled = mutableStateOf(false)
    val isShuffleEnabled: State<Boolean> = _isShuffleEnabled

    private var _repeatMode = mutableStateOf(RepeatMode.OFF)
    val repeatMode: State<RepeatMode> = _repeatMode

    enum class RepeatMode {
        OFF, ONE, ALL
    }

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
            val gain = volume.coerceIn(0f, 1f)
            _volume.value = gain
            player.setGain(gain.toDouble())
        } catch (e: Exception) {
            println("Error setting volume: ${e.message}")
        }
    }

    fun seek(positionMs: Long) {
        try {
            val positionBytes = (positionMs * 1000).toInt() // Convert to microseconds
            player.seek(positionBytes.toLong())
        } catch (e: Exception) {
            println("Error seeking: ${e.message}")
        }
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.OFF
        }
    }

    fun nextTrack(
        playlist: List<Track>,
        current: Track?,
    ): Track? {
        if (playlist.isEmpty()) return null
        
        return when {
            _isShuffleEnabled.value -> playlist.randomOrNull()
            else -> {
                val currentIndex = playlist.indexOf(current)
                if (currentIndex >= 0 && currentIndex < playlist.size - 1) {
                    playlist[currentIndex + 1]
                } else {
                    when (_repeatMode.value) {
                        RepeatMode.ALL -> playlist.firstOrNull()
                        else -> null
                    }
                }
            }
        }
    }

    fun previousTrack(
        playlist: List<Track>,
        current: Track?,
    ): Track? {
        if (playlist.isEmpty()) return null
        
        return when {
            _isShuffleEnabled.value -> playlist.randomOrNull()
            else -> {
                val currentIndex = playlist.indexOf(current)
                if (currentIndex > 0) {
                    playlist[currentIndex - 1]
                } else {
                    when (_repeatMode.value) {
                        RepeatMode.ALL -> playlist.lastOrNull()
                        else -> null
                    }
                }
            }
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
                _duration.value = dur / 1000000 // Convert microseconds to seconds
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
        _currentPosition.value = microseconds / 1000000 // Convert to seconds
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
                    println("End of media reached")
                    
                    // Handle auto-play next track based on repeat mode
                    when (_repeatMode.value) {
                        RepeatMode.ONE -> {
                            _currentTrack.value?.let { track ->
                                try {
                                    val file = File(track.filePath)
                                    if (file.exists()) {
                                        player.open(file)
                                        player.play()
                                    }
                                } catch (e: Exception) {
                                    println("Error repeating track: ${e.message}")
                                }
                            }
                        }
                        else -> {
                            // Handle next track - this would need to be managed by the UI layer
                            // since we don't have access to the current playlist here
                            println("Track ended - next track should be handled by UI")
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

    fun cleanup() {
        try {
            player.stop()
        } catch (e: Exception) {
            println("Error during cleanup: ${e.message}")
        }
    }
}