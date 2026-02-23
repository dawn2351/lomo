package com.lomo.app.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUpdateDialogState(
    val url: String,
    val version: String,
    val releaseNotes: String,
)

@HiltViewModel
class AppUpdateViewModel
    @Inject
    constructor(
        private val appUpdateChecker: AppUpdateChecker,
    ) : ViewModel() {
        private val _dialogState = MutableStateFlow<AppUpdateDialogState?>(null)
        val dialogState: StateFlow<AppUpdateDialogState?> = _dialogState

        init {
            checkForUpdates()
        }

        fun checkForUpdates() {
            viewModelScope.launch {
                try {
                    val info = appUpdateChecker.checkForStartupUpdate() ?: return@launch
                    _dialogState.value =
                        AppUpdateDialogState(
                            url = info.url,
                            version = info.version,
                            releaseNotes = info.releaseNotes,
                        )
                } catch (_: Exception) {
                    // Ignore update check errors.
                }
            }
        }

        fun dismissUpdateDialog() {
            _dialogState.value = null
        }
    }
