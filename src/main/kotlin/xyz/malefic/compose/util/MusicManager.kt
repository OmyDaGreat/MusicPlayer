package xyz.malefic.compose.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.imageio.ImageIO

class MusicManager {
    private val json = Json { prettyPrint = true }

    val musicDirectory: File by lazy {
        File(System.getProperty("user.home"), "Music")
    }

    private val playlistsFile: File by lazy {
        File(musicDirectory, "playlists.json")
    }

    val downloadsDirectory: File by lazy {
        File(musicDirectory, "Downloads").apply { mkdirs() }
    }

    private val artworkDirectory: File by lazy {
        File(musicDirectory, "Artwork").apply { mkdirs() }
    }

    suspend fun initializeMusicDirectory() =
        withContext(Dispatchers.IO) {
            musicDirectory.mkdirs()
            downloadsDirectory.mkdirs()
            artworkDirectory.mkdirs()

            if (!playlistsFile.exists()) {
                val defaultPlaylists =
                    listOf(
                        Playlist("All Music", emptyList()),
                        Playlist("Favorites", emptyList()),
                    )
                savePlaylist(defaultPlaylists)
            }

            // Scan for existing music
            scanMusicDirectory()
        }

    suspend fun scanMusicDirectory(): List<Track> =
        withContext(Dispatchers.IO) {
            // Enhanced format support including modern codecs
            val audioExtensions =
                setOf(
                    "mp3",
                    "wav",
                    "flac",
                    "au",
                    "aiff",
                    "ogg",
                    "opus",
                    "m4a",
                    "aac",
                    "wma",
                    "mp4",
                    "3gp",
                    "webm",
                )
            val tracks = mutableListOf<Track>()

            fun scanDirectory(directory: File) {
                directory.listFiles()?.forEach { file ->
                    when {
                        file.isDirectory -> scanDirectory(file)
                        file.extension.lowercase() in audioExtensions -> {
                            try {
                                // Skip placeholder files created by failed downloads
                                if (isPlaceholderFile(file)) {
                                    println("Skipping placeholder file: ${file.name}")
                                    return@forEach
                                }

                                val track = extractEnhancedMetadata(file)
                                tracks.add(track)
                                println("Added track: ${track.title} by ${track.artist}")
                            } catch (e: Exception) {
                                println("Error reading metadata for ${file.name}: ${e.message}")
                                // Fallback to filename-based track
                                val fallbackTrack =
                                    Track(
                                        id = UUID.randomUUID().toString(),
                                        title = file.nameWithoutExtension,
                                        artist = "Unknown Artist",
                                        album = "Unknown Album",
                                        filePath = file.absolutePath,
                                    )
                                tracks.add(fallbackTrack)
                            }
                        }
                    }
                }
            }

            scanDirectory(musicDirectory)

            // Update "All Music" playlist
            val playlists = loadPlaylists().toMutableList()
            val allMusicIndex = playlists.indexOfFirst { it.name == "All Music" }
            if (allMusicIndex >= 0) {
                playlists[allMusicIndex] = playlists[allMusicIndex].copy(tracks = tracks)
                savePlaylist(playlists)
            }

            tracks
        }

    private fun isPlaceholderFile(file: File): Boolean =
        try {
            if (file.length() < 1024) { // Placeholder files are very small
                val content = file.readText()
                content.startsWith("# Placeholder for YouTube download:")
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }

    private fun extractEnhancedMetadata(file: File): Track {
        return try {
            // Skip JAudioTagger for Opus files to avoid TagLib errors - go straight to fallback
            if (file.extension.lowercase() == "opus") {
                println("Skipping JAudioTagger for Opus file, using filename parsing: ${file.name}")
                return extractOpusFallbackMetadata(file)
            }

            // First try with JAudioTagger for supported formats
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            // Enhanced metadata extraction with better fallbacks
            val title =
                tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
                    ?: tag?.getFirst(FieldKey.TITLE_SORT)?.takeIf { it.isNotBlank() }
                    ?: file.nameWithoutExtension

            val artist =
                tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }
                    ?: tag?.getFirst(FieldKey.ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
                    ?: tag?.getFirst(FieldKey.COMPOSER)?.takeIf { it.isNotBlank() }
                    ?: "Unknown Artist"

            val album =
                tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() }
                    ?: tag?.getFirst(FieldKey.ALBUM_SORT)?.takeIf { it.isNotBlank() }
                    ?: "Unknown Album"

            // Better duration extraction with fallbacks
            val duration = header?.trackLength?.toLong() ?: 0L

            // Enhanced year extraction
            val year =
                tag?.getFirst(FieldKey.YEAR)?.takeIf { it.isNotBlank() }
                    ?: tag?.getFirst(FieldKey.ORIGINAL_YEAR)?.takeIf { it.isNotBlank() }
                    ?: ""

            // Enhanced genre extraction
            val genre =
                tag?.getFirst(FieldKey.GENRE)?.takeIf { it.isNotBlank() }
                    ?: tag?.getFirst(FieldKey.GROUPING)?.takeIf { it.isNotBlank() }
                    ?: ""

            // Better track number extraction
            val trackNumber =
                try {
                    tag?.getFirst(FieldKey.TRACK)?.let { trackStr ->
                        // Handle "1/12" format
                        if (trackStr.contains("/")) {
                            trackStr.substringBefore("/").toIntOrNull() ?: 0
                        } else {
                            trackStr.toIntOrNull() ?: 0
                        }
                    } ?: 0
                } catch (e: Exception) {
                    0
                }

            // Extract and save album artwork
            val artworkPath = extractAlbumArtwork(file, tag, album, artist)

            Track(
                id = UUID.randomUUID().toString(),
                title = title,
                artist = artist,
                album = album,
                filePath = file.absolutePath,
                duration = duration,
                year = year,
                genre = genre,
                trackNumber = trackNumber,
                artworkPath = artworkPath,
            )
        } catch (e: Exception) {
            println("Enhanced metadata extraction failed for ${file.name}: ${e.message}")

            // Special handling for different formats
            when (file.extension.lowercase()) {
                "opus" -> {
                    println("Attempting fallback metadata extraction for Opus file")
                    extractOpusFallbackMetadata(file)
                }
                "m4a", "mp4", "aac" -> {
                    println("Attempting fallback metadata extraction for M4A/AAC file")
                    extractM4AFallbackMetadata(file)
                }
                else -> {
                    createFallbackTrack(file)
                }
            }
        }
    }

    private fun extractOpusFallbackMetadata(file: File): Track {
        // For opus files, try to extract basic info from filename
        return try {
            // First try to check for embedded artwork using JAudioTagger but catch TagLib errors
            var embeddedArtworkPath: String? = null
            try {
                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tag

                // Check for embedded picture in Opus metadata
                if (tag != null) {
                    val artwork = tag.firstArtwork
                    if (artwork != null) {
                        // Save embedded artwork
                        val safeFileName = file.nameWithoutExtension.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                        val extension =
                            when (artwork.mimeType) {
                                "image/jpeg", "image/jpg" -> "jpg"
                                "image/png" -> "png"
                                "image/bmp" -> "bmp"
                                "image/gif" -> "gif"
                                else -> "jpg"
                            }

                        val artworkFile = File(artworkDirectory, "$safeFileName.$extension")

                        if (!artworkFile.exists()) {
                            Files.write(artworkFile.toPath(), artwork.binaryData)
                            println("Extracted embedded artwork from Opus file: ${file.name}")
                        }
                        embeddedArtworkPath = artworkFile.absolutePath
                    }
                }
            } catch (e: Exception) {
                // TagLib errors are expected for Opus files, ignore and continue with filename parsing
                println("Note: TagLib couldn't read Opus metadata (expected), using filename parsing: ${e.message}")
            }

            // Try to parse filename patterns like "Title - Artist.opus"
            val nameWithoutExt = file.nameWithoutExtension
            val parts = nameWithoutExt.split(" - ", limit = 2)

            val artist: String
            val title: String
            when {
                parts.size >= 2 -> {
                    title = parts[0] // First part is title
                    artist = parts[1] // Second part is artist
                }
                nameWithoutExt.contains(" by ") -> {
                    val byParts = nameWithoutExt.split(" by ", limit = 2)
                    title = byParts[0]
                    artist = byParts.getOrNull(1) ?: "Unknown Artist"
                }
                else -> {
                    title = nameWithoutExt
                    artist = "Unknown Artist"
                }
            }

            // Use embedded artwork if found, otherwise try to find downloaded artwork
            val artworkPath = embeddedArtworkPath ?: findDownloadedArtwork(title, artist)

            Track(
                id = UUID.randomUUID().toString(),
                title = title,
                artist = artist,
                album = "Unknown Album",
                filePath = file.absolutePath,
                duration = 0L,
                year = "",
                genre = "",
                trackNumber = 0,
                artworkPath = artworkPath,
            )
        } catch (e: Exception) {
            createFallbackTrack(file)
        }
    }

    private fun extractM4AFallbackMetadata(file: File): Track {
        // For M4A files, similar filename parsing approach
        return try {
            val nameWithoutExt = file.nameWithoutExtension
            val parts = nameWithoutExt.split(" - ", limit = 2)

            val artist: String
            val title: String
            when {
                parts.size >= 2 -> {
                    title = parts[0] // First part is title
                    artist = parts[1] // Second part is artist
                }
                nameWithoutExt.contains(" by ") -> {
                    val byParts = nameWithoutExt.split(" by ", limit = 2)
                    title = byParts[0]
                    artist = byParts.getOrNull(1) ?: "Unknown Artist"
                }
                else -> {
                    title = nameWithoutExt
                    artist = "Unknown Artist"
                }
            }

            // Try to find downloaded artwork for this track
            val artworkPath = findDownloadedArtwork(title, artist)

            Track(
                id = UUID.randomUUID().toString(),
                title = title,
                artist = artist,
                album = "Unknown Album",
                filePath = file.absolutePath,
                duration = 0L,
                year = "",
                genre = "",
                trackNumber = 0,
                artworkPath = artworkPath,
            )
        } catch (e: Exception) {
            createFallbackTrack(file)
        }
    }

    private fun createFallbackTrack(file: File): Track =
        Track(
            id = UUID.randomUUID().toString(),
            title = file.nameWithoutExtension,
            artist = "Unknown Artist",
            album = "Unknown Album",
            filePath = file.absolutePath,
            duration = 0L,
            year = "",
            genre = "",
            trackNumber = 0,
            artworkPath = null,
        )

    private fun findDownloadedArtwork(
        title: String,
        artist: String,
    ): String? {
        return try {
            // Create safe filename components for artwork lookup
            val safeTitle = title.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val safeArtist = artist.replace("[^a-zA-Z0-9._-]".toRegex(), "_")

            // Try different artwork filename patterns
            val patterns =
                listOf(
                    "${safeArtist}_$safeTitle.jpg",
                    "${safeArtist}_$safeTitle.png",
                    "${safeTitle}_$safeArtist.jpg",
                    "${safeTitle}_$safeArtist.png",
                )

            for (pattern in patterns) {
                val artworkFile = File(artworkDirectory, pattern)
                if (artworkFile.exists()) {
                    return artworkFile.absolutePath
                }
            }
            null
        } catch (e: Exception) {
            println("Error finding downloaded artwork: ${e.message}")
            null
        }
    }

    private fun extractAlbumArtwork(
        audioFile: File,
        tag: org.jaudiotagger.tag.Tag?,
        album: String,
        artist: String,
    ): String? =
        try {
            val artwork = tag?.firstArtwork
            if (artwork != null) {
                // Create a safe filename for the artwork
                val safeAlbum = album.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val safeArtist = artist.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val extension =
                    when (artwork.mimeType) {
                        "image/jpeg", "image/jpg" -> "jpg"
                        "image/png" -> "png"
                        "image/bmp" -> "bmp"
                        "image/gif" -> "gif"
                        else -> "jpg" // Default to JPG
                    }

                val artworkFile = File(artworkDirectory, "${safeArtist}_$safeAlbum.$extension")

                // Only save if doesn't exist already
                if (!artworkFile.exists()) {
                    Files.write(artworkFile.toPath(), artwork.binaryData)
                    println("Extracted artwork for $album by $artist")
                }

                artworkFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error extracting artwork from ${audioFile.name}: ${e.message}")
            null
        }

    suspend fun searchTracks(
        query: String,
        playlists: List<Playlist>,
    ): List<Track> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()

            val allTracks = playlists.flatMap { it.tracks }.distinctBy { it.filePath }
            val searchTerms = query.lowercase().split(" ").filter { it.isNotBlank() }

            allTracks.filter { track ->
                val searchableText = "${track.title} ${track.artist} ${track.album} ${track.genre}".lowercase()
                searchTerms.all { term -> searchableText.contains(term) }
            }
        }

    suspend fun loadPlaylists(): List<Playlist> =
        withContext(Dispatchers.IO) {
            try {
                if (playlistsFile.exists()) {
                    val jsonContent = playlistsFile.readText()
                    json.decodeFromString<List<Playlist>>(jsonContent)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                println("Error loading playlists: ${e.message}")
                emptyList()
            }
        }

    private suspend fun savePlaylist(playlists: List<Playlist>) =
        withContext(Dispatchers.IO) {
            try {
                val jsonContent = json.encodeToString(playlists)
                playlistsFile.writeText(jsonContent)
            } catch (e: Exception) {
                println("Error saving playlists: ${e.message}")
            }
        }

    suspend fun createPlaylist(name: String) =
        withContext(Dispatchers.IO) {
            val playlists = loadPlaylists().toMutableList()
            if (playlists.none { it.name == name }) {
                playlists.add(Playlist(name, emptyList()))
                savePlaylist(playlists)
            }
        }

    suspend fun addTrackToAllMusic(track: Track) =
        withContext(Dispatchers.IO) {
            val playlists = loadPlaylists().toMutableList()
            val allMusicIndex = playlists.indexOfFirst { it.name == "All Music" }
            if (allMusicIndex >= 0) {
                val updatedTracks = playlists[allMusicIndex].tracks + track
                playlists[allMusicIndex] = playlists[allMusicIndex].copy(tracks = updatedTracks)
                savePlaylist(playlists)
            }
        }

    suspend fun addTrackToPlaylist(
        playlistName: String,
        track: Track,
    ) = withContext(Dispatchers.IO) {
        val playlists = loadPlaylists().toMutableList()
        val playlistIndex = playlists.indexOfFirst { it.name == playlistName }
        if (playlistIndex >= 0) {
            val playlist = playlists[playlistIndex]
            if (playlist.tracks.none { it.filePath == track.filePath }) {
                val updatedTracks = playlist.tracks + track
                playlists[playlistIndex] = playlist.copy(tracks = updatedTracks)
                savePlaylist(playlists)
            }
        }
    }

    suspend fun removeTrackFromPlaylist(
        playlistName: String,
        track: Track,
    ) = withContext(Dispatchers.IO) {
        val playlists = loadPlaylists().toMutableList()
        val playlistIndex = playlists.indexOfFirst { it.name == playlistName }
        if (playlistIndex >= 0) {
            val playlist = playlists[playlistIndex]
            val updatedTracks = playlist.tracks.filter { it.filePath != track.filePath }
            playlists[playlistIndex] = playlist.copy(tracks = updatedTracks)
            savePlaylist(playlists)
        }
    }

    suspend fun deletePlaylist(playlistName: String) =
        withContext(Dispatchers.IO) {
            if (playlistName != "All Music" && playlistName != "Favorites") {
                val playlists = loadPlaylists().toMutableList()
                val filteredPlaylists = playlists.filter { it.name != playlistName }
                savePlaylist(filteredPlaylists)
            }
        }

    suspend fun reorderTracksInPlaylist(
        playlistName: String,
        fromIndex: Int,
        toIndex: Int,
    ) = withContext(Dispatchers.IO) {
        val playlists = loadPlaylists().toMutableList()
        val playlistIndex = playlists.indexOfFirst { it.name == playlistName }
        if (playlistIndex >= 0) {
            val playlist = playlists[playlistIndex]
            val tracks = playlist.tracks.toMutableList()
            if (fromIndex in tracks.indices && toIndex in tracks.indices) {
                val track = tracks.removeAt(fromIndex)
                tracks.add(toIndex, track)
                playlists[playlistIndex] = playlist.copy(tracks = tracks)
                savePlaylist(playlists)
            }
        }
    }

    suspend fun renamePlaylist(
        oldName: String,
        newName: String,
    ) = withContext(Dispatchers.IO) {
        if (oldName != "All Music" && oldName != "Favorites") {
            val playlists = loadPlaylists().toMutableList()
            val playlistIndex = playlists.indexOfFirst { it.name == oldName }
            if (playlistIndex >= 0 && playlists.none { it.name == newName }) {
                playlists[playlistIndex] = playlists[playlistIndex].copy(name = newName)
                savePlaylist(playlists)
            }
        }
    }

    suspend fun duplicatePlaylist(
        originalName: String,
        newName: String,
    ) = withContext(Dispatchers.IO) {
        val playlists = loadPlaylists().toMutableList()
        val originalPlaylist = playlists.find { it.name == originalName }
        if (originalPlaylist != null && playlists.none { it.name == newName }) {
            val duplicatedPlaylist =
                originalPlaylist.copy(
                    name = newName,
                    created = System.currentTimeMillis(),
                )
            playlists.add(duplicatedPlaylist)
            savePlaylist(playlists)
        }
    }

    suspend fun clearPlaylist(playlistName: String) =
        withContext(Dispatchers.IO) {
            if (playlistName != "All Music") {
                val playlists = loadPlaylists().toMutableList()
                val playlistIndex = playlists.indexOfFirst { it.name == playlistName }
                if (playlistIndex >= 0) {
                    playlists[playlistIndex] = playlists[playlistIndex].copy(tracks = emptyList())
                    savePlaylist(playlists)
                }
            }
        }

    suspend fun getPlaylistStats(): Map<String, Int> =
        withContext(Dispatchers.IO) {
            val playlists = loadPlaylists()
            playlists.associate { it.name to it.tracks.size }
        }
}
