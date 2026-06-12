package com.example.mypodcast.data.transcription

import com.example.mypodcast.domain.model.TranscriptCue

/**
 * Sentence boundary: ASCII enders need trailing whitespace ("Dr." guard does
 * the rest); full-width CJK enders (。！？) are boundaries on their own since
 * CJK text carries no spaces.
 */
private val SENTENCE_BOUNDARY = Regex("(?<=[.?!])\\s+|(?<=[。！？])\\s*")

/**
 * Pieces below this weighted length merge into a neighbor instead of becoming
 * their own cue — keeps abbreviations ("Dr.", "U.S.") and clipped fragments
 * from exploding a segment into confetti. CJK characters weigh more than
 * Latin letters because a handful of them already form a full sentence.
 */
private const val MIN_SENTENCE_WEIGHT = 25
private const val CJK_CHAR_WEIGHT = 4

private fun isCjk(codePoint: Int): Boolean =
    codePoint in 0x3000..0x303F || // CJK symbols & punctuation (。！？…)
        codePoint in 0x3040..0x30FF || // Hiragana, Katakana
        codePoint in 0x4E00..0x9FFF || // CJK Unified Ideographs
        codePoint in 0xAC00..0xD7AF || // Hangul syllables
        codePoint in 0xFF00..0xFF60 // full-width forms

private fun weightedLength(text: String): Int {
    var weight = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        weight += if (isCjk(cp)) CJK_CHAR_WEIGHT else 1
        i += Character.charCount(cp)
    }
    return weight
}

/**
 * Splits one finalized engine segment into sentence-level cues. The engine
 * gives no per-word timing, so timestamps are interpolated proportionally to
 * weighted character length across the segment's [startMs, endMs] span. Cues
 * are contiguous: each starts where the previous one ended; the last ends
 * exactly at [endMs].
 */
internal fun splitIntoSentenceCues(text: String, startMs: Long, endMs: Long): List<TranscriptCue> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()

    val sentences = mutableListOf<String>()
    var pendingPrefix = ""
    for (piece in SENTENCE_BOUNDARY.split(trimmed)) {
        if (piece.isBlank()) continue
        val candidate = if (pendingPrefix.isEmpty()) piece else "$pendingPrefix $piece"
        if (weightedLength(candidate) < MIN_SENTENCE_WEIGHT) {
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
    val totalWeight = sentences.sumOf { weightedLength(it) }.toLong().coerceAtLeast(1)
    val cues = ArrayList<TranscriptCue>(sentences.size)
    var cumulativeWeight = 0L
    var cueStart = startMs
    sentences.forEachIndexed { index, sentence ->
        cumulativeWeight += weightedLength(sentence)
        val cueEnd =
            if (index == sentences.lastIndex) endMs
            else startMs + span * cumulativeWeight / totalWeight
        cues.add(TranscriptCue(startMs = cueStart, endMs = cueEnd, text = sentence))
        cueStart = cueEnd
    }
    return cues
}
