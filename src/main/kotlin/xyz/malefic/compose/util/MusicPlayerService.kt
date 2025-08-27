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

    init {
        player.addBasicPlayerListener(this)
    }

    suspend fun play(track: Track) =
        withContext(Dispatchers.IO) {
            try {
                val file = File(track.filePath)
                if (file.exists()) {
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

    fun stop() {
        try {
            player.stop()
        } catch (e: Exception) {
            println("Error stopping: ${e.message}")
        }
    }

    fun nextTrack(
        playlist: List<Track>,
        current: Track?,
    ): Track? {
        if (playlist.isEmpty()) return null
        val currentIndex = playlist.indexOf(current)
        return if (currentIndex >= 0 && currentIndex < playlist.size - 1) {
            playlist[currentIndex + 1]
        } else {
            playlist.firstOrNull()
        }
    }

    fun previousTrack(
        playlist: List<Track>,
        current: Track?,
    ): Track? {
        if (playlist.isEmpty()) return null
        val currentIndex = playlist.indexOf(current)
        return if (currentIndex > 0) {
            playlist[currentIndex - 1]
        } else {
            playlist.lastOrNull()
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
                    println("End of media reached")
                }
                BasicPlayerEvent.UNKNOWN -> println("Unknown player event")
            }
        }
    }

    override fun setController(controller: BasicController?) {
        println("Controller set: $controller")
    }
}
