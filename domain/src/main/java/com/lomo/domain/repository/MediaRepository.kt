package com.lomo.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for media file operations (images and voice recordings).
 * Default directory creation is here because it's a media concern, not a settings concern.
 */
interface MediaRepository {
    // Images
    suspend fun saveImage(sourceUri: String): String

    suspend fun deleteImage(filename: String)

    fun getImageUriMap(): Flow<Map<String, String>>

    suspend fun syncImageCache()

    suspend fun createDefaultImageDirectory(): String?

    // Voice
    suspend fun createVoiceFile(filename: String): String

    suspend fun deleteVoiceFile(filename: String)

    suspend fun createDefaultVoiceDirectory(): String?
}
