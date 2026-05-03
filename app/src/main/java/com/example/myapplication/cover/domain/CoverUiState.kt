package com.example.myapplication.cover.domain

data class CoverUiState(
    val referenceAudioUrl: String = "",
    /** 本机已选音频的展示名（不含 base64 本体，避免状态过大） */
    val localPickedLabel: String = "",
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
