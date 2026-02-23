package com.lomo.app.feature.main

import com.lomo.app.BuildConfig
import com.lomo.app.feature.update.UpdateManager
import com.lomo.data.local.datastore.LomoDataStore
import com.lomo.domain.repository.MediaRepository
import com.lomo.domain.repository.MemoRepository
import com.lomo.domain.repository.SettingsRepository
import com.lomo.ui.media.AudioPlayerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class MainUpdateInfo(
    val url: String,
    val version: String,
    val releaseNotes: String,
)

class MainStartupCoordinator
    @Inject
    constructor(
        private val repository: MemoRepository,
        private val mediaRepository: MediaRepository,
        private val settingsRepository: SettingsRepository,
        private val dataStore: LomoDataStore,
        private val audioPlayerManager: AudioPlayerManager,
        private val updateManager: UpdateManager,
    ) {
        suspend fun initializeRootDirectory(): String? {
            val rootDirectory = repository.getRootDirectoryOnce()
            audioPlayerManager.setRootDirectory(rootDirectory)
            resyncCachesIfAppVersionChanged(rootDirectory)
            return rootDirectory
        }

        fun observeRootDirectoryChanges(): Flow<String?> =
            repository
                .getRootDirectory()
                .drop(1)
                .onEach { directory ->
                    audioPlayerManager.setRootDirectory(directory)
                }

        fun observeVoiceDirectoryChanges(): Flow<String?> =
            repository
                .getVoiceDirectory()
                .onEach { voiceDirectory ->
                    audioPlayerManager.setVoiceDirectory(voiceDirectory)
                }

        suspend fun checkForUpdatesIfEnabled(): MainUpdateInfo? {
            if (!settingsRepository.isCheckUpdatesOnStartupEnabled().first()) return null
            return updateManager.checkForUpdatesInfo()?.let { info ->
                MainUpdateInfo(
                    url = info.htmlUrl,
                    version = info.version,
                    releaseNotes = info.releaseNotes,
                )
            }
        }

        private suspend fun resyncCachesIfAppVersionChanged(rootDir: String?) {
            val currentVersion = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
            val lastVersion = dataStore.getLastAppVersionOnce()
            if (lastVersion == currentVersion) return

            if (rootDir != null) {
                try {
                    repository.refreshMemos()
                } catch (_: Exception) {
                    // best-effort refresh
                }
                try {
                    mediaRepository.syncImageCache()
                } catch (_: Exception) {
                    // best-effort cache rebuild
                }
            }

            dataStore.updateLastAppVersion(currentVersion)
        }
    }
