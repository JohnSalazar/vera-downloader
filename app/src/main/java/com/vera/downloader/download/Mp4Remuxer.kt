package com.vera.downloader.download

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Junta uma faixa de vídeo (MP4/H.264) e uma faixa de áudio (M4A/AAC) em um único MP4,
 * sem recodificar. Usado quando a plataforma entrega DASH (faixas separadas).
 */
object Mp4Remuxer {
    fun remux(video: File, audio: File, output: File): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var transcoded: File? = null
        var success = false
        try {
            var audioSource = audio
            val probe = MediaExtractor().apply { setDataSource(audio.absolutePath) }
            val probeTrack = findTrack(probe, "audio/")
            val probeMime = if (probeTrack >= 0) {
                probe.getTrackFormat(probeTrack).getString(MediaFormat.KEY_MIME)
            } else null
            probe.release()
            if (probeMime != null && probeMime != MediaFormat.MIMETYPE_AUDIO_AAC) {
                val aac = File(audio.parentFile, "vera_aac_${System.nanoTime()}.m4a")
                if (AudioTranscoder.toAac(audio, aac)) {
                    transcoded = aac
                    audioSource = aac
                }
            }
            videoExtractor = MediaExtractor().apply { setDataSource(video.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioSource.absolutePath) }
            val videoTrack = findTrack(videoExtractor, "video/")
            val audioTrack = findTrack(audioExtractor, "audio/")
            if (videoTrack < 0 || audioTrack < 0) return false

            val videoFormat = videoExtractor.getTrackFormat(videoTrack)
            val rawAudioFormat = audioExtractor.getTrackFormat(audioTrack)
            val audioFormat = normalizeAudioFormat(rawAudioFormat)
            Log.d("VeraMuxer", "video=$videoFormat audio=$rawAudioFormat normalized=$audioFormat")
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoOut = muxer.addTrack(videoFormat)
            val audioOut = muxer.addTrack(audioFormat)
            muxer.start()
            copyInterleaved(
                videoExtractor, videoTrack, audioExtractor, audioTrack,
                muxer, videoOut, audioOut
            )
            muxer.stop()
            success = output.length() > 0
        } catch (throwable: Throwable) {
            Log.e("VeraMuxer", "Falha ao remuxar", throwable)
            success = false
            output.delete()
        } finally {
            runCatching { muxer?.release() }
            runCatching { videoExtractor?.release() }
            runCatching { audioExtractor?.release() }
            transcoded?.delete()
        }
        return success
    }

    private fun normalizeAudioFormat(raw: MediaFormat): MediaFormat {
        val mime = raw.getString(MediaFormat.KEY_MIME) ?: return raw
        if (mime != MediaFormat.MIMETYPE_AUDIO_AAC) return raw
        val sampleRate = if (raw.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            raw.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else 0
        val channels = if (raw.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            raw.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else 0
        if (sampleRate <= 0 || channels <= 0) return raw
        val normalized = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channels
        ).apply {
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            raw.getByteBuffer("csd-0")?.let { setByteBuffer("csd-0", it.duplicate()) }
        }
        return normalized
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int {        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return index
        }
        return -1
    }

    private fun copyInterleaved(
        videoExtractor: MediaExtractor,
        videoTrack: Int,
        audioExtractor: MediaExtractor,
        audioTrack: Int,
        muxer: MediaMuxer,
        videoOut: Int,
        audioOut: Int
    ) {
        videoExtractor.selectTrack(videoTrack)
        audioExtractor.selectTrack(audioTrack)
        val videoBuffer = bufferFor(videoExtractor.getTrackFormat(videoTrack))
        val audioBuffer = bufferFor(audioExtractor.getTrackFormat(audioTrack))
        val info = MediaCodec.BufferInfo()
        var videoDone = false
        var audioDone = false
        while (!videoDone || !audioDone) {
            val videoTime = if (!videoDone) videoExtractor.sampleTime else Long.MAX_VALUE
            val audioTime = if (!audioDone) audioExtractor.sampleTime else Long.MAX_VALUE
            if (videoTime == -1L) videoDone = true
            if (audioTime == -1L) audioDone = true
            if (videoDone && audioDone) break
            if (videoTime <= audioTime) {
                videoDone = !writeSample(videoExtractor, videoBuffer, muxer, videoOut, info)
            } else {
                audioDone = !writeSample(audioExtractor, audioBuffer, muxer, audioOut, info)
            }
        }
        videoExtractor.unselectTrack(videoTrack)
        audioExtractor.unselectTrack(audioTrack)
    }

    private fun bufferFor(format: MediaFormat): ByteBuffer {
        val hinted = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else 0
        return ByteBuffer.allocateDirect(if (hinted > 0) hinted else 1 shl 20)
    }

    private fun writeSample(
        extractor: MediaExtractor,
        buffer: ByteBuffer,
        muxer: MediaMuxer,
        outputTrack: Int,
        info: MediaCodec.BufferInfo
    ): Boolean {
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) return false
        info.offset = 0
        info.size = size
        info.presentationTimeUs = extractor.sampleTime
        info.flags = extractor.sampleFlags
        muxer.writeSampleData(outputTrack, buffer, info)
        return extractor.advance()
    }

    private fun copyTrack(
        extractor: MediaExtractor,
        track: Int,
        muxer: MediaMuxer,
        outputTrack: Int
    ) {
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val hinted = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else 0
        val bufferSize = if (hinted > 0) hinted else 1 shl 20
        val buffer = ByteBuffer.allocateDirect(bufferSize)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(outputTrack, buffer, info)
            if (!extractor.advance()) break
        }
        extractor.unselectTrack(track)
    }
}
