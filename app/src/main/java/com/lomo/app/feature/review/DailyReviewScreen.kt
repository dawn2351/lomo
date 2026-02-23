package com.lomo.app.feature.review

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lomo.app.R
import com.lomo.ui.component.card.MemoCard
import com.lomo.ui.component.common.EmptyState
import com.lomo.ui.util.UiState
import com.lomo.ui.util.formatAsDateTime
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReviewScreen(
    onBackClick: () -> Unit,
    onNavigateToImage: (String) -> Unit,
    onNavigateToShare: (String, Long) -> Unit = { _, _ -> },
    viewModel: DailyReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
    val dateFormat = appPreferences.dateFormat
    val timeFormat = appPreferences.timeFormat
    val shareCardStyle = appPreferences.shareCardStyle
    val shareCardShowTime = appPreferences.shareCardShowTime
    val doubleTapEditEnabled = appPreferences.doubleTapEditEnabled
    val activeDayCount by viewModel.activeDayCount.collectAsStateWithLifecycle()
    val imageDirectory by viewModel.imageDirectory.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    var showInputSheet by remember { mutableStateOf(false) }
    var editingMemo by remember { mutableStateOf<com.lomo.domain.model.Memo?>(null) }
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun appendImageMarkdown(path: String) {
        val markdown = "![image]($path)"
        val current = inputText.text
        val newText = if (current.isEmpty()) markdown else "$current\n$markdown"
        inputText = TextFieldValue(newText, TextRange(newText.length))
    }

    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.saveImage(it, ::appendImageMarkdown) }
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            val file = pendingCameraFile
            val uri = pendingCameraUri
            if (isSuccess && uri != null) {
                viewModel.saveImage(
                    uri = uri,
                    onResult = { path ->
                        appendImageMarkdown(path)
                        runCatching { file?.delete() }
                        pendingCameraFile = null
                        pendingCameraUri = null
                    },
                    onError = {
                        runCatching { file?.delete() }
                        pendingCameraFile = null
                        pendingCameraUri = null
                    },
                )
            } else {
                runCatching { file?.delete() }
                pendingCameraFile = null
                pendingCameraUri = null
            }
        }

    com.lomo.ui.component.menu.MemoMenuHost(
        onEdit = { state ->
            val memo = state.memo as? com.lomo.domain.model.Memo
            if (memo != null) {
                editingMemo = memo
                inputText = TextFieldValue(memo.content, TextRange(memo.content.length))
                showInputSheet = true
            }
        },
        onDelete = { state ->
            val memo = state.memo as? com.lomo.domain.model.Memo
            if (memo != null) {
                viewModel.deleteMemo(memo)
            }
        },
        onShare = { state ->
            val memo = state.memo as? com.lomo.domain.model.Memo
            com.lomo.app.util.ShareUtils.shareMemoAsImage(
                context = context,
                content = state.content,
                style = shareCardStyle,
                showTime = shareCardShowTime,
                timestamp = memo?.timestamp,
                tags = memo?.tags.orEmpty(),
                activeDayCount = activeDayCount,
            )
        },
        onLanShare = { state ->
            val memo = state.memo as? com.lomo.domain.model.Memo
            if (memo != null) {
                onNavigateToShare(memo.content, memo.timestamp)
            }
        },
    ) { showMenu ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            androidx.compose.ui.res
                                .stringResource(R.string.sidebar_daily_review),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription =
                                    androidx.compose.ui.res
                                        .stringResource(R.string.back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { scaffoldPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                contentAlignment = Alignment.Center,
            ) {
                when (val state = uiState) {
                    is UiState.Loading -> {
                        androidx.compose.material3.CircularProgressIndicator()
                    }

                    is UiState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }

                    is UiState.Success -> {
                        val memos = state.data
                        if (memos.isEmpty()) {
                            EmptyState(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                title =
                                    androidx.compose.ui.res
                                        .stringResource(R.string.review_no_memos_title),
                                description =
                                    androidx.compose.ui.res
                                        .stringResource(R.string.review_no_memos_desc),
                            )
                        } else {
                            val pagerState = rememberPagerState(pageCount = { memos.size })

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                HorizontalPager(
                                    state = pagerState,
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    pageSpacing = 16.dp,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                ) { page ->
                                    val memo = memos[page]
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 32.dp)
                                                    .verticalScroll(rememberScrollState()),
                                        ) {
                                            MemoCard(
                                                content = memo.memo.content,
                                                processedContent = memo.processedContent,
                                                timestamp = memo.memo.timestamp,
                                                dateFormat = dateFormat,
                                                timeFormat = timeFormat,
                                                tags = memo.tags,
                                                onDoubleClick =
                                                    if (doubleTapEditEnabled) {
                                                        {
                                                            editingMemo = memo.memo
                                                            inputText =
                                                                TextFieldValue(
                                                                    memo.memo.content,
                                                                    TextRange(memo.memo.content.length),
                                                                )
                                                            showInputSheet = true
                                                        }
                                                    } else {
                                                        null
                                                    },
                                                onImageClick = onNavigateToImage,
                                                onMenuClick = {
                                                    showMenu(
                                                        com.lomo.ui.component.menu.MemoMenuState(
                                                            wordCount = memo.memo.content.length,
                                                            createdTime = memo.memo.timestamp.formatAsDateTime(dateFormat, timeFormat),
                                                            content = memo.memo.content,
                                                            memo = memo.memo,
                                                        ),
                                                    )
                                                },
                                                menuContent = {},
                                            )

                                            Text(
                                                text = "${page + 1} / ${memos.size}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 16.dp),
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }
        }

        if (showInputSheet) {
            com.lomo.ui.component.input.InputSheet(
                inputValue = inputText,
                onInputValueChange = { inputText = it },
                onDismiss = {
                    showInputSheet = false
                    editingMemo = null
                    inputText = TextFieldValue("")
                },
                onSubmit = { content ->
                    editingMemo?.let { viewModel.updateMemo(it, content) }
                    showInputSheet = false
                    editingMemo = null
                    inputText = TextFieldValue("")
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
                            val (file, uri) =
                                com.lomo.app.util.CameraCaptureUtils
                                    .createTempCaptureUri(context)
                            pendingCameraFile = file
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        }.onFailure {
                            runCatching { pendingCameraFile?.delete() }
                            pendingCameraFile = null
                            pendingCameraUri = null
                        }
                    }
                },
                availableTags = emptyList(),
            )
        }
    }
}
