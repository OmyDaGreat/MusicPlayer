package xyz.malefic.compose.util

import kotlinx.serialization.Serializable

/**
 * Represents a music track with all its metadata
 */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val filePath: String,
    val duration: Long = 0L, // Duration in seconds
    val year: String = "",
    val genre: String = "",
    val trackNumber: Int = 0,
    val url: String? = null, // For downloaded tracks
    val albumArt: String? = null, // Path to album art file
    val bitrate: String = "",
    val sampleRate: String = "",
    val channels: String = "",
    val fileSize: Long = 0L, // File size in bytes
    val format: String = "", // File format (mp3, flac, etc.)
    val addedDate: Long = System.currentTimeMillis() // When track was added to library
)