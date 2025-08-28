package xyz.malefic.compose

import androidx.compose.desktop.ui.tooling.preview.Preview
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch
import xyz.malefic.compose.util.DownloadManager
import xyz.malefic.compose.util.MusicManager
import xyz.malefic.compose.util.MusicPlayerService
import xyz.malefic.compose.util.Playlist
import xyz.malefic.compose.util.Track

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Kotlin Music Player",
        ) {
            MusicPlayerApp()
        }
    }

@OptIn(ExperimentalMaterialApi::class)
@Composable
@Preview
fun MusicPlayerApp() {
    val musicManager = remember { MusicManager() }
    val musicPlayer = remember { MusicPlayerService() }
    val scope = rememberCoroutineScope()

    var currentTrack by remember { mutableStateOf<Track?>(null) }
    val isPlaying by musicPlayer.isPlaying
    val currentPosition by musicPlayer.currentPosition
    val duration by musicPlayer.duration

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showDownloader by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        musicManager.initializeMusicDirectory()
        playlists = musicManager.loadPlaylists()
        selectedPlaylist = playlists.firstOrNull { it.name == "All Music" }
    }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar - Playlists
            Card(
                modifier = Modifier.width(250.dp).fillMaxHeight(),
                elevation = 4.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Playlists", style = MaterialTheme.typography.h6)
                        Row {
                            IconButton(
                                onClick = { showNewPlaylistDialog = true },
                            ) {
                                Icon(Icons.Default.Add, "New Playlist")
                            }
                            IconButton(
                                onClick = { showDownloader = !showDownloader },
                            ) {
                                Icon(Icons.Default.Download, "Downloads")
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    LazyColumn {
                        items(playlists) { playlist ->
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                backgroundColor =
                                    if (playlist == selectedPlaylist) {
                                        MaterialTheme.colors.primary.copy(alpha = 0.1f)
                                    } else {
                                        MaterialTheme.colors.surface
                                    },
                                onClick = {
                                    selectedPlaylist = playlist
                                    showDownloader = false
                                },
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                ) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.body1,
                                    )
                                    Text(
                                        text = "${playlist.tracks.size} songs",
                                        style = MaterialTheme.typography.caption,
                                        color = Color.Gray,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Main Content
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp),
            ) {
                if (showDownloader) {
                    DownloaderSection(
                        onTrackDownloaded = { track ->
                            scope.launch {
                                musicManager.addTrackToAllMusic(track)
                                playlists = musicManager.loadPlaylists()
                            }
                        },
                    )
                } else {
                    selectedPlaylist?.let { playlist ->
                        PlaylistView(
                            playlist = playlist,
                            currentTrack = currentTrack,
                            onTrackSelected = { track ->
                                currentTrack = track
                                scope.launch {
                                    musicPlayer.playWithPlaylist(track, playlist.tracks)
                                }
                            },
                            onRefreshMusic = {
                                scope.launch {
                                    musicManager.scanMusicDirectory()
                                    playlists = musicManager.loadPlaylists()
                                }
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Player Controls at Bottom
                PlayerControls(
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPause = {
                        scope.launch {
                            if (isPlaying) {
                                musicPlayer.pause()
                            } else {
                                currentTrack?.let { musicPlayer.play(it) }
                            }
                        }
                    },
                    onPrevious = {
                        selectedPlaylist?.let { playlist ->
                            scope.launch {
                                musicPlayer.playWithPlaylist(currentTrack!!, playlist.tracks)
                            }
                            val prevTrack = musicPlayer.previousTrack()
                            prevTrack?.let {
                                currentTrack = it
                                scope.launch { musicPlayer.play(it) }
                            }
                        }
                    },
                    onNext = {
                        selectedPlaylist?.let { playlist ->
                            scope.launch {
                                musicPlayer.playWithPlaylist(currentTrack!!, playlist.tracks)
                            }
                            val nextTrack = musicPlayer.nextTrack()
                            nextTrack?.let {
                                currentTrack = it
                                scope.launch { musicPlayer.play(it) }
                            }
                        }
                    },
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
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                scope.launch {
                                    musicManager.createPlaylist(newPlaylistName)
                                    playlists = musicManager.loadPlaylists()
                                    showNewPlaylistDialog = false
                                    newPlaylistName = ""
                                }
                            }
                        },
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showNewPlaylistDialog = false },
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
fun PlaylistView(
    playlist: Playlist,
    currentTrack: Track?,
    onTrackSelected: (Track) -> Unit,
    onRefreshMusic: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.h5,
                )
                Text(
                    text = "${playlist.tracks.size} songs",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                )
            }

            if (playlist.name == "All Music") {
                IconButton(onClick = onRefreshMusic) {
                    Icon(Icons.Default.Refresh, "Refresh Music")
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        if (playlist.tracks.isEmpty()) {
            // Empty playlist placeholder
            Card(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                elevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = "No Music",
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text =
                            if (playlist.name == "All Music") {
                                "No music found in your music directory.\nClick the refresh button to scan for music files."
                            } else {
                                "This playlist is empty.\nAdd some tracks to get started!"
                            },
                        style = MaterialTheme.typography.body1,
                        color = Color.Gray,
                    )
                }
            }
        } else {
            LazyColumn {
                items(playlist.tracks) { track ->
                    TrackItem(
                        track = track,
                        isCurrentTrack = track == currentTrack,
                        onClick = { onTrackSelected(track) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TrackItem(
    track: Track,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        backgroundColor =
            if (isCurrentTrack) {
                MaterialTheme.colors.primary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colors.surface
            },
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = "Track",
                tint = if (isCurrentTrack) MaterialTheme.colors.primary else Color.Gray,
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.body1,
                    color = if (isCurrentTrack) MaterialTheme.colors.primary else Color.Unspecified,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray,
                )
            }

            if (track.duration > 0) {
                Text(
                    text = formatTime(track.duration),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                )
            }
        }
    }
}

@Composable
fun PlayerControls(
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            currentTrack?.let { track ->
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.h6,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray,
                )

                if (duration > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = currentPosition.toFloat() / duration.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            style = MaterialTheme.typography.caption,
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.caption,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, "Previous")
                }

                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                    )
                }

                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, "Next")
                }
            }
        }
    }
}

@Composable
fun DownloaderSection(onTrackDownloaded: (Track) -> Unit) {
    var downloadUrl by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val downloadManager = remember { DownloadManager() }

    Column {
        Text("Download Music", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    label = { Text("Enter direct download URL") },
                    placeholder = { Text("https://example.com/song.mp3") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloading,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (downloadUrl.isNotBlank()) {
                            scope.launch {
                                isDownloading = true
                                downloadStatus = "Downloading..."
                                try {
                                    val track = downloadManager.downloadTrack(downloadUrl)
                                    onTrackDownloaded(track)
                                    downloadStatus = "Download completed!"
                                    downloadUrl = ""
                                } catch (e: Exception) {
                                    downloadStatus = "Download failed: ${e.message}"
                                }
                                isDownloading = false
                            }
                        }
                    },
                    enabled = !isDownloading && downloadUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colors.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isDownloading) "Downloading..." else "Download")
                }

                if (downloadStatus.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = downloadStatus,
                        style = MaterialTheme.typography.body2,
                        color = if (downloadStatus.contains("failed")) Color.Red else Color.Green,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text =
                "Note: This downloader works with direct file URLs only. " +
                    "It does not support streaming services or protected content.",
            style = MaterialTheme.typography.caption,
            color = Color.Gray,
        )
    }
}

fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}
