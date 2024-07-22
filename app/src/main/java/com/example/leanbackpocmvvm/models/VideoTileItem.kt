package com.example.leanbackpocmvvm.models



data class VideoTileItem(
    val tid: String,
    val title: String,
    val poster: String,
    val videoUrl: String? = ""
)