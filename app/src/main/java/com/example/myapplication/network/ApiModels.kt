package com.example.myapplication.network

data class GenerateRequest(
    val emotion_text: String,
    val style: String = "lofi",
    val duration: Int = 30
)

data class GenerateResponse(
    val task_id: String,
    val status: String
)

data class TaskResponse(
    val task_id: String,
    val status: String,
    val title: String? = null,
    val audio_url: String? = null,
    val preview_url: String? = null,
    val is_export_paid: Boolean = false,
    val error_message: String? = null
)

data class MockPayResponse(
    val task_id: String,
    val is_export_paid: Boolean
)

data class DownloadResponse(
    val task_id: String,
    val download_url: String
)
