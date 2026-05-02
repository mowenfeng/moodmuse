package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.BuildConfig

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    emotionText: String,
    errorMessage: String?,
    onEmotionChange: (String) -> Unit,
    onQuickEmotionClick: (String) -> Unit,
    onGenerateClick: () -> Unit
) {
    val quickEmotions = remember { listOf("孤独", "开心", "失恋", "治愈", "夜晚开车", "电影感") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "MoodMuse AI", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "版本: ${BuildConfig.MOODMUSE_BUILD_VERSION}",
            style = MaterialTheme.typography.labelSmall
        )
        Text(text = "输入一句情绪描述，让 AI 为你生成音乐")

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickEmotions.forEach { emotion ->
                ElevatedAssistChip(
                    onClick = { onQuickEmotionClick(emotion) },
                    label = { Text(text = emotion) }
                )
            }
        }

        OutlinedTextField(
            value = emotionText,
            onValueChange = onEmotionChange,
            label = { Text("例如：下雨天一个人吃泡面") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = onGenerateClick,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text("生成音乐")
        }
    }
}
