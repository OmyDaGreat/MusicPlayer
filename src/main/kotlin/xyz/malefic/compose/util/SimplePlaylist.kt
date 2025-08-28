package xyz.malefic.compose.util

/**
 * Represents a playlist containing tracks
 */
data class SimplePlaylist(
    val id: String,
    val name: String,
    val tracks: List<SimpleTrack> = emptyList(),
    val isSystemPlaylist: Boolean = false
)