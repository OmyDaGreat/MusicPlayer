package xyz.malefic.compose.util

import kotlinx.serialization.Serializable

/**
 * Represents a playlist containing a collection of tracks
 */
@Serializable
data class Playlist(
    val id: String = "",
    val name: String,
    val tracks: List<Track> = emptyList(),
    val description: String = "",
    val createdDate: Long = System.currentTimeMillis(),
    val modifiedDate: Long = System.currentTimeMillis(),
    val isSystemPlaylist: Boolean = false // For "All Music", "Favorites", etc.
)