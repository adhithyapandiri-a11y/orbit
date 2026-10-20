package com.orbit.core.domain.repository

import com.orbit.core.domain.model.VideoItem
import kotlinx.coroutines.flow.Flow

interface VideoInboxRepository {
    fun getVideos(includeArchived: Boolean): Flow<List<VideoItem>>
    fun getVideoById(id: String): Flow<VideoItem?>
    suspend fun upsertVideo(video: VideoItem)
    suspend fun archiveVideo(id: String, isArchived: Boolean)
    suspend fun deleteVideo(id: String)
    suspend fun getAllVideosDirect(): List<VideoItem>
    suspend fun deleteAllVideos()
}
