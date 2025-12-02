# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android media player application using ExoPlayer 2.19.1 with background playback support. Features local (MediaStore) and remote media playback with notification controls.

**Tech Stack**:
- Kotlin 2.0.21
- Jetpack Compose + Material3
- ExoPlayer 2.19.1 (`com.google.android.exoplayer`)
- MVVM + Repository pattern
- minSdk: 29 (Android 10), targetSdk: 36

## Architecture

### MVVM + Repository Pattern

```
UI Layer (Compose)
    ↓ StateFlow
ViewModel Layer
    ↓ Service Connection
Service Layer (PlaybackService)
    ↓ ExoPlayer
Repository Layer
    ↓
Data Source (MediaStore / Remote URL)
```

### Core Components

**Service Layer** (`service/`):
- `PlaybackService`: Manages ExoPlayer instance, MediaSession, and foreground service for background playback
- `PlaybackServiceConnection`: Bridges ViewModel and Service using StateFlow

**ViewModel Layer** (`viewmodel/`):
- `PlayerViewModel`: Manages playback state (playing/paused/stopped), current media, and position
- `MediaViewModel`: Manages media list from repository

**Repository Layer** (`repository/`):
- `MediaRepositoryImpl`: Combines local (MediaStore) and remote data sources

**UI Layer** (`ui/`):
- `MediaListScreen`: Main screen with LazyColumn + mini player at bottom
- `FullPlayerScreen`: Expanded player (PlayerView for video, info + controls for audio)
- `MiniPlayer`: Fixed bottom bar showing current media and play/pause button

## Important Technical Decisions

### ExoPlayer 2.19.1 (Not Media3)

**Critical**: This project intentionally uses ExoPlayer 2.19.1 (`com.google.android.exoplayer`), not Media3 (`androidx.media3`). Do not upgrade to Media3 without explicit approval.

Reasons:
- Specific requirement to use ExoPlayer 2.19.1
- Uses `MediaSessionCompat` and `MediaSessionConnector`
- Namespace: `com.google.android.exoplayer2.*`

### Background Playback Implementation

Uses ForegroundService with `foregroundServiceType="mediaPlayback"` (required for Android 14+):
- Service starts immediately with `startForeground()` within 5 seconds
- Notification uses `MediaStyle` with play/pause/stop actions
- `MediaSessionConnector` synchronizes ExoPlayer with MediaSession

### State Management

- ViewModels use `StateFlow` for reactive UI updates
- ExoPlayer instance lives in Service (survives configuration changes)
- PlayerViewModel observes Player.Listener for state changes
- Position updates every 100ms via coroutine while playing

### Remote Media URLs

Fixed URLs defined in `util/Constants.kt`:
- Audio: `https://storage.googleapis.com/uamp/Kai_Engel_-_Irsens_Tale/01_-_Intro_udonthear.mp3`
- Video: `https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4`

## Common Development Tasks

### Building

```bash
# Build debug APK
./gradlew assembleDebug

# Install on device/emulator
./gradlew installDebug

# Build both debug and release
./gradlew build
```

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

### Permissions

The app requires runtime permissions:
- Android 13+ (API 33): `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `POST_NOTIFICATIONS`
- Android 12 and below: `READ_EXTERNAL_STORAGE`

MainActivity handles permission requests on startup using `ActivityResultContracts`.

## Key Files

**Critical for playback**:
- `service/PlaybackService.kt`: ExoPlayer + MediaSession integration, notification management
- `viewmodel/PlayerViewModel.kt`: UI state management, Service bridge
- `ui/component/PlayerView.kt`: AndroidView wrapper for ExoPlayer's PlayerView (video playback)

**Data flow**:
- `repository/MediaRepositoryImpl.kt`: Combines local and remote media sources
- `data/source/LocalMediaDataSource.kt`: MediaStore queries (ContentResolver)
- `data/source/RemoteMediaDataSource.kt`: Fixed URL list

**UI entry points**:
- `MainActivity.kt`: ViewModel initialization, Service binding, permission requests
- `ui/navigation/AppNavigation.kt`: NavHost managing MediaListScreen ↔ FullPlayerScreen

## Project-Specific Notes

### AndroidManifest.xml

Ensure these are present:
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions
- `PlaybackService` with `foregroundServiceType="mediaPlayback"`
- `android:exported="false"` on Service

### gradle Dependencies

Version catalog (`gradle/libs.versions.toml`) includes:
- ExoPlayer 2.19.1 (core, ui, extension-mediasession)
- Material Icons Extended (for UI icons)
- Navigation Compose
- ViewModel Compose

### Jetpack Compose Integration

**PlayerView integration**: Uses `AndroidView` with `DisposableEffect` for proper lifecycle management:
```kotlin
DisposableEffect(player) {
    playerView.player = player
    onDispose { playerView.player = null }
}
```

**Mini Player**: Always visible when media is playing, tapping expands to full player via Navigation.

### MediaStore Queries

- Runs on `Dispatchers.IO` using `withContext`
- Uses `ContentUris.withAppendedId()` to build URIs
- Handles null safety for title/artist fields
- Catches exceptions silently (logs errors)

## Troubleshooting

**Build errors with ExoPlayer**:
- Ensure using `com.google.android.exoplayer` (not `androidx.media3`)
- Check version is exactly `2.19.1`

**Playback not working**:
- Verify permissions granted (check Settings → Apps → Permissions)
- Check Service is running (`adb shell dumpsys activity services`)
- Verify MediaSession is active (`adb shell dumpsys media_session`)

**Background playback stops**:
- Ensure foregroundServiceType is set in AndroidManifest
- Check notification is visible (required for foreground service)
- Verify device battery optimization settings

**No local media showing**:
- Check READ_MEDIA_AUDIO/VIDEO permissions
- Verify device has media files (use built-in media player to confirm)
- Check MediaStore queries in Logcat for errors
