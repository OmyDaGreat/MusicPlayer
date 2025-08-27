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
            val audioExtensions = setOf("mp3", "wav", "flac", "au", "aiff", "ogg")
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

    private fun extractMetadata(file: File): Track {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tag
        val header = audioFile.audioHeader

        return Track(
            id = UUID.randomUUID().toString(),
            title =
                tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
                    ?: file.nameWithoutExtension,
            artist =
                tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }
                    ?: "Unknown Artist",
            album =
                tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() }
                    ?: "Unknown Album",
            filePath = file.absolutePath,
            duration = header?.trackLength?.toLong() ?: 0L,
            year = tag?.getFirst(FieldKey.YEAR)?.takeIf { it.isNotBlank() } ?: "",
            genre = tag?.getFirst(FieldKey.GENRE)?.takeIf { it.isNotBlank() } ?: "",
            trackNumber =
                try {
                    tag?.getFirst(FieldKey.TRACK)?.toIntOrNull() ?: 0
                } catch (e: Exception) {
                    0
                },
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
}
