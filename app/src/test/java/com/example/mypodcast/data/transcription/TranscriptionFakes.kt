package com.example.mypodcast.data.transcription

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * Scripted PCM source: emits [chunks] without real-time pacing (tests must not
 * sleep). Each entry is (byteCount, endPositionMs).
 */
class FakePcmSource(private val chunks: List<Pair<Int, Long>>) : PcmSource {
    override fun stream(startMs: Long): Flow<PcmChunk> = flow {
        chunks.forEach { (size, endMs) -> emit(PcmChunk(ByteArray(size), endMs)) }
    }
}

/**
 * Scripted engine: emits a queued text after the session has been fed at least
 * the paired number of bytes. Completion happens when finish() is called.
 */
class FakeSpeechEngine(
    private val script: List<Pair<Long, String>>, // fedBytesAtLeast -> segment text
    var availability: EngineAvailability = EngineAvailability.AVAILABLE,
    var failOnFeed: Boolean = false
) : SpeechTranscriptionEngine {
    var modelDownloadRequests = 0
        private set
    val openedSessions = mutableListOf<FakeSpeechSession>()

    override suspend fun checkAvailability(): EngineAvailability = availability
    override suspend fun requestModelDownload() { modelDownloadRequests++ }
    override fun openSession(locale: Locale): SpeechSession =
        FakeSpeechSession(script, failOnFeed).also { openedSessions.add(it) }
}

class FakeSpeechSession(
    script: List<Pair<Long, String>>,
    private val failOnFeed: Boolean
) : SpeechSession {
    private val pending = script.toMutableList()
    private val channel = Channel<String>(Channel.UNLIMITED)
    private var fedBytes = 0L
    var closed = false
        private set

    override val results: Flow<String> = channel.consumeAsFlow()

    override suspend fun feed(chunk: ByteArray) {
        if (failOnFeed) throw IllegalStateException("engine exploded")
        fedBytes += chunk.size
        while (pending.isNotEmpty() && fedBytes >= pending.first().first) {
            channel.send(pending.removeAt(0).second)
        }
    }

    override suspend fun finish() {
        while (pending.isNotEmpty()) channel.send(pending.removeAt(0).second)
        channel.close()
    }

    override fun close() {
        closed = true
        channel.close()
    }
}
