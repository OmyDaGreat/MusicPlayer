package xyz.malefic.compose.util

import kotlinx.serialization.Serializable

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
)

@Serializable
data class Playlist(
    val name: String,
    val tracks: List<Track>,
    val created: Long = System.currentTimeMillis(),
)
