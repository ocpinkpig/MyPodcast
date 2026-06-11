package com.example.mypodcast.data.transcription

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Decodes a local audio file with MediaExtractor/MediaCodec, converts the
 * output to the recognizer's format (16-bit mono 16 kHz PCM) and emits it in
 * ~100 ms chunks paced at real-time rate, as the ML Kit alpha API requires.
 * Supports starting from an arbitrary media position for resume.
 */
class Mp3PcmStreamer(private val filePath: String) : PcmSource {

    class Factory @Inject constructor() : PcmSourceFactory {
        override fun create(filePath: String): PcmSource = Mp3PcmStreamer(filePath)
    }

    override fun stream(startMs: Long): Flow<PcmChunk> = flow {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(filePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("No audio track in $filePath")
            extractor.selectTrack(trackIndex)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val format = extractor.getTrackFormat(trackIndex)
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val pending = ByteArrayOutputStream(TARGET_CHUNK_BYTES * 2)
            var emittedBytes = 0L
            val startedAtNanos = System.nanoTime()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            suspend fun emitPending(flush: Boolean) {
                val data = pending.toByteArray()
                var offset = 0
                while (data.size - offset >= TARGET_CHUNK_BYTES || (flush && offset < data.size)) {
                    val len = minOf(TARGET_CHUNK_BYTES, data.size - offset)
                    val chunk = data.copyOfRange(offset, offset + len)
                    offset += len
                    emittedBytes += len
                    // Real-time pacing against the byte clock, keeping ~200 ms headroom.
                    val mediaAheadMs = pcm16BytesToMs(emittedBytes) -
                        (System.nanoTime() - startedAtNanos) / 1_000_000
                    if (mediaAheadMs > PACING_HEADROOM_MS) delay(mediaAheadMs - PACING_HEADROOM_MS)
                    emit(PcmChunk(chunk, startMs + pcm16BytesToMs(emittedBytes)))
                }
                pending.reset()
                pending.write(data, offset, data.size - offset)
            }

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inIndex, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                if (outIndex >= 0) {
                    val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    // Drop pre-roll decoded before the requested start position
                    // (seekTo lands on the previous sync frame).
                    if (bufferInfo.size > 0 && bufferInfo.presentationTimeUs >= startMs * 1000) {
                        val outBuffer = codec.getOutputBuffer(outIndex)!!
                        val outFormat = codec.getOutputFormat(outIndex)
                        val sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                        val shorts = ShortArray(bufferInfo.size / 2)
                        outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                        val mono = downmixToMono(shorts, channels)
                        val resampled = resampleLinear(mono, sampleRate, TARGET_SAMPLE_RATE)
                        val bytes = ByteArray(resampled.size * 2)
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer().put(resampled)
                        pending.write(bytes)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    emitPending(flush = isEos)
                    if (isEos) outputDone = true
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        /** 100 ms of target-format PCM. */
        const val TARGET_CHUNK_BYTES = TARGET_BYTES_PER_SECOND / 10
        const val PACING_HEADROOM_MS = 200L
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
