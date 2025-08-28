package xyz.malefic.compose.util

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.toByteArray
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern

@kotlinx.serialization.Serializable
data class SearchResult(
    val title: String,
    val artist: String,
    val duration: String,
    val videoId: String,
    val thumbnail: String,
)

class YtDlpNotInstalledException(
    message: String,
) : Exception(message)

class DownloadManager {
    private val client =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
        }
    private val musicManager = MusicManager()

    suspend fun searchYouTube(query: String): List<SearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode("$query music", "UTF-8")
                val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery"

                val response: HttpResponse =
                    client.get(searchUrl) {
                        headers {
                            append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        }
                    }

                val html = response.bodyAsText()
                parseYouTubeResults(html)
            } catch (e: Exception) {
                println("YouTube search failed: ${e.message}")
                emptyList()
            }
        }

    private fun parseYouTubeResults(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val doc = Jsoup.parse(html)

            // Look for JSON data containing video information
            val scripts = doc.select("script")
            for (script in scripts) {
                val scriptData = script.data()
                if (scriptData.contains("ytInitialData")) {
                    // Extract video data using regex patterns
                    val videoIdPattern = Pattern.compile("\"videoId\":\"([^\"]+)\"")
                    val titlePattern = Pattern.compile("\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"")
                    val durationPattern = Pattern.compile("\"lengthText\":\\{\"simpleText\":\"([^\"]+)\"")

                    val videoIdMatcher = videoIdPattern.matcher(scriptData)
                    val titleMatcher = titlePattern.matcher(scriptData)
                    val durationMatcher = durationPattern.matcher(scriptData)

                    val videoIds = mutableListOf<String>()
                    val titles = mutableListOf<String>()
                    val durations = mutableListOf<String>()

                    while (videoIdMatcher.find() && videoIds.size < 10) {
                        videoIds.add(videoIdMatcher.group(1))
                    }

                    while (titleMatcher.find() && titles.size < 10) {
                        titles.add(titleMatcher.group(1))
                    }

                    while (durationMatcher.find() && durations.size < 10) {
                        durations.add(durationMatcher.group(1))
                    }

                    // Combine the data
                    for (i in 0 until minOf(videoIds.size, titles.size)) {
                        val title = titles[i]
                        val videoId = videoIds[i]
                        val duration = if (i < durations.size) durations[i] else "Unknown"

                        // Try to extract artist from title
                        val (extractedTitle, artist) = extractTitleAndArtist(title)

                        results.add(
                            SearchResult(
                                title = extractedTitle,
                                artist = artist,
                                duration = duration,
                                videoId = videoId,
                                thumbnail = "https://img.youtube.com/vi/$videoId/mqdefault.jpg",
                            ),
                        )
                    }
                    break
                }
            }
        } catch (e: Exception) {
            println("Error parsing YouTube results: ${e.message}")
        }

        return results.take(10) // Limit to 10 results
    }

    private fun extractTitleAndArtist(fullTitle: String): Pair<String, String> {
        // Common patterns: "Artist - Title", "Title by Artist", "Artist: Title"
        return when {
            fullTitle.contains(" - ") -> {
                val parts = fullTitle.split(" - ", limit = 2)
                if (parts.size == 2) {
                    Pair(parts[1].trim(), parts[0].trim())
                } else {
                    Pair(fullTitle, "Unknown Artist")
                }
            }
            fullTitle.contains(" by ") -> {
                val parts = fullTitle.split(" by ", limit = 2)
                if (parts.size == 2) {
                    Pair(parts[0].trim(), parts[1].trim())
                } else {
                    Pair(fullTitle, "Unknown Artist")
                }
            }
            fullTitle.contains(": ") -> {
                val parts = fullTitle.split(": ", limit = 2)
                if (parts.size == 2) {
                    Pair(parts[1].trim(), parts[0].trim())
                } else {
                    Pair(fullTitle, "Unknown Artist")
                }
            }
            else -> Pair(fullTitle, "Unknown Artist")
        }
    }

    suspend fun downloadFromYouTube(searchResult: SearchResult): Track =
        withContext(Dispatchers.IO) {
            try {
                // Use MP3 format for reliable downloads
                val format = "mp3"

                // Use "Title - Artist" format to match the parsing logic in MusicManager
                val fileName = "${searchResult.title} - ${searchResult.artist}".replace("[^a-zA-Z0-9 .-]".toRegex(), "")
                val safeFileName = "$fileName.$format"
                val file = File(musicManager.downloadsDirectory, safeFileName)

                // Download thumbnail as artwork first
                val artworkPath = downloadThumbnail(searchResult)

                // Try to download with yt-dlp - throw exception if not available
                val success = downloadWithYtDlp(file, searchResult, format)

                if (!success) {
                    throw YtDlpNotInstalledException("yt-dlp is not installed. Please install yt-dlp to download music from YouTube.")
                }

                val track =
                    Track(
                        id = UUID.randomUUID().toString(),
                        title = searchResult.title,
                        artist = searchResult.artist,
                        album = "Downloaded from YouTube",
                        filePath = file.absolutePath,
                        duration = parseDuration(searchResult.duration),
                        url = "https://youtube.com/watch?v=${searchResult.videoId}",
                        artworkPath = artworkPath,
                    )

                // Add to All Music playlist
                musicManager.addTrackToAllMusic(track)

                track
            } catch (e: Exception) {
                throw Exception("Failed to download from YouTube: ${e.message}")
            }
        }

    private suspend fun downloadWithYtDlp(
        outputFile: File,
        searchResult: SearchResult,
        format: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Check if yt-dlp is available
                val ytDlpCheck = ProcessBuilder("yt-dlp", "--version").start()
                ytDlpCheck.waitFor()

                if (ytDlpCheck.exitValue() != 0) {
                    println("yt-dlp not found in PATH, trying youtube-dl")

                    // Try youtube-dl as fallback
                    val youtubeDlCheck = ProcessBuilder("youtube-dl", "--version").start()
                    youtubeDlCheck.waitFor()

                    if (youtubeDlCheck.exitValue() != 0) {
                        println("Neither yt-dlp nor youtube-dl found in PATH")
                        return@withContext false
                    }

                    return@withContext downloadWithYoutubeDl(outputFile, searchResult, format)
                }

                // Create the download command for yt-dlp
                val url = "https://youtube.com/watch?v=${searchResult.videoId}"
                val outputTemplate = outputFile.absolutePath.removeSuffix(".${outputFile.extension}")

                val command =
                    listOf(
                        "yt-dlp",
                        "--extract-audio",
                        "--audio-format",
                        format,
                        "--audio-quality",
                        "0", // Best quality
                        "--embed-thumbnail", // Embed artwork
                        "--add-metadata", // Add metadata
                        "--output",
                        "$outputTemplate.%(ext)s",
                        "--no-playlist",
                        url,
                    )

                println("Downloading ${searchResult.title} by ${searchResult.artist} as MP3...")
                println("Command: ${command.joinToString(" ")}")

                val process =
                    ProcessBuilder(command)
                        .apply {
                            redirectErrorStream(true)
                        }.start()

                // Read output for debugging
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    // yt-dlp might change the extension, find the actual downloaded file
                    val downloadedFiles =
                        outputFile.parentFile.listFiles { _, name ->
                            name.startsWith(outputFile.nameWithoutExtension) &&
                                name != outputFile.name &&
                                name.endsWith(".mp3")
                        }

                    if (downloadedFiles?.isNotEmpty() == true) {
                        val actualFile = downloadedFiles.first()
                        // Rename to our expected filename if different
                        if (actualFile.name != outputFile.name) {
                            actualFile.renameTo(outputFile)
                        }
                        println("Successfully downloaded: ${outputFile.name}")
                        return@withContext true
                    }
                }

                println("yt-dlp download failed with exit code: $exitCode")
                println("Output: $output")
                false
            } catch (e: Exception) {
                println("Error running yt-dlp: ${e.message}")
                // If we can't even execute the command, it's likely not installed
                false
            }
        }

    private suspend fun downloadWithYoutubeDl(
        outputFile: File,
        searchResult: SearchResult,
        format: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://youtube.com/watch?v=${searchResult.videoId}"
                val outputTemplate = outputFile.absolutePath.removeSuffix(".${outputFile.extension}")

                val command =
                    listOf(
                        "youtube-dl",
                        "--extract-audio",
                        "--audio-format",
                        format,
                        "--audio-quality",
                        "0", // Best quality
                        "--embed-thumbnail", // Embed artwork
                        "--add-metadata", // Add metadata
                        "--output",
                        "$outputTemplate.%(ext)s",
                        "--no-playlist",
                        url,
                    )

                println("Downloading with youtube-dl: ${searchResult.title} by ${searchResult.artist} as MP3...")

                val process =
                    ProcessBuilder(command)
                        .apply {
                            redirectErrorStream(true)
                        }.start()

                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    val downloadedFiles =
                        outputFile.parentFile.listFiles { _, name ->
                            name.startsWith(outputFile.nameWithoutExtension) &&
                                name != outputFile.name &&
                                name.endsWith(".mp3")
                        }

                    if (downloadedFiles?.isNotEmpty() == true) {
                        val actualFile = downloadedFiles.first()
                        if (actualFile.name != outputFile.name) {
                            actualFile.renameTo(outputFile)
                        }
                        println("Successfully downloaded with youtube-dl: ${outputFile.name}")
                        return@withContext true
                    }
                }

                println("youtube-dl download failed with exit code: $exitCode")
                println("Output: $output")
                false
            } catch (e: Exception) {
                println("Error running youtube-dl: ${e.message}")
                false
            }
        }

    private suspend fun downloadThumbnail(searchResult: SearchResult): String? =
        withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.get(searchResult.thumbnail)
                val artworkDir = File(musicManager.musicDirectory, "Artwork")
                artworkDir.mkdirs()

                val safeFileName = "${searchResult.artist}_${searchResult.title}".replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val artworkFile = File(artworkDir, "$safeFileName.jpg")

                val channel: ByteReadChannel = response.bodyAsChannel()
                val bytes = channel.toByteArray()
                artworkFile.writeBytes(bytes)

                artworkFile.absolutePath
            } catch (e: Exception) {
                println("Failed to download thumbnail: ${e.message}")
                null
            }
        }

    private fun parseDuration(durationStr: String): Long =
        try {
            val parts = durationStr.split(":")
            when (parts.size) {
                1 -> parts[0].toLongOrNull() ?: 0L
                2 -> (parts[0].toLong() * 60) + parts[1].toLong()
                3 -> (parts[0].toLong() * 3600) + (parts[1].toLong() * 60) + parts[2].toLong()
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }

    suspend fun downloadTrack(url: String): Track =
        withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.get(url)
                val fileName = extractFileName(url) ?: "download_${UUID.randomUUID()}.mp3"
                val file = File(musicManager.downloadsDirectory, fileName)

                val channel: ByteReadChannel = response.bodyAsChannel()
                val bytes = channel.toByteArray()
                file.writeBytes(bytes)

                val track =
                    Track(
                        id = UUID.randomUUID().toString(),
                        title = file.nameWithoutExtension,
                        artist = "Downloaded",
                        filePath = file.absolutePath,
                        url = url,
                    )

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

    fun close() {
        client.close()
    }
}
