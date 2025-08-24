package com.footstique.player.feature.player.service

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.footstique.player.feature.player.streaming.LiveStreamDataSourceFactory
import timber.log.Timber

@UnstableApi
class PlayerService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var liveStreamDataSourceFactory: LiveStreamDataSourceFactory? = null

    // Create your Player and MediaSession in onCreate
    override fun onCreate() {
        super.onCreate()
        
        // Create live stream data source factory for handling redirects and preloading
        liveStreamDataSourceFactory = LiveStreamDataSourceFactory.createDefault()
        
        // Create default data source factory that falls back to regular HTTP for non-live streams
        val defaultDataSourceFactory = DefaultDataSource.Factory(
            this,
            liveStreamDataSourceFactory!!
        )
        
        // Create media source factory with custom data source
        val mediaSourceFactory = DefaultMediaSourceFactory(defaultDataSourceFactory)
        
        // Build ExoPlayer with custom media source factory
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            
        mediaSession = MediaSession.Builder(this, player).build()
        
        Timber.d("PlayerService created with live streaming support")
    }

    // The user accepted the call to return the MediaSession instance.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // Remember to release the player and media session in onDestroy
    override fun onDestroy() {
        mediaSession?.run {
            player.release() // Release the player instance
            release()
            mediaSession = null
        }
        
        // Clear streaming cache
        liveStreamDataSourceFactory?.let { factory ->
            kotlinx.coroutines.runBlocking {
                factory.clearCache()
            }
        }
        liveStreamDataSourceFactory = null
        
        Timber.d("PlayerService destroyed")
        super.onDestroy()
    }
}