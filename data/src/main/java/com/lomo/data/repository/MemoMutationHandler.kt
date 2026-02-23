package com.lomo.data.repository

import android.net.Uri
import com.lomo.data.local.dao.LocalFileStateDao
import com.lomo.data.local.dao.MemoDao
import com.lomo.data.local.datastore.LomoDataStore
import com.lomo.data.local.entity.LocalFileStateEntity
import com.lomo.data.local.entity.MemoEntity
import com.lomo.data.local.entity.MemoFtsEntity
import com.lomo.data.source.FileDataSource
import com.lomo.data.source.MemoDirectoryType
import com.lomo.data.util.MemoTextProcessor
import com.lomo.data.util.SearchTokenizer
import com.lomo.domain.model.Memo
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Handles active memo mutations (save/update). Trash lifecycle is delegated to [MemoTrashMutationHandler].
 */
class MemoMutationHandler
    @Inject
    constructor(
        private val fileDataSource: FileDataSource,
        private val dao: MemoDao,
        private val localFileStateDao: LocalFileStateDao,
        private val savePlanFactory: MemoSavePlanFactory,
        private val textProcessor: MemoTextProcessor,
        private val dataStore: LomoDataStore,
        private val trashMutationHandler: MemoTrashMutationHandler,
    ) {
        suspend fun saveMemo(
            content: String,
            timestamp: Long,
        ) {
            val filenameFormat = dataStore.storageFilenameFormat.first()
            val timestampFormat = dataStore.storageTimestampFormat.first()
            val candidateFilename =
                DateTimeFormatter
                    .ofPattern(filenameFormat)
                    .format(
                        Instant
                            .ofEpochMilli(timestamp)
                            .atZone(ZoneId.systemDefault()),
                    ) + ".md"
            val existingFileContent = fileDataSource.readFileIn(MemoDirectoryType.MAIN, candidateFilename).orEmpty()
            val savePlan =
                savePlanFactory.create(
                    content = content,
                    timestamp = timestamp,
                    filenameFormat = filenameFormat,
                    timestampFormat = timestampFormat,
                    existingFileContent = existingFileContent,
                )

            val savedUriString =
                fileDataSource.saveFileIn(
                    directory = MemoDirectoryType.MAIN,
                    filename = savePlan.filename,
                    content = "\n${savePlan.rawContent}",
                    append = true,
                )
            val metadata = fileDataSource.getFileMetadataIn(MemoDirectoryType.MAIN, savePlan.filename)
            if (metadata == null) throw java.io.IOException("Failed to read metadata after save")
            upsertMainState(savePlan.filename, metadata.lastModified, savedUriString)

            persistMainMemoEntity(MemoEntity.fromDomain(savePlan.memo))
        }

        suspend fun updateMemo(
            memo: Memo,
            newContent: String,
        ) {
            if (newContent.isBlank()) {
                trashMutationHandler.moveToTrash(memo)
                return
            }
            if (dao.getMemo(memo.id) == null) return

            val timestampFormat = dataStore.storageTimestampFormat.first()
            val filename = memo.date + ".md"
            val updatedMemo =
                memo.copy(
                    content = newContent,
                    rawContent = newContent,
                    timestamp = memo.timestamp,
                    tags = textProcessor.extractTags(newContent),
                    imageUrls = textProcessor.extractImages(newContent),
                )
            val timeString =
                DateTimeFormatter
                    .ofPattern(timestampFormat)
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(memo.timestamp))
            val finalUpdatedMemo = updatedMemo.copy(rawContent = "- $timeString $newContent")

            val cachedUriString = getMainSafUri(filename)
            val currentFileContent =
                if (cachedUriString != null) {
                    fileDataSource.readFile(Uri.parse(cachedUriString))
                        ?: fileDataSource.readFileIn(MemoDirectoryType.MAIN, filename)
                } else {
                    fileDataSource.readFileIn(MemoDirectoryType.MAIN, filename)
                }

            if (currentFileContent != null) {
                val lines = currentFileContent.lines().toMutableList()
                val success =
                    textProcessor.replaceMemoBlock(
                        lines,
                        memo.rawContent,
                        memo.timestamp,
                        newContent,
                        timeString,
                        memo.id,
                    )
                if (success) {
                    val savedUri =
                        fileDataSource.saveFileIn(
                            directory = MemoDirectoryType.MAIN,
                            filename = filename,
                            content = lines.joinToString("\n"),
                            append = false,
                        )
                    val metadata = fileDataSource.getFileMetadataIn(MemoDirectoryType.MAIN, filename)
                    if (metadata != null) {
                        upsertMainState(filename, metadata.lastModified, savedUri)
                    }
                    persistMainMemoEntity(MemoEntity.fromDomain(finalUpdatedMemo))
                }
            }
        }

        suspend fun deleteMemo(memo: Memo) {
            trashMutationHandler.moveToTrash(memo)
        }

        suspend fun restoreMemo(memo: Memo) {
            trashMutationHandler.restoreFromTrash(memo)
        }

        suspend fun deletePermanently(memo: Memo) {
            trashMutationHandler.deleteFromTrashPermanently(memo)
        }

        private suspend fun persistMainMemoEntity(entity: MemoEntity) {
            dao.insertMemo(entity)
            dao.replaceTagRefsForMemo(entity)
            val tokenizedContent = SearchTokenizer.tokenize(entity.content)
            dao.insertMemoFts(MemoFtsEntity(entity.id, tokenizedContent))
        }

        private suspend fun getMainSafUri(filename: String): String? = localFileStateDao.getByFilename(filename, false)?.safUri

        private suspend fun upsertMainState(
            filename: String,
            lastModified: Long,
            safUri: String? = null,
        ) {
            val existing = localFileStateDao.getByFilename(filename, false)
            localFileStateDao.upsert(
                LocalFileStateEntity(
                    filename = filename,
                    isTrash = false,
                    safUri = safUri ?: existing?.safUri,
                    lastKnownModifiedTime = lastModified,
                ),
            )
        }
    }
