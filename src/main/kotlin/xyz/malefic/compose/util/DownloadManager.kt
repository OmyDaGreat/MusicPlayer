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
import java.net.URL
import java.util.*

class DownloadManager {
    private val client = HttpClient(CIO)
    private val musicManager = MusicManager()

    suspend fun downloadTrack(url: String): Track =
        withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.get(url)
                val fileName = extractFileName(url) ?: "download_${UUID.randomUUID()}.mp3"
                val file = File(musicManager.downloadsDirectory, fileName)

                val channel: ByteReadChannel = response.bodyAsChannel()
                val bytes = channel.toByteArray()
                file.writeBytes(bytes)

                Track(
                    id = UUID.randomUUID().toString(),
                    title = file.nameWithoutExtension,
                    artist = "Downloaded",
                    filePath = file.absolutePath,
                    url = url,
                )
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
}
