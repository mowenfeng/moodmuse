package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.MusicRepository
import com.example.myapplication.domain.GenerationStatus
import com.example.myapplication.domain.MoodMuseUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MoodMuseViewModel(
    private val repository: MusicRepository = MusicRepository()
) : ViewModel() {
    private companion object {
        const val POLL_INTERVAL_MS = 2500L
        const val MAX_POLL_TIMES = 90
    }

    private val _uiState = MutableStateFlow(MoodMuseUiState())
    val uiState: StateFlow<MoodMuseUiState> = _uiState.asStateFlow()

    fun updateEmotionText(value: String) {
        _uiState.value = _uiState.value.copy(emotionText = value)
    }

    fun applyQuickEmotion(value: String) {
        updateEmotionText(value)
    }

    fun generateMusic() {
        val text = _uiState.value.emotionText.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            runCatching {
                _uiState.value = _uiState.value.copy(
                    status = GenerationStatus.Generating,
                    errorMessage = null,
                    title = "",
                    audioUrl = "",
                    previewUrl = ""
                )
                repository.generate(text, _uiState.value.style, _uiState.value.duration)
            }.onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    taskId = response.task_id,
                    status = GenerationStatus.Generating
                )
                pollTask(response.task_id)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    status = GenerationStatus.Failed,
                    errorMessage = it.message ?: "请求失败"
                )
            }
        }
    }

    private suspend fun pollTask(taskId: String) {
        repeat(MAX_POLL_TIMES) {
            runCatching { repository.getTask(taskId) }
                .onSuccess { task ->
                    when (task.status) {
                        "completed" -> {
                            val playableUrl = task.audio_url?.takeIf { it.isNotBlank() }
                                ?: task.preview_url?.takeIf { it.isNotBlank() }
                            if (playableUrl.isNullOrBlank()) {
                                // 任务状态已完成但 URL 尚未落库，继续轮询避免过早进入结果页。
                                _uiState.value = _uiState.value.copy(status = GenerationStatus.Generating)
                                return@onSuccess
                            }
                            _uiState.value = _uiState.value.copy(
                                status = GenerationStatus.Completed,
                                taskId = task.task_id,
                                title = task.title.orEmpty(),
                                audioUrl = task.audio_url.orEmpty().ifBlank { playableUrl },
                                previewUrl = task.preview_url.orEmpty().ifBlank { playableUrl },
                                isExportPaid = task.is_export_paid,
                                errorMessage = null
                            )
                            return
                        }

                        "failed" -> {
                            _uiState.value = _uiState.value.copy(
                                status = GenerationStatus.Failed,
                                errorMessage = task.error_message ?: "生成失败"
                            )
                            return
                        }

                        else -> {
                            _uiState.value = _uiState.value.copy(status = GenerationStatus.Generating)
                        }
                    }
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        status = GenerationStatus.Failed,
                        errorMessage = it.message ?: "状态查询失败"
                    )
                    return
                }
            delay(POLL_INTERVAL_MS)
        }
        _uiState.value = _uiState.value.copy(
            status = GenerationStatus.Failed,
            errorMessage = "生成超时，请重试"
        )
    }

    fun mockPay(onSuccess: (() -> Unit)? = null) {
        val taskId = _uiState.value.taskId ?: return
        viewModelScope.launch {
            runCatching { repository.mockPay(taskId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isExportPaid = it.is_export_paid)
                    onSuccess?.invoke()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "支付失败")
                }
        }
    }

    fun requestDownloadUrl(onSuccess: (String) -> Unit, onDenied: (String) -> Unit) {
        val taskId = _uiState.value.taskId ?: return
        viewModelScope.launch {
            runCatching { repository.download(taskId) }
                .onSuccess { onSuccess(it.download_url) }
                .onFailure { onDenied(it.message ?: "未支付，无法下载") }
        }
    }
}
