package com.example.mypodcast.data.remote.transcript

import com.example.mypodcast.domain.model.Transcript
import com.example.mypodcast.domain.model.TranscriptCue

/**
 * Parses plain text into an unsynced transcript: one cue per paragraph
 * (paragraphs separated by blank lines). Used directly for `text/plain` and,
 * after HTML-to-text conversion, for `text/html` transcripts.
 */
object PlainTextTranscriptParser {
    fun parse(text: String): Transcript {
        val cues = text
            .split(Regex("\\n\\s*\\n"))
            .map { paragraph ->
                paragraph.lines().joinToString(" ") { it.trim() }.trim()
            }
            .filter { it.isNotEmpty() }
            .map { TranscriptCue(startMs = 0L, endMs = 0L, text = it) }
        return Transcript(cues = cues, isSynced = false)
    }
}
