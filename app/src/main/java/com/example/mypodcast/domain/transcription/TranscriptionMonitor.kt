package com.example.mypodcast.domain.transcription

import com.example.mypodcast.domain.model.TranscriptCue
import kotlinx.coroutines.flow.StateFlow

/** UI-facing port of the on-device transcription session. */
interface TranscriptionMonitor {
    /** Null when no session is active and none has produced cues this process. */
    val live: StateFlow<LiveTranscription?>

    /**
     * Re-evaluates session eligibility for the currently playing episode —
     * call after an external precondition changes (e.g. the RECORD_AUDIO
     * permission was just granted).
     */
    fun refresh()
}

/** Snapshot of an in-flight (or just-finished) generation session. */
data class LiveTranscription(
    val episodeGuid: String,
    val cues: List<TranscriptCue>,
    val transcribedUpToMs: Long,
    val isComplete: Boolean
)
