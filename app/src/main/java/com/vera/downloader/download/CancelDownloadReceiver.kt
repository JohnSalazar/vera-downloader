package com.vera.downloader.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vera.downloader.data.Graph

/** Cancelamento explícito pela notificação (ação ou swipe). */
class CancelDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        if (id <= 0) return
        Downloads.cancel(context, id)
        Graph.history.delete(id)
    }

    companion object {
        const val ACTION_CANCEL = "com.vera.downloader.CANCEL_DOWNLOAD"
    }
}
