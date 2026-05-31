package com.example.mypodcast.data.remote.transcript

/**
 * Transcript formats we understand, in order of preference (most preferred
 * first). Timestamped formats rank above text-only ones so the Player can sync.
 */
enum class TranscriptFormat { VTT, SRT, JSON, HTML, PLAIN }

/** A `<podcast:transcript>` reference from an RSS feed item. */
data class TranscriptRef(
    val url: String,
    val type: String?,
    val language: String? = null
)

/** Maps a transcript MIME type (falling back to the URL extension) to a format. */
fun transcriptFormatOf(type: String?, url: String? = null): TranscriptFormat {
    when (type?.trim()?.lowercase()?.substringBefore(';')) {
        "text/vtt" -> return TranscriptFormat.VTT
        "application/srt", "application/x-subrip", "text/srt" -> return TranscriptFormat.SRT
        "application/json" -> return TranscriptFormat.JSON
        "text/html" -> return TranscriptFormat.HTML
        "text/plain" -> return TranscriptFormat.PLAIN
    }
    return when (url?.substringAfterLast('.', "")?.lowercase()) {
        "vtt" -> TranscriptFormat.VTT
        "srt" -> TranscriptFormat.SRT
        "json" -> TranscriptFormat.JSON
        "html", "htm" -> TranscriptFormat.HTML
        else -> TranscriptFormat.PLAIN
    }
}

/**
 * Picks the best transcript reference from an item's candidates, preferring
 * timestamped formats. Returns null when there are none.
 */
fun selectBestTranscript(refs: List<TranscriptRef>): TranscriptRef? =
    refs.minByOrNull { transcriptFormatOf(it.type, it.url).ordinal }
