package xyz.malefic.compose.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

/**
 * Simple music manager for scanning files and managing basic playlists
 */
class SimpleMusicManager {
    val musicDirectory: File by lazy {
        File(System.getProperty("user.home"), "Music")
    }

    val downloadsDirectory: File by lazy {
        File(musicDirectory, "Downloads").apply { mkdirs() }
    }

    // In-memory storage for simplicity
    private val playlists = mutableListOf<SimplePlaylist>()
    private val allTracks = mutableListOf<SimpleTrack>()

    suspend fun initializeMusicDirectory() = withContext(Dispatchers.IO) {
        musicDirectory.mkdirs()
        downloadsDirectory.mkdirs()

        // Create default playlists
        if (playlists.isEmpty()) {
            playlists.addAll(
                listOf(
                    SimplePlaylist(
                        id = UUID.randomUUID().toString(),
                        name = "All Music",
                        isSystemPlaylist = true
                    ),
                    SimplePlaylist(
                        id = UUID.randomUUID().toString(),
                        name = "Favorites",
                        isSystemPlaylist = true
                    )
                )
            )
        }

        // Scan for music files
        scanMusicDirectory()
    }

    suspend fun scanMusicDirectory(): List<SimpleTrack> = withContext(Dispatchers.IO) {
        allTracks.clear()
        
        // Support for basic formats that Java Sound API can handle
        // Plus opus and m4a (though we note they need additional libraries)
        val audioExtensions = setOf(
            "wav", "au", "aiff", // Java Sound API supported
            "mp3", "flac", "ogg", "opus", "m4a", "aac", "mp4", "wma" // Need additional libraries
        )

        fun scanDirectory(directory: File) {
            directory.listFiles()?.forEach { file ->
                when {
                    file.isDirectory -> scanDirectory(file)
                    file.extension.lowercase() in audioExtensions -> {
                        val track = SimpleTrack(
                            id = UUID.randomUUID().toString(),
                            title = file.nameWithoutExtension,
                            artist = "Unknown Artist",
                            album = "Unknown Album",
                            filePath = file.absolutePath,
                            format = file.extension.lowercase(),
                            fileSize = file.length()
                        )
                        allTracks.add(track)
                        println("Found: ${track.title} (${track.format})")
                    }
                }
            }
        }

        scanDirectory(musicDirectory)

        // Update "All Music" playlist
        val allMusicIndex = playlists.indexOfFirst { it.name == "All Music" }
        if (allMusicIndex >= 0) {
            playlists[allMusicIndex] = playlists[allMusicIndex].copy(tracks = allTracks.toList())
        }

        allTracks.toList()
    }

    fun getPlaylists(): List<SimplePlaylist> = playlists.toList()

    fun createPlaylist(name: String): Boolean {
        if (playlists.none { it.name == name }) {
            playlists.add(
                SimplePlaylist(
                    id = UUID.randomUUID().toString(),
                    name = name
                )
            )
            return true
        }
        return false
    }

    fun deletePlaylist(playlistId: String): Boolean {
        return playlists.removeIf { it.id == playlistId && !it.isSystemPlaylist }
    }

    fun addTrackToPlaylist(playlistId: String, track: SimpleTrack): Boolean {
        val playlistIndex = playlists.indexOfFirst { it.id == playlistId }
        if (playlistIndex >= 0) {
            val playlist = playlists[playlistIndex]
            if (playlist.tracks.none { it.id == track.id }) {
                playlists[playlistIndex] = playlist.copy(tracks = playlist.tracks + track)
                return true
            }
        }
        return false
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String): Boolean {
        val playlistIndex = playlists.indexOfFirst { it.id == playlistId }
        if (playlistIndex >= 0) {
            val playlist = playlists[playlistIndex]
            val updatedTracks = playlist.tracks.filter { it.id != trackId }
            if (updatedTracks.size != playlist.tracks.size) {
                playlists[playlistIndex] = playlist.copy(tracks = updatedTracks)
                return true
            }
        }
        return false
    }
}