package com.lomo.app.feature.memo

import android.net.Uri
import androidx.compose.runtime.Composable
import com.lomo.domain.model.Memo
import com.lomo.ui.component.menu.MemoMenuState

@Composable
fun MemoInteractionHost(
    shareCardStyle: String,
    shareCardShowTime: Boolean,
    activeDayCount: Int,
    imageDirectory: String?,
    onDeleteMemo: (Memo) -> Unit,
    onUpdateMemo: (
        memo: Memo,
        content: String,
    ) -> Unit,
    onSaveImage: (
        uri: Uri,
        onResult: (String) -> Unit,
        onError: (() -> Unit)?,
    ) -> Unit,
    onLanShare: (
        content: String,
        timestamp: Long,
    ) -> Unit,
    availableTags: List<String> = emptyList(),
    content: @Composable (
        showMenu: (MemoMenuState) -> Unit,
        openEditor: (Memo) -> Unit,
    ) -> Unit,
) {
    val editorController = rememberMemoEditorController()

    MemoMenuBinder(
        shareCardStyle = shareCardStyle,
        shareCardShowTime = shareCardShowTime,
        activeDayCount = activeDayCount,
        onEditMemo = editorController::openForEdit,
        onDeleteMemo = onDeleteMemo,
        onLanShare = onLanShare,
    ) { showMenu ->
        content(showMenu, editorController::openForEdit)

        MemoEditorSheetHost(
            controller = editorController,
            imageDirectory = imageDirectory,
            onSaveImage = onSaveImage,
            onSubmit = onUpdateMemo,
            availableTags = availableTags,
        )
    }
}
