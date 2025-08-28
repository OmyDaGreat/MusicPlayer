package xyz.malefic.compose.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.malefic.compose.util.DownloadManager
import xyz.malefic.compose.util.MusicManager
import xyz.malefic.compose.util.SearchResult
import xyz.malefic.compose.util.YtDlpNotInstalledException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

@Composable
fun SearchDownloadScreen(
    musicManager: MusicManager,
    onTrackDownloaded: () -> Unit,
) {
    val downloadManager = remember { DownloadManager() }
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var downloadingItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var showYtDlpWarning by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            downloadManager.close()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        // Title
        Text(
            text = "Search & Download Music",
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Search Section
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            elevation = 4.dp,
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = "YouTube Search",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search for music...") },
                        placeholder = { Text("Artist - Song Title") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSearching,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                scope.launch {
                                    isSearching = true
                                    try {
                                        searchResults = downloadManager.searchYouTube(searchQuery)
                                        downloadStatus =
                                            if (searchResults.isEmpty()) {
                                                "No results found"
                                            } else {
                                                "Found ${searchResults.size} results"
                                            }
                                    } catch (e: Exception) {
                                        downloadStatus = "Search failed: ${e.message}"
                                        searchResults = emptyList()
                                    } finally {
                                        isSearching = false
                                    }
                                }
                            }
                        },
                        enabled = searchQuery.isNotBlank() && !isSearching,
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colors.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSearching) "Searching..." else "Search")
                    }
                }

                downloadStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        // Search Results
        if (searchResults.isNotEmpty()) {
            Text(
                text = "Search Results",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(searchResults) { result ->
                    SearchResultItem(
                        result = result,
                        isDownloading = downloadingItems.contains(result.videoId),
                        onDownload = {
                            scope.launch {
                                downloadingItems = downloadingItems + result.videoId
                                try {
                                    downloadManager.downloadFromYouTube(result)
                                    downloadStatus = "Downloaded: ${result.title}"
                                    onTrackDownloaded()
                                } catch (e: YtDlpNotInstalledException) {
                                    downloadStatus = null
                                    showYtDlpWarning = true
                                } catch (e: Exception) {
                                    downloadStatus = "Download failed: ${e.message}"
                                } finally {
                                    downloadingItems = downloadingItems - result.videoId
                                }
                            }
                        },
                    )
                }
            }
        }

        // Instructions when no results
        if (searchResults.isEmpty() && !isSearching) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = "Music",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Search for music to download",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    )
                    Text(
                        text = "Try searching for \"Artist - Song\" or just the song title",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }

    // yt-dlp Installation Warning Dialog
    if (showYtDlpWarning) {
        AlertDialog(
            onDismissRequest = { showYtDlpWarning = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colors.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("yt-dlp Not Installed")
                }
            },
            text = {
                Column {
                    Text("yt-dlp is required to download music from YouTube. Please install it to continue.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Installation instructions:",
                        style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• Windows: Download from github.com/yt-dlp/yt-dlp/releases\n" +
                            "• macOS: brew install yt-dlp\n" +
                            "• Linux: pip install yt-dlp",
                        style = MaterialTheme.typography.body2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showYtDlpWarning = false },
                ) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    isDownloading: Boolean,
    onDownload: () -> Unit,
) {
    val thumbnailState = remember { mutableStateOf<ImageBitmap?>(null) }
    val httpClient = remember { HttpClient(CIO) }

    // Load thumbnail
    LaunchedEffect(result.thumbnail) {
        try {
            withContext(Dispatchers.IO) {
                val response = httpClient.get(result.thumbnail)
                val bytes = response.bodyAsChannel().toByteArray()
                val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
                bufferedImage?.let {
                    thumbnailState.value = it.toComposeImageBitmap()
                }
            }
        } catch (e: Exception) {
            println("Failed to load thumbnail for ${result.title}: ${e.message}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // HttpClient cleanup is handled by parent component
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail or placeholder
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                thumbnailState.value?.let { thumbnail ->
                    Image(
                        bitmap = thumbnail,
                        contentDescription = "Video thumbnail",
                        modifier = Modifier.fillMaxSize(),
                    )
                } ?: Icon(
                    Icons.Default.MusicVideo,
                    contentDescription = "Video thumbnail",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colors.primary,
                )
            }

            // Track info
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = result.artist,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = result.duration,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                )
            }

            // Download button
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                )
            } else {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colors.primary,
                    )
                }
            }
        }
    }
}
