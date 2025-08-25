package com.footstique.player.feature.player.streaming

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Test class for RedirectingDataSource segment continuation functionality
 */
@UnstableApi
class RedirectingDataSourceTest {

    private lateinit var segmentManager: SegmentManager

    @Before
    fun setUp() {
        segmentManager = SegmentManager()
    }

    @After
    fun tearDown() {
        runBlocking {
            segmentManager.clearCache()
        }
    }

    @Test
    fun testSegmentManagerIntegration() = runBlocking {
        // Test that segment completion is recorded properly
        val testUri = Uri.parse("http://cdn.example.com/chunk-test.ts")
        
        // Preload a segment
        segmentManager.preloadSegment(testUri)
        Thread.sleep(100) // Wait for async operation
        
        assertTrue("Segment should be preloaded", segmentManager.isSegmentPreloaded(testUri))
        
        // Record completion
        val segmentInfo = SegmentManager.SegmentInfo(
            uri = testUri,
            startPts = 0L,
            endPts = 4000L,
            duration = 4000L
        )
        
        segmentManager.recordSegmentCompletion(segmentInfo)
        
        // Segment should be removed from preloaded cache after completion
        assertFalse("Segment should not be preloaded after completion", 
                   segmentManager.isSegmentPreloaded(testUri))
    }

    @Test
    fun testSegmentTransitionSequence() = runBlocking {
        // Test the sequence of segment transitions
        val segment1Uri = Uri.parse("http://cdn.example.com/chunk-1.ts")
        val segment2Uri = Uri.parse("http://cdn.example.com/chunk-2.ts")
        val segment3Uri = Uri.parse("http://cdn.example.com/chunk-3.ts")
        
        // Preload all segments
        segmentManager.preloadSegment(segment1Uri)
        segmentManager.preloadSegment(segment2Uri)
        segmentManager.preloadSegment(segment3Uri)
        
        Thread.sleep(200) // Wait for async operations
        
        // All should be preloaded
        assertTrue("Segment 1 should be preloaded", segmentManager.isSegmentPreloaded(segment1Uri))
        assertTrue("Segment 2 should be preloaded", segmentManager.isSegmentPreloaded(segment2Uri))
        assertTrue("Segment 3 should be preloaded", segmentManager.isSegmentPreloaded(segment3Uri))
        
        // Complete segments in order
        val segment1Info = SegmentManager.SegmentInfo(
            uri = segment1Uri,
            startPts = 0L,
            endPts = 4000L,
            duration = 4000L
        )
        
        val segment2Info = SegmentManager.SegmentInfo(
            uri = segment2Uri,
            startPts = 4000L,
            endPts = 8000L,
            duration = 4000L
        )
        
        segmentManager.recordSegmentCompletion(segment1Info)
        segmentManager.recordSegmentCompletion(segment2Info)
        
        // Completed segments should be removed from cache
        assertFalse("Segment 1 should not be preloaded after completion", 
                   segmentManager.isSegmentPreloaded(segment1Uri))
        assertFalse("Segment 2 should not be preloaded after completion", 
                   segmentManager.isSegmentPreloaded(segment2Uri))
        
        // Segment 3 should still be preloaded
        assertTrue("Segment 3 should still be preloaded", 
                   segmentManager.isSegmentPreloaded(segment3Uri))
    }

    @Test
    fun testPreloadTimingCalculation() {
        // Test preload timing for different segment durations
        val testCases = mapOf(
            5000L to 3000L,   // Short segment
            10000L to 3000L,  // Medium segment  
            20000L to 5000L,  // Long segment
            40000L to 8000L,  // Very long segment
            2000L to 3000L,   // Very short segment (minimum)
            100000L to 8000L  // Extremely long segment (maximum)
        )
        
        testCases.forEach { (duration, expectedTiming) ->
            val actualTiming = segmentManager.calculatePreloadTiming(duration)
            assertEquals("Preload timing for ${duration}ms segment should be ${expectedTiming}ms", 
                        expectedTiming, actualTiming)
        }
    }

    @Test
    fun testNextSegmentUriGeneration() {
        val currentUri = Uri.parse("http://cdn.example.com/chunk-1692951223.ts")
        val baseUri = Uri.parse("http://example.com/live/stream")
        
        val nextUri = segmentManager.getNextExpectedSegmentUri(currentUri, baseUri)
        
        // Should return the base URI for redirect-based streaming
        assertEquals("Next segment URI should be the base URI for redirects", 
                    baseUri, nextUri)
    }
}