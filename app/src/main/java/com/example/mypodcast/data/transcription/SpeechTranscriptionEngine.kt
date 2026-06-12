package com.example.mypodcast.data.transcription

import kotlinx.coroutines.flow.Flow
import java.io.Closeable
import java.util.Locale

enum class EngineAvailability { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

/**
 * Thin, swappable wrapper around an ASR engine (ML Kit GenAI Speech
 * Recognition in production). Everything above this interface is JVM-testable.
 */
interface SpeechTranscriptionEngine {
    /** Availability of the model pack for [locale] — each language has its own. */
    suspend fun checkAvailability(locale: Locale): EngineAvailability

    /** Kick off the model download for [locale]. Fire-and-forget; safe to repeat. */
    suspend fun requestModelDownload(locale: Locale)

    /** Opens a recognition session expecting 16-bit mono 16 kHz PCM via [SpeechSession.feed]. */
    fun openSession(locale: Locale): SpeechSession

    companion object {
        const val VERSION = "mlkit-genai-1.0.0-alpha1"
    }
}

interface SpeechSession : Closeable {
    /**
     * Finalized text segments, in order. The flow completes after [finish] once
     * the engine has flushed its last segment, and fails if recognition fails.
     */
    val results: Flow<String>

    /** Feed the next PCM chunk. Suspends if the engine applies backpressure. */
    suspend fun feed(chunk: ByteArray)

    /** Signal end of audio so the engine finalizes pending output. */
    suspend fun finish()
}
