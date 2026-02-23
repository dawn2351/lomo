package com.lomo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lomo.data.local.entity.LocalFileStateEntity

@Dao
interface LocalFileStateDao {
    @Query("SELECT * FROM local_file_state WHERE filename = :filename AND isTrash = :isTrash")
    suspend fun getByFilename(
        filename: String,
        isTrash: Boolean,
    ): LocalFileStateEntity?

    @Query("SELECT * FROM local_file_state")
    suspend fun getAll(): List<LocalFileStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalFileStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LocalFileStateEntity>)

    @Query("DELETE FROM local_file_state WHERE filename = :filename AND isTrash = :isTrash")
    suspend fun deleteByFilename(
        filename: String,
        isTrash: Boolean,
    )

    @Query("DELETE FROM local_file_state")
    suspend fun clearAll()
}
