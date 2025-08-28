package xyz.malefic.compose.util

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.sound.sampled.*

/**
 * Simple audio player service using Java Sound API
 * Supports basic audio formats (WAV, AU, AIFF)
 * For MP3, FLAC, Opus, M4A support, additional libraries would be needed
 */
class SimpleAudioPlayer {
    private var clip: Clip? = null
    private var audioInputStream: AudioInputStream? = null
    
    // State variables for UI updates
    private var _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    private var _currentPosition = mutableStateOf(0L)
    val currentPosition: State<Long> = _currentPosition

    private var _duration = mutableStateOf(0L)
    val duration: State<Long> = _duration

    private var _currentTrack = mutableStateOf<SimpleTrack?>(null)
    val currentTrack: State<SimpleTrack?> = _currentTrack

    suspend fun play(track: SimpleTrack) = withContext(Dispatchers.IO) {
        try {
            stop() // Stop any currently playing track
            
            val file = File(track.filePath)
            if (!file.exists()) {
                println("File not found: ${track.filePath}")
                return@withContext
            }

            // Check if format is supported by Java Sound API
            val format = track.format.lowercase()
            if (format !in setOf("wav", "au", "aiff")) {
                println("Format $format not supported by basic Java Sound API")
                println("For MP3, FLAC, Opus, M4A support, additional libraries needed")
                return@withContext
            }

            audioInputStream = AudioSystem.getAudioInputStream(file)
            clip = AudioSystem.getClip()
            
            clip?.let { c ->
                c.open(audioInputStream)
                _duration.value = c.microsecondLength / 1_000_000 // Convert to seconds
                _currentTrack.value = track
                c.start()
                _isPlaying.value = true
                
                // Start position tracking
                startPositionTracking()
                
                println("Playing: ${track.title} by ${track.artist}")
            }
        } catch (e: Exception) {
            println("Error playing track: ${e.message}")
            e.printStackTrace()
        }
    }

    fun pause() {
        clip?.let { c ->
            if (c.isRunning) {
                c.stop()
                _isPlaying.value = false
                println("Paused")
            }
        }
    }

    fun resume() {
        clip?.let { c ->
            if (!c.isRunning && c.isOpen) {
                c.start()
                _isPlaying.value = true
                startPositionTracking()
                println("Resumed")
            }
        }
    }

    fun stop() {
        clip?.let { c ->
            c.stop()
            c.close()
        }
        audioInputStream?.close()
        clip = null
        audioInputStream = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _currentTrack.value = null
        println("Stopped")
    }

    fun seek(positionSeconds: Long) {
        clip?.let { c ->
            val positionMicroseconds = positionSeconds * 1_000_000
            if (positionMicroseconds <= c.microsecondLength) {
                c.microsecondPosition = positionMicroseconds
                _currentPosition.value = positionSeconds
            }
        }
    }

    private fun startPositionTracking() {
        // Simple position tracking - in a real app you'd use a timer/coroutine
        Thread {
            while (_isPlaying.value && clip?.isRunning == true) {
                clip?.let { c ->
                    _currentPosition.value = c.microsecondPosition / 1_000_000
                }
                Thread.sleep(1000) // Update every second
            }
        }.start()
    }

    fun cleanup() {
        stop()
    }
}