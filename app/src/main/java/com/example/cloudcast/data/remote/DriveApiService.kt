package com.example.cloudcast.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface DriveApiService {
    @GET("drive/v3/files")
    suspend fun getDriveVideos(
        @Header("Authorization") token: String,
        @Query("q") query: String = "mimeType contains 'video/' and trashed = false",
        @Query("fields") fields: String = "files(id, name, thumbnailLink, webContentLink, mimeType)",
        @Query("pageSize") pageSize: Int = 50
    ): DriveResponse
}

data class DriveResponse(val files: List<DriveFileDto>)
data class DriveFileDto(
    val id: String, val name: String, val thumbnailLink: String?,
    val webContentLink: String?, val mimeType: String
)