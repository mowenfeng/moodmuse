package com.example.myapplication.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("api/generate")
    suspend fun generate(@Body request: GenerateRequest): GenerateResponse

    @GET("api/tasks/{taskId}")
    suspend fun getTask(@Path("taskId") taskId: String): TaskResponse

    @POST("api/tasks/{taskId}/mock-pay")
    suspend fun mockPay(@Path("taskId") taskId: String): MockPayResponse

    @GET("api/tasks/{taskId}/download")
    suspend fun download(@Path("taskId") taskId: String): DownloadResponse
}

typealias MoodMuseApi = ApiService
