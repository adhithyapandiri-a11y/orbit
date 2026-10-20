package com.orbit.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.orbit.core.data.db.entity.VideoItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoInboxDao {

    @Query("SELECT * FROM video_items WHERE (:includeArchived = 1 OR is_archived = 0) ORDER BY created_at_epoch_ms DESC")
    fun getVideos(includeArchived: Boolean): Flow<List<VideoItemEntity>>

    @Query("SELECT * FROM video_items WHERE id = :id")
    fun getVideoById(id: String): Flow<VideoItemEntity?>

    @Query("SELECT * FROM video_items WHERE id = :id")
    suspend fun getVideoByIdDirect(id: String): VideoItemEntity?

    @Query("SELECT * FROM video_items WHERE url = :url LIMIT 1")
    suspend fun getVideoByUrlDirect(url: String): VideoItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideo(video: VideoItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideos(videos: List<VideoItemEntity>)

    @Query("UPDATE video_items SET is_archived = :isArchived, updated_at_epoch_ms = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, isArchived: Boolean, updatedAt: Long)

    @Query("DELETE FROM video_items WHERE id = :id")
    suspend fun deleteVideoById(id: String)

    @Query("SELECT * FROM video_items")
    suspend fun getAllVideosDirect(): List<VideoItemEntity>

    @Query("DELETE FROM video_items")
    suspend fun deleteAll()
}
