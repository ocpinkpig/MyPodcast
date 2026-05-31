package com.example.mypodcast.data.remote.transcript

import com.example.mypodcast.domain.model.TranscriptCue

private const val ARROW = "-->"

/**
 * Parses cue blocks delimited by `start --> end` lines, used by both WebVTT and
 * SubRip. Non-timestamp lines (headers, NOTE comments, numeric cue indices) are
 * skipped; the lines following a timestamp up to the next blank line form the
 * cue text, joined with spaces.
 */
internal fun parseArrowCues(raw: String): List<TranscriptCue> {
    val cues = mutableListOf<TranscriptCue>()
    val lines = raw.lines()

    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        val arrowAt = line.indexOf(ARROW)
        if (arrowAt < 0) {
            i++
            continue
        }

        val start = parseCueTimeMs(line.substring(0, arrowAt))
        // The end token may carry trailing cue settings, e.g. "... align:start".
        val end = line.substring(arrowAt + ARROW.length)
            .trim()
            .substringBefore(' ')
            .let(::parseCueTimeMs)

        if (start == null || end == null) {
            i++
            continue
        }

        val text = StringBuilder()
        i++
        while (i < lines.size && lines[i].isNotBlank()) {
            if (text.isNotEmpty()) text.append(' ')
            text.append(lines[i].trim())
            i++
        }

        if (text.isNotEmpty()) {
            cues += TranscriptCue(startMs = start, endMs = end, text = text.toString())
        }
    }

    return cues
}

/**
 * Parses a cue timestamp into milliseconds, tolerant of the formats used by
 * both WebVTT (`HH:MM:SS.mmm` / `MM:SS.mmm`) and SubRip (`HH:MM:SS,mmm`).
 * Returns null when the token isn't a timestamp.
 */
internal fun parseCueTimeMs(token: String): Long? {
    val normalized = token.trim().replace(',', '.')
    if (normalized.isEmpty()) return null

    val dot = normalized.indexOf('.')
    val clockPart = if (dot >= 0) normalized.substring(0, dot) else normalized
    val millisPart = if (dot >= 0) normalized.substring(dot + 1) else ""

    val units = clockPart.split(':')
    if (units.isEmpty() || units.size > 3) return null

    var seconds = 0L
    for (unit in units) {
        val value = unit.toLongOrNull() ?: return null
        seconds = seconds * 60 + value
    }

    val millis = when {
        millisPart.isEmpty() -> 0L
        else -> millisPart.padEnd(3, '0').take(3).toLongOrNull() ?: return null
    }
    return seconds * 1_000 + millis
}
