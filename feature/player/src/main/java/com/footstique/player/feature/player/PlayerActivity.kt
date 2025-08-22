package com.footstique.player.feature.player

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.footstique.player.core.common.Utils
import com.footstique.player.core.model.VideoZoom
import com.footstique.player.core.ui.R as coreUiR
import com.footstique.player.feature.player.databinding.ActivityPlayerBinding
import com.footstique.player.feature.player.dialogs.nameRes
import com.footstique.player.feature.player.extensions.next
import com.footstique.player.feature.player.extensions.setImageDrawable
import com.footstique.player.feature.player.extensions.toggleSystemBars
import com.footstique.player.feature.player.service.PlayerService
import com.footstique.player.feature.player.service.getAudioSessionId
import com.footstique.player.feature.player.service.stopPlayerSession
import com.footstique.player.feature.player.utils.BrightnessManager
import com.footstique.player.feature.player.utils.PlayerApi
import com.footstique.player.feature.player.utils.PlayerGestureHelper
import com.footstique.player.feature.player.utils.VolumeManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@SuppressLint("UnsafeOptInUsageError")
class PlayerActivity : AppCompatActivity() {

    lateinit var binding: ActivityPlayerBinding

    private var currentVideoZoom = VideoZoom.STRETCH

    private var isPlaybackFinished = false
    var isMediaItemReady = false
    var isControlsLocked = false
    private var hideVolumeIndicatorJob: Job? = null
    private var hideBrightnessIndicatorJob: Job? = null
    private var hideInfoLayoutJob: Job? = null

    private var playInBackground: Boolean = false
    private var isIntentNew: Boolean = true
    private var isPipActive: Boolean = false

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private lateinit var playerApi: PlayerApi
    private lateinit var volumeManager: VolumeManager
    private lateinit var brightnessManager: BrightnessManager
    private var pipBroadcastReceiver: BroadcastReceiver? = null

    private val playbackStateListener: Player.Listener = playbackStateListener()

    private lateinit var exoContentFrameLayout: AspectRatioFrameLayout
    private lateinit var lockControlsButton: ImageButton
    private lateinit var playerLockControls: FrameLayout
    private lateinit var playerUnlockControls: FrameLayout
    private lateinit var screenRotateButton: ImageButton
    private lateinit var pipButton: ImageButton
    private lateinit var unlockControlsButton: ImageButton
    private lateinit var videoZoomButton: ImageButton
    private lateinit var playInBackgroundButton: ImageButton

    private val isPipSupported: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private val isPipEnabled: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
            } else {
                @Suppress("DEPRECATION")
                appOps?.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
            }
        } else {
            false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        exoContentFrameLayout = binding.playerView.findViewById(R.id.exo_content_frame)
        lockControlsButton = binding.playerView.findViewById(R.id.btn_lock_controls)
        playerLockControls = binding.playerView.findViewById(R.id.player_lock_controls)
        playerUnlockControls = binding.playerView.findViewById(R.id.player_unlock_controls)
        screenRotateButton = binding.playerView.findViewById(R.id.screen_rotate)
        pipButton = binding.playerView.findViewById(R.id.btn_pip)
        unlockControlsButton = binding.playerView.findViewById(R.id.btn_unlock_controls)
        videoZoomButton = binding.playerView.findViewById(R.id.btn_video_zoom)
        playInBackgroundButton = binding.playerView.findViewById(R.id.btn_background)

        if (!isPipSupported) pipButton.visibility = View.GONE

        volumeManager = VolumeManager(getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        brightnessManager = BrightnessManager(this)
        PlayerGestureHelper(this, volumeManager, brightnessManager) {}
        playerApi = PlayerApi(this)

        onBackPressedDispatcher.addCallback {
            finish()
            mediaController?.stopPlayerSession()
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            maybeInitControllerFuture()
            mediaController = controllerFuture?.await()
            applyVideoZoom(currentVideoZoom)
            applyVideoScale(1f)
            mediaController?.run {
                binding.playerView.player = this
                isMediaItemReady = currentMediaItem != null
                toggleSystemBars(false)
                try {
                    volumeManager.loudnessEnhancer = LoudnessEnhancer(getAudioSessionId())
                } catch (e: Exception) { e.printStackTrace() }
                updateKeepScreenOnFlag()
                addListener(playbackStateListener)
                startPlayback()
            }
        }
        initializePlayerView()
    }

    override fun onStop() {
        super.onStop()
        binding.playerView.player = null

        // إلغاء جميع الـ jobs وإخفاء المؤشرات
        hideVolumeIndicatorJob?.cancel()
        hideBrightnessIndicatorJob?.cancel()
        hideInfoLayoutJob?.cancel()

        binding.volumeGestureLayout.visibility = View.GONE
        binding.brightnessGestureLayout.visibility = View.GONE
        binding.infoLayout.visibility = View.GONE

        mediaController?.removeListener(playbackStateListener)
        mediaController?.pause()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        if (isPipActive) finishAndRemoveTask()
    }

    private fun maybeInitControllerFuture() {
        if (controllerFuture == null) {
            val sessionToken = SessionToken(applicationContext, ComponentName(applicationContext, PlayerService::class.java))
            controllerFuture = MediaController.Builder(applicationContext, sessionToken).buildAsync()
        }
    }

    @SuppressLint("NewApi")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported && mediaController?.isPlaying == true && !isControlsLocked) {
            try {
                enterPictureInPictureMode(updatePictureInPictureParams())
            } catch (e: IllegalStateException) { e.printStackTrace() }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipActive = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            playerUnlockControls.visibility = View.INVISIBLE
            pipBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != PIP_INTENT_ACTION) return
                    when (intent.getIntExtra(PIP_INTENT_ACTION_CODE, 0)) {
                        PIP_ACTION_PLAY -> mediaController?.play()
                        PIP_ACTION_PAUSE -> mediaController?.pause()
                    }
                    updatePictureInPictureParams()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipBroadcastReceiver, IntentFilter(PIP_INTENT_ACTION), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipBroadcastReceiver, IntentFilter(PIP_INTENT_ACTION))
            }
        } else {
            if (!isControlsLocked) playerUnlockControls.visibility = View.VISIBLE
            pipBroadcastReceiver?.let {
                unregisterReceiver(it)
                pipBroadcastReceiver = null
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePictureInPictureParams(enableAutoEnter: Boolean = mediaController?.isPlaying == true): PictureInPictureParams {
        return PictureInPictureParams.Builder().apply {
            calculateVideoAspectRatio()?.let {
                setAspectRatio(it)
                setSourceRectHint(calculateSourceRectHint(Rational(binding.playerView.width, binding.playerView.height), it))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setSeamlessResizeEnabled(true)
                setAutoEnterEnabled(enableAutoEnter)
            }
            setActions(listOf(
                if (mediaController?.isPlaying == true) createPipAction(this@PlayerActivity, "pause", coreUiR.drawable.ic_pause, PIP_ACTION_PAUSE)
                else createPipAction(this@PlayerActivity, "play", coreUiR.drawable.ic_play, PIP_ACTION_PLAY)
            ))
        }.build().also { setPictureInPictureParams(it) }
    }

    private fun calculateVideoAspectRatio(): Rational? = binding.playerView.player?.videoSize?.let {
        if (it.width == 0 || it.height == 0) null else Rational(it.width, it.height).takeIf { r -> r.toFloat() in 0.5f..2.39f }
    }

    private fun calculateSourceRectHint(displayAspectRatio: Rational, aspectRatio: Rational): Rect {
        val playerWidth = binding.playerView.width.toFloat()
        val playerHeight = binding.playerView.height.toFloat()
        return if (displayAspectRatio < aspectRatio) {
            val space = ((playerHeight - (playerWidth / aspectRatio.toFloat())) / 2).toInt()
            Rect(0, space, playerWidth.toInt(), (playerWidth / aspectRatio.toFloat()).toInt() + space)
        } else {
            val space = ((playerWidth - (playerHeight * aspectRatio.toFloat())) / 2).toInt()
            Rect(space, 0, (playerHeight * aspectRatio.toFloat()).toInt() + space, playerHeight.toInt())
        }
    }

    private fun initializePlayerView() {
        binding.playerView.apply {
            controllerShowTimeoutMs = 3000
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                if (visibility == View.VISIBLE) {
                    updateIndicators()
                    // لا نظهر المؤشرات تلقائياً عند ظهور الكنترولز
                } else {
                    // نخفي المؤشرات عند اختفاء الكنترولز
                    hideVolumeGestureLayout()
                    hideBrightnessGestureLayout()
                }
            })
        }

        lockControlsButton.setOnClickListener {
            playerUnlockControls.visibility = View.INVISIBLE
            playerLockControls.visibility = View.VISIBLE
            isControlsLocked = true
        }
        unlockControlsButton.setOnClickListener {
            playerLockControls.visibility = View.INVISIBLE
            playerUnlockControls.visibility = View.VISIBLE
            isControlsLocked = false
            binding.playerView.showController()
        }
        videoZoomButton.setOnClickListener {
            currentVideoZoom = currentVideoZoom.next()
            changeAndSaveVideoZoom(currentVideoZoom)
        }
        videoZoomButton.setOnLongClickListener {
            // VideoZoomOptionsDialogFragment(currentVideoZoom) { changeAndSaveVideoZoom(it) }
            //     .show(supportFragmentManager, "VideoZoomOptionsDialog")
            true
        }
        screenRotateButton.setOnClickListener {
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        pipButton.setOnClickListener {
            if (isPipSupported && !isPipEnabled) {
                Toast.makeText(this, "Please enable Picture-in-Picture for this app in settings.", Toast.LENGTH_SHORT).show()
                try {
                    startActivity(Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", "package:$packageName".toUri()))
                } catch (e: Exception) { e.printStackTrace() }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) {
                enterPictureInPictureMode(updatePictureInPictureParams())
            }
        }
    }

    private fun startPlayback() {
        val uri = intent.data ?: return
        if (!isIntentNew && mediaController?.currentMediaItem?.localConfiguration?.uri == uri) {
            mediaController?.prepare()
            mediaController?.play()
            return
        }
        isIntentNew = false
        lifecycleScope.launch { playVideo(uri) }
    }

    private suspend fun playVideo(uri: Uri) = withContext(Dispatchers.Default) {
        val mediaItem = MediaItem.Builder().setUri(uri).setMediaId(uri.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(playerApi.title).build()).build()
        withContext(Dispatchers.Main) {
            mediaController?.run {
                setMediaItem(mediaItem, playerApi.position?.toLong() ?: C.TIME_UNSET)
                playWhenReady = true
                prepare()
            }
        }
    }

    private fun playbackStateListener() = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateKeepScreenOnFlag()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) updatePictureInPictureParams()
        }
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            volumeManager.loudnessEnhancer?.release()
            try {
                volumeManager.loudnessEnhancer = LoudnessEnhancer(audioSessionId)
            } catch (e: Exception) { e.printStackTrace() }
        }
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width != 0 && videoSize.height != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) {
                updatePictureInPictureParams()
            }
            applyVideoZoom(currentVideoZoom)
            applyVideoScale(1f)
        }
        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error)
            MaterialAlertDialogBuilder(this@PlayerActivity)
                .setTitle("Playback Error")
                .setMessage(error.message ?: "An unknown error occurred")
                .setNegativeButton("Exit") { _, _ -> finish() }
                .create().show()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                isPlaybackFinished = true
                finish()
            }
        }
    }

    override fun finish() {
        if (playerApi.shouldReturnResult) {
            setResult(RESULT_OK, playerApi.getResult(isPlaybackFinished, mediaController?.duration ?: C.TIME_UNSET, mediaController?.currentPosition ?: C.TIME_UNSET))
        }
        super.finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null) {
            setIntent(intent)
            isIntentNew = true
            mediaController?.let { startPlayback() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumeManager.increaseVolume(true)
            showVolumeGestureLayout()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeManager.decreaseVolume(true)
            showVolumeGestureLayout()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            hideVolumeGestureLayout()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    fun updateIndicators() {
        binding.volumeProgressBar.max = volumeManager.maxVolume.times(100)
        binding.volumeProgressBar.progress = volumeManager.currentVolume.times(100).toInt()
        binding.brightnessProgressBar.max = brightnessManager.maxBrightness.times(100).toInt()
        binding.brightnessProgressBar.progress = brightnessManager.currentBrightness.times(100).toInt()
    }

    // دالة إظهار مؤشر الصوت - تم إصلاحها
    fun showVolumeGestureLayout() {
        hideVolumeIndicatorJob?.cancel() // إلغاء أي مهمة إخفاء سابقة
        updateIndicators()
        binding.volumeGestureLayout.visibility = View.VISIBLE
        hideVolumeGestureLayout() // تشغيل مؤقت الإخفاء
    }

    // دالة إظهار مؤشر الإضاءة - تم إصلاحها
    fun showBrightnessGestureLayout() {
        hideBrightnessIndicatorJob?.cancel() // إلغاء أي مهمة إخفاء سابقة
        updateIndicators()
        binding.brightnessGestureLayout.visibility = View.VISIBLE
        hideBrightnessGestureLayout() // تشغيل مؤقت الإخفاء
    }

    // دالة إخفاء مؤشر الصوت - تم إصلاحها
    fun hideVolumeGestureLayout(delay: Long = HIDE_DELAY_MILLIS) {
        hideVolumeIndicatorJob?.cancel()
        hideVolumeIndicatorJob = lifecycleScope.launch {
            delay(delay)
            binding.volumeGestureLayout.visibility = View.GONE
        }
    }

    // دالة إخفاء مؤشر الإضاءة - تم إضافتها
    fun hideBrightnessGestureLayout(delay: Long = HIDE_DELAY_MILLIS) {
        hideBrightnessIndicatorJob?.cancel()
        hideBrightnessIndicatorJob = lifecycleScope.launch {
            delay(delay)
            binding.brightnessGestureLayout.visibility = View.GONE
        }
    }

    fun showPlayerInfo(info: String, subInfo: String? = null) {
        hideInfoLayoutJob?.cancel()
        binding.infoLayout.visibility = View.VISIBLE
        binding.infoText.text = info
        binding.infoSubtext.visibility = if (subInfo == null) View.GONE else View.VISIBLE
        binding.infoSubtext.text = subInfo
        hidePlayerInfo() // تشغيل مؤقت الإخفاء
    }

    fun hidePlayerInfo(delay: Long = HIDE_DELAY_MILLIS) {
        hideInfoLayoutJob?.cancel()
        hideInfoLayoutJob = lifecycleScope.launch {
            delay(delay)
            binding.infoLayout.visibility = View.GONE
        }
    }

    private fun updateKeepScreenOnFlag() {
        if (mediaController?.isPlaying == true) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun applyVideoScale(videoScale: Float) {
        exoContentFrameLayout.scaleX = videoScale
        exoContentFrameLayout.scaleY = videoScale
    }

    private fun resetExoContentFrameWidthAndHeight() {
        exoContentFrameLayout.layoutParams.width = LayoutParams.MATCH_PARENT
        exoContentFrameLayout.layoutParams.height = LayoutParams.MATCH_PARENT
        exoContentFrameLayout.scaleX = 1.0f
        exoContentFrameLayout.scaleY = 1.0f
    }

    private fun applyVideoZoom(videoZoom: VideoZoom) {
        resetExoContentFrameWidthAndHeight()
        videoZoomButton.setImageDrawable(this, when(videoZoom) {
            VideoZoom.BEST_FIT -> coreUiR.drawable.ic_fit_screen
            VideoZoom.STRETCH -> coreUiR.drawable.ic_aspect_ratio
            VideoZoom.CROP -> coreUiR.drawable.ic_crop_landscape
            VideoZoom.HUNDRED_PERCENT -> coreUiR.drawable.ic_width_wide
        })
        binding.playerView.resizeMode = when (videoZoom) {
            VideoZoom.BEST_FIT, VideoZoom.HUNDRED_PERCENT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            VideoZoom.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            VideoZoom.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        if (videoZoom == VideoZoom.HUNDRED_PERCENT) {
            mediaController?.videoSize?.let {
                exoContentFrameLayout.layoutParams.width = it.width
                exoContentFrameLayout.layoutParams.height = it.height
            }
        }
    }

    private fun changeAndSaveVideoZoom(videoZoom: VideoZoom) {
        applyVideoZoom(videoZoom)
        currentVideoZoom = videoZoom
        showPlayerInfo(getString(videoZoom.nameRes()))
    }

    companion object {
        const val HIDE_DELAY_MILLIS = 1500L // زيادة المدة لتكون أكثر وضوحاً
        const val PIP_INTENT_ACTION = "pip_action"
        const val PIP_INTENT_ACTION_CODE = "pip_action_code"
        const val PIP_ACTION_PLAY = 1
        const val PIP_ACTION_PAUSE = 2
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun createPipAction(context: Context, title: String, @DrawableRes icon: Int, actionCode: Int): RemoteAction {
    return RemoteAction(
        Icon.createWithResource(context, icon), title, title,
        PendingIntent.getBroadcast(
            context, actionCode,
            Intent(PlayerActivity.PIP_INTENT_ACTION).apply {
                putExtra(PlayerActivity.PIP_INTENT_ACTION_CODE, actionCode)
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_IMMUTABLE,
        ),
    )
}