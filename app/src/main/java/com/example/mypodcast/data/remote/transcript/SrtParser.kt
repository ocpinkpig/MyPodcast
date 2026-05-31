package com.example.mypodcast.data.remote.transcript

import com.example.mypodcast.domain.model.Transcript

/** Parses SubRip (`application/srt`) transcripts into timed cues. */
object SrtParser {
    fun parse(raw: String): Transcript =
        Transcript(cues = parseArrowCues(raw), isSynced = true)
}
