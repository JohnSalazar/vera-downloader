package com.vera.downloader.download

import android.app.Notification
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vera.downloader.MainActivity
import com.vera.downloader.VeraApp
import com.vera.downloader.i18n.I18n
import com.vera.downloader.core.MediaItem
import com.vera.downloader.core.MediaKind
import com.vera.downloader.core.ParseResult
import com.vera.downloader.data.Graph
import com.vera.downloader.data.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

enum class EnqueueState { NEW, QUEUED, DONE }

data class EnqueueResult(val id: Long, val state: EnqueueState)

object Downloads {
    fun cancel(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("vera-dl-$id")
    }

    fun enqueue(context: Context, result: ParseResult, item: MediaItem): EnqueueResult {
        val existing = Graph.history.findLatest(result.sourceUrl, item.kind.name, item.quality)
        if (existing != null && existing.isDone) {
            return EnqueueResult(existing.id, EnqueueState.DONE)
        }
        if (existing != null && existing.isRunning) {
            return EnqueueResult(existing.id, EnqueueState.QUEUED)
        }
        val fileName: String
        val id: Long
        if (existing != null && existing.status == Status.ERROR) {
            id = existing.id
            fileName = existing.fileName
            Graph.history.requeue(id)
        } else {
            fileName = fileName(result, item)
            id = Graph.history.insert(
                sourceUrl = result.sourceUrl,
                platform = result.platform.name,
                title = result.title,
                author = result.author,
                thumb = if (item.kind == MediaKind.IMAGE) item.url else result.coverUrl,
                kind = item.kind.name,
                quality = item.quality,
                mime = item.mime,
                fileName = fileName,
                total = item.sizeBytes ?: 0L
            )
        }
        val headersJson = JSONObject(item.headers as Map<*, *>).toString()
        val data = workDataOf(
            "id" to id,
            "url" to item.url,
            "mime" to item.mime,
            "kind" to item.kind.name,
            "fileName" to fileName,
            "title" to result.title,
            "platform" to result.platform.name,
            "sourceUrl" to result.sourceUrl,
            "thumb" to (result.coverUrl ?: ""),
            "quality" to (item.quality ?: ""),
            "headers" to headersJson,
            "size" to (item.sizeBytes ?: 0L),
            "audioUrl" to (item.muxAudioUrl ?: ""),
            "slideshowImages" to JSONArray(item.slideshowImages).toString(),
            "extractAudio" to item.extractAudio
        )
        val network = if (Graph.settings.state.value.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .addTag("vera-download")
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("vera-dl-$id", ExistingWorkPolicy.KEEP, request)
        return EnqueueResult(id, EnqueueState.NEW)
    }

    private fun fileName(result: ParseResult, item: MediaItem): String {
        val safe = result.title
            .replace(Regex("""[\\/:*?"<>|#]"""), " ")
            .replace(Regex("[^\\p{L}\\p{N} _-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(40)
            .trim()
            .ifBlank { "midia" }
        return "${safe}_${System.currentTimeMillis()}.${item.extension}"
    }
}

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong("id", -1L)
        if (id <= 0) return Result.failure()
        val url = inputData.getString("url") ?: return Result.failure()
        val audioUrl = inputData.getString("audioUrl")?.takeIf { it.isNotBlank() }
        val mime = inputData.getString("mime") ?: "application/octet-stream"
        val kind = inputData.getString("kind") ?: MediaKind.VIDEO.name
        val fileName = inputData.getString("fileName") ?: "vera_${System.currentTimeMillis()}"
        val title = inputData.getString("title") ?: "Download"
        val headers = parseHeaders(inputData.getString("headers"))
        val totalHint = inputData.getLong("size", 0L)
        val extractAudio = inputData.getBoolean("extractAudio", false)
        val slideshowImages = runCatching {
            val array = JSONArray(inputData.getString("slideshowImages").orEmpty())
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())

        setForeground(foreground(0, totalHint, title))
        return try {
            val videoFile = File(applicationContext.cacheDir, "vera_${id}_v.tmp")
            var downloaded = 0L
            val finalFile: File
            if (extractAudio) {
                downloadTo(id, url, headers, videoFile, totalHint, title, 0L) { value ->
                    downloaded = value
                }
                val audioFile = File(applicationContext.cacheDir, "vera_${id}_audio.m4a")
                if (!AudioExtractor.extract(videoFile, audioFile) &&
                    !AudioTranscoder.toAac(videoFile, audioFile)
                ) {
                    throw IOException("Não foi possível extrair o áudio deste vídeo")
                }
                finalFile = audioFile
            } else if (slideshowImages.isNotEmpty()) {
                val audioFile = File(applicationContext.cacheDir, "vera_${id}_a.tmp")
                if (url.isNotBlank()) {
                    downloadTo(id, url, headers, audioFile, 0L, title, 0L, {})
                }
                val output = File(applicationContext.cacheDir, "vera_${id}_slideshow.mp4")
                val built = SlideshowBuilder.build(
                    applicationContext,
                    slideshowImages,
                    audioFile.takeIf { it.exists() && it.length() > 0 },
                    output,
                    headers,
                    inputData.getString("thumb")
                ) { progress ->
                    report(id, (progress * 1000).toLong(), 1000L, title)
                }
                audioFile.delete()
                if (built == null || !built.exists() || built.length() == 0L) {
                    throw IOException("Falha ao gerar o vídeo do slideshow")
                }
                finalFile = built
            } else {
                downloadTo(id, url, headers, videoFile, totalHint, title, 0L) { value ->
                    downloaded = value
                }
                var result = videoFile
                if (audioUrl != null) {
                    val audioFile = File(applicationContext.cacheDir, "vera_${id}_a.tmp")
                    val base = downloaded
                    downloadTo(id, audioUrl, headers, audioFile, totalHint, title, base) { value ->
                        downloaded = base + value
                    }
                    val muxed = File(applicationContext.cacheDir, "vera_${id}_mux.mp4")
                    result = if (Mp4Remuxer.remux(videoFile, audioFile, muxed)) muxed else videoFile
                    audioFile.delete()
                    if (result == videoFile) muxed.delete()
                }
                finalFile = result
            }
            val uri = saveToGallery(finalFile, fileName, mime, kind)
            Graph.history.done(id, uri.toString())
            notifyDone(id, title, uri, mime)
            videoFile.delete()
            finalFile.delete()
            Result.success()
        } catch (throwable: Throwable) {
            val raw = throwable.message ?: "Falha no download"
            if (isStopped || throwable is kotlinx.coroutines.CancellationException ||
                raw.contains("Cancelado", ignoreCase = true)
            ) {
                return Result.failure()
            }
            val message = if (raw.contains("403") && url.contains("googlevideo")) {
                "O YouTube recusou este download (HTTP 403). O vídeo pode ter restrição de idade/região ou proteção anti-bot. Tente outro vídeo ou abra no navegador."
            } else {
                raw
            }
            Graph.history.error(id, message)
            notifyError(id, title, message)
            Result.success()
        }
    }

    private suspend fun downloadTo(
        id: Long,
        url: String,
        headers: Map<String, String>,
        destination: File,
        totalHint: Long,
        title: String,
        baseBytes: Long,
        onBytes: (Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        var lastError: IOException? = null
        var forceRange = false
        repeat(4) {
            if (isStopped) throw IOException("Cancelado")
            try {
                if (forceRange || destination.length() > 0) {
                    return@withContext rangedDownload(
                        id, url, headers, destination, totalHint, title, baseBytes, onBytes
                    )
                }
                return@withContext directDownload(
                    id, url, headers, destination, totalHint, title, baseBytes, onBytes
                )
            } catch (error: IOException) {
                lastError = error
                if (error.message?.contains("403") == true ||
                    error.message?.contains("timeout", ignoreCase = true) == true ||
                    error.message?.contains("timed out", ignoreCase = true) == true
                ) {
                    forceRange = true
                }
                if (error.message?.contains("Range ignorado") == true) {
                    destination.delete()
                    forceRange = false
                }
                kotlinx.coroutines.delay(700)
            }
        }
        throw lastError ?: IOException("Falha no download")
    }

    private suspend fun directDownload(
        id: Long,
        url: String,
        headers: Map<String, String>,
        destination: File,
        totalHint: Long,
        title: String,
        baseBytes: Long,
        onBytes: (Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()
        Graph.http.okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Resposta vazia")
            var written = 0L
            var lastUpdate = 0L
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isStopped) throw IOException("Cancelado")
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 400) {
                            lastUpdate = now
                            onBytes(written)
                            report(id, baseBytes + written, totalHint, title)
                        }
                    }
                }
            }
            onBytes(written)
            report(id, baseBytes + written, totalHint, title)
            destination
        }
    }

    /**
     * Alguns streams do googlevideo recusam GET completo com 403, mas aceitam
     * requisições Range com fim limitado (206). Baixamos em blocos de 4 MB.
     */
    private suspend fun rangedDownload(
        id: Long,
        url: String,
        headers: Map<String, String>,
        destination: File,
        totalHint: Long,
        title: String,
        baseBytes: Long,
        onBytes: (Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val chunkSize = 4L * 1024 * 1024
        var start = if (destination.exists()) destination.length() else 0L
        var written = start
        var total = totalHint
        var lastUpdate = 0L
        java.io.FileOutputStream(destination, start > 0).use { output ->
            while (true) {
                if (isStopped) throw IOException("Cancelado")
                val end = start + chunkSize - 1
                var chunkBytes = 0L
                var finished = false
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        headers.forEach { (key, value) -> header(key, value) }
                        header("Range", "bytes=$start-$end")
                    }
                    .build()
                Graph.http.okHttp.newCall(request).execute().use { response ->
                    val body = response.body ?: throw IOException("Resposta vazia")
                    when (response.code) {
                        200 -> {
                            if (start > 0L) throw IOException("Range ignorado")
                            body.byteStream().use { input ->
                                chunkBytes = input.copyTo(output)
                            }
                            written += chunkBytes
                            start += chunkBytes
                            finished = true
                        }
                        206 -> {
                            if (total <= 0) {
                                total = response.header("Content-Range")
                                    ?.substringAfterLast('/')
                                    ?.toLongOrNull()
                                    ?: 0L
                            }
                            body.byteStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    if (isStopped) throw IOException("Cancelado")
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    chunkBytes += read
                                    written += read
                                    start += read
                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdate > 400) {
                                        lastUpdate = now
                                        onBytes(written)
                                        report(id, baseBytes + written, total, title)
                                    }
                                }
                            }
                        }
                        416 -> finished = true
                        else -> throw IOException("HTTP ${response.code}")
                    }
                }
                onBytes(written)
                report(id, baseBytes + written, total, title)
                if (finished) break
                if (chunkBytes <= 0L) break
                if (total > 0 && start >= total) break
                if (total <= 0 && chunkBytes < chunkSize) break
            }
        }
        destination
    }

    private suspend fun report(id: Long, done: Long, totalHint: Long, title: String) {
        val total = if (totalHint > 0) totalHint else 0L
        Graph.history.progress(id, done, if (total > 0) total else done)
        setProgress(workDataOf("bytes" to done, "total" to total))
        setForeground(foreground(if (total > 0) percent(done, total) else 0, total, title))
    }

    private fun percent(done: Long, total: Long): Int =
        if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0

    private fun saveToGallery(file: File, fileName: String, mime: String, kind: String): Uri {
        val resolver = applicationContext.contentResolver
        return if (Build.VERSION.SDK_INT >= 29) {
            val target = when (kind) {
                MediaKind.IMAGE.name ->
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI to
                        "${Environment.DIRECTORY_PICTURES}/Vera Downloader"
                MediaKind.AUDIO.name ->
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to
                        "${Environment.DIRECTORY_MUSIC}/Vera Downloader"
                else ->
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI to
                        "${Environment.DIRECTORY_MOVIES}/Vera Downloader"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, target.second)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(target.first, values)
                ?: throw IOException("MediaStore recusou o arquivo")
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Não foi possível abrir a saída")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            uri
        } else {
            val folder = when (kind) {
                MediaKind.IMAGE.name -> Environment.DIRECTORY_PICTURES
                MediaKind.AUDIO.name -> Environment.DIRECTORY_MUSIC
                else -> Environment.DIRECTORY_MOVIES
            }
            val dir = File(Environment.getExternalStoragePublicDirectory(folder), "Vera Downloader")
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, fileName)
            file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            MediaScannerConnection.scanFile(
                applicationContext, arrayOf(target.absolutePath), arrayOf(mime), null
            )
            Uri.fromFile(target)
        }
    }

    private fun parseHeaders(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.optString(key)
            }
            result
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun foreground(
        progress: Int,
        total: Long,
        title: String,
        id: Long = inputData.getLong("id", -1L)
    ): ForegroundInfo {
        val cancelIntent = Intent(applicationContext, CancelDownloadReceiver::class.java)
            .setAction(CancelDownloadReceiver.ACTION_CANCEL)
            .putExtra("id", id)
        val cancelPending = PendingIntent.getBroadcast(
            applicationContext,
            id.toInt(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(applicationContext, VeraApp.CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(I18n.t(applicationContext, "Baixando"))
            .setContentText(title)
            .setProgress(100, progress, total <= 0)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (id > 0) {
            builder
                .setDeleteIntent(cancelPending)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    I18n.t(applicationContext, "Cancelar"),
                    cancelPending
                )
        }
        val notification = builder.build()
        val type = if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        return ForegroundInfo(NOTIFICATION_ID, notification, type)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = inputData.getString("title") ?: "Download"
        return foreground(0, 0, title)
    }

    private fun notifyDone(id: Long, title: String, uri: Uri, mime: String) {
        val open = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pending = PendingIntent.getActivity(
            applicationContext, id.toInt(), open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, VeraApp.CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(I18n.t(applicationContext, "Download concluído"))
            .setContentText(title)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerHolder.manager(applicationContext)?.notify(id.toInt(), notification)
    }

    private fun notifyError(id: Long, title: String, message: String) {
        val notification = NotificationCompat.Builder(applicationContext, VeraApp.CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(I18n.t(applicationContext, "Falha no download"))
            .setContentText("$title: $message")
            .setAutoCancel(true)
            .build()
        NotificationManagerHolder.manager(applicationContext)?.notify(id.toInt(), notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
    }
}

object NotificationManagerHolder {
    fun manager(context: Context): android.app.NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
}
