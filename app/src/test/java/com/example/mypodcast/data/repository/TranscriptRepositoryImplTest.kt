package com.example.mypodcast.data.repository

import com.example.mypodcast.data.transcription.GeneratedTranscript
import com.example.mypodcast.data.transcription.GeneratedTranscriptStore
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.TranscriptCue
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TranscriptRepositoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun episode(transcriptUrl: String? = null) = Episode(
        guid = "ep-1",
        podcastId = 1L,
        title = "Episode",
        description = null,
        audioUrl = "https://example.com/ep.mp3",
        artworkUrl = null,
        publishedAt = 0L,
        durationSeconds = 120,
        fileSizeBytes = 0L,
        transcriptUrl = transcriptUrl
    )

    private fun repository(store: GeneratedTranscriptStore) = TranscriptRepositoryImpl(
        filesDir = tmp.root,
        okHttpClient = OkHttpClient(),
        generatedTranscriptStore = store
    )

    @Test
    fun `no publisher url and no generated transcript yields empty unsynced`() = runTest {
        val result = repository(GeneratedTranscriptStore(tmp.root)).getTranscript(episode())

        val transcript = result.getOrThrow()
        assertTrue(transcript.cues.isEmpty())
        assertEquals(false, transcript.isSynced)
    }

    @Test
    fun `partial generated transcript is synced with coverage marker`() = runTest {
        val store = GeneratedTranscriptStore(tmp.root)
        store.write(
            "ep-1",
            GeneratedTranscript(
                cues = listOf(TranscriptCue(0, 5_000, "Generated words")),
                transcribedUpToMs = 5_000,
                isComplete = false,
                engineVersion = "x"
            )
        )

        val transcript = repository(store).getTranscript(episode()).getOrThrow()

        assertEquals("Generated words", transcript.cues.single().text)
        assertTrue(transcript.isSynced)
        assertEquals(5_000L, transcript.transcribedUpToMs)
    }

    @Test
    fun `complete generated transcript has null coverage marker`() = runTest {
        val store = GeneratedTranscriptStore(tmp.root)
        store.write(
            "ep-1",
            GeneratedTranscript(
                cues = listOf(TranscriptCue(0, 5_000, "Done")),
                transcribedUpToMs = 120_000,
                isComplete = true,
                engineVersion = "x"
            )
        )

        val transcript = repository(store).getTranscript(episode()).getOrThrow()

        assertNull(transcript.transcribedUpToMs)
    }

    @Test
    fun `publisher transcript wins - generated store not consulted when url present`() = runTest {
        // Pre-seed the publisher cache file so no network call happens
        // (cache path: <filesDir>/transcripts/<guid>, raw VTT body).
        val cacheDir = File(tmp.root, "transcripts").apply { mkdirs() }
        File(cacheDir, "ep-1").writeText(
            "WEBVTT\n\n00:00:00.000 --> 00:00:04.000\nPublisher cue\n"
        )
        val store = GeneratedTranscriptStore(tmp.root)
        store.write(
            "ep-1",
            GeneratedTranscript(
                cues = listOf(TranscriptCue(0, 1_000, "Generated cue")),
                transcribedUpToMs = 1_000,
                isComplete = false,
                engineVersion = "x"
            )
        )

        val transcript = repository(store)
            .getTranscript(episode(transcriptUrl = "https://feeds.example.com/t.vtt"))
            .getOrThrow()

        assertEquals("Publisher cue", transcript.cues.single().text)
        assertNull(transcript.transcribedUpToMs)
    }
}
