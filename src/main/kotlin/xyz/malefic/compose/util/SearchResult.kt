package xyz.malefic.compose.util

import kotlinx.serialization.Serializable

/**
 * Represents search results from music services
 */
@Serializable
data class SearchResult(
    val title: String,
    val artist: String,
    val album: String = "",
    val duration: String = "",
    val thumbnailUrl: String = "",
    val downloadUrl: String = "",
    val source: String = "", // "youtube", "spotify", etc.
    val sourceId: String = "", // Video ID, track ID, etc.
    val quality: String = "" // "high", "medium", "low"
)