package com.footstique.player.feature.player.streaming

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages segment preloading and continuity for seamless live streaming.
 * 
 * This class:
 * 1. Preloads segments before they are needed
 * 2. Tracks PTS (Presentation Time Stamps) for continuity
 * 3. Ensures smooth transitions between segments
 */
@UnstableApi
class SegmentManager {
    
    private val preloadedSegments = ConcurrentHashMap<Uri, SegmentInfo>()
    private val segmentHistory = mutableListOf<SegmentInfo>()
    private val mutex = Mutex()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Data class representing segment information
     */
    data class SegmentInfo(
        val uri: Uri,
        val startPts: Long = 0L,
        val endPts: Long = 0L,
        val duration: Long = 0L,
        val preloaded: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Preloads a segment for future use
     */
    suspend fun preloadSegment(uri: Uri) {
        mutex.withLock {
            if (preloadedSegments.containsKey(uri)) {
                Timber.d("Segment already preloaded: $uri")
                return
            }
            
            coroutineScope.launch {
                try {
                    // Here we would normally analyze the segment for PTS info
                    // For now, we'll store basic info and mark as preloaded
                    val segmentInfo = SegmentInfo(
                        uri = uri,
                        preloaded = true,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    preloadedSegments[uri] = segmentInfo
                    Timber.d("Successfully preloaded segment: $uri")
                    
                    // Clean up old preloaded segments (keep only last 3)
                    cleanupOldSegments()
                    
                } catch (e: Exception) {
                    Timber.e(e, "Failed to preload segment: $uri")
                }
            }
        }
    }
    
    /**
     * Checks if a segment is already preloaded
     */
    fun isSegmentPreloaded(uri: Uri): Boolean {
        return preloadedSegments.containsKey(uri)
    }
    
    /**
     * Gets preloaded segment info
     */
    fun getPreloadedSegment(uri: Uri): SegmentInfo? {
        return preloadedSegments[uri]
    }
    
    /**
     * Records segment completion and analyzes PTS for continuity
     */
    suspend fun recordSegmentCompletion(segmentInfo: SegmentInfo) {
        mutex.withLock {
            segmentHistory.add(segmentInfo)
            
            // Analyze PTS continuity
            if (segmentHistory.size >= 2) {
                val previousSegment = segmentHistory[segmentHistory.size - 2]
                val currentSegment = segmentHistory[segmentHistory.size - 1]
                
                analyzePtsContinuity(previousSegment, currentSegment)
            }
            
            // Keep only last 10 segments in history
            if (segmentHistory.size > 10) {
                segmentHistory.removeAt(0)
            }
            
            // Remove from preloaded cache
            preloadedSegments.remove(segmentInfo.uri)
        }
    }
    
    /**
     * Analyzes PTS continuity between segments
     */
    private fun analyzePtsContinuity(previous: SegmentInfo, current: SegmentInfo) {
        val expectedStartPts = previous.endPts
        val actualStartPts = current.startPts
        val ptsDifference = actualStartPts - expectedStartPts
        
        if (Math.abs(ptsDifference) > 1000) { // 1ms tolerance
            Timber.w(
                "PTS discontinuity detected: expected=$expectedStartPts, actual=$actualStartPts, " +
                "difference=$ptsDifference ms"
            )
            
            // Here you could implement corrective measures:
            // 1. Adjust playback timing
            // 2. Insert silence/black frames
            // 3. Request segment re-fetch
        } else {
            Timber.d("PTS continuity maintained: difference=$ptsDifference ms")
        }
    }
    
    /**
     * Cleans up old preloaded segments to prevent memory leaks
     */
    private fun cleanupOldSegments() {
        val maxPreloadedSegments = 3
        val currentTime = System.currentTimeMillis()
        val maxAge = 30000L // 30 seconds
        
        val segmentsToRemove = preloadedSegments.entries.filter { (_, segmentInfo) ->
            currentTime - segmentInfo.timestamp > maxAge
        }.take(preloadedSegments.size - maxPreloadedSegments)
        
        segmentsToRemove.forEach { (uri, _) ->
            preloadedSegments.remove(uri)
            Timber.d("Cleaned up old preloaded segment: $uri")
        }
    }
    
    /**
     * Gets the next expected segment based on the pattern
     */
    fun getNextExpectedSegmentUri(currentUri: Uri, baseUri: Uri): Uri {
        // This is a simplified implementation
        // In practice, you'd need to analyze the current segment URL pattern
        // and generate the next segment URL or use the base URI for redirects
        
        return baseUri // Always request the base URI for redirect
    }
    
    /**
     * Calculates optimal preload timing based on segment duration
     */
    fun calculatePreloadTiming(segmentDuration: Long): Long {
        // Preload when 25% of segment remains (minimum 3 seconds, maximum 8 seconds)
        val preloadTime = (segmentDuration * 0.25).toLong()
        return preloadTime.coerceIn(3000L, 8000L)
    }
    
    /**
     * Clears all cached data
     */
    suspend fun clearCache() {
        mutex.withLock {
            preloadedSegments.clear()
            segmentHistory.clear()
            Timber.d("Segment manager cache cleared")
        }
    }
}