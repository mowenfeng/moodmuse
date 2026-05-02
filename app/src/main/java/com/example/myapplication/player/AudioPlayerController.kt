package com.example.myapplication.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class AudioPlayerController(context: Context) {
    private val player = ExoPlayer.Builder(context).build()

    fun prepare(url: String) {
        if (url.isBlank()) return
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
    }

    fun play() {
        player.playWhenReady = true
    }

    fun pause() {
        player.pause()
    }

    fun release() {
        player.release()
    }
}
