package com.example.mypodcast.data.transcription

import kotlinx.coroutines.flow.Flow

/**
 * A chunk of 16-bit mono 16 kHz PCM. [endPositionMs] is the media position at
 * the END of [bytes], computed from the byte clock — exact, not estimated.
 */
class PcmChunk(val bytes: ByteArray, val endPositionMs: Long)

/** Produces real-time-paced PCM in recognizer format from a local audio file. */
interface PcmSource {
    /** Streams from [startMs] until end of file. Pacing ~1s of audio per 1s wall time. */
    fun stream(startMs: Long): Flow<PcmChunk>
}

interface PcmSourceFactory {
    fun create(filePath: String): PcmSource
}
