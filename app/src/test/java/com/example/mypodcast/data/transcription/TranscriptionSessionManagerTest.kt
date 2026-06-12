package com.example.mypodcast.data.transcription

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.model.TranscriptStatus
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranscriptionSessionManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun episode(
        guid: String = "ep-1",
        transcriptUrl: String? = null
    ) = Episode(
        guid = guid,
        podcastId = 1L,
        title = "Episode",
        description = null,
        audioUrl = "https://example.com/$guid.mp3",
        artworkUrl = null,
        publishedAt = 0L,
        durationSeconds = 120,
        fileSizeBytes = 0L,
        transcriptUrl = transcriptUrl
    )

    private fun manager(
        player: FakePlayerRepository,
        library: FakeTranscriptionLibraryRepository,
        engine: FakeSpeechEngine,
        sourcesByPath: Map<String, PcmSource>
    ): Pair<TranscriptionSessionManager, GeneratedTranscriptStore> {
        val store = GeneratedTranscriptStore(tmp.root)
        val mgr = TranscriptionSessionManager(
            playerRepository = player,
            libraryRepository = library,
            store = store,
            engine = engine,
            pcmSourceFactory = object : PcmSourceFactory {
                override fun create(filePath: String): PcmSource =
                    sourcesByPath.getValue(filePath)
            }
        )
        return mgr to store
    }

    @Test
    fun `completes transcription and marks status COMPLETE`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeTranscriptionLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(listOf(32_000L to "hello world"))
        val source = FakePcmSource(listOf(32_000 to 1_000L))
        val (mgr, store) = manager(player, library, engine, mapOf("/files/ep-1.mp3" to source))

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()

        val saved = store.read("ep-1")
        assertNotNull(saved)
        assertTrue(saved!!.isComplete)
        assertEquals("hello world", saved.cues.single().text)
        assertEquals(
            listOf("ep-1" to TranscriptStatus.IN_PROGRESS, "ep-1" to TranscriptStatus.COMPLETE),
            library.statusUpdates
        )
        assertEquals(true, mgr.live.value?.isComplete)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `pause persists partial progress`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeTranscriptionLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))

        // A source that never finishes: pause must cancel the session, and the
        // partial progress must be persisted.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val hangingSource = object : PcmSource {
            override fun stream(startMs: Long) = kotlinx.coroutines.flow.flow {
                emit(PcmChunk(ByteArray(32_000), 1_000L))
                gate.await() // never completes
            }
        }
        val engine = FakeSpeechEngine(listOf(32_000L to "first part"))
        val (mgr, store) = manager(player, library, engine, mapOf("/files/ep-1.mp3" to hangingSource))

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()
        player.state.value = PlayerState(episode = episode(), isPlaying = false) // pause
        advanceUntilIdle()

        val saved = store.read("ep-1")
        assertNotNull(saved)
        assertEquals(false, saved!!.isComplete)
        assertEquals("first part", saved.cues.single().text)
        assertTrue(saved.transcribedUpToMs >= 1_000L)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `skips episodes with a publisher transcript`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeTranscriptionLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(emptyList())
        val (mgr, store) = manager(player, library, engine, emptyMap())

        mgr.start(this)
        player.state.value = PlayerState(
            episode = episode(transcriptUrl = "https://feeds.example.com/t.vtt"),
            isPlaying = true
        )
        advanceUntilIdle()

        assertNull(store.read("ep-1"))
        assertTrue(library.statusUpdates.isEmpty())
        assertTrue(engine.openedSessions.isEmpty())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `skips non-downloaded episodes`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeTranscriptionLibraryRepository(emptyMap()) // nothing downloaded
        val engine = FakeSpeechEngine(emptyList())
        val (mgr, store) = manager(player, library, engine, emptyMap())

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()

        assertNull(store.read("ep-1"))
        assertTrue(engine.openedSessions.isEmpty())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `silently skips when engine unavailable`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeTranscriptionLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(emptyList(), availability = EngineAvailability.UNAVAILABLE)
        val (mgr, store) = manager(player, library, engine, emptyMap())

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()

        assertNull(store.read("ep-1"))
        assertTrue(library.statusUpdates.isEmpty())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `requests model download once when downloadable, does not transcribe`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeTranscriptionLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(emptyList(), availability = EngineAvailability.DOWNLOADABLE)
        val (mgr, _) = manager(player, library, engine, emptyMap())

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()

        assertEquals(1, engine.modelDownloadRequests)
        assertTrue(engine.openedSessions.isEmpty())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `does not rerun an already complete transcript`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeTranscriptionLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(emptyList())
        val (mgr, store) = manager(player, library, engine, emptyMap())
        store.write(
            "ep-1",
            GeneratedTranscript(emptyList(), 120_000L, isComplete = true, engineVersion = "x")
        )

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()

        assertTrue(engine.openedSessions.isEmpty())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `refresh re-runs a session that previously failed`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeTranscriptionLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(listOf(32_000L to "recovered"), failOnFeed = true)
        val source = FakePcmSource(listOf(32_000 to 1_000L))
        val (mgr, store) = manager(player, library, engine, mapOf("/files/ep-1.mp3" to source))

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()
        assertEquals(false, store.read("ep-1")?.isComplete) // first run failed

        engine.failOnFeed = false
        mgr.refresh() // e.g. RECORD_AUDIO was just granted
        advanceUntilIdle()

        assertEquals(true, store.read("ep-1")?.isComplete)
        assertEquals("recovered", store.read("ep-1")?.cues?.single()?.text)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `engine error persists progress without crashing`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeTranscriptionLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(emptyList(), failOnFeed = true)
        val source = FakePcmSource(listOf(32_000 to 1_000L))
        val (mgr, store) = manager(player, library, engine, mapOf("/files/ep-1.mp3" to source))

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()

        val saved = store.read("ep-1")
        assertNotNull(saved) // progress file exists, not complete
        assertEquals(false, saved!!.isComplete)
        coroutineContext.cancelChildren()
    }
}
