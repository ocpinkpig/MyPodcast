package com.example.mypodcast.data.remote.transcript

import com.example.mypodcast.domain.model.Transcript

/** Parses WebVTT (`text/vtt`) transcripts into timed cues. */
object VttParser {
    fun parse(raw: String): Transcript =
        Transcript(cues = parseArrowCues(raw), isSynced = true)
}
