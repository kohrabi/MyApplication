package com.example.myapplication.models

import kotlinx.serialization.Serializable

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String?,
    val cpn: String?,
    val param: String? = "8AUB",
)