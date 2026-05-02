package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.player.AudioPlayerController

@Composable
fun ResultScreen(
    title: String,
    previewUrl: String,
    isPaid: Boolean,
    onRegenerate: () -> Unit,
    onExportClick: () -> Unit
) {
    val context = LocalContext.current
    val playerController = remember { AudioPlayerController(context) }

    DisposableEffect(previewUrl) {
        playerController.prepare(previewUrl)
        onDispose { playerController.release() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = "状态: completed", style = MaterialTheme.typography.labelLarge)
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(text = "试听链接: $previewUrl", style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { playerController.play() }) { Text("播放") }
            OutlinedButton(onClick = { playerController.pause() }) { Text("暂停") }
        }

        Button(
            onClick = onRegenerate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("再生成一次")
        }

        Button(
            onClick = onExportClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isPaid) "导出/下载（已付费）" else "导出/下载")
        }
    }
}
