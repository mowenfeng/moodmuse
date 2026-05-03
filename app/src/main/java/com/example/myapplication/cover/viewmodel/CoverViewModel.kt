package com.example.myapplication.cover.viewmodel

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.cover.data.CoverRepository
import com.example.myapplication.cover.domain.CoverUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class CoverViewModel(
    private val repository: CoverRepository = CoverRepository()
) : ViewModel() {
    private companion object {
        /** 原始音频上限，避免 base64 后请求体过大导致超时/内存压力（MiniMax 文档约 50MB） */
        const val MAX_RAW_BYTES = 35 * 1024 * 1024
    }

    /** 与 `audio_url` 二选一；不放进 StateFlow，避免巨型字符串触发频繁重组 */
    private var pendingAudioBase64: String? = null

    private val _uiState = MutableStateFlow(CoverUiState())
    val uiState: StateFlow<CoverUiState> = _uiState.asStateFlow()

    fun updateReferenceAudioUrl(value: String) {
        pendingAudioBase64 = null
        _uiState.value = _uiState.value.copy(
            referenceAudioUrl = value,
            localPickedLabel = "",
            errorMessage = null
        )
    }

    /**
     * 从系统文件选择器读入整文件并转 base64（在后台线程执行编码）。
     */
    fun ingestLocalAudio(displayName: String, bytes: ByteArray) {
        if (bytes.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "读取到的文件为空")
            return
        }
        if (bytes.size > MAX_RAW_BYTES) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "文件过大（>${MAX_RAW_BYTES / 1024 / 1024}MB），请压缩或换短音频",
                localPickedLabel = ""
            )
            pendingAudioBase64 = null
            return
        }
        viewModelScope.launch {
            try {
                val b64 = withContext(Dispatchers.Default) {
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
                pendingAudioBase64 = b64
                _uiState.value = _uiState.value.copy(
                    referenceAudioUrl = "",
                    localPickedLabel = displayName.ifBlank { "已选本地音频" },
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message?.takeIf { it.isNotBlank() } ?: "读取本地文件失败",
                    localPickedLabel = ""
                )
                pendingAudioBase64 = null
            }
        }
    }

    fun updateStylePrompt(value: String) {
        _uiState.value = _uiState.value.copy(stylePrompt = value, errorMessage = null)
    }

    fun updateLyrics(value: String) {
        _uiState.value = _uiState.value.copy(lyrics = value, errorMessage = null)
    }

    fun preprocess() {
        val url = _uiState.value.referenceAudioUrl.trim()
        val b64 = pendingAudioBase64?.takeIf { it.isNotBlank() }
        if (url.isBlank() && b64.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "请填写 audio_url，或使用「从本机选择音频」"
            )
            return
        }
        if (url.isNotBlank() && !b64.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "audio_url 与本地文件二选一，请清空其一")
            return
        }

        viewModelScope.launch {
            runCatching {
                _uiState.value = _uiState.value.copy(
                    isPreprocessing = true,
                    errorMessage = null
                )
                if (url.isNotBlank()) {
                    repository.preprocess(audioUrl = url, audioBase64 = null)
                } else {
                    repository.preprocess(audioUrl = null, audioBase64 = b64)
                }
            }.onSuccess { resp ->
                val lyrics = resp.formatted_lyrics.orEmpty()
                _uiState.value = _uiState.value.copy(
                    isPreprocessing = false,
                    coverFeatureId = resp.cover_feature_id.orEmpty(),
                    audioDuration = resp.audio_duration,
                    structureResult = resp.structure_result.orEmpty(),
                    lyrics = lyrics.ifBlank { _uiState.value.lyrics },
                    errorMessage = null
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isPreprocessing = false,
                    errorMessage = e.toReadableMessage()
                )
            }
        }
    }

    fun generateCover() {
        val prompt = _uiState.value.stylePrompt.trim()
        val lyrics = _uiState.value.lyrics.trim()
        val featureId = _uiState.value.coverFeatureId.trim()
        if (prompt.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请先填写翻唱风格 prompt")
            return
        }
        if (lyrics.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请先填写歌词（可先预处理自动填入）")
            return
        }
        if (featureId.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "缺少 cover_feature_id，请先预处理")
            return
        }

        viewModelScope.launch {
            runCatching {
                _uiState.value = _uiState.value.copy(
                    isGenerating = true,
                    errorMessage = null,
                    outputAudioUrl = ""
                )
                repository.generateCover(prompt = prompt, lyrics = lyrics, coverFeatureId = featureId)
            }.onSuccess { resp ->
                val out = (
                    resp.audio_url?.takeIf { it.isNotBlank() }
                        ?: resp.preview_url?.takeIf { it.isNotBlank() }
                    ).orEmpty()
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    outputAudioUrl = out,
                    errorMessage = if (out.isBlank()) "生成成功但未返回可播放 URL" else null
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    errorMessage = e.toReadableMessage()
                )
            }
        }
    }

    private fun Throwable.toReadableMessage(): String {
        if (this is HttpException) {
            val body = runCatching { response()?.errorBody()?.string() }.getOrNull().orEmpty()
            if (body.isNotBlank()) return body
        }
        return message?.takeIf { it.isNotBlank() } ?: "请求失败"
    }
}
