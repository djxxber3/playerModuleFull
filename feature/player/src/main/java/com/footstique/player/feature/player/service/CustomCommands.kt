package com.footstique.player.feature.player.service

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import kotlinx.coroutines.guava.await

enum class CustomCommands(val customAction: String) {
    SET_SKIP_SILENCE_ENABLED(customAction = "SET_SKIP_SILENCE_ENABLED"),
    GET_SKIP_SILENCE_ENABLED(customAction = "GET_SKIP_SILENCE_ENABLED"),
    GET_AUDIO_SESSION_ID(customAction = "GET_AUDIO_SESSION_ID"),
    STOP_PLAYER_SESSION(customAction = "STOP_PLAYER_SESSION"),
    ;

    val sessionCommand = SessionCommand(customAction, Bundle.EMPTY)

    companion object {
        fun fromSessionCommand(sessionCommand: SessionCommand): CustomCommands? {
            return entries.find { it.customAction == sessionCommand.customAction }
        }

        fun asSessionCommands(): List<SessionCommand> {
            return entries.map { it.sessionCommand }
        }

        const val SKIP_SILENCE_ENABLED_KEY = "skip_silence_enabled"
        const val AUDIO_SESSION_ID_KEY = "audio_session_id"
    }
}





fun MediaController.setSkipSilenceEnabled(enabled: Boolean) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, enabled)
    }
    sendCustomCommand(CustomCommands.SET_SKIP_SILENCE_ENABLED.sessionCommand, args)
}

suspend fun MediaController.getSkipSilenceEnabled(): Boolean {
    val result = sendCustomCommand(CustomCommands.GET_SKIP_SILENCE_ENABLED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, false)
}



@OptIn(UnstableApi::class)
suspend fun MediaController.getAudioSessionId(): Int {
    val result = sendCustomCommand(CustomCommands.GET_AUDIO_SESSION_ID.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getInt(CustomCommands.AUDIO_SESSION_ID_KEY, C.AUDIO_SESSION_ID_UNSET)
}

fun MediaController.stopPlayerSession() {
    sendCustomCommand(CustomCommands.STOP_PLAYER_SESSION.sessionCommand, Bundle.EMPTY)
}
