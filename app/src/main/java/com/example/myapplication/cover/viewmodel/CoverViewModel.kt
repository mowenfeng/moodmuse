package com.example.myapplication.cover.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.cover.data.CoverRepository
import com.example.myapplication.cover.domain.CoverUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class CoverViewModel(
    private val repository: CoverRepository = CoverRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(CoverUiState())
    val uiState: StateFlow<CoverUiState> = _uiState.asStateFlow()

    fun updateReferenceAudioUrl(value: String) {
        _uiState.value = _uiState.value.copy(referenceAudioUrl = value, errorMessage = null)
    }

    fun updateStylePrompt(value: String) {
        _uiState.value = _uiState.value.copy(stylePrompt = value, errorMessage = null)
    }

    fun updateLyrics(value: String) {
        _uiState.value = _uiState.value.copy(lyrics = value, errorMessage = null)
    }

    fun preprocess() {
        val url = _uiState.value.referenceAudioUrl.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请先填写参考音频 audio_url")
            return
        }

        viewModelScope.launch {
            runCatching {
                _uiState.value = _uiState.value.copy(
                    isPreprocessing = true,
                    errorMessage = null
                )
                repository.preprocess(url)
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
