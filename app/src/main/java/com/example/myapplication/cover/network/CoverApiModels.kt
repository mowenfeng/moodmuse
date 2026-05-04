package com.example.myapplication.cover.network

import com.google.gson.JsonElement

data class CoverPreprocessRequest(
    val audio_url: String? = null,
    val audio_base64: String? = null
)

data class CoverPreprocessResponse(
    val cover_feature_id: String? = null,
    val formatted_lyrics: String? = null,
    val audio_duration: Double? = null,
    val structure_result: String? = null,
    val dtw_result: JsonElement? = null,
    val beat_result: JsonElement? = null,
    val raw: Map<String, Any?>? = null
)

data class CoverGenerateRequest(
    val prompt: String,
    val lyrics: String,
    val cover_feature_id: String,
    val audio_duration: Double? = null,
    val dtw_result: JsonElement? = null,
    val beat_result: JsonElement? = null,
    /** 预处理返回的 structure_result，供服务端在缺少 dtw/beat 时兼容填参 */
    val structure_result: String? = null
)

data class CoverGenerateResponse(
    val audio_url: String? = null,
    val preview_url: String? = null,
    val raw: Map<String, Any?>? = null
)
