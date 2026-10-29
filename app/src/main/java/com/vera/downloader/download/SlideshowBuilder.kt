package com.vera.downloader.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import com.vera.downloader.data.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer

/**
 * Gera um MP4 a partir das imagens de um post de foto do TikTok + o áudio original.
 * O TikTok não entrega um vídeo pronto nesses posts; o vídeo é criado localmente.
 */
object SlideshowBuilder {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val FPS = 24

    suspend fun build(
        context: Context,
        imageUrls: List<String>,
        audio: File?,
        output: File,
        headers: Map<String, String>,
        coverUrl: String? = null,
        onProgress: suspend (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "slideshow").apply { mkdirs() }
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val width = if (imageUrls.size > 4) 720 else WIDTH
            val height = if (width == WIDTH) HEIGHT else 1280
            for ((index, url) in imageUrls.take(6).withIndex()) {
                val file = File(dir, "img_$index")
                runCatching {
                    val request = Request.Builder()
                        .url(url)
                        .apply { headers.forEach { (key, value) -> header(key, value) } }
                        .build()
                    Graph.http.okHttp.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        response.body?.byteStream()?.use { input ->
                            file.outputStream().use { outputStream -> input.copyTo(outputStream) }
                        }
                    }
                }
                val bitmap = runCatching { decode(file) }.getOrNull()
                if (bitmap != null) {
                    bitmaps += fitFrame(bitmap, width, height)
                    if (bitmap !== bitmaps.last()) bitmap.recycle()
                }
                file.delete()
            }
            if (bitmaps.isEmpty() && !coverUrl.isNullOrBlank()) {
                val cover = File(dir, "cover")
                runCatching {
                    val request = Request.Builder()
                        .url(coverUrl)
                        .apply { headers.forEach { (key, value) -> header(key, value) } }
                        .build()
                    Graph.http.okHttp.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        response.body?.byteStream()?.use { input ->
                            cover.outputStream().use { outputStream -> input.copyTo(outputStream) }
                        }
                    }
                }
                val bitmap = runCatching { decode(cover) }.getOrNull()
                if (bitmap != null) {
                    bitmaps += fitFrame(bitmap, width, height)
                    if (bitmap !== bitmaps.last()) bitmap.recycle()
                }
                cover.delete()
            }
            if (bitmaps.isEmpty()) return@withContext null

            val durationMs = audio?.let { durationOf(it) } ?: 15_000L
            val safeDuration = durationMs.coerceIn(1_000L, 180_000L)
            val frameCount = ((safeDuration / 1000.0) * FPS).toInt().coerceAtLeast(FPS)
            val videoOnly = File(dir, "video_${System.currentTimeMillis()}.mp4")
            Log.d(
                "VeraSlideshow",
                "images=${imageUrls.size} bitmaps=${bitmaps.size} frames=$frameCount audio=${audio?.length()}"
            )
            val encoded = encode(bitmaps, frameCount, videoOnly, onProgress, width, height)
            Log.d("VeraSlideshow", "encoded=$encoded size=${videoOnly.length()}")
            if (!encoded && width != 720) {
                // Alguns encoders recusam 1080p; tenta 720p antes de desistir.
                val small = bitmaps.map {
                    Bitmap.createScaledBitmap(it, 720, 1280, true)
                }
                videoOnly.delete()
                val retry = encode(small, frameCount, videoOnly, onProgress, 720, 1280)
                small.forEach { it.recycle() }
                if (!retry) {
                    videoOnly.delete()
                    return@withContext null
                }
            } else if (!encoded) {
                videoOnly.delete()
                return@withContext null
            }
            if (audio == null || !audio.exists()) {
                if (!videoOnly.renameTo(output)) {
                    videoOnly.copyTo(output, overwrite = true)
                    videoOnly.delete()
                }
                return@withContext output
            }
            output.delete()
            val muxed = Mp4Remuxer.remux(videoOnly, audio, output)
            Log.d(
                "VeraSlideshow",
                "remux=$muxed video=${videoOnly.length()} audio=${audio.length()} output=${output.length()}"
            )
            if (muxed) {
                videoOnly.delete()
                output
            } else {
                // Mesmo sem a faixa de áudio, entrega o vídeo em vez de falhar.
                videoOnly.copyTo(output, overwrite = true)
                videoOnly.delete()
                output
            }
        } finally {
            bitmaps.forEach { runCatching { it.recycle() } }
        }
    }

    private fun durationOf(audio: File): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(audio.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 15_000L
        } finally {
            retriever.release()
        }
    }.getOrDefault(15_000L)

    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun fitFrame(source: Bitmap, width: Int, height: Int): Bitmap {
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val backgroundScale = maxOf(
            width.toFloat() / source.width,
            height.toFloat() / source.height
        )
        val background = Bitmap.createScaledBitmap(
            source,
            (source.width * backgroundScale).toInt().coerceAtLeast(width),
            (source.height * backgroundScale).toInt().coerceAtLeast(height),
            true
        )
        canvas.drawBitmap(
            background,
            (width - background.width) / 2f,
            (height - background.height) / 2f,
            paint
        )
        if (background !== source) background.recycle()
        canvas.drawColor(Color.argb(70, 0, 0, 0))

        val padding = 42f
        val foregroundScale = minOf(
            (width - padding * 2) / source.width,
            (height - padding * 2) / source.height
        )
        val foreground = Bitmap.createScaledBitmap(
            source,
            (source.width * foregroundScale).toInt().coerceAtLeast(1),
            (source.height * foregroundScale).toInt().coerceAtLeast(1),
            true
        )
        canvas.drawBitmap(
            foreground,
            (width - foreground.width) / 2f,
            (height - foreground.height) / 2f,
            paint
        )
        if (foreground !== source) foreground.recycle()
        source.recycle()
        return frame
    }

    private suspend fun encode(
        bitmaps: List<Bitmap>,
        frameCount: Int,
        output: File,
        onProgress: suspend (Float) -> Unit,
        width: Int,
        height: Int
    ): Boolean {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var started = false
        var track = -1
        var samples = 0
        return try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()
            val pixels = IntArray(width * height)
            var frame = 0
            var eosQueued = false
            var eosWritten = false
            while (!eosWritten) {
                if (!eosQueued && frame < frameCount) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex) ?: error("Buffer nulo")
                        buffer.clear()
                        val bitmap = bitmaps[(frame.toLong() * bitmaps.size / frameCount)
                            .toInt().coerceIn(0, bitmaps.lastIndex)]
                        fillYuv(bitmap, buffer, pixels, width, height)
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            width * height * 3 / 2,
                            frame * 1_000_000L / FPS,
                            0
                        )
                        frame++
                        onProgress((frame.toFloat() / frameCount).coerceIn(0f, 1f))
                    }
                } else if (!eosQueued) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, frame * 1_000_000L / FPS,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eosQueued = true
                    }
                }
                while (true) {
                    val outputIndex = codec.dequeueOutputBuffer(info, 0)
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    } else if (outputIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0 && started &&
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                            samples++
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            eosWritten = true
                        }
                    } else {
                        break
                    }
                }
            }
            samples > 0 && output.length() > 0
        } catch (throwable: Throwable) {
            Log.e("VeraSlideshow", "Falha ao codificar vídeo", throwable)
            output.delete()
            false
        } finally {
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
        }
    }

    private fun fillYuv(bitmap: Bitmap, buffer: ByteBuffer, argb: IntArray, width: Int, height: Int) {
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val ySize = width * height
        val uvSize = ySize / 4
        buffer.clear()
        for (color in argb) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
            buffer.put(y.toByte())
        }
        for (j in 0 until height step 2) {
            for (i in 0 until width step 2) {
                val color = argb[j * width + i]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                buffer.put(u.toByte())
                buffer.put(v.toByte())
            }
        }
        buffer.position(0)
        buffer.limit(ySize + uvSize * 2)
    }
}
