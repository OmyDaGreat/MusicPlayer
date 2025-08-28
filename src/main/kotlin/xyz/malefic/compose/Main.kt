package xyz.malefic.compose

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch
import xyz.malefic.compose.screens.SearchDownloadScreen
import xyz.malefic.compose.util.ModernMusicPlayerService
import xyz.malefic.compose.util.MusicManager
import xyz.malefic.compose.util.Playlist
import xyz.malefic.compose.util.RepeatMode
import xyz.malefic.compose.util.Track
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Modern Kotlin Music Player",
        ) {
            window.preferredSize = java.awt.Dimension(1200, 800)
            window.minimumSize = java.awt.Dimension(1000, 600)
            MusicPlayerApp()
        }
    }

@OptIn(ExperimentalMaterialApi::class)
@Composable
@Preview
fun MusicPlayerApp() {
    val musicManager = remember { MusicManager() }
    val musicPlayer = remember { ModernMusicPlayerService() }
    val scope = rememberCoroutineScope()

    // Sync currentTrack with musicPlayer's current track
    val musicPlayerCurrentTrack by musicPlayer.currentTrack
    var currentTrack by remember { mutableStateOf<Track?>(null) }

    // Update currentTrack whenever the music player's track changes
    LaunchedEffect(musicPlayerCurrentTrack) {
        if (musicPlayerCurrentTrack != currentTrack) {
            currentTrack = musicPlayerCurrentTrack
        }
    }

    val isPlaying by musicPlayer.isPlaying
    val currentPosition by musicPlayer.currentPosition
    val duration by musicPlayer.duration
    val volume by musicPlayer.volume
    val isShuffleEnabled by musicPlayer.isShuffleEnabled
    val repeatMode by musicPlayer.repeatMode

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showDownloader by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Handle search
    LaunchedEffect(searchQuery, playlists) {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            searchResults = musicManager.searchTracks(searchQuery, playlists)
            isSearching = false
        } else {
            searchResults = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        musicManager.initializeMusicDirectory()
        playlists = musicManager.loadPlaylists()
        selectedPlaylist = playlists.firstOrNull { it.name == "All Music" }
    }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar - Playlists
            Card(
                modifier = Modifier.width(300.dp).fillMaxHeight(),
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
                            IconButton(onClick = { showNewPlaylistDialog = true }) {
                                Icon(Icons.Default.Add, "New Playlist")
                            }
                            IconButton(onClick = { showDownloader = !showDownloader }) {
                                Icon(Icons.Default.Download, "Downloads")
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Box(modifier = Modifier.fillMaxSize()) {
                        val playlistScrollState = rememberLazyListState()
                        LazyColumn(state = playlistScrollState) {
                            items(playlists) { playlist ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    backgroundColor =
                                        if (playlist == selectedPlaylist) {
                                            MaterialTheme.colors.primary.copy(alpha = 0.1f)
                                        } else {
                                            MaterialTheme.colors.surface
                                        },
                                    onClick = {
                                        selectedPlaylist = playlist
                                        showDownloader = false
                                        searchQuery = "" // Clear search when switching playlists
                                    },
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
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

                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(scrollState = playlistScrollState),
                        )
                    }
                }
            }

            // Main Content
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(20.dp),
            ) {
                if (showDownloader) {
                    SearchDownloadScreen(
                        musicManager = musicManager,
                        onTrackDownloaded = {
                            scope.launch {
                                musicManager.scanMusicDirectory()
                                playlists = musicManager.loadPlaylists()
                            }
                        },
                    )
                } else {
                    selectedPlaylist?.let { playlist ->
                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                            placeholder = { Text("Search music...") },
                            leadingIcon = { Icon(Icons.Default.Search, "Search") },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                        )

                        PlaylistView(
                            playlist = playlist,
                            searchResults = if (searchQuery.isNotBlank()) searchResults else emptyList(),
                            isSearching = isSearching,
                            currentTrack = currentTrack,
                            onTrackSelected = { track ->
                                currentTrack = track
                                scope.launch {
                                    val tracksToPlay = if (searchQuery.isNotBlank()) searchResults else playlist.tracks
                                    musicPlayer.playWithPlaylist(track, tracksToPlay)
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
                    volume = volume,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    onPlayPause = {
                        scope.launch {
                            if (isPlaying) {
                                musicPlayer.pause()
                            } else {
                                currentTrack?.let {
                                    if (musicPlayer.currentTrack.value == it) {
                                        musicPlayer.resume()
                                    } else {
                                        musicPlayer.play(it)
                                    }
                                }
                            }
                        }
                    },
                    onPrevious = {
                        selectedPlaylist?.let { playlist ->
                            val prevTrack = musicPlayer.previousTrack()
                            prevTrack?.let {
                                currentTrack = it
                                scope.launch { musicPlayer.play(it) }
                            }
                        }
                    },
                    onNext = {
                        selectedPlaylist?.let { playlist ->
                            val nextTrack = musicPlayer.nextTrack()
                            nextTrack?.let {
                                currentTrack = it
                                scope.launch { musicPlayer.play(it) }
                            }
                        }
                    },
                    onSeek = { positionSeconds ->
                        musicPlayer.seek(positionSeconds)
                    },
                    onVolumeChanged = { newVolume ->
                        musicPlayer.setVolume(newVolume)
                    },
                    onShuffleToggle = {
                        musicPlayer.toggleShuffle()
                    },
                    onRepeatToggle = {
                        musicPlayer.toggleRepeatMode()
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
                    TextButton(onClick = { showNewPlaylistDialog = false }) {
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
    searchResults: List<Track>,
    isSearching: Boolean,
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
                val displayName =
                    if (searchResults.isNotEmpty()) {
                        "Search Results"
                    } else {
                        playlist.name
                    }

                val trackCount =
                    if (searchResults.isNotEmpty()) {
                        searchResults.size
                    } else {
                        playlist.tracks.size
                    }

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.h5,
                )
                Text(
                    text = "$trackCount songs",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                )
            }

            if (playlist.name == "All Music" && searchResults.isEmpty()) {
                IconButton(onClick = onRefreshMusic) {
                    Icon(Icons.Default.Refresh, "Refresh Music")
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        val tracksToDisplay = if (searchResults.isNotEmpty()) searchResults else playlist.tracks

        if (isSearching) {
            // Show loading indicator
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (tracksToDisplay.isEmpty()) {
            // Empty state
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
                            when {
                                searchResults.isEmpty() && playlist.name != "All Music" ->
                                    "This playlist is empty.\nAdd some tracks to get started!"
                                searchResults.isEmpty() && playlist.name == "All Music" ->
                                    "No music found in your music directory.\nClick the refresh button to scan for music files."
                                else -> "No tracks found matching your search."
                            },
                        style = MaterialTheme.typography.body1,
                        color = Color.Gray,
                    )
                }
            }
        } else {
            val trackListState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = trackListState) {
                    items(tracksToDisplay) { track ->
                        TrackItem(
                            track = track,
                            isCurrentTrack = track == currentTrack,
                            onClick = { onTrackSelected(track) },
                        )
                    }
                }

                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = trackListState),
                )
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        backgroundColor =
            if (isCurrentTrack) {
                MaterialTheme.colors.primary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colors.surface
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Album artwork or music note icon
            if (track.artworkPath != null && File(track.artworkPath).exists()) {
                val artwork =
                    remember(track.artworkPath) {
                        loadArtwork(track.artworkPath)
                    }
                if (artwork != null) {
                    Image(
                        bitmap = artwork,
                        contentDescription = "Album Art",
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = "Track",
                        modifier = Modifier.size(56.dp),
                        tint = if (isCurrentTrack) MaterialTheme.colors.primary else Color.Gray,
                    )
                }
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = "Track",
                    modifier = Modifier.size(56.dp),
                    tint = if (isCurrentTrack) MaterialTheme.colors.primary else Color.Gray,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.body1,
                    color = if (isCurrentTrack) MaterialTheme.colors.primary else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.album != "Unknown Album") {
                    Text(
                        text = track.album,
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

private fun loadArtwork(artworkPath: String): ImageBitmap? =
    try {
        val bufferedImage = ImageIO.read(File(artworkPath))
        bufferedImage?.toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }

@Composable
fun PlayerControls(
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    volume: Float,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        elevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            currentTrack?.let { track ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Album artwork or music note icon
                    if (track.artworkPath != null && File(track.artworkPath).exists()) {
                        val artwork =
                            remember(track.artworkPath) {
                                loadArtwork(track.artworkPath)
                            }
                        if (artwork != null) {
                            Image(
                                bitmap = artwork,
                                contentDescription = "Album Art",
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "Track",
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colors.primary,
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = "Track",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colors.primary,
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.h6,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.body2,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (track.album != "Unknown Album") {
                            Text(
                                text = track.album,
                                style = MaterialTheme.typography.caption,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (duration > 0) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Seekable progress bar
                    var seekPosition by remember { mutableStateOf(currentPosition) }
                    var isSeeking by remember { mutableStateOf(false) }

                    Slider(
                        value = if (isSeeking) seekPosition.toFloat() else currentPosition.toFloat(),
                        onValueChange = {
                            seekPosition = it.toLong()
                            isSeeking = true
                        },
                        onValueChangeFinished = {
                            onSeek(seekPosition)
                            isSeeking = false
                        },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatTime(if (isSeeking) seekPosition else currentPosition),
                            style = MaterialTheme.typography.caption,
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.caption,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Main Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Shuffle button
                IconButton(
                    onClick = onShuffleToggle,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        "Shuffle",
                        tint = if (isShuffleEnabled) MaterialTheme.colors.primary else Color.Gray,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        "Previous",
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        "Next",
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Repeat button
                IconButton(
                    onClick = onRepeatToggle,
                    modifier = Modifier.size(48.dp),
                ) {
                    val (icon, tint) =
                        when (repeatMode) {
                            RepeatMode.OFF -> Icons.Default.Repeat to Color.Gray
                            RepeatMode.ALL -> Icons.Default.Repeat to MaterialTheme.colors.primary
                            RepeatMode.ONE -> Icons.Default.RepeatOne to MaterialTheme.colors.primary
                        }
                    Icon(
                        icon,
                        "Repeat",
                        tint = tint,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // Volume Control
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.VolumeDown,
                    contentDescription = "Volume",
                    modifier = Modifier.size(24.dp),
                )
                Slider(
                    value = volume,
                    onValueChange = onVolumeChanged,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    valueRange = 0f..1f,
                )
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = "Volume",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}
