package com.example.myapplication.cover.domain

data class CoverUiState(
    val referenceAudioUrl: String = "",
    val stylePrompt: String = "",
    val lyrics: String = "",
    val coverFeatureId: String = "",
    val audioDuration: Double? = null,
    val structureResult: String = "",
    val outputAudioUrl: String = "",
    val isPreprocessing: Boolean = false,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)
