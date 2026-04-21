package com.example.cloudcast.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val driveId: String,
    val title: String,
    val thumbnailUrl: String?,
    val webContentLink: String?,
    val mimeType: String
)