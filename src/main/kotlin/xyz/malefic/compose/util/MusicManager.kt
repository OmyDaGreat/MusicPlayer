package xyz.malefic.compose.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.*

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

    suspend fun initializeMusicDirectory() =
        withContext(Dispatchers.IO) {
            musicDirectory.mkdirs()
            downloadsDirectory.mkdirs()

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
            val audioExtensions = setOf("mp3", "wav", "flac", "au", "aiff", "ogg", "opus", "m4a", "aac", "wma")
            val tracks = mutableListOf<Track>()

            fun scanDirectory(directory: File) {
                directory.listFiles()?.forEach { file ->
                    when {
                        file.isDirectory -> scanDirectory(file)
                        file.extension.lowercase() in audioExtensions -> {
                            try {
                                val track = extractMetadata(file)
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

    private fun extractMetadata(file: File): Track =
        try {
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
            val duration =
                header?.trackLength?.toLong()
                    ?: 0L

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
            )
        } catch (e: Exception) {
            println("Enhanced metadata extraction failed for ${file.name}: ${e.message}")
            // Fallback to basic file information
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
            )
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
