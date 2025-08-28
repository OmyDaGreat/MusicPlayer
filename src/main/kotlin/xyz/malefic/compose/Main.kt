package xyz.malefic.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import xyz.malefic.compose.util.*

/**
 * Entry point of the Music Player application
 */
fun main() =
    application {
        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
        
        Window(
            onCloseRequest = ::exitApplication,
            title = "Music Player - Supports Opus, M4A and More",
            state = windowState
        ) {
            MusicPlayerApp()
        }
    }

@Composable
fun MusicPlayerApp() {
    val musicManager = remember { SimpleMusicManager() }
    val audioPlayer = remember { SimpleAudioPlayer() }
    val scope = rememberCoroutineScope()

    var currentTrack by remember { mutableStateOf<SimpleTrack?>(null) }
    val isPlaying by audioPlayer.isPlaying
    val currentPosition by audioPlayer.currentPosition
    val duration by audioPlayer.duration

    var playlists by remember { mutableStateOf<List<SimplePlaylist>>(emptyList()) }
    var selectedPlaylist by remember { mutableStateOf<SimplePlaylist?>(null) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Initialize music directory and load playlists
    LaunchedEffect(Unit) {
        musicManager.initializeMusicDirectory()
        playlists = musicManager.getPlaylists()
        selectedPlaylist = playlists.firstOrNull { it.name == "All Music" }
    }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar - Playlists
            Card(
                modifier = Modifier.width(300.dp).fillMaxHeight(),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Music Player",
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            IconButton(onClick = { showNewPlaylistDialog = true }) {
                                Icon(Icons.Default.Add, "Create Playlist")
                            }
                            IconButton(onClick = { showAboutDialog = true }) {
                                Icon(Icons.Default.Info, "About")
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn {
                        items(playlists) { playlist ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        selectedPlaylist = playlist
                                    },
                                backgroundColor = if (selectedPlaylist?.id == playlist.id) 
                                    MaterialTheme.colors.primary.copy(alpha = 0.1f) 
                                else 
                                    MaterialTheme.colors.surface,
                                elevation = if (selectedPlaylist?.id == playlist.id) 4.dp else 1.dp
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = playlist.name,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${playlist.tracks.size} songs",
                                        style = MaterialTheme.typography.caption,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Main Content Area
            Column(modifier = Modifier.weight(1f)) {
                // Track List
                selectedPlaylist?.let { playlist ->
                    PlaylistView(
                        playlist = playlist,
                        currentTrack = currentTrack,
                        onTrackSelected = { track ->
                            currentTrack = track
                            scope.launch {
                                audioPlayer.play(track)
                            }
                        },
                        onRefreshMusic = {
                            scope.launch {
                                musicManager.scanMusicDirectory()
                                playlists = musicManager.getPlaylists()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Player Controls at Bottom
                PlayerControls(
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPause = {
                        if (isPlaying) {
                            audioPlayer.pause()
                        } else {
                            if (currentTrack != null) {
                                audioPlayer.resume()
                            }
                        }
                    },
                    onStop = {
                        audioPlayer.stop()
                        currentTrack = null
                    },
                    onSeek = { position ->
                        audioPlayer.seek(position)
                    }
                )
            }
        }

        // New Playlist Dialog
        if (showNewPlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showNewPlaylistDialog = false },
                title = { Text("Create New Playlist") },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                if (musicManager.createPlaylist(newPlaylistName)) {
                                    playlists = musicManager.getPlaylists()
                                    showNewPlaylistDialog = false
                                    newPlaylistName = ""
                                }
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showNewPlaylistDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // About Dialog
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text("Music Player - Format Support") },
                text = {
                    Column {
                        Text(
                            text = "Supported Audio Formats:",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text("✅ WAV, AU, AIFF (Java Sound API)")
                        Text("⚠️ MP3, FLAC, OGG (Need additional libraries)")
                        Text("⚠️ Opus, M4A, AAC (Need additional libraries)")
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Features Implemented:",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text("✅ File scanning and library management")
                        Text("✅ Playlist creation and management")
                        Text("✅ Basic audio playback controls")
                        Text("✅ Support for multiple audio formats (with proper libraries)")
                        Text("✅ Music directory scanning")
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "To fully support Opus and M4A:",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text("• Add JAudioTagger for metadata")
                        Text("• Add BasicPlayer for MP3/advanced formats")
                        Text("• Add FFmpeg bindings for Opus/M4A")
                        Text("• Add YouTube/Spotify API integration")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }

    // Cleanup when app closes
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.cleanup()
        }
    }
}

@Composable
fun PlaylistView(
    playlist: SimplePlaylist,
    currentTrack: SimpleTrack?,
    onTrackSelected: (SimpleTrack) -> Unit,
    onRefreshMusic: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${playlist.tracks.size} songs",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }

            if (playlist.name == "All Music") {
                IconButton(onClick = onRefreshMusic) {
                    Icon(Icons.Default.Refresh, "Refresh Music Library")
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        if (playlist.tracks.isEmpty()) {
            // Empty playlist placeholder
            Card(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                backgroundColor = Color.LightGray.copy(alpha = 0.1f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No music found",
                        style = MaterialTheme.typography.h6,
                        color = Color.Gray
                    )
                    Text(
                        text = if (playlist.name == "All Music") 
                            "Add music files to your Music folder and click refresh" 
                        else 
                            "This playlist is empty",
                        style = MaterialTheme.typography.body2,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn {
                items(playlist.tracks) { track ->
                    TrackItem(
                        track = track,
                        isCurrentTrack = currentTrack?.id == track.id,
                        onClick = { onTrackSelected(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun TrackItem(
    track: SimpleTrack,
    isCurrentTrack: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
        backgroundColor = if (isCurrentTrack) 
            MaterialTheme.colors.primary.copy(alpha = 0.1f) 
        else 
            MaterialTheme.colors.surface,
        elevation = if (isCurrentTrack) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.format.uppercase()} • ${formatFileSize(track.fileSize)}",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }

            // Format indicator
            Card(
                backgroundColor = when (track.format.lowercase()) {
                    "wav", "au", "aiff" -> Color.Green.copy(alpha = 0.2f)
                    "opus", "m4a" -> Color.Blue.copy(alpha = 0.2f)
                    else -> Color.Orange.copy(alpha = 0.2f)
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = track.format.uppercase(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold
                )
            }

            // Play indicator
            if (isCurrentTrack) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Currently Playing",
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun PlayerControls(
    currentTrack: SimpleTrack?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Progress bar
            if (currentTrack != null && duration > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(currentPosition),
                        style = MaterialTheme.typography.caption
                    )
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { progress ->
                            onSeek((progress * duration).toLong())
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.caption
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Track info
                Column(modifier = Modifier.weight(1f)) {
                    if (currentTrack != null) {
                        Text(
                            text = currentTrack.title,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${currentTrack.artist} • ${currentTrack.album}",
                            style = MaterialTheme.typography.body2,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "No track selected",
                            color = Color.Gray
                        )
                    }
                }

                // Control buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Stop, "Stop")
                    }

                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(56.dp),
                        enabled = currentTrack != null
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Format info
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    if (currentTrack != null) {
                        val supportStatus = when (currentTrack.format.lowercase()) {
                            "wav", "au", "aiff" -> "✅ Supported"
                            "opus", "m4a" -> "⚠️ Needs library"
                            else -> "⚠️ Needs library"
                        }
                        Text(
                            text = "${currentTrack.format.uppercase()} - $supportStatus",
                            style = MaterialTheme.typography.caption,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> "%.1f MB".format(mb)
        kb >= 1 -> "%.1f KB".format(kb)
        else -> "$bytes B"
    }
}