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
    /** 预处理是否带回 music-cover 第二步常见所需的分析字段（便于自查） */
    val hasDtwFromPreprocess: Boolean = false,
    val hasBeatFromPreprocess: Boolean = false,
    val outputAudioUrl: String = "",
    val isPreprocessing: Boolean = false,
    val isGenerating: Boolean = false,
    /** 本机选歌后正在读文件 / Base64 编码，避免用户以为卡住 */
    val isPreparingLocalAudio: Boolean = false,
    val errorMessage: String? = null
)
