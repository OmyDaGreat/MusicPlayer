package xyz.malefic.compose.util

import kotlinx.serialization.Serializable

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "Unknown Album",
    val filePath: String,
    val duration: Long = 0L, // in seconds
    val year: String = "",
    val genre: String = "",
    val trackNumber: Int = 0,
    val url: String? = null,
    val artworkPath: String? = null, // Path to extracted album artwork
)

@Serializable
data class Playlist(
    val name: String,
    val tracks: List<Track>,
    val created: Long = System.currentTimeMillis(),
)
