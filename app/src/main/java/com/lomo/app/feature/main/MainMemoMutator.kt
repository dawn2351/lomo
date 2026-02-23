package com.lomo.app.feature.main

import com.lomo.app.repository.AppWidgetRepository
import com.lomo.domain.model.Memo
import com.lomo.domain.repository.MemoRepository
import com.lomo.domain.usecase.CreateMemoUseCase
import com.lomo.domain.usecase.UpdateMemoUseCase
import javax.inject.Inject

/**
 * Encapsulates memo mutation workflows used by Main screen.
 */
class MainMemoMutator
    @Inject
    constructor(
        private val createMemoUseCase: CreateMemoUseCase,
        private val memoRepository: MemoRepository,
        private val updateMemoUseCase: UpdateMemoUseCase,
        private val appWidgetRepository: AppWidgetRepository,
        private val textProcessor: com.lomo.data.util.MemoTextProcessor,
    ) {
        suspend fun addMemo(content: String) {
            createMemoUseCase(content)
            appWidgetRepository.updateAllWidgets()
        }

        suspend fun deleteMemo(memo: Memo) {
            memoRepository.deleteMemo(memo)
            appWidgetRepository.updateAllWidgets()
        }

        suspend fun updateMemo(
            memo: Memo,
            newContent: String,
        ) {
            updateMemoUseCase(memo, newContent)
            appWidgetRepository.updateAllWidgets()
        }

        suspend fun toggleCheckbox(
            memo: Memo,
            lineIndex: Int,
            checked: Boolean,
        ) {
            val newContent = textProcessor.toggleCheckbox(memo.content, lineIndex, checked)
            if (newContent != memo.content) {
                updateMemoUseCase(memo, newContent)
            }
        }
    }
