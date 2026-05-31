package com.example.mypodcast.domain.usecase.episode

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Transcript
import com.example.mypodcast.domain.repository.TranscriptRepository
import javax.inject.Inject

class GetTranscriptUseCase @Inject constructor(
    private val transcriptRepository: TranscriptRepository
) {
    suspend operator fun invoke(episode: Episode): Result<Transcript> =
        transcriptRepository.getTranscript(episode)
}
