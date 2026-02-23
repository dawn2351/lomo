package com.lomo.app.feature.memo

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.lomo.app.R
import com.lomo.app.util.CameraCaptureUtils
import com.lomo.domain.model.Memo
import java.io.File

@Stable
class MemoEditorController
    internal constructor() {
        var isVisible by mutableStateOf(false)
            private set

        var editingMemo: Memo? by mutableStateOf(null)
            private set

        var inputValue by mutableStateOf(TextFieldValue(""))
            private set

        fun openForEdit(memo: Memo) {
            editingMemo = memo
            inputValue = TextFieldValue(memo.content, TextRange(memo.content.length))
            isVisible = true
        }

        fun appendImageMarkdown(path: String) {
            val markdown = "![image]($path)"
            val current = inputValue.text
            val newText = if (current.isEmpty()) markdown else "$current\n$markdown"
            inputValue = TextFieldValue(newText, TextRange(newText.length))
        }

        fun updateInputValue(value: TextFieldValue) {
            inputValue = value
        }

        fun close() {
            isVisible = false
            editingMemo = null
            inputValue = TextFieldValue("")
        }
    }

@Composable
fun rememberMemoEditorController(): MemoEditorController = remember { MemoEditorController() }

@Composable
fun MemoEditorSheetHost(
    controller: MemoEditorController,
    imageDirectory: String?,
    onSaveImage: (
        uri: Uri,
        onResult: (String) -> Unit,
        onError: (() -> Unit)?,
    ) -> Unit,
    onSubmit: (
        memo: Memo,
        content: String,
    ) -> Unit,
    availableTags: List<String> = emptyList(),
) {
    if (!controller.isVisible) return

    val context = LocalContext.current
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun clearPendingCapture() {
        runCatching { pendingCameraFile?.delete() }
        pendingCameraFile = null
        pendingCameraUri = null
    }

    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                onSaveImage(
                    it,
                    controller::appendImageMarkdown,
                    null,
                )
            }
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            val file = pendingCameraFile
            val uri = pendingCameraUri
            if (isSuccess && uri != null) {
                onSaveImage(
                    uri,
                    { path ->
                        controller.appendImageMarkdown(path)
                        runCatching { file?.delete() }
                        pendingCameraFile = null
                        pendingCameraUri = null
                    },
                    ::clearPendingCapture,
                )
            } else {
                clearPendingCapture()
            }
        }

    com.lomo.ui.component.input.InputSheet(
        inputValue = controller.inputValue,
        onInputValueChange = controller::updateInputValue,
        onDismiss = controller::close,
        onSubmit = { content ->
            val memo = controller.editingMemo
            if (memo != null) {
                onSubmit(memo, content)
            }
            controller.close()
        },
        onImageClick = {
            if (imageDirectory == null) {
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.settings_not_set),
                        Toast.LENGTH_SHORT,
                    ).show()
            } else {
                imagePicker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            }
        },
        onCameraClick = {
            if (imageDirectory == null) {
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.settings_not_set),
                        Toast.LENGTH_SHORT,
                    ).show()
            } else {
                runCatching {
                    val (file, uri) = CameraCaptureUtils.createTempCaptureUri(context)
                    pendingCameraFile = file
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                }.onFailure {
                    clearPendingCapture()
                }
            }
        },
        availableTags = availableTags,
    )
}
