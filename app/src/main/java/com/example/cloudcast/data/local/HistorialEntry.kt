package com.example.cloudcast.data.local

data class HistorialEntry(
    val driveId: String,
    val title: String,
    val thumbnailUrl: String?,
    val timestamp: Long = System.currentTimeMillis()
)