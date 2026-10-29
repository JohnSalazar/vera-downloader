package com.vera.downloader.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Extrai a faixa de áudio de um MP4/M4A para um arquivo M4A, sem recodificar. */
object AudioExtractor {
    fun extract(video: File, output: File): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            extractor = MediaExtractor().apply { setDataSource(video.absolutePath) }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return false
            val format = extractor.getTrackFormat(track)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            started = true
            extractor.selectTrack(track)
            val buffer = ByteBuffer.allocateDirect(
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                } else {
                    1 shl 20
                }
            )
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(outTrack, buffer, info)
                if (!extractor.advance()) break
            }
            muxer.stop()
            output.length() > 0
        } catch (_: Throwable) {
            output.delete()
            false
        } finally {
            runCatching { if (started) muxer?.release() }
            runCatching { extractor?.release() }
        }
    }
}
