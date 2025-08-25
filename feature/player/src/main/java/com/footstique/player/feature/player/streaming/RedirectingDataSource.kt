package com.footstique.player.feature.player.streaming

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * A DataSource that handles HTTP 302 redirects for segmented live streaming.
 * 
 * This implementation:
 * 1. Handles redirect responses to get actual segment URLs
 * 2. Supports preloading of next segments
 * 3. Manages seamless transitions between segments
 */
@UnstableApi
class RedirectingDataSource(
    private val baseDataSource: HttpDataSource,
    private val segmentManager: SegmentManager,
    private val originalUri: Uri
) : DataSource {

    private var dataSpec: DataSpec? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET
    private var opened = false
    private var preloadJob: Job? = null
    private var currentSegmentUri: Uri? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun addTransferListener(transferListener: TransferListener) {
        baseDataSource.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        
        try {
            // Get the actual segment URL through redirect
            val actualUri = resolveRedirect(dataSpec.uri)
            currentSegmentUri = actualUri
            val redirectedDataSpec = dataSpec.buildUpon().setUri(actualUri).build()
            
            // Open the base data source with the redirected URI
            val bytesReturned = baseDataSource.open(redirectedDataSpec)
            bytesRemaining = if (bytesReturned == C.LENGTH_UNSET) C.LENGTH_UNSET else bytesReturned
            opened = true
            
            // Schedule preloading of next segment
            scheduleNextSegmentPreload(actualUri)
            
            return bytesReturned
        } catch (e: Exception) {
            Timber.e(e, "Failed to open redirecting data source")
            throw IOException("Failed to resolve redirect", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!opened) {
            throw IOException("DataSource not opened")
        }
        
        val bytesRead = baseDataSource.read(buffer, offset, length)
        
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            // Current segment ended, try to transition to next segment
            return handleSegmentTransition(buffer, offset, length)
        }
        
        if (bytesRead != C.RESULT_END_OF_INPUT && bytesRemaining != C.LENGTH_UNSET) {
            bytesRemaining -= bytesRead.toLong()
        }
        
        return bytesRead
    }

    override fun getUri(): Uri? = baseDataSource.uri

    @Throws(IOException::class)
    override fun close() {
        if (opened) {
            try {
                baseDataSource.close()
                preloadJob?.cancel()
            } finally {
                opened = false
                dataSpec = null
                currentSegmentUri = null
                bytesRemaining = C.LENGTH_UNSET
            }
        }
    }

    /**
     * Resolves HTTP 302 redirect to get the actual segment URL
     */
    private fun resolveRedirect(uri: Uri): Uri {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(uri.toString()).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000 // 5 seconds timeout
            connection.readTimeout = 5000    // 5 seconds timeout
            
            val responseCode = connection.responseCode
            when (responseCode) {
                HttpURLConnection.HTTP_MOVED_TEMP, 
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_SEE_OTHER,
                307, 308 -> {
                    val location = connection.getHeaderField("Location")
                    if (location != null) {
                        val redirectUri = if (location.startsWith("http")) {
                            Uri.parse(location)
                        } else {
                            // Handle relative redirects
                            val baseUri = "${uri.scheme}://${uri.host}"
                            val port = if (uri.port != -1) ":${uri.port}" else ""
                            Uri.parse("$baseUri$port$location")
                        }
                        Timber.d("Redirect from $uri to $redirectUri")
                        redirectUri
                    } else {
                        Timber.w("Redirect response but no Location header found")
                        uri
                    }
                }
                HttpURLConnection.HTTP_OK -> {
                    // Direct access without redirect
                    Timber.d("Direct access (no redirect): $uri")
                    uri
                }
                else -> {
                    Timber.w("Unexpected response code: $responseCode for $uri")
                    uri
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to resolve redirect for $uri")
            // Fallback to original URI on error
            uri
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Handles transition to the next segment when current segment ends
     */
    private fun handleSegmentTransition(buffer: ByteArray, offset: Int, length: Int): Int {
        try {
            Timber.d("Current segment ended, transitioning to next segment")
            
            // Record completion of current segment
            if (currentSegmentUri != null) {
                val currentSegmentInfo = SegmentManager.SegmentInfo(
                    uri = currentSegmentUri!!,
                    preloaded = false,
                    timestamp = System.currentTimeMillis()
                )
                
                // Record completion asynchronously to avoid blocking read operation
                coroutineScope.launch {
                    segmentManager.recordSegmentCompletion(currentSegmentInfo)
                }
            }
            
            // Close current data source
            baseDataSource.close()
            
            // Get next segment URL through redirect
            val nextSegmentUri = resolveRedirect(originalUri)
            
            // Check if we got a different segment (not the same as current)
            if (nextSegmentUri != currentSegmentUri) {
                Timber.d("Transitioning to next segment: $nextSegmentUri")
                
                // Open next segment
                val nextDataSpec = dataSpec?.buildUpon()?.setUri(nextSegmentUri)?.build()
                if (nextDataSpec != null) {
                    val nextBytesReturned = baseDataSource.open(nextDataSpec)
                    bytesRemaining = if (nextBytesReturned == C.LENGTH_UNSET) C.LENGTH_UNSET else nextBytesReturned
                    currentSegmentUri = nextSegmentUri
                    
                    // Try to read from the new segment immediately
                    val bytesRead = baseDataSource.read(buffer, offset, length)
                    
                    if (bytesRead != C.RESULT_END_OF_INPUT) {
                        if (bytesRemaining != C.LENGTH_UNSET) {
                            bytesRemaining -= bytesRead.toLong()
                        }
                        
                        // Schedule preloading of the segment after this one
                        scheduleNextSegmentPreload(nextSegmentUri)
                        
                        return bytesRead
                    }
                }
            }
            
            // If we couldn't transition to next segment or got the same URI, 
            // wait a moment and try again (for live streams)
            Thread.sleep(100) // Brief pause before retry
            val retrySegmentUri = resolveRedirect(originalUri)
            
            if (retrySegmentUri != currentSegmentUri) {
                Timber.d("Retrying with new segment: $retrySegmentUri")
                val retryDataSpec = dataSpec?.buildUpon()?.setUri(retrySegmentUri)?.build()
                if (retryDataSpec != null) {
                    val retryBytesReturned = baseDataSource.open(retryDataSpec)
                    bytesRemaining = if (retryBytesReturned == C.LENGTH_UNSET) C.LENGTH_UNSET else retryBytesReturned
                    currentSegmentUri = retrySegmentUri
                    
                    val bytesRead = baseDataSource.read(buffer, offset, length)
                    if (bytesRead != C.RESULT_END_OF_INPUT) {
                        if (bytesRemaining != C.LENGTH_UNSET) {
                            bytesRemaining -= bytesRead.toLong()
                        }
                        scheduleNextSegmentPreload(retrySegmentUri)
                        return bytesRead
                    }
                }
            }
            
            // If all attempts failed, signal end of stream
            Timber.w("Could not transition to next segment, ending stream")
            return C.RESULT_END_OF_INPUT
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to transition to next segment")
            return C.RESULT_END_OF_INPUT
        }
    }

    /**
     * Schedules preloading of the next segment based on dynamic timing
     */
    private fun scheduleNextSegmentPreload(currentSegmentUri: Uri) {
        preloadJob?.cancel()
        preloadJob = coroutineScope.launch {
            try {
                // Try to estimate segment duration from current playback
                // For now, use a default of 6 seconds (typical segment length)
                val estimatedDuration = 6000L
                val preloadDelay = segmentManager.calculatePreloadTiming(estimatedDuration)
                
                Timber.d("Scheduling next segment preload in ${preloadDelay}ms")
                delay(preloadDelay)
                
                // Request next segment redirect
                val nextSegmentUri = resolveRedirect(originalUri)
                if (nextSegmentUri != currentSegmentUri) {
                    Timber.d("Preloading next segment: $nextSegmentUri")
                    segmentManager.preloadSegment(nextSegmentUri)
                } else {
                    Timber.d("Next segment URI same as current, will retry in 1 second")
                    delay(1000)
                    // Try once more
                    val retrySegmentUri = resolveRedirect(originalUri)
                    if (retrySegmentUri != currentSegmentUri) {
                        segmentManager.preloadSegment(retrySegmentUri)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to preload next segment")
            }
        }
    }
}