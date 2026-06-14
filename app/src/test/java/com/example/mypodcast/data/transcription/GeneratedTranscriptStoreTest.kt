package com.example.mypodcast.data.transcription

import com.example.mypodcast.domain.model.TranscriptCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GeneratedTranscriptStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = GeneratedTranscriptStore(tmp.root)

    private val sample = GeneratedTranscript(
        cues = listOf(
            TranscriptCue(startMs = 0, endMs = 4_200, text = "Welcome back to the show."),
            TranscriptCue(startMs = 4_200, endMs = 9_000, text = "Today we talk transcripts.")
        ),
        transcribedUpToMs = 9_000,
        isComplete = false,
        engineVersion = "mlkit-genai-1.0.0-alpha1"
    )

    @Test
    fun `read returns null when nothing was written`() {
        assertNull(store().read("guid-1"))
    }

    @Test
    fun `write then read round-trips all fields`() {
        store().write("guid-1", sample)
        assertEquals(sample, store().read("guid-1"))
    }

    @Test
    fun `write replaces previous content atomically`() {
        val s = store()
        s.write("guid-1", sample)
        val completed = sample.copy(isComplete = true, transcribedUpToMs = 60_000)
        s.write("guid-1", completed)
        assertEquals(completed, s.read("guid-1"))
        // No leftover temp file.
        val dir = File(tmp.root, "transcripts/generated")
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun `corrupt file reads as null instead of throwing`() {
        val dir = File(tmp.root, "transcripts/generated").apply { mkdirs() }
        File(dir, "guid-1.json").writeText("{ not json")
        assertNull(store().read("guid-1"))
    }

    @Test
    fun `delete removes the file`() {
        val s = store()
        s.write("guid-1", sample)
        s.delete("guid-1")
        assertNull(s.read("guid-1"))
        assertFalse(File(tmp.root, "transcripts/generated/guid-1.json").exists())
    }
}
