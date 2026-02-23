package com.lomo.app.feature.update

import com.lomo.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class AppUpdateInfo(
    val url: String,
    val version: String,
    val releaseNotes: String,
)

class AppUpdateChecker
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val updateManager: UpdateManager,
    ) {
        suspend fun checkForStartupUpdate(): AppUpdateInfo? {
            if (!settingsRepository.isCheckUpdatesOnStartupEnabled().first()) return null
            return updateManager.checkForUpdatesInfo()?.let { info ->
                AppUpdateInfo(
                    url = info.htmlUrl,
                    version = info.version,
                    releaseNotes = info.releaseNotes,
                )
            }
        }
    }
