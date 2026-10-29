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
 * Converte a faixa de áudio de um MP3 (ou outro formato que o MediaMuxer MP4 não aceita)
 * para AAC-LC, permitindo juntar com o vídeo gerado.
 */
object AudioTranscoder {
    fun toAac(input: File, output: File): Boolean {
        output.delete()
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            extractor = MediaExtractor().apply { setDataSource(input.absolutePath) }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return false
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            val encoderFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channels
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65_536)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            extractor.selectTrack(track)

            val decoderInfo = MediaCodec.BufferInfo()
            val encoderInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var encoderEos = false
            var encoderDone = false
            var outputTrack = -1

            while (!encoderDone) {
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(5_000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index) ?: error("Buffer nulo")
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                index, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                while (true) {
                    val index = decoder.dequeueOutputBuffer(decoderInfo, 0)
                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
                    if (index < 0) break
                    if (decoderInfo.size > 0) {
                        val encoderIndex = encoder.dequeueInputBuffer(10_000)
                        if (encoderIndex >= 0) {
                            val source = decoder.getOutputBuffer(index)
                            val target = encoder.getInputBuffer(encoderIndex)
                            if (source != null && target != null) {
                                source.position(decoderInfo.offset)
                                source.limit(decoderInfo.offset + decoderInfo.size)
                                target.clear()
                                target.put(source)
                                encoder.queueInputBuffer(
                                    encoderIndex,
                                    0,
                                    decoderInfo.size,
                                    decoderInfo.presentationTimeUs,
                                    0
                                )
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(index, false)
                    if ((decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        decoderDone = true
                    }
                }

                if (decoderDone && !encoderEos) {
                    val index = encoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        encoder.queueInputBuffer(
                            index, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        encoderEos = true
                    }
                }

                while (true) {
                    val index = encoder.dequeueOutputBuffer(encoderInfo, 0)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        outputTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        started = true
                    } else if (index >= 0) {
                        if (encoderInfo.size > 0 && started) {
                            val buffer = encoder.getOutputBuffer(index)
                            if (buffer != null) {
                                buffer.position(encoderInfo.offset)
                                buffer.limit(encoderInfo.offset + encoderInfo.size)
                                muxer.writeSampleData(outputTrack, buffer, encoderInfo)
                            }
                        }
                        encoder.releaseOutputBuffer(index, false)
                        if ((encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                        }
                    } else {
                        break
                    }
                }
            }
            if (started) muxer.stop()
            output.length() > 0
        } catch (throwable: Throwable) {
            Log.e("VeraAudio", "Falha ao converter áudio para AAC", throwable)
            output.delete()
            false
        } finally {
            runCatching { muxer?.release() }
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { extractor?.release() }
        }
    }
}
