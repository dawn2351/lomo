package com.lomo.app.feature.main

import android.net.Uri
import com.lomo.domain.model.Memo
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Encapsulates main-screen memo/media mutation actions so MainViewModel can focus on UI state.
 */
class MainMemoActionDelegate
    @Inject
    constructor(
        private val memoMutator: MainMemoMutator,
        private val mediaCoordinator: MainMediaCoordinator,
    ) {
        suspend fun createDefaultDirectories(
            forImage: Boolean,
            forVoice: Boolean,
        ): Result<Unit> =
            runAction {
                mediaCoordinator.createDefaultDirectories(forImage, forVoice)
            }

        suspend fun addMemo(content: String): Result<Unit> =
            runAction {
                memoMutator.addMemo(content)
                mediaCoordinator.clearTrackedImages()
            }

        suspend fun deleteMemo(memo: Memo): Result<Unit> =
            runAction {
                memoMutator.deleteMemo(memo)
            }

        suspend fun updateMemo(
            memo: Memo,
            newContent: String,
        ): Result<Unit> =
            runAction {
                memoMutator.updateMemo(memo, newContent)
                mediaCoordinator.clearTrackedImages()
            }

        suspend fun toggleCheckbox(
            memo: Memo,
            lineIndex: Int,
            checked: Boolean,
        ): Result<Unit> =
            runAction {
                memoMutator.toggleCheckbox(memo, lineIndex, checked)
            }

        suspend fun saveImage(uri: Uri): Result<String> =
            runAction {
                mediaCoordinator.saveImageAndTrack(uri)
            }

        suspend fun discardInputs(): Result<Unit> =
            runAction {
                mediaCoordinator.discardTrackedImages()
            }

        suspend fun syncImageCacheBestEffort(): Result<Unit> =
            runAction {
                mediaCoordinator.syncImageCacheBestEffort()
            }

        private suspend inline fun <T> runAction(crossinline block: suspend () -> T): Result<T> =
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
    }
