package com.lomo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lomo.data.local.dao.FileSyncDao
import com.lomo.data.local.dao.ImageCacheDao
import com.lomo.data.local.dao.MemoDao
import com.lomo.data.local.entity.FileSyncEntity
import com.lomo.data.local.entity.ImageCacheEntity
import com.lomo.data.local.entity.MemoEntity
import com.lomo.data.local.entity.MemoFtsEntity

@Database(
    entities =
        [
            MemoEntity::class,
            ImageCacheEntity::class,
            MemoFtsEntity::class,
            FileSyncEntity::class,
            com.lomo.data.local.entity.FileCacheEntity::class,
        ],
    version = 15,
    exportSchema = false,
)
abstract class MemoDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao

    abstract fun imageCacheDao(): ImageCacheDao

    abstract fun fileSyncDao(): FileSyncDao

    abstract fun fileCacheDao(): com.lomo.data.local.dao.FileCacheDao
}
