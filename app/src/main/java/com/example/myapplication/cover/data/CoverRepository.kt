package com.example.myapplication.cover.data

import com.example.myapplication.cover.network.CoverGenerateRequest
import com.example.myapplication.cover.network.CoverGenerateResponse
import com.example.myapplication.cover.network.CoverPreprocessRequest
import com.example.myapplication.cover.network.CoverPreprocessResponse
import com.example.myapplication.network.ApiService
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
