package com.footstique.player.feature.player.streaming

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test class for SegmentManager functionality
 * 
 * Tests the core functionality of segment preloading and PTS continuity
 */
@UnstableApi
class SegmentManagerTest {

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
    fun testSegmentPreloading() = runBlocking {
        val testUri = Uri.parse("http://cdn.exemple.com/chunk-1692951223.ts")
        
        // Test preloading a segment
        segmentManager.preloadSegment(testUri)
        
        // Wait a bit for async operation
        Thread.sleep(100)
        
        // Verify segment is marked as preloaded
        assertTrue("Segment should be preloaded", segmentManager.isSegmentPreloaded(testUri))
        
        // Verify we can get the preloaded segment info
        val segmentInfo = segmentManager.getPreloadedSegment(testUri)
        assertNotNull("Preloaded segment info should not be null", segmentInfo)
        assertTrue("Segment should be marked as preloaded", segmentInfo?.preloaded == true)
    }

    @Test
    fun testSegmentCompletion() = runBlocking {
        val segmentInfo = SegmentManager.SegmentInfo(
            uri = Uri.parse("http://cdn.exemple.com/chunk-1692951223.ts"),
            startPts = 1000L,
            endPts = 5000L,
            duration = 4000L
        )
        
        // Record segment completion
        segmentManager.recordSegmentCompletion(segmentInfo)
        
        // Verify segment is no longer in preloaded cache
        assertFalse("Completed segment should not be in preloaded cache", 
                   segmentManager.isSegmentPreloaded(segmentInfo.uri))
    }

    @Test
    fun testPreloadTiming() {
        // Test different segment durations
        assertEquals("Short segment preload timing", 3000L, segmentManager.calculatePreloadTiming(10000L))
        assertEquals("Medium segment preload timing", 5000L, segmentManager.calculatePreloadTiming(20000L))
        assertEquals("Long segment preload timing", 8000L, segmentManager.calculatePreloadTiming(40000L))
        
        // Test edge cases
        assertEquals("Very short segment", 3000L, segmentManager.calculatePreloadTiming(2000L))
        assertEquals("Very long segment", 8000L, segmentManager.calculatePreloadTiming(100000L))
    }

    @Test
    fun testNextExpectedSegmentUri() {
        val currentUri = Uri.parse("http://cdn.exemple.com/chunk-1692951223.ts")
        val baseUri = Uri.parse("http://exemple.com/454674/654545")
        
        val nextUri = segmentManager.getNextExpectedSegmentUri(currentUri, baseUri)
        
        // Should return the base URI for redirect-based streaming
        assertEquals("Next segment should be base URI", baseUri, nextUri)
    }

    @Test
    fun testCacheCleaning() = runBlocking {
        // Preload multiple segments
        val uris = listOf(
            Uri.parse("http://cdn.exemple.com/chunk-1.ts"),
            Uri.parse("http://cdn.exemple.com/chunk-2.ts"),
            Uri.parse("http://cdn.exemple.com/chunk-3.ts"),
            Uri.parse("http://cdn.exemple.com/chunk-4.ts")
        )
        
        uris.forEach { uri ->
            segmentManager.preloadSegment(uri)
        }
        
        // Wait for preloading
        Thread.sleep(200)
        
        // Verify all segments are preloaded
        uris.forEach { uri ->
            assertTrue("Segment $uri should be preloaded", segmentManager.isSegmentPreloaded(uri))
        }
        
        // Clear cache
        segmentManager.clearCache()
        
        // Verify cache is cleared
        uris.forEach { uri ->
            assertFalse("Segment $uri should not be preloaded after cache clear", 
                       segmentManager.isSegmentPreloaded(uri))
        }
    }
}