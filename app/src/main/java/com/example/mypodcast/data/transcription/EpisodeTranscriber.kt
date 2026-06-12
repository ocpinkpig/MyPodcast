package com.example.mypodcast.data.transcription

import com.example.mypodcast.domain.model.TranscriptCue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface TranscriberEvent {
    /** A finalized cue with byte-clock timestamps. */
    data class Cue(val cue: TranscriptCue) : TranscriberEvent

    /** Audio fed up to this media position (also advances through silence). */
    data class Progress(val upToMs: Long) : TranscriberEvent

    /** The whole file (from startMs to EOF) has been transcribed. */
    data object Completed : TranscriberEvent
}

/**
 * Drives one transcription pass: pulls paced PCM from a [PcmSource], feeds an
 * engine session, and stamps each finalized segment with timestamps derived
 * from the amount of audio fed so far ([PcmChunk.endPositionMs]). Cues are
 * contiguous: each starts where the previous one ended.
 */
class EpisodeTranscriber(private val engine: SpeechTranscriptionEngine) {

    fun transcribe(source: PcmSource, startMs: Long, locale: Locale): Flow<TranscriberEvent> =
        channelFlow {
            engine.openSession(locale).use { session ->
                var lastFedMs = startMs
                var lastCueEndMs = startMs

                val resultsJob = launch {
                    session.results.collect { text ->
                        val end = lastFedMs
                        // The engine can finalize 60-90s of speech as one block;
                        // sentence-split it so highlight/seek granularity stays useful.
                        val cues = splitIntoSentenceCues(text, startMs = lastCueEndMs, endMs = end)
                        if (cues.isNotEmpty()) {
                            cues.forEach { send(TranscriberEvent.Cue(it)) }
                            lastCueEndMs = end
                        }
                    }
                }

                source.stream(startMs).collect { chunk ->
                    session.feed(chunk.bytes)
                    lastFedMs = chunk.endPositionMs
                    send(TranscriberEvent.Progress(chunk.endPositionMs))
                }
                session.finish()
                resultsJob.join()
                send(TranscriberEvent.Completed)
            }
        }
}
