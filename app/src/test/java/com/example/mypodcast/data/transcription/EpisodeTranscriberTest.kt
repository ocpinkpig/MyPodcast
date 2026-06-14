package com.example.mypodcast.data.transcription

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class EpisodeTranscriberTest {

    @Test
    fun `assigns contiguous timestamps from the byte clock`() = runTest {
        // Two 1-second chunks (32_000 bytes each). Engine finalizes one segment
        // after the first chunk, one after the second.
        val source = FakePcmSource(listOf(32_000 to 1_000L, 32_000 to 2_000L))
        val engine = FakeSpeechEngine(listOf(32_000L to "hello", 64_000L to "world"))

        val events = EpisodeTranscriber(engine)
            .transcribe(source, startMs = 0L, locale = Locale.US)
            .toList()

        val cues = events.filterIsInstance<TranscriberEvent.Cue>().map { it.cue }
        assertEquals(listOf("hello", "world"), cues.map { it.text })
        assertEquals(0L, cues[0].startMs)
        assertEquals(cues[0].endMs, cues[1].startMs) // contiguous
        assertTrue(cues[1].endMs <= 2_000L)
        assertEquals(TranscriberEvent.Completed, events.last())
    }

    @Test
    fun `resume offsets timestamps by startMs`() = runTest {
        val source = FakePcmSource(listOf(32_000 to 61_000L))
        val engine = FakeSpeechEngine(listOf(32_000L to "later words"))

        val events = EpisodeTranscriber(engine)
            .transcribe(source, startMs = 60_000L, locale = Locale.US)
            .toList()

        val cue = events.filterIsInstance<TranscriberEvent.Cue>().single().cue
        assertEquals(60_000L, cue.startMs)
        assertEquals(61_000L, cue.endMs)
    }

    @Test
    fun `emits progress for silent audio with no cues`() = runTest {
        val source = FakePcmSource(listOf(32_000 to 1_000L, 32_000 to 2_000L))
        val engine = FakeSpeechEngine(emptyList())

        val events = EpisodeTranscriber(engine)
            .transcribe(source, startMs = 0L, locale = Locale.US)
            .toList()

        val progress = events.filterIsInstance<TranscriberEvent.Progress>()
        assertEquals(listOf(1_000L, 2_000L), progress.map { it.upToMs })
        assertEquals(TranscriberEvent.Completed, events.last())
    }

    @Test
    fun `closes the session even when the engine fails`() = runTest {
        val source = FakePcmSource(listOf(32_000 to 1_000L))
        val engine = FakeSpeechEngine(emptyList(), failOnFeed = true)

        val result = runCatching {
            EpisodeTranscriber(engine).transcribe(source, 0L, Locale.US).toList()
        }

        assertTrue(result.isFailure)
        assertTrue(engine.openedSessions.single().closed)
    }

    @Test
    fun `multi-sentence segments are split into contiguous sentence cues`() = runTest {
        val source = FakePcmSource(listOf(64_000 to 2_000L))
        val engine = FakeSpeechEngine(
            listOf(64_000L to "Hello there my good friend. The weather is nice today!")
        )

        val events = EpisodeTranscriber(engine)
            .transcribe(source, startMs = 0L, locale = Locale.US)
            .toList()

        val cues = events.filterIsInstance<TranscriberEvent.Cue>().map { it.cue }
        assertEquals(2, cues.size)
        assertEquals("Hello there my good friend.", cues[0].text)
        assertEquals("The weather is nice today!", cues[1].text)
        assertEquals(0L, cues[0].startMs)
        assertEquals(cues[0].endMs, cues[1].startMs)
        assertEquals(2_000L, cues[1].endMs)
    }

    @Test
    fun `applies the text transform to finalized segments`() = runTest {
        val source = FakePcmSource(listOf(32_000 to 1_000L))
        val engine = FakeSpeechEngine(listOf(32_000L to "歡迎收聽"))

        val events = EpisodeTranscriber(engine, textTransform = ::toSimplifiedChinese)
            .transcribe(source, startMs = 0L, locale = Locale.US)
            .toList()

        val cue = events.filterIsInstance<TranscriberEvent.Cue>().single().cue
        assertEquals("欢迎收听", cue.text)
    }

    @Test
    fun `blank segments are dropped`() = runTest {
        val source = FakePcmSource(listOf(32_000 to 1_000L))
        val engine = FakeSpeechEngine(listOf(32_000L to "   "))

        val events = EpisodeTranscriber(engine).transcribe(source, 0L, Locale.US).toList()

        assertTrue(events.filterIsInstance<TranscriberEvent.Cue>().isEmpty())
    }
}
