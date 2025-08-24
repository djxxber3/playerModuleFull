package com.footstique.player.feature.player.streaming

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import timber.log.Timber

/**
 * Factory for creating data sources that handle segmented live streaming with HTTP redirects.
 * 
 * This factory creates RedirectingDataSource instances that:
 * 1. Handle HTTP 302 redirects to get actual segment URLs
 * 2. Preload next segments for seamless playback
 * 3. Manage PTS continuity between segments
 */
@UnstableApi
class LiveStreamDataSourceFactory(
    private val baseDataSourceFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory(),
    private val segmentManager: SegmentManager = SegmentManager()
) : DataSource.Factory {

    private var userAgent: String? = null
    private var connectTimeoutMs: Int = DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS
    private var readTimeoutMs: Int = DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS
    private var allowCrossProtocolRedirects: Boolean = false

    /**
     * Sets the user agent for HTTP requests
     */
    fun setUserAgent(userAgent: String): LiveStreamDataSourceFactory {
        this.userAgent = userAgent
        return this
    }

    /**
     * Sets the connection timeout in milliseconds
     */
    fun setConnectTimeoutMs(connectTimeoutMs: Int): LiveStreamDataSourceFactory {
        this.connectTimeoutMs = connectTimeoutMs
        return this
    }

    /**
     * Sets the read timeout in milliseconds
     */
    fun setReadTimeoutMs(readTimeoutMs: Int): LiveStreamDataSourceFactory {
        this.readTimeoutMs = readTimeoutMs
        return this
    }

    /**
     * Sets whether to allow cross-protocol redirects
     */
    fun setAllowCrossProtocolRedirects(allowCrossProtocolRedirects: Boolean): LiveStreamDataSourceFactory {
        this.allowCrossProtocolRedirects = allowCrossProtocolRedirects
        return this
    }

    override fun createDataSource(): DataSource {
        // Configure the base HTTP data source factory
        val configuredFactory = baseDataSourceFactory.apply {
            userAgent?.let { setUserAgent(it) }
            setConnectTimeoutMs(connectTimeoutMs)
            setReadTimeoutMs(readTimeoutMs)
            setAllowCrossProtocolRedirects(allowCrossProtocolRedirects)
        }

        val baseDataSource = configuredFactory.createDataSource()
        
        return LiveStreamDataSource(baseDataSource as HttpDataSource, segmentManager)
    }

    /**
     * Creates a data source for a specific URI (used internally for redirects)
     */
    fun createDataSourceForUri(uri: Uri): DataSource {
        val baseDataSource = baseDataSourceFactory.createDataSource()
        return RedirectingDataSource(baseDataSource as HttpDataSource, segmentManager, uri)
    }

    /**
     * Gets the segment manager instance
     */
    fun getSegmentManager(): SegmentManager = segmentManager

    /**
     * Clears all cached segments
     */
    suspend fun clearCache() {
        segmentManager.clearCache()
    }

    companion object {
        /**
         * Creates a default LiveStreamDataSourceFactory with recommended settings for live streaming
         */
        fun createDefault(): LiveStreamDataSourceFactory {
            return LiveStreamDataSourceFactory(
                baseDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("ExoPlayer-LiveStream/1.0")
                    .setConnectTimeoutMs(10000) // 10 seconds
                    .setReadTimeoutMs(10000) // 10 seconds
                    .setAllowCrossProtocolRedirects(true)
            )
        }

        /**
         * Checks if a URI is suitable for live streaming with redirects
         */
        fun isLiveStreamUri(uri: Uri): Boolean {
            val uriString = uri.toString()
            return uriString.startsWith("http://") || uriString.startsWith("https://")
        }
    }
}

/**
 * Main DataSource implementation that handles live streaming with redirects
 */
@UnstableApi
private class LiveStreamDataSource(
    private val baseDataSource: HttpDataSource,
    private val segmentManager: SegmentManager
) : DataSource {
    
    private var redirectingDataSource: RedirectingDataSource? = null
    
    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        baseDataSource.addTransferListener(transferListener)
    }
    
    override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
        // Create the redirecting data source with the actual URI from the DataSpec
        redirectingDataSource = RedirectingDataSource(baseDataSource, segmentManager, dataSpec.uri)
        return redirectingDataSource!!.open(dataSpec)
    }
    
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return redirectingDataSource?.read(buffer, offset, length) 
            ?: throw IllegalStateException("DataSource not opened")
    }
    
    override fun getUri(): Uri? {
        return redirectingDataSource?.getUri()
    }
    
    override fun close() {
        redirectingDataSource?.close()
        redirectingDataSource = null
    }
}