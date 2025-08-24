package com.footstique.player.feature.player.streaming

/**
 * Configuration class for segmented live streaming parameters
 * 
 * Allows easy customization of streaming behavior without modifying core code
 */
data class StreamingConfig(
    // Preloading settings
    val preloadEnabled: Boolean = true,
    val preloadMinTimeMs: Long = 3000L,        // Minimum 3 seconds before segment end
    val preloadMaxTimeMs: Long = 8000L,        // Maximum 8 seconds before segment end
    val preloadPercentage: Float = 0.25f,      // Preload when 25% of segment remains
    
    // Cache management
    val maxPreloadedSegments: Int = 3,         // Keep max 3 preloaded segments
    val segmentCacheMaxAgeMs: Long = 30000L,   // 30 seconds max age for cached segments
    val segmentHistorySize: Int = 10,          // Keep history of last 10 segments
    
    // Network settings
    val redirectTimeoutMs: Int = 5000,         // 5 seconds timeout for redirect requests
    val maxRedirectRetries: Int = 3,           // Maximum redirect retry attempts
    val allowCrossProtocolRedirects: Boolean = true,
    
    // PTS continuity
    val ptsToleranceMs: Long = 1000L,          // 1ms tolerance for PTS continuity
    val enablePtsContinuityCheck: Boolean = true,
    
    // Live streaming offsets
    val targetOffsetMs: Long = 3000L,          // 3 seconds target offset from live edge
    val minOffsetMs: Long = 1000L,             // Minimum 1 second offset
    val maxOffsetMs: Long = 10000L,            // Maximum 10 seconds offset
    
    // Playback speed adjustments for live sync
    val minPlaybackSpeed: Float = 0.97f,       // Slightly slower to catch up
    val maxPlaybackSpeed: Float = 1.03f,       // Slightly faster to catch up
    
    // User agent and headers
    val userAgent: String = "ExoPlayer-LiveStream/1.0",
    val additionalHeaders: Map<String, String> = emptyMap(),
    
    // Debug and logging
    val enableDebugLogging: Boolean = true,
    val logSegmentTransitions: Boolean = true,
    val logPtsContinuity: Boolean = true
) {
    companion object {
        /**
         * Default configuration optimized for most live streaming scenarios
         */
        fun default() = StreamingConfig()
        
        /**
         * Low latency configuration for real-time streaming
         */
        fun lowLatency() = StreamingConfig(
            preloadMinTimeMs = 1500L,
            preloadMaxTimeMs = 4000L,
            targetOffsetMs = 1500L,
            minOffsetMs = 500L,
            maxOffsetMs = 5000L,
            maxPreloadedSegments = 2
        )
        
        /**
         * High stability configuration for unreliable networks
         */
        fun highStability() = StreamingConfig(
            preloadMinTimeMs = 5000L,
            preloadMaxTimeMs = 12000L,
            targetOffsetMs = 6000L,
            minOffsetMs = 2000L,
            maxOffsetMs = 15000L,
            maxPreloadedSegments = 5,
            redirectTimeoutMs = 10000,
            maxRedirectRetries = 5
        )
        
        /**
         * Minimal resource usage configuration
         */
        fun minimal() = StreamingConfig(
            maxPreloadedSegments = 1,
            segmentHistorySize = 5,
            enableDebugLogging = false,
            logSegmentTransitions = false,
            logPtsContinuity = false
        )
    }
    
    /**
     * Validates the configuration and returns a corrected version if needed
     */
    fun validate(): StreamingConfig {
        return copy(
            preloadMinTimeMs = preloadMinTimeMs.coerceAtLeast(1000L),
            preloadMaxTimeMs = preloadMaxTimeMs.coerceAtLeast(preloadMinTimeMs),
            maxPreloadedSegments = maxPreloadedSegments.coerceAtLeast(1),
            segmentHistorySize = segmentHistorySize.coerceAtLeast(1),
            redirectTimeoutMs = redirectTimeoutMs.coerceAtLeast(1000),
            maxRedirectRetries = maxRedirectRetries.coerceAtLeast(1),
            ptsToleranceMs = ptsToleranceMs.coerceAtLeast(0L),
            targetOffsetMs = targetOffsetMs.coerceAtLeast(minOffsetMs),
            maxOffsetMs = maxOffsetMs.coerceAtLeast(targetOffsetMs),
            minPlaybackSpeed = minPlaybackSpeed.coerceIn(0.5f, 1.0f),
            maxPlaybackSpeed = maxPlaybackSpeed.coerceIn(1.0f, 2.0f)
        )
    }
    
    /**
     * Calculates preload timing based on segment duration
     */
    fun calculatePreloadTiming(segmentDurationMs: Long): Long {
        val percentageBased = (segmentDurationMs * preloadPercentage).toLong()
        return percentageBased.coerceIn(preloadMinTimeMs, preloadMaxTimeMs)
    }
}