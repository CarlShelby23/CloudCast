package com.example.cloudcast.data.local

data class DownloadRecord(
    val driveId: String,
    val title: String,
    val thumbnail: String?,
    val downloadId: Long
)