package xyz.malefic.compose.util

/**
 * Represents a music track with its metadata
 */
data class SimpleTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val filePath: String,
    val duration: Long = 0L, // Duration in seconds
    val year: String = "",
    val genre: String = "",
    val trackNumber: Int = 0,
    val format: String = "",
    val fileSize: Long = 0L
)