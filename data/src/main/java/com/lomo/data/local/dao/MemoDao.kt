package com.lomo.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lomo.data.local.entity.MemoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Query("SELECT * FROM Lomo WHERE isDeleted = 0 ORDER BY timestamp DESC, id DESC")
    fun getAllMemos(): PagingSource<Int, MemoEntity>

    @Query("SELECT * FROM Lomo WHERE isDeleted = 0 ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomMemos(limit: Int): List<MemoEntity>

    @Query("SELECT * FROM Lomo WHERE isDeleted = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMemos(limit: Int): List<MemoEntity>

    @Query("SELECT id FROM Lomo WHERE isDeleted = 0")
    suspend fun getAllMemoIds(): List<String>

    @Query("SELECT * FROM Lomo WHERE id IN (:ids)")
    suspend fun getMemosByIds(ids: List<String>): List<MemoEntity>

    @Query("SELECT COUNT(*) FROM Lomo WHERE isDeleted = 0")
    suspend fun getMemoCountSync(): Int

    @Query(
        """
        SELECT * FROM Lomo
        WHERE isDeleted = 0
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getMemosPage(
        limit: Int,
        offset: Int,
    ): List<MemoEntity>

    // 保留原 LIKE 作为兜底或英文/符号搜索
    @Query(
        """
        SELECT * FROM Lomo 
        WHERE content LIKE '%' || :query || '%' 
        AND isDeleted = 0 
        ORDER BY timestamp DESC, id DESC
        """,
    )
    fun searchMemos(query: String): PagingSource<Int, MemoEntity>

    @Query(
        """
        SELECT Lomo.* FROM Lomo
        INNER JOIN lomo_fts ON lomo_fts.memoId = Lomo.id
        WHERE Lomo.isDeleted = 0 AND lomo_fts MATCH :matchQuery
        ORDER BY Lomo.timestamp DESC, Lomo.id DESC
        """,
    )
    fun searchMemosByFts(matchQuery: String): PagingSource<Int, MemoEntity>

    // FTS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoFts(fts: com.lomo.data.local.entity.MemoFtsEntity)

    @Query("DELETE FROM lomo_fts WHERE memoId = :memoId")
    suspend fun deleteMemoFts(memoId: String)

    @Query("DELETE FROM lomo_fts WHERE memoId IN (:memoIds)")
    suspend fun deleteMemoFtsByIds(memoIds: List<String>)

    @Query("DELETE FROM lomo_fts")
    suspend fun clearFts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemos(memos: List<MemoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemo(memo: MemoEntity)

    @Delete suspend fun deleteMemo(memo: MemoEntity) // Used for permanent delete

    @Query("UPDATE Lomo SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteMemo(id: String)

    @Query("SELECT * FROM Lomo WHERE isDeleted = 1 ORDER BY timestamp DESC, id DESC")
    fun getDeletedMemos(): PagingSource<Int, MemoEntity>

    @Query("DELETE FROM Lomo WHERE id = :id")
    suspend fun deleteMemoById(id: String)

    @Query("DELETE FROM Lomo WHERE id IN (:ids)")
    suspend fun deleteMemosByIds(ids: List<String>)

    @Query("DELETE FROM Lomo")
    suspend fun clearAll()

    @Query("DELETE FROM Lomo WHERE id NOT IN (:ids)")
    suspend fun deleteMemosNotIn(ids: List<String>)

    @Query("SELECT * FROM Lomo WHERE id = :id")
    suspend fun getMemo(id: String): MemoEntity?

    @Query("SELECT * FROM Lomo")
    suspend fun getAllMemosSync(): List<MemoEntity>

    @Query("SELECT * FROM Lomo WHERE date = :date AND isDeleted = :isDeleted")
    suspend fun getMemosByDate(
        date: String,
        isDeleted: Boolean,
    ): List<MemoEntity>

    @Query("DELETE FROM Lomo WHERE date = :date AND isDeleted = :isDeleted")
    suspend fun deleteMemosByDate(
        date: String,
        isDeleted: Boolean,
    )

    // Tag Support (flat tags column in Lomo table)
    @Query(
        """
        SELECT * FROM Lomo
        WHERE isDeleted = 0
        AND (
            (',' || tags || ',') LIKE '%,' || :tag || ',%'
            OR (',' || tags || ',') LIKE '%,' || :tag || '/%'
        )
        ORDER BY Lomo.timestamp DESC, Lomo.id DESC
    """,
    )
    fun getMemosByTag(tag: String): PagingSource<Int, MemoEntity>

    @Query("SELECT tags FROM Lomo WHERE isDeleted = 0 AND tags != ''")
    fun getAllTagStrings(): Flow<List<String>>

    // Stats
    @Query("SELECT COUNT(*) FROM Lomo WHERE isDeleted = 0")
    fun getMemoCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT date) FROM Lomo WHERE isDeleted = 0")
    fun getActiveDayCount(): Flow<Int>

    @Query("SELECT timestamp FROM Lomo WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllTimestamps(): Flow<List<Long>>

    @Query(
        "SELECT COUNT(*) FROM Lomo WHERE imageUrls LIKE '%' || :imagePath || '%' AND id != :excludeId",
    )
    suspend fun countMemosWithImage(
        imagePath: String,
        excludeId: String,
    ): Int
}
