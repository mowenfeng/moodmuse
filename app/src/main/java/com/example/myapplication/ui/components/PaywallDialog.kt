package com.example.myapplication.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun PaywallDialog(
    onDismiss: () -> Unit,
    onMockPay: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "导出需要付费") },
        text = { Text(text = "试听免费。MVP 阶段使用模拟支付，点击后即可导出下载。") },
        confirmButton = {
            TextButton(onClick = onMockPay) {
                Text("模拟支付")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
