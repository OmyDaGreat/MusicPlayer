package xyz.malefic.compose.util

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import javax.sound.sampled.*
import kotlin.random.Random

class ModernMusicPlayerService {
    private var audioInputStream: AudioInputStream? = null
    private var clip: Clip? = null

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
        // Start position monitoring
        GlobalScope.launch {
            monitorPosition()
        }
    }

    private suspend fun monitorPosition() {
        while (!isDisposed) {
            clip?.let { c ->
                if (c.isRunning) {
                    val framePosition = c.framePosition
                    val frameRate = c.format.frameRate
                    if (frameRate > 0) {
                        _currentPosition.value = (framePosition / frameRate).toLong()
                    }

                    // Check if playback is finished
                    if (!c.isRunning && _isPlaying.value) {
                        _isPlaying.value = false
                        handleEndOfMedia()
                    }
                }
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

                    // Try to load the audio file
                    val fileInputStream = FileInputStream(file)
                    val bufferedInputStream = BufferedInputStream(fileInputStream)

                    try {
                        audioInputStream = AudioSystem.getAudioInputStream(bufferedInputStream)
                        val format = audioInputStream!!.format

                        // Convert to PCM format if needed
                        val decodedFormat =
                            AudioFormat(
                                AudioFormat.Encoding.PCM_SIGNED,
                                format.sampleRate,
                                16,
                                format.channels,
                                format.channels * 2,
                                format.sampleRate,
                                false,
                            )

                        val decodedStream = AudioSystem.getAudioInputStream(decodedFormat, audioInputStream!!)

                        clip = AudioSystem.getClip()
                        clip!!.open(decodedStream)

                        // Calculate duration
                        val frameLength = clip!!.frameLength
                        val frameRate = clip!!.format.frameRate
                        _duration.value = if (frameRate > 0) (frameLength / frameRate).toLong() else 0L

                        // Set volume
                        setVolumeInternal(_volume.value)

                        _currentTrack.value = track
                        clip!!.start()
                        _isPlaying.value = true

                        println("Playing: ${track.title} by ${track.artist}")
                    } catch (e: UnsupportedAudioFileException) {
                        println("Unsupported audio format for ${track.title}: ${e.message}")
                        // Try fallback for unsupported formats
                        tryFallbackPlay(file, track)
                    } catch (e: Exception) {
                        println("Error creating audio stream for ${track.title}: ${e.message}")
                        e.printStackTrace()
                    }
                } else {
                    println("File does not exist: ${track.filePath}")
                }
            } catch (e: Exception) {
                println("Error playing track: ${e.message}")
                e.printStackTrace()
            }
        }

    private fun tryFallbackPlay(
        file: File,
        track: Track,
    ) {
        // Advanced fallback handling for unsupported formats
        val extension = file.extension.lowercase()

        when (extension) {
            "opus" -> {
                println("Opus format detected but not supported by Java Sound API.")
                println("Opus files require native codec support or external conversion.")
            }
            "m4a", "mp4", "aac" -> {
                println("M4A/AAC format detected but may have limited support.")
                println("Some M4A files may require additional codecs or external conversion.")
            }
            else -> {
                println("Format not supported natively: $extension. Consider using an external converter.")
            }
        }

        // Set track as current but not playing
        _currentTrack.value = track
        _duration.value = track.duration
        _isPlaying.value = false

        // Show helpful message to user
        println("Track '${track.title}' loaded but cannot be played due to format limitations.")
        println("Supported formats: MP3, WAV, FLAC, OGG (Vorbis)")
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
            clip?.let { c ->
                if (c.isRunning) {
                    c.stop()
                    _isPlaying.value = false
                }
            }
        } catch (e: Exception) {
            println("Error pausing: ${e.message}")
        }
    }

    fun resume() {
        try {
            clip?.let { c ->
                if (!c.isRunning && c.framePosition < c.frameLength) {
                    c.start()
                    _isPlaying.value = true
                }
            }
        } catch (e: Exception) {
            println("Error resuming: ${e.message}")
        }
    }

    fun stop() {
        try {
            clip?.let { c ->
                if (c.isOpen) {
                    c.stop()
                    c.close()
                }
            }
            audioInputStream?.close()
            clip = null
            audioInputStream = null
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
            clip?.let { c ->
                if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    val gainControl = c.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                    val range = gainControl.maximum - gainControl.minimum
                    val gain = (range * volume) + gainControl.minimum
                    gainControl.value = gain
                }
            }
        } catch (e: Exception) {
            println("Error setting volume: ${e.message}")
        }
    }

    fun seek(positionSeconds: Long) {
        try {
            clip?.let { c ->
                val frameRate = c.format.frameRate
                if (frameRate > 0) {
                    val framePosition = (positionSeconds * frameRate).toLong()
                    if (framePosition >= 0 && framePosition < c.frameLength) {
                        c.framePosition = framePosition.toInt()
                        _currentPosition.value = positionSeconds
                    }
                }
            }
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
                    GlobalScope.launch {
                        play(nextTrack)
                    }
                } else if (_repeatMode.value == RepeatMode.ALL && currentPlaylist.isNotEmpty()) {
                    // Restart playlist
                    GlobalScope.launch {
                        play(currentPlaylist.first())
                    }
                }
            }
        }
    }

    fun dispose() {
        isDisposed = true
        stop()
    }
}
