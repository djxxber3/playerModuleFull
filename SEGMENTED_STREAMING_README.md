# Segmented Live Streaming Implementation

This implementation adds support for segmented live streaming with HTTP 302 redirects, preloading, and seamless transitions to the ExoPlayer-based video player.

## Features

### 1. HTTP 302 Redirect Handling
- Automatically handles redirect responses from the server
- Supports temporary URLs for media segments
- Maintains compatibility with existing streaming protocols

### 2. Segment Preloading
- Preloads next segments 3-4 seconds before current segment ends
- Configurable preload timing based on segment duration
- Memory-efficient caching with automatic cleanup

### 3. Seamless Transitions
- PTS (Presentation Time Stamps) continuity checking
- Smooth transitions between segments
- Discontinuity detection and handling

## How It Works

### Streaming Flow
1. ExoPlayer requests the base URL: `http://exemple.com/454674/654545`
2. Server responds with HTTP 302 Redirect to: `http://cdn.exemple.com/chunk-1692951223.ts`
3. ExoPlayer downloads and plays the segment
4. Before segment ends, the system requests the base URL again for the next segment
5. Process continues for live streaming

### Components

#### RedirectingDataSource
- Handles HTTP redirects transparently
- Manages segment requests and responses
- Schedules preloading operations

#### SegmentManager
- Tracks segment history and continuity
- Manages preloaded segments cache
- Analyzes PTS for seamless transitions

#### LiveStreamDataSourceFactory
- Factory pattern for creating redirect-capable data sources
- Configurable timeout and retry settings
- Automatic fallback for non-live streams

## Configuration

### PlayerService
The `PlayerService` has been enhanced to use the custom data source factory:

```kotlin
val liveStreamDataSourceFactory = LiveStreamDataSourceFactory.createDefault()
val defaultDataSourceFactory = DefaultDataSource.Factory(this, liveStreamDataSourceFactory)
val mediaSourceFactory = DefaultMediaSourceFactory(defaultDataSourceFactory)
```

### PlayerActivity
Enhanced with live streaming detection and optimized configuration:

```kotlin
private fun isSegmentedLiveStream(uri: Uri): Boolean {
    return (uri.toString().startsWith("http://") || uri.toString().startsWith("https://")) &&
           !uri.toString().contains(".m3u8") && // Not regular HLS
           !uri.toString().contains(".mpd")     // Not DASH
}
```

## Usage

### Basic Usage
The implementation is automatic and transparent. Simply provide a URL that responds with HTTP 302 redirects:

```kotlin
val uri = Uri.parse("http://exemple.com/454674/654545")
mediaController.setMediaItem(MediaItem.fromUri(uri))
```

### Advanced Configuration
For fine-tuning live stream behavior:

```kotlin
val liveConfiguration = MediaItem.LiveConfiguration.Builder()
    .setTargetOffsetMs(3000)     // 3 seconds offset
    .setMinOffsetMs(1000)        // Minimum 1 second
    .setMaxOffsetMs(10000)       // Maximum 10 seconds
    .setMinPlaybackSpeed(0.97f)  // Slightly slower to catch up
    .setMaxPlaybackSpeed(1.03f)  // Slightly faster to catch up
    .build()
```

## Testing

Run the included tests to verify functionality:

```bash
./gradlew :feature:player:test
```

The test suite includes:
- Segment preloading verification
- PTS continuity checking
- Cache management testing
- Redirect handling validation

## Troubleshooting

### Common Issues

1. **Segments not preloading**: Check network connectivity and server response times
2. **Playback discontinuities**: Verify PTS continuity in server-side segment generation
3. **Memory usage**: Adjust cache size in SegmentManager if needed

### Logging
Enable debug logging to monitor streaming behavior:

```kotlin
Timber.d("Redirect from $originalUri to $redirectedUri")
Timber.d("Preloading next segment: $nextSegmentUri")
Timber.d("PTS continuity maintained: difference=$ptsDifference ms")
```

## Performance Considerations

- Preloading uses background coroutines to avoid blocking the main thread
- Cache size is automatically managed to prevent memory leaks
- Network requests are optimized with configurable timeouts
- Only HTTP HEAD requests are used for redirect resolution to minimize bandwidth

## Compatibility

- Android API 21+
- Media3/ExoPlayer 1.8.0+
- Supports both HTTP and HTTPS protocols
- Compatible with existing media formats when not using redirects