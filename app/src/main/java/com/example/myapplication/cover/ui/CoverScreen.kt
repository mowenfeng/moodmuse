package com.example.myapplication.cover.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.cover.domain.CoverUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverScreen(
    state: CoverUiState,
    onBack: () -> Unit,
    onReferenceAudioUrlChange: (String) -> Unit,
    onStylePromptChange: (String) -> Unit,
    onLyricsChange: (String) -> Unit,
    onPreprocessClick: () -> Unit,
    onGenerateClick: () -> Unit
) {
    val context = LocalContext.current

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

            if (state.isPreprocessing || state.isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                value = state.referenceAudioUrl,
                onValueChange = onReferenceAudioUrlChange,
                label = { Text("参考音频 audio_url（后续可扩展本地上传）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !state.isPreprocessing && !state.isGenerating
            )

            OutlinedTextField(
                value = state.stylePrompt,
                onValueChange = onStylePromptChange,
                label = { Text("翻唱风格 prompt") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !state.isPreprocessing && !state.isGenerating
            )

            Button(
                onClick = onPreprocessClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isPreprocessing && !state.isGenerating
            ) {
                Text(if (state.isPreprocessing) "预处理中…" else "预处理（music-cover）")
            }

            OutlinedTextField(
                value = state.lyrics,
                onValueChange = onLyricsChange,
                label = { Text("歌词（formatted_lyrics）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                enabled = !state.isPreprocessing && !state.isGenerating
            )

            Text(text = "调试信息", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = buildString {
                    appendLine("cover_feature_id=${state.coverFeatureId}")
                    appendLine("audio_duration=${state.audioDuration}")
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
                enabled = !state.isPreprocessing && !state.isGenerating
            ) {
                Text(if (state.isGenerating) "生成中…" else "生成翻唱（music-cover-free）")
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
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
