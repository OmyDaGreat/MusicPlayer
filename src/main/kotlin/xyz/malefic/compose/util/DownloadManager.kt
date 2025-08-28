package xyz.malefic.compose.util

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.toByteArray
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.*

class DownloadManager {
    private val client = HttpClient(CIO)
    private val musicManager = MusicManager()

    /**
     * Search for music from various sources
     * Note: This is a simplified implementation. In a real app, you would need:
     * - YouTube API integration
     * - Spotify API integration (though Spotify doesn't allow direct downloads)
     * - Proper authentication and API keys
     * - Legal compliance with terms of service
     */
    suspend fun searchMusic(query: String, source: String = "youtube"): List<SearchResult> =
        withContext(Dispatchers.IO) {
            try {
                when (source.lowercase()) {
                    "youtube" -> searchYouTube(query)
                    "spotify" -> searchSpotify(query)
                    else -> emptyList()
                }
            } catch (e: Exception) {
                println("Error searching music: ${e.message}")
                emptyList()
            }
        }

    private suspend fun searchYouTube(query: String): List<SearchResult> {
        // TODO: Implement YouTube API search
        // For now, return mock results
        return listOf(
            SearchResult(
                title = "Sample Track - $query",
                artist = "Sample Artist",
                album = "Sample Album",
                duration = "3:45",
                source = "youtube",
                sourceId = "sample_id_${UUID.randomUUID()}",
                quality = "high"
            )
        )
    }

    private suspend fun searchSpotify(query: String): List<SearchResult> {
        // TODO: Implement Spotify API search
        // Note: Spotify doesn't allow downloads, this would be for metadata only
        return listOf(
            SearchResult(
                title = "Spotify Track - $query",
                artist = "Spotify Artist",
                album = "Spotify Album",
                duration = "4:12",
                source = "spotify",
                sourceId = "spotify_id_${UUID.randomUUID()}",
                quality = "high"
            )
        )
    }

    /**
     * Download a track from a direct URL
     * Note: This is for educational purposes only. 
     * In a production app, ensure you have proper rights and permissions
     */
    suspend fun downloadTrack(
        searchResult: SearchResult,
        onProgress: (Int) -> Unit = {}
    ): Track =
        withContext(Dispatchers.IO) {
            try {
                // For demo purposes, we'll create a placeholder download
                // In reality, you'd need to:
                // 1. Get actual download URL from YouTube/other APIs
                // 2. Handle authentication
                // 3. Respect rate limits and terms of service
                
                val fileName = "${searchResult.artist} - ${searchResult.title}.${getFileExtension(searchResult.quality)}"
                val sanitizedFileName = sanitizeFileName(fileName)
                val file = File(musicManager.downloadsDirectory, sanitizedFileName)

                // Simulate download progress
                for (i in 0..100 step 10) {
                    onProgress(i)
                    kotlinx.coroutines.delay(100)
                }

                // Create a placeholder file (in reality, this would be the actual download)
                if (!file.exists()) {
                    file.createNewFile()
                    file.writeText("This is a placeholder for downloaded content from ${searchResult.source}")
                }

                val track = Track(
                    id = UUID.randomUUID().toString(),
                    title = searchResult.title,
                    artist = searchResult.artist,
                    album = searchResult.album,
                    filePath = file.absolutePath,
                    url = searchResult.downloadUrl,
                    format = getFileExtension(searchResult.quality),
                    fileSize = file.length(),
                    addedDate = System.currentTimeMillis()
                )

                // Add to All Music playlist
                musicManager.addTrackToAllMusic(track)

                track
            } catch (e: Exception) {
                throw Exception("Failed to download: ${e.message}")
            }
        }

    /**
     * Download track from direct URL
     */
    suspend fun downloadFromUrl(
        url: String,
        filename: String? = null,
        onProgress: (Int) -> Unit = {}
    ): Track =
        withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.get(url)
                val fileName = filename ?: extractFileName(url) ?: "download_${UUID.randomUUID()}.mp3"
                val sanitizedFileName = sanitizeFileName(fileName)
                val file = File(musicManager.downloadsDirectory, sanitizedFileName)

                val channel: ByteReadChannel = response.bodyAsChannel()
                val totalSize = response.headers["Content-Length"]?.toLongOrNull() ?: 0L
                
                var downloadedBytes = 0L
                val buffer = ByteArray(8192)
                
                file.outputStream().use { output ->
                    while (!channel.isClosedForRead) {
                        val packet = channel.readAvailable(buffer)
                        if (packet == 0) break
                        output.write(buffer, 0, packet)
                        downloadedBytes += packet
                        
                        if (totalSize > 0) {
                            onProgress((downloadedBytes * 100 / totalSize).toInt())
                        }
                    }
                }

                // Extract metadata from downloaded file
                val track = try {
                    musicManager.extractMetadata(file)
                } catch (e: Exception) {
                    Track(
                        id = UUID.randomUUID().toString(),
                        title = file.nameWithoutExtension,
                        artist = "Downloaded",
                        album = "Downloads",
                        filePath = file.absolutePath,
                        url = url,
                        fileSize = file.length(),
                        format = file.extension.lowercase()
                    )
                }

                // Add to All Music playlist
                musicManager.addTrackToAllMusic(track)

                track
            } catch (e: Exception) {
                throw Exception("Failed to download: ${e.message}")
            }
        }

    private fun extractFileName(url: String): String? =
        try {
            val urlObj = URI(url).toURL()
            val path = urlObj.path
            if (path.isNotEmpty() && path.contains("/")) {
                path.substringAfterLast("/")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
    }

    private fun getFileExtension(quality: String): String {
        return when (quality.lowercase()) {
            "high" -> "flac"
            "medium" -> "mp3"
            "low" -> "mp3"
            else -> "mp3"
        }
    }

    fun cleanup() {
        client.close()
    }
}