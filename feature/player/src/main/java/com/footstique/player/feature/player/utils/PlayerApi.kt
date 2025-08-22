package com.footstique.player.feature.player.utils
import android.content.Intent
import android.net.Uri
import androidx.media3.common.C
import com.footstique.player.feature.player.PlayerActivity
import com.footstique.player.feature.player.extensions.getParcelableUriArray

class PlayerApi(val activity: PlayerActivity) {

    private val extras = activity.intent.extras
    val isApiAccess: Boolean get() = extras != null
    val hasPosition: Boolean get() = extras?.containsKey(API_POSITION) == true
    val hasTitle: Boolean get() = extras?.containsKey(API_TITLE) == true
    val shouldReturnResult: Boolean get() = extras?.containsKey(API_RETURN_RESULT) == true
    val position: Int? get() = if (hasPosition) extras?.getInt(API_POSITION) else null
    val title: String? get() = if (hasTitle) extras?.getString(API_TITLE) else null



    fun getResult(isPlaybackFinished: Boolean, duration: Long, position: Long): Intent {
        return Intent(API_RESULT_INTENT).apply {
            if (isPlaybackFinished) {
                putExtra(API_END_BY, API_END_BY_COMPLETION)
            } else {
                putExtra(API_END_BY, API_END_BY_USER)
                if (duration != C.TIME_UNSET) putExtra(API_DURATION, duration.toInt())
                if (position != C.TIME_UNSET) putExtra(API_POSITION, position.toInt())
            }
        }
    }

    companion object {
        const val API_TITLE = "title"
        const val API_POSITION = "position"
        const val API_DURATION = "duration"
        const val API_RETURN_RESULT = "return_result"
        const val API_END_BY = "end_by"
        const val API_RESULT_INTENT = "com.mxtech.intent.result.VIEW"

        private const val API_END_BY_USER = "user"
        private const val API_END_BY_COMPLETION = "playback_completion"
    }
}
