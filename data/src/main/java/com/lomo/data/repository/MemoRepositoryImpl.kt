package com.lomo.data.repository

import com.lomo.data.local.dao.MemoDao
import com.lomo.domain.model.Memo
import com.lomo.domain.repository.MemoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MemoRepositoryImpl
    @Inject
    constructor(
        private val dao: MemoDao,
        private val synchronizer: MemoSynchronizer,
    ) : MemoRepository {
        override fun getAllMemosList(): Flow<List<Memo>> = dao.getAllMemosFlow().map { entities -> entities.map { it.toDomain() } }

        override suspend fun getRandomMemos(limit: Int): List<Memo> = dao.getRandomMemos(limit).map { it.toDomain() }

        override suspend fun getDailyReviewMemos(
            limit: Int,
            seedDate: java.time.LocalDate,
        ): List<Memo> {
            if (limit <= 0) return emptyList()

            val total = dao.getMemoCountSync()
            if (total <= 0) return emptyList()

            val safeLimit = limit.coerceAtMost(total)
            val maxOffset = (total - safeLimit).coerceAtLeast(0)
            val offset =
                if (maxOffset == 0) {
                    0
                } else {
                    kotlin.random.Random(seedDate.toEpochDay()).nextInt(maxOffset + 1)
                }
            return dao.getMemosPage(limit = safeLimit, offset = offset).map { it.toDomain() }
        }

        override suspend fun refreshMemos() {
            synchronizer.refresh()
        }

        override fun isSyncing(): Flow<Boolean> = synchronizer.isSyncing

        override suspend fun saveMemo(
            content: String,
            timestamp: Long,
        ) {
            synchronizer.saveMemo(content, timestamp)
        }

        override suspend fun updateMemo(
            memo: Memo,
            newContent: String,
        ) {
            synchronizer.updateMemo(memo, newContent)
        }

        override suspend fun deleteMemo(memo: Memo) {
            synchronizer.deleteMemo(memo)
        }

        override fun searchMemosList(query: String): Flow<List<Memo>> {
            val trimmed = query.trim()
            val hasCjk =
                trimmed.any {
                    val block =
                        java.lang.Character.UnicodeBlock
                            .of(it)
                    block == java.lang.Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                        block == java.lang.Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                        block == java.lang.Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                        block == java.lang.Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                        block == java.lang.Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT ||
                        block == java.lang.Character.UnicodeBlock.HIRAGANA ||
                        block == java.lang.Character.UnicodeBlock.KATAKANA ||
                        block == java.lang.Character.UnicodeBlock.HANGUL_SYLLABLES
                }

            val source =
                if (hasCjk) {
                    val tokens =
                        com.lomo.data.util.SearchTokenizer
                            .tokenize(trimmed)
                            .split(Regex("\\s+"))
                            .filter { it.isNotBlank() }
                            .distinct()
                            .take(5)
                    if (tokens.isEmpty()) {
                        dao.searchMemosFlow(trimmed)
                    } else {
                        val matchQuery = tokens.joinToString(" OR ") { token -> "$token*" }
                        dao.searchMemosByFtsFlow(matchQuery)
                    }
                } else {
                    dao.searchMemosFlow(trimmed)
                }
            return source.map { entities -> entities.map { it.toDomain() } }
        }

        override fun getMemosByTagList(tag: String): Flow<List<Memo>> =
            dao.getMemosByTagFlow(tag, "$tag/%").map { entities -> entities.map { it.toDomain() } }

        override fun getAllTags(): Flow<List<String>> = dao.getAllTagsFlow()

        override fun getMemoCount(): Flow<Int> = dao.getMemoCount()

        override fun getActiveDayCount(): Flow<Int> = dao.getActiveDayCount()

        override fun getAllTimestamps(): Flow<List<Long>> = dao.getAllTimestamps()

        override fun getTagCounts(): Flow<List<com.lomo.domain.model.TagCount>> =
            dao.getTagCountsFlow().map { rows ->
                rows.map { row ->
                    com.lomo.domain.model
                        .TagCount(row.name, row.count)
                }
            }

        override fun getDeletedMemosList(): Flow<List<Memo>> = dao.getDeletedMemosFlow().map { entities -> entities.map { it.toDomain() } }

        override suspend fun restoreMemo(memo: Memo) {
            synchronizer.restoreMemo(memo)
        }

        override suspend fun deletePermanently(memo: Memo) {
            synchronizer.deletePermanently(memo)
        }
    }
