package com.example.myapplication.data

import com.example.myapplication.network.ApiService
import com.example.myapplication.network.CoverGenerateRequest
import com.example.myapplication.network.CoverGenerateResponse
import com.example.myapplication.network.CoverPreprocessRequest
import com.example.myapplication.network.CoverPreprocessResponse
import com.example.myapplication.network.NetworkModule

class CoverRepository(
    private val api: ApiService = NetworkModule.apiService
) {
    suspend fun preprocess(audioUrl: String): CoverPreprocessResponse {
        return api.coverPreprocess(CoverPreprocessRequest(audio_url = audioUrl))
    }

    suspend fun generateCover(
        prompt: String,
        lyrics: String,
        coverFeatureId: String
    ): CoverGenerateResponse {
        return api.coverGenerate(
            CoverGenerateRequest(
                prompt = prompt,
                lyrics = lyrics,
                cover_feature_id = coverFeatureId
            )
        )
    }
}
