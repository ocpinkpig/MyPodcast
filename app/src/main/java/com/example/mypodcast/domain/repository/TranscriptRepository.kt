package com.example.mypodcast.domain.repository

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Transcript

interface TranscriptRepository {
    /**
     * Fetches and parses the transcript referenced by the episode's
     * [Episode.transcriptUrl]. Callers are expected to only invoke this when a
     * transcript URL is present.
     */
    suspend fun getTranscript(episode: Episode): Result<Transcript>
}
