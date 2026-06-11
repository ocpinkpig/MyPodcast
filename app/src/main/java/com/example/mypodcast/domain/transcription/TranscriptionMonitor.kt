package com.example.mypodcast.domain.transcription

import com.example.mypodcast.domain.model.TranscriptCue
import kotlinx.coroutines.flow.StateFlow

/** Read-only view of the live on-device transcription session, for the UI. */
interface TranscriptionMonitor {
    /** Null when no session is active and none has produced cues this process. */
    val live: StateFlow<LiveTranscription?>
}

/** Snapshot of an in-flight (or just-finished) generation session. */
data class LiveTranscription(
    val episodeGuid: String,
    val cues: List<TranscriptCue>,
    val transcribedUpToMs: Long,
    val isComplete: Boolean
)
