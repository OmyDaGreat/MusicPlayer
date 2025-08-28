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
                        Playlist(
                            id = UUID.randomUUID().toString(),
                            name = "All Music",
                            isSystemPlaylist = true
                        ),
                        Playlist(
                            id = UUID.randomUUID().toString(),
                            name = "Favorites",
                            isSystemPlaylist = true
                        ),
                    )
                savePlaylist(defaultPlaylists)
            }

            // Scan for existing music
            scanMusicDirectory()
        }

    suspend fun scanMusicDirectory(): List<Track> =
        withContext(Dispatchers.IO) {
            // Support for opus and m4a along with common formats
            val audioExtensions = setOf(
                "mp3", "wav", "flac", "au", "aiff", "ogg", 
                "opus", "m4a", "aac", "mp4", "wma"
            )
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
                                        fileSize = file.length(),
                                        format = file.extension.lowercase()
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

    fun extractMetadata(file: File): Track {
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
            bitrate = header?.bitRate ?: "",
            sampleRate = header?.sampleRate?.toString() ?: "",
            channels = header?.channels ?: "",
            fileSize = file.length(),
            format = file.extension.lowercase(),
            albumArt = extractAlbumArt(tag, file)
        )
    }

    private fun extractAlbumArt(tag: org.jaudiotagger.tag.Tag?, file: File): String? {
        return try {
            tag?.firstArtwork?.let { artwork ->
                val artFile = File(file.parent, "${file.nameWithoutExtension}_albumart.${artwork.mimeType.substringAfter("/")}")
                if (!artFile.exists()) {
                    artFile.writeBytes(artwork.binaryData)
                }
                artFile.absolutePath
            }
        } catch (e: Exception) {
            null
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

    suspend fun createPlaylist(name: String): Boolean =
        withContext(Dispatchers.IO) {
            val playlists = loadPlaylists().toMutableList()
            if (playlists.none { it.name == name }) {
                playlists.add(
                    Playlist(
                        id = UUID.randomUUID().toString(),
                        name = name
                    )
                )
                savePlaylist(playlists)
                true
            } else {
                false
            }
        }

    suspend fun deletePlaylist(playlistId: String): Boolean =
        withContext(Dispatchers.IO) {
            val playlists = loadPlaylists().toMutableList()
            val removed = playlists.removeIf { it.id == playlistId && !it.isSystemPlaylist }
            if (removed) {
                savePlaylist(playlists)
            }
            removed
        }

    suspend fun addTrackToPlaylist(playlistId: String, track: Track): Boolean =
        withContext(Dispatchers.IO) {
            val playlists = loadPlaylists().toMutableList()
            val playlistIndex = playlists.indexOfFirst { it.id == playlistId }
            if (playlistIndex >= 0) {
                val playlist = playlists[playlistIndex]
                if (playlist.tracks.none { it.id == track.id }) {
                    playlists[playlistIndex] = playlist.copy(
                        tracks = playlist.tracks + track,
                        modifiedDate = System.currentTimeMillis()
                    )
                    savePlaylist(playlists)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Boolean =
        withContext(Dispatchers.IO) {
            val playlists = loadPlaylists().toMutableList()
            val playlistIndex = playlists.indexOfFirst { it.id == playlistId }
            if (playlistIndex >= 0) {
                val playlist = playlists[playlistIndex]
                val updatedTracks = playlist.tracks.filter { it.id != trackId }
                if (updatedTracks.size != playlist.tracks.size) {
                    playlists[playlistIndex] = playlist.copy(
                        tracks = updatedTracks,
                        modifiedDate = System.currentTimeMillis()
                    )
                    savePlaylist(playlists)
                    true
                } else {
                    false
                }
            } else {
                false
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