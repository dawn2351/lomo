package com.lomo.data.repository

import com.lomo.domain.model.Memo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class MemoSynchronizer
    @Inject
    constructor(
        private val refreshEngine: MemoRefreshEngine,
        private val mutationHandler: MemoMutationHandler,
    ) {
        private val mutex = Mutex()

        // Sync state for UI observation - helps prevent writes during active sync
        private val _isSyncing = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isSyncing: kotlinx.coroutines.flow.StateFlow<Boolean> = _isSyncing

        suspend fun refresh(targetFilename: String? = null) =
            mutex.withLock {
                _isSyncing.value = true
                try {
                    refreshEngine.refresh(targetFilename)
                } finally {
                    _isSyncing.value = false
                }
            }

        suspend fun saveMemo(
            content: String,
            timestamp: Long,
        ) = mutex.withLock { mutationHandler.saveMemo(content, timestamp) }

        suspend fun updateMemo(
            memo: Memo,
            newContent: String,
        ) = mutex.withLock { mutationHandler.updateMemo(memo, newContent) }

        suspend fun deleteMemo(memo: Memo) = mutex.withLock { mutationHandler.deleteMemo(memo) }

        suspend fun restoreMemo(memo: Memo) = mutex.withLock { mutationHandler.restoreMemo(memo) }

        suspend fun deletePermanently(memo: Memo) = mutex.withLock { mutationHandler.deletePermanently(memo) }
    }
