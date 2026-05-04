package com.example.myapplication.cover.ui

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.cover.domain.CoverUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) {
                    val n = c.getString(i)
                    if (!n.isNullOrBlank()) return n
                }
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "audio"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverScreen(
    state: CoverUiState,
    onBack: () -> Unit,
    onReferenceAudioUrlChange: (String) -> Unit,
    onStylePromptChange: (String) -> Unit,
    onLyricsChange: (String) -> Unit,
    onPreprocessClick: () -> Unit,
    onGenerateClick: () -> Unit,
    onLocalAudioPicked: (displayName: String, bytes: ByteArray) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                withContext(Dispatchers.Main) {
                    onLocalAudioPicked("(无法打开文件)", byteArrayOf())
                }
                return@launch
            }
            val name = resolveDisplayName(context, uri)
            withContext(Dispatchers.Main) {
                onLocalAudioPicked(name, bytes)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 翻唱（music-cover）") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "通过后端转发调用 MiniMax，不在客户端保存 API Key。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "说明：在电脑 PowerShell 里跑脚本成功，不会自动出现在模拟器里；请在下方「从本机选择音频」或填写公网 audio_url。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!state.errorMessage.isNullOrBlank()) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (state.isPreparingLocalAudio || state.isPreprocessing || state.isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.isPreparingLocalAudio) {
                Text(
                    text = "正在读取并编码本地音频（大文件可能要几十秒）…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = state.referenceAudioUrl,
                onValueChange = onReferenceAudioUrlChange,
                label = { Text("参考音频 audio_url（与下方本机文件二选一）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !state.isPreparingLocalAudio && !state.isPreprocessing && !state.isGenerating
            )

            OutlinedButton(
                onClick = { pickAudio.launch("audio/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isPreparingLocalAudio && !state.isPreprocessing && !state.isGenerating
            ) {
                Text("从本机选择音频（走 audio_base64）")
            }
            if (state.localPickedLabel.isNotBlank()) {
                Text(
                    text = "已选本地：${state.localPickedLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = state.stylePrompt,
                onValueChange = onStylePromptChange,
                label = { Text("翻唱风格 prompt") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !state.isPreparingLocalAudio && !state.isPreprocessing && !state.isGenerating
            )

            Button(
                onClick = onPreprocessClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isPreparingLocalAudio && !state.isPreprocessing && !state.isGenerating
            ) {
                Text(if (state.isPreprocessing) "预处理中…" else "预处理（music-cover）")
            }

            OutlinedTextField(
                value = state.lyrics,
                onValueChange = onLyricsChange,
                label = { Text("歌词（formatted_lyrics）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                enabled = !state.isPreparingLocalAudio && !state.isPreprocessing && !state.isGenerating
            )

            Text(text = "调试信息", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = buildString {
                    appendLine("cover_feature_id=${state.coverFeatureId}")
                    appendLine("audio_duration=${state.audioDuration}")
                    appendLine("dtw_result=${if (state.hasDtwFromPreprocess) "已带回" else "未带回"}")
                    appendLine("beat_result=${if (state.hasBeatFromPreprocess) "已带回" else "未带回"}")
                    appendLine("structure_result=${state.structureResult}")
                }.trim(),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("预处理结果字段") }
            )

            Button(
                onClick = onGenerateClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isPreparingLocalAudio && !state.isPreprocessing && !state.isGenerating
            ) {
                Text(if (state.isGenerating) "生成中…" else "生成翻唱（music-cover-free）")
            }

            if (state.outputAudioUrl.isNotBlank()) {
                Text(text = "输出音频", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.outputAudioUrl,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("audio_url") }
                )

                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(state.outputAudioUrl))
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text("播放 / 打开链接")
                }

                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, state.outputAudioUrl)
                        }
                        context.startActivity(Intent.createChooser(send, "分享下载链接"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text("复制/分享下载入口（链接）")
                }
            }
        }
    }
}
