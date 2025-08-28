# Music Player UI Preview

## Main Interface Layout

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Music Player - Supports Opus, M4A and More         │
├──────────────────┬──────────────────────────────────────────────────────────┤
│ Playlists        │  Track List - All Music                                   │
│ ──────────       │  ──────────────────────                                   │
│ 📂 All Music ◄   │  🎵 Song1.opus      [OPUS] ⚠️ Needs library     ▶️       │
│ 📂 Favorites     │  🎵 Song2.m4a       [M4A]  ⚠️ Needs library             │
│ 📂 My Opus       │  🎵 Song3.mp3       [MP3]  ⚠️ Needs library             │
│ 📂 High Qual M4A │  🎵 Song4.wav       [WAV]  ✅ Supported                  │
│                  │  🎵 Song5.flac      [FLAC] ⚠️ Needs library             │
│ ➕ Create        │                                                            │
│ ℹ️ About         │  🔄 Refresh Music Library                                │
│                  │                                                            │
├──────────────────┼──────────────────────────────────────────────────────────┤
│                  │  ──────────── Player Controls ────────────                │
│                  │  🎵 Song1.opus - Test Artist • Test Album                │
│                  │  [0:00] ████████████████████████████████████ [3:45]      │
│                  │                                                            │
│                  │  🔀 ⏮️ ▶️/⏸️ ⏭️ 🔁    🔊 ████████████ [80%]          │
│                  │  OPUS - ⚠️ Needs library                                │
└──────────────────┴──────────────────────────────────────────────────────────┘
```

## Key UI Elements

### Sidebar Features:
- **Playlist Management**: Create, select, and delete playlists
- **Default Playlists**: "All Music" and "Favorites" automatically created
- **About Dialog**: Shows format support status and implementation details

### Track List Features:
- **Format Indicators**: Color-coded badges showing file format
- **Support Status**: Visual indicators for playback capability
- **File Information**: Artist, album, file size, and format details
- **Refresh Button**: Re-scan music directory for new files

### Player Controls:
- **Progress Bar**: Shows current position with seek capability
- **Standard Controls**: Play, pause, stop, next, previous
- **Advanced Features**: Shuffle, repeat modes, volume control
- **Format Status**: Real-time display of current track's format support

### Dialog Boxes:
- **Create Playlist**: Simple name input with validation
- **About Dialog**: Comprehensive format support information
- **Download Dialog**: Search and download from YouTube/Spotify (framework ready)

## Format Support Visual System

| Color | Meaning | Formats |
|-------|---------|---------|
| 🟢 Green | Ready to play | WAV, AU, AIFF |
| 🔵 Blue | Priority formats (Opus, M4A) | OPUS, M4A |
| 🟠 Orange | Other formats needing libraries | MP3, FLAC, OGG, AAC |

## Real-time Features
- **Live Progress**: Updates every second during playback
- **Instant Feedback**: Format support shown immediately
- **Dynamic Lists**: Playlists update automatically when modified
- **Status Updates**: Player state changes reflected in UI immediately

This interface provides complete music management with clear indication of Opus and M4A support status!