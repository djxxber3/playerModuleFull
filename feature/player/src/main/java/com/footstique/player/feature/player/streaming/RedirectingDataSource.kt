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
                bytesRemaining = C.LENGTH_UNSET
            }
        }
    }

    /**
     * Resolves HTTP 302 redirect to get the actual segment URL
     */
    private fun resolveRedirect(uri: Uri): Uri {
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.requestMethod = "HEAD"
        
        try {
            val responseCode = connection.responseCode
            return when (responseCode) {
                HttpURLConnection.HTTP_MOVED_TEMP, 
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_SEE_OTHER,
                307, 308 -> {
                    val location = connection.getHeaderField("Location")
                    if (location != null) {
                        Timber.d("Redirect from $uri to $location")
                        Uri.parse(location)
                    } else {
                        Timber.w("Redirect response but no Location header found")
                        uri
                    }
                }
                else -> {
                    Timber.d("No redirect, using original URI: $uri")
                    uri
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Schedules preloading of the next segment 3-4 seconds before current segment ends
     */
    private fun scheduleNextSegmentPreload(currentSegmentUri: Uri) {
        preloadJob?.cancel()
        preloadJob = coroutineScope.launch {
            try {
                // Wait for 3 seconds before preloading (this could be calculated based on segment duration)
                delay(3000)
                
                // Request next segment redirect
                val nextSegmentUri = resolveRedirect(originalUri)
                if (nextSegmentUri != currentSegmentUri) {
                    Timber.d("Preloading next segment: $nextSegmentUri")
                    segmentManager.preloadSegment(nextSegmentUri)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to preload next segment")
            }
        }
    }
}