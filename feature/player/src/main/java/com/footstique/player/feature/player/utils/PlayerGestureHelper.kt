package com.footstique.player.feature.player.utils

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.footstique.player.core.common.Utils
import com.footstique.player.core.common.extensions.dpToPx
import com.footstique.player.feature.player.PlayerActivity
import com.footstique.player.feature.player.R
import com.footstique.player.feature.player.extensions.seekBack
import com.footstique.player.feature.player.extensions.seekForward
import kotlin.math.abs
import kotlin.math.roundToInt

@UnstableApi
@SuppressLint("ClickableViewAccessibility")
class PlayerGestureHelper(
    private val activity: PlayerActivity,
    private val volumeManager: VolumeManager,
    private val brightnessManager: BrightnessManager,
    private val onScaleChanged: (Float) -> Unit,
) {
    private val playerView: PlayerView get() = activity.binding.playerView
    private val shouldFastSeek: Boolean get() = (playerView.player?.duration ?: 0) > 0
    private var exoContentFrameLayout: AspectRatioFrameLayout = playerView.findViewById(R.id.exo_content_frame)

    private var currentGestureAction: GestureAction? = null
    private var seekStart = 0L
    private var position = 0L
    private var seekChange = 0L
    private var isPlayingOnSeekStart: Boolean = false

    private val tapGestureDetector = GestureDetector(
        playerView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                with(playerView) { if (!isControllerFullyVisible) showController() else hideController() }
                return true
            }
            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (activity.isControlsLocked) return false
                playerView.player?.run {
                    val viewCenterX = playerView.measuredWidth / 2
                    val seekIncrementMillis = 10000L // 10 seconds
                    if (event.x.toInt() < viewCenterX) {
                        seekBack((currentPosition - seekIncrementMillis).coerceAtLeast(0), shouldFastSeek)
                    } else {
                        seekForward((currentPosition + seekIncrementMillis).coerceAtMost(duration), shouldFastSeek)
                    }
                }
                return true
            }
        },
    )

    private val seekGestureDetector = GestureDetector(
        playerView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null || inExclusionArea(e1) || activity.isControlsLocked || !activity.isMediaItemReady || abs(distanceX / distanceY) < 2) return false

                if (currentGestureAction == null) {
                    seekChange = 0L
                    seekStart = playerView.player?.currentPosition ?: 0L
                    if (playerView.player?.isPlaying == true) {
                        playerView.player?.pause()
                        isPlayingOnSeekStart = true
                    }
                    currentGestureAction = GestureAction.SEEK
                }
                if (currentGestureAction != GestureAction.SEEK) return false

                val change = (abs(Utils.pxToDp(distanceX) / 4).coerceIn(0.5f, 10f) * 1000L).toLong()
                playerView.player?.run {
                    position = if (distanceX < 0) (seekStart + seekChange + change).coerceAtMost(duration)
                    else (seekStart + seekChange - change).coerceAtLeast(0)
                    seekChange = position - seekStart
                    if (distanceX < 0) seekForward(position, shouldFastSeek) else seekBack(position, shouldFastSeek)
                    activity.showPlayerInfo(Utils.formatDurationMillis(position), "[${Utils.formatDurationMillisSign(seekChange)}]")
                }
                return true
            }
        },
    )

    private val volumeAndBrightnessGestureDetector = GestureDetector(
        playerView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null || inExclusionArea(e1) || activity.isControlsLocked || abs(distanceY / distanceX) < 2) return false

                if (currentGestureAction == null) currentGestureAction = GestureAction.SWIPE
                if (currentGestureAction != GestureAction.SWIPE) return false

                val ratioChange = distanceY / (playerView.measuredHeight * 0.66f)
                if (e1.x.toInt() > playerView.measuredWidth / 2) {
                    volumeManager.setVolume(volumeManager.currentVolume + (ratioChange * volumeManager.maxStreamVolume), true)
                    activity.showVolumeGestureLayout()
                } else {
                    brightnessManager.setBrightness(brightnessManager.currentBrightness + (ratioChange * brightnessManager.maxBrightness))
                    activity.showBrightnessGestureLayout()
                }
                return true
            }
        },
    )

    private val zoomGestureDetector = ScaleGestureDetector(
        playerView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (activity.isControlsLocked) return false
                if (currentGestureAction == null) currentGestureAction = GestureAction.ZOOM
                if (currentGestureAction != GestureAction.ZOOM) return false

                playerView.player?.videoSize?.let {
                    val scaleFactor = (exoContentFrameLayout.scaleX * detector.scaleFactor).coerceIn(0.25f, 4.0f)
                    exoContentFrameLayout.scaleX = scaleFactor
                    exoContentFrameLayout.scaleY = scaleFactor
                    onScaleChanged(scaleFactor)
                    activity.showPlayerInfo("${(scaleFactor * 100).roundToInt()}%")
                }
                return true
            }
        },
    )

    private fun releaseGestures() {
        if (currentGestureAction == GestureAction.SWIPE) {
            activity.hideVolumeGestureLayout()
            // You might want a similar hide function for brightness if it's temporary
        }
        activity.hidePlayerInfo()
        if (isPlayingOnSeekStart) playerView.player?.play()
        isPlayingOnSeekStart = false
        currentGestureAction = null
    }

    private fun inExclusionArea(event: MotionEvent): Boolean {
        val gestureExclusionBorder = playerView.context.dpToPx(20f)
        return event.y < gestureExclusionBorder || event.y > playerView.height - gestureExclusionBorder ||
                event.x < gestureExclusionBorder || event.x > playerView.width - gestureExclusionBorder
    }

    init {
        playerView.setOnTouchListener { _, event ->
            when (event.pointerCount) {
                1 -> {
                    tapGestureDetector.onTouchEvent(event)
                    volumeAndBrightnessGestureDetector.onTouchEvent(event)
                    seekGestureDetector.onTouchEvent(event)
                }
                2 -> zoomGestureDetector.onTouchEvent(event)
            }
            if (event.action == MotionEvent.ACTION_UP) releaseGestures()
            true
        }
    }
}

private enum class GestureAction { SWIPE, SEEK, ZOOM }