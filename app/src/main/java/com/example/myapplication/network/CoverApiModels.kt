package com.example.myapplication.network

data class CoverPreprocessRequest(
    val audio_url: String
)

data class CoverPreprocessResponse(
    val cover_feature_id: String? = null,
    val formatted_lyrics: String? = null,
    val audio_duration: Double? = null,
    val structure_result: String? = null,
    val raw: Map<String, Any?>? = null
)

data class CoverGenerateRequest(
    val prompt: String,
    val lyrics: String,
    val cover_feature_id: String
)

data class CoverGenerateResponse(
    val audio_url: String? = null,
    val preview_url: String? = null,
    val raw: Map<String, Any?>? = null
)
