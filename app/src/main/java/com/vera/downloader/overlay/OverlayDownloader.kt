package com.vera.downloader.overlay

import android.content.Context
import com.vera.downloader.core.LinkDetector
import com.vera.downloader.core.MediaKind
import com.vera.downloader.data.Graph
import com.vera.downloader.download.Downloads
import com.vera.downloader.download.EnqueueState

object OverlayDownloader {
    suspend fun enqueue(context: Context, text: String): Result<String> {
        val urls = LinkDetector.extractAll(text)
            .filter { it.contains("tiktok.com", ignoreCase = true) }
        if (urls.isEmpty()) {
            return Result.failure(IllegalStateException("Copie um link do TikTok primeiro"))
        }
        return runCatching {
            val result = Graph.parsers.parse(urls.first())
            val item = result.items.firstOrNull { it.kind == MediaKind.VIDEO }
                ?: result.items.firstOrNull()
                ?: error("Nenhum vídeo encontrado")
            val enqueued = Downloads.enqueue(context, result, item)
            when (enqueued.state) {
                EnqueueState.DONE -> "Este item já foi baixado"
                EnqueueState.QUEUED -> "Este download já está na fila"
                EnqueueState.NEW -> "Download adicionado à fila"
            }
        }
    }
}
