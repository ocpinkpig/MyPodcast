package com.example.mypodcast.ui.player

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.model.SavedMoment
import com.example.mypodcast.domain.model.Transcript
import com.example.mypodcast.domain.model.TranscriptCue
import com.example.mypodcast.domain.repository.SavedMomentRepository
import com.example.mypodcast.domain.repository.TranscriptRepository
import com.example.mypodcast.domain.usecase.episode.GetTranscriptUseCase
import com.example.mypodcast.ui.library.MainDispatcherRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerTranscriptViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadTranscript_emitsEmptyWhenEpisodeHasNoTranscriptUrl() = runTest {
        val viewModel = viewModel(GetTranscriptUseCase(FakeTranscriptRepository()))

        viewModel.loadTranscript(episode("a", transcriptUrl = null))
        advanceUntilIdle()

        assertEquals(TranscriptUiState.Empty, viewModel.transcriptState.value)
    }

    @Test
    fun loadTranscript_emitsLoadedWhenTranscriptHasCues() = runTest {
        val transcript = Transcript(
            cues = listOf(TranscriptCue(0L, 1_000L, "hi")),
            isSynced = true
        )
        val viewModel = viewModel(
            GetTranscriptUseCase(FakeTranscriptRepository(Result.success(transcript)))
        )

        viewModel.loadTranscript(episode("a", transcriptUrl = "https://x/a.vtt"))
        advanceUntilIdle()

        assertEquals(TranscriptUiState.Loaded(transcript), viewModel.transcriptState.value)
    }

    @Test
    fun loadTranscript_emitsEmptyWhenParsedTranscriptHasNoCues() = runTest {
        val viewModel = viewModel(
            GetTranscriptUseCase(
                FakeTranscriptRepository(Result.success(Transcript(emptyList(), isSynced = true)))
            )
        )

        viewModel.loadTranscript(episode("a", transcriptUrl = "https://x/a.vtt"))
        advanceUntilIdle()

        assertEquals(TranscriptUiState.Empty, viewModel.transcriptState.value)
    }

    @Test
    fun loadTranscript_emitsErrorWhenFetchFails() = runTest {
        val viewModel = viewModel(
            GetTranscriptUseCase(FakeTranscriptRepository(Result.failure(RuntimeException("boom"))))
        )

        viewModel.loadTranscript(episode("a", transcriptUrl = "https://x/a.vtt"))
        advanceUntilIdle()

        assertTrue(viewModel.transcriptState.value is TranscriptUiState.Error)
    }

    private fun viewModel(getTranscript: GetTranscriptUseCase) =
        PlayerViewModel(
            FakePlayerRepository(PlayerState()),
            getTranscript,
            TranscriptFakeSavedMomentRepository(),
            FakeTranscriptionMonitor()
        )

    private fun episode(guid: String, transcriptUrl: String?) = Episode(
        guid = guid,
        podcastId = 1L,
        title = "Episode $guid",
        description = null,
        audioUrl = "https://example.com/$guid.mp3",
        artworkUrl = null,
        publishedAt = 0L,
        durationSeconds = 60,
        fileSizeBytes = 1_024L,
        transcriptUrl = transcriptUrl,
        transcriptType = transcriptUrl?.let { "text/vtt" }
    )
}

private class TranscriptFakeSavedMomentRepository : SavedMomentRepository {
    override fun observeSavedMoments(): Flow<List<SavedMoment>> = flowOf(emptyList())
    override fun observeHasSavedMoments(episodeGuid: String): Flow<Boolean> = flowOf(false)

    override suspend fun saveMoment(
        episode: Episode,
        positionMs: Long,
        clipStartMs: Long,
        clipEndMs: Long,
        transcriptText: String?
    ) = Unit

    override suspend fun deleteMoment(id: Long) = Unit
}

internal class FakeTranscriptRepository(
    private val result: Result<Transcript> = Result.success(Transcript(emptyList(), isSynced = true))
) : TranscriptRepository {
    override suspend fun getTranscript(episode: Episode): Result<Transcript> = result
}
