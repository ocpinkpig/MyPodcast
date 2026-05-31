package com.example.mypodcast.data.remote.transcript

import com.example.mypodcast.domain.model.Transcript
import com.example.mypodcast.domain.model.TranscriptCue
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Parses the Podcasting 2.0 JSON transcript format
 * (`application/json`, an object with a `segments` array). `startTime`/`endTime`
 * are expressed in (fractional) seconds and converted to milliseconds.
 */
object PodcastJsonParser {

    private val gson = Gson()

    private data class Document(val segments: List<Segment>? = null)

    private data class Segment(
        val speaker: String? = null,
        @SerializedName("startTime") val startTime: Double? = null,
        @SerializedName("endTime") val endTime: Double? = null,
        val body: String? = null
    )

    fun parse(raw: String): Transcript {
        val document = runCatching { gson.fromJson(raw, Document::class.java) }.getOrNull()
        val cues = document?.segments.orEmpty().mapNotNull { segment ->
            val text = segment.body?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            TranscriptCue(
                startMs = ((segment.startTime ?: 0.0) * 1_000).toLong(),
                endMs = ((segment.endTime ?: 0.0) * 1_000).toLong(),
                text = text,
                speaker = segment.speaker?.takeIf { it.isNotBlank() }
            )
        }
        return Transcript(cues = cues, isSynced = true)
    }
}
