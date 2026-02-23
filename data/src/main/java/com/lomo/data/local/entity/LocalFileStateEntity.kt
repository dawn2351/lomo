package com.lomo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "local_file_state",
    primaryKeys = ["filename", "isTrash"],
)
data class LocalFileStateEntity(
    val filename: String,
    val isTrash: Boolean = false,
    @ColumnInfo(name = "saf_uri") val safUri: String? = null,
    @ColumnInfo(name = "last_known_modified_time") val lastKnownModifiedTime: Long,
)
