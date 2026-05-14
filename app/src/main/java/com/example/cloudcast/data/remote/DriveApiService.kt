package com.example.cloudcast.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface DriveApiService {
    @GET("drive/v3/files")
    suspend fun getDriveVideos(
        @Header("Authorization") token: String,
        @Query("q") query: String = "mimeType contains 'video/' and trashed = false",
        @Query("fields") fields: String = "nextPageToken,files(id,name,thumbnailLink,webContentLink,mimeType,size,createdTime,videoMediaMetadata)",
        @Query("pageSize") pageSize: Int = 100,
        @Query("orderBy") orderBy: String = "name"
    ): DriveResponse

    @GET("drive/v3/files")
    suspend fun getDriveVideosPage(
        @Header("Authorization") token: String,
        @Query("q") query: String = "mimeType contains 'video/' and trashed = false",
        @Query("fields") fields: String = "nextPageToken,files(id,name,thumbnailLink,webContentLink,mimeType,size,createdTime,videoMediaMetadata)",
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String
    ): DriveResponse
}

data class DriveResponse(
    val files: List<DriveFileDto>,
    val nextPageToken: String? = null
)

data class DriveFileDto(
    val id: String,
    val name: String,
    val thumbnailLink: String?,
    val webContentLink: String?,
    val mimeType: String,
    val size: String? = null,
    val createdTime: String? = null,
    val videoMediaMetadata: VideoMediaMetadataDto? = null
)

data class VideoMediaMetadataDto(
    val durationMillis: Long? = null
)