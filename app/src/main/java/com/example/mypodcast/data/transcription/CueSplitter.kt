package com.example.mypodcast.data.transcription

import com.example.mypodcast.domain.model.TranscriptCue

/** Whitespace following a sentence-ending punctuation mark. */
private val SENTENCE_BOUNDARY = Regex("(?<=[.?!])\\s+")

/**
 * Pieces shorter than this merge into a neighbor instead of becoming their own
 * cue — keeps abbreviations ("Dr.", "U.S.") and clipped fragments from
 * exploding a segment into confetti.
 */
private const val MIN_SENTENCE_CHARS = 25

/**
 * Splits one finalized engine segment into sentence-level cues. The engine
 * gives no per-word timing, so timestamps are interpolated proportionally to
 * character length across the segment's [startMs, endMs] span. Cues are
 * contiguous: each starts where the previous one ended; the last ends exactly
 * at [endMs].
 */
internal fun splitIntoSentenceCues(text: String, startMs: Long, endMs: Long): List<TranscriptCue> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()

    val sentences = mutableListOf<String>()
    var pendingPrefix = ""
    for (piece in SENTENCE_BOUNDARY.split(trimmed)) {
        val candidate = if (pendingPrefix.isEmpty()) piece else "$pendingPrefix $piece"
        if (candidate.length < MIN_SENTENCE_CHARS) {
            pendingPrefix = candidate
        } else {
            sentences.add(candidate)
            pendingPrefix = ""
        }
    }
    if (pendingPrefix.isNotEmpty()) {
        if (sentences.isEmpty()) {
            sentences.add(pendingPrefix)
        } else {
            sentences[sentences.lastIndex] = "${sentences.last()} $pendingPrefix"
        }
    }

    if (sentences.size == 1) {
        return listOf(TranscriptCue(startMs = startMs, endMs = endMs, text = sentences.single()))
    }

    val span = endMs - startMs
    val totalChars = sentences.sumOf { it.length }.toLong().coerceAtLeast(1)
    val cues = ArrayList<TranscriptCue>(sentences.size)
    var cumulativeChars = 0L
    var cueStart = startMs
    sentences.forEachIndexed { index, sentence ->
        cumulativeChars += sentence.length
        val cueEnd =
            if (index == sentences.lastIndex) endMs
            else startMs + span * cumulativeChars / totalChars
        cues.add(TranscriptCue(startMs = cueStart, endMs = cueEnd, text = sentence))
        cueStart = cueEnd
    }
    return cues
}
