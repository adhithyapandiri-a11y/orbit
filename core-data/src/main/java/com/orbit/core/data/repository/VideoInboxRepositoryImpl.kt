package com.orbit.core.data.repository

import com.orbit.core.data.db.dao.VideoInboxDao
import com.orbit.core.data.db.entity.VideoItemEntity
import com.orbit.core.domain.model.VideoItem
import com.orbit.core.domain.repository.VideoInboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VideoInboxRepositoryImpl(
    private val videoInboxDao: VideoInboxDao
) : VideoInboxRepository {

    override fun getVideos(includeArchived: Boolean): Flow<List<VideoItem>> {
        return videoInboxDao.getVideos(includeArchived).map { list -> list.map { it.toDomain() } }
    }

    override fun getVideoById(id: String): Flow<VideoItem?> {
        return videoInboxDao.getVideoById(id).map { it?.toDomain() }
    }

    override suspend fun upsertVideo(video: VideoItem) {
        videoInboxDao.upsertVideo(VideoItemEntity.fromDomain(video))
    }

    override suspend fun archiveVideo(id: String, isArchived: Boolean) {
        videoInboxDao.setArchived(id, isArchived, System.currentTimeMillis())
    }

    override suspend fun deleteVideo(id: String) {
        videoInboxDao.deleteVideoById(id)
    }

    override suspend fun getAllVideosDirect(): List<VideoItem> {
        return videoInboxDao.getAllVideosDirect().map { it.toDomain() }
    }

    override suspend fun deleteAllVideos() {
        videoInboxDao.deleteAll()
    }
}
