package com.example.myapplication.domain

enum class GenerationStatus {
    Idle,
    Generating,
    Completed,
    Failed
}

data class MoodMuseUiState(
    val emotionText: String = "",
    val style: String = "lofi",
    val duration: Int = 30,
    val taskId: String? = null,
    val status: GenerationStatus = GenerationStatus.Idle,
    val title: String = "",
    val audioUrl: String = "",
    val previewUrl: String = "",
    val isExportPaid: Boolean = false,
    val errorMessage: String? = null
)
