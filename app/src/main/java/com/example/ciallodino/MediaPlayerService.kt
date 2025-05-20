package com.example.ciallodino

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import com.example.ciallodino.R

class MediaPlayerService : Service() {
    private val binder = LocalBinder()
    private var jumpSoundPlayer: MediaPlayer? = null

    inner class LocalBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        // 初始化跳跃音效播放器
        jumpSoundPlayer = MediaPlayer.create(this, R.raw.ciallo)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    // 播放跳跃音效
    fun playJumpSound() {
        jumpSoundPlayer?.apply {
            if (isPlaying) {
                stop()
                prepare()
            }
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放资源
        jumpSoundPlayer?.release()
        jumpSoundPlayer = null
    }
}