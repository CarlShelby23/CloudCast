package com.example.cloudcast.domain.model

data class VideoItem(
    val id: String,
    val title: String,
    val thumbnail: String?,
    val mimeType: String = "",
    val isFavorite: Boolean = false
)
