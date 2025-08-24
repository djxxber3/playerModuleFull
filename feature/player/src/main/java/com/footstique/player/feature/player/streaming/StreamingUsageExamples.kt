package com.footstique.player.feature.player.streaming

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi

/**
 * Usage examples for the segmented live streaming implementation
 */
@UnstableApi
object StreamingUsageExamples {
    
    /**
     * Basic usage example - just provide the base URL that responds with redirects
     */
    fun basicUsage(mediaController: androidx.media3.session.MediaController) {
        // The server at this URL should respond with HTTP 302 redirects
        val liveStreamUrl = "http://exemple.com/454674/654545"
        
        // Create MediaItem with live configuration
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(liveStreamUrl))
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(3000)  // 3 second buffer
                    .setMinOffsetMs(1000)     // Minimum buffer
                    .setMaxOffsetMs(10000)    // Maximum buffer
                    .build()
            )
            .build()
        
        // Start playback - the system will automatically handle redirects and preloading
        mediaController.setMediaItem(mediaItem)
        mediaController.prepare()
        mediaController.play()
    }
    
    /**
     * Advanced usage with custom configuration
     */
    fun advancedUsage(mediaController: androidx.media3.session.MediaController) {
        val liveStreamUrl = "http://your-server.com/live/stream/endpoint"
        
        // Enhanced live configuration for optimal streaming
        val liveConfiguration = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(2000)      // Shorter buffer for lower latency
            .setMinOffsetMs(500)          // Minimum buffer
            .setMaxOffsetMs(8000)         // Maximum buffer
            .setMinPlaybackSpeed(0.98f)   // Slightly slower to catch up
            .setMaxPlaybackSpeed(1.02f)   // Slightly faster to catch up
            .build()
        
        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle("Live Stream")
            .setDisplayTitle("Segmented Live Stream")
            .build()
        
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(liveStreamUrl))
            .setLiveConfiguration(liveConfiguration)
            .setMediaMetadata(mediaMetadata)
            .build()
        
        mediaController.setMediaItem(mediaItem)
        mediaController.prepare()
        mediaController.play()
    }
    
    /**
     * Example of how to detect if a URL is suitable for segmented live streaming
     */
    fun detectLiveStreamType(url: String): StreamType {
        return when {
            url.contains(".m3u8") -> StreamType.HLS
            url.contains(".mpd") -> StreamType.DASH
            url.startsWith("rtmp://") -> StreamType.RTMP
            url.startsWith("http") && !url.contains(".") -> StreamType.SEGMENTED_REDIRECT
            else -> StreamType.REGULAR_VIDEO
        }
    }
    
    enum class StreamType {
        HLS,                    // HTTP Live Streaming
        DASH,                   // Dynamic Adaptive Streaming over HTTP  
        RTMP,                   // Real-Time Messaging Protocol
        SEGMENTED_REDIRECT,     // Our custom segmented streaming with redirects
        REGULAR_VIDEO           // Regular video file
    }
    
    /**
     * Example of monitoring stream health and handling issues
     */
    fun monitorStreamHealth(mediaController: androidx.media3.session.MediaController) {
        // Add listener to monitor playback state
        mediaController.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    androidx.media3.common.Player.STATE_BUFFERING -> {
                        // Stream is buffering - this is normal for live streams
                        println("Stream buffering...")
                    }
                    androidx.media3.common.Player.STATE_READY -> {
                        // Stream is playing smoothly
                        println("Stream playing smoothly")
                    }
                    androidx.media3.common.Player.STATE_ENDED -> {
                        // Live stream ended (unusual)
                        println("Live stream ended")
                    }
                }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Handle streaming errors
                when (error.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                        println("Network connection failed - check internet connectivity")
                    }
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                        println("Network timeout - server may be slow")
                    }
                    else -> {
                        println("Playback error: ${error.message}")
                    }
                }
            }
        })
    }
}