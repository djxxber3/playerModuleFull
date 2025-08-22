package com.footstique.player.ui.urlinput

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UrlInputUiState(
    val videoUrl: String = "",
    val isUrlEmptyError: Boolean = false
)

// تم حذف @HiltViewModel و @Inject
class UrlInputViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UrlInputUiState())
    val uiState: StateFlow<UrlInputUiState> = _uiState.asStateFlow()

    fun onVideoUrlChange(newUrl: String) {
        _uiState.update {
            it.copy(videoUrl = newUrl, isUrlEmptyError = false)
        }
    }

    fun onPlayVideoClick(): Boolean {
        if (_uiState.value.videoUrl.isBlank()) {
            _uiState.update { it.copy(isUrlEmptyError = true) }
            return false
        }
        return true
    }
}