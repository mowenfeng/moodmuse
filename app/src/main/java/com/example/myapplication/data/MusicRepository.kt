package com.example.myapplication.data

import com.example.myapplication.network.DownloadResponse
import com.example.myapplication.network.GenerateRequest
import com.example.myapplication.network.GenerateResponse
import com.example.myapplication.network.MockPayResponse
import com.example.myapplication.network.ApiService
import com.example.myapplication.network.NetworkModule
import com.example.myapplication.network.TaskResponse

class MusicRepository(
    private val api: ApiService = NetworkModule.apiService
) {
    suspend fun generate(emotionText: String, style: String, duration: Int): GenerateResponse {
        return api.generate(
            GenerateRequest(
                emotion_text = emotionText,
                style = style,
                duration = duration
            )
        )
    }

    suspend fun getTask(taskId: String): TaskResponse = api.getTask(taskId)

    suspend fun mockPay(taskId: String): MockPayResponse = api.mockPay(taskId)

    suspend fun download(taskId: String): DownloadResponse = api.download(taskId)
}
