package com.example.mypodcast.ui.player

/** A timestamp span found in show-notes text, with its parsed playback position. */
data class TimestampLink(
    val start: Int,
    val endExclusive: Int,
    val positionMs: Long
)

// Matches H:MM:SS / HH:MM:SS or M:SS / MM:SS. Seconds and the middle field of the
// HMS form are constrained to 00-59; the leading field allows 1+ digits so 90:00
// works. The boundary guards only exclude adjacent digits/colons, so any other
// surrounding character (e.g. parens or brackets) naturally falls outside the match.
private val TIMESTAMP_REGEX =
    Regex("""(?<![\d:])(?:(\d{1,2}):)?(\d{1,3}):([0-5]\d)(?![\d:])""")

/** Finds clickable timestamps in already-flattened (plain-text) show notes. */
fun findTimestampLinks(text: String): List<TimestampLink> =
    TIMESTAMP_REGEX.findAll(text).mapNotNull { match ->
        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLong()
        val seconds = match.groupValues[3].toLong()
        // In the HH:MM:SS form the minutes field must be a real minute count.
        if (match.groupValues[1].isNotEmpty() && minutes > 59) return@mapNotNull null
        val positionMs = ((hours * 3600) + (minutes * 60) + seconds) * 1000L
        TimestampLink(
            start = match.range.first,
            endExclusive = match.range.last + 1,
            positionMs = positionMs
        )
    }.toList()
