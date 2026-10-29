package com.vera.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.vera.downloader.data.Graph
import com.vera.downloader.i18n.I18n

class VeraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        I18n.load(this)
        Graph.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL,
            getString(R.string.channel_downloads),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL = "vera_downloads"
    }
}
