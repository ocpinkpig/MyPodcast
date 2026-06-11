package com.example.mypodcast.domain.model

/**
 * A single timed segment of a transcript. For transcripts without timing
 * information (plain text / HTML), [startMs] and [endMs] are 0 and the cue
 * represents a paragraph rather than a spoken line.
 */
data class TranscriptCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val speaker: String? = null
)

/**
 * A parsed episode transcript.
 *
 * [isSynced] is true when cues carry real timestamps (VTT/SRT/JSON) so the
 * Player can highlight, auto-scroll, and seek. It is false for plain-text or
 * HTML transcripts, which render as a simple scrollable block.
 *
 * [transcribedUpToMs] is non-null only for partially generated on-device
 * transcripts: the position up to which audio has been transcribed. Null means
 * the transcript is complete (publisher transcripts are always complete).
 */
data class Transcript(
    val cues: List<TranscriptCue>,
    val isSynced: Boolean,
    val transcribedUpToMs: Long? = null
)
