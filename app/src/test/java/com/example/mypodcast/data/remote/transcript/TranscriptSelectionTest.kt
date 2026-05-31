package com.example.mypodcast.data.remote.transcript

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class TranscriptSelectionTest {

    @Test
    fun mapsMimeTypesToFormats() {
        assertEquals(TranscriptFormat.VTT, transcriptFormatOf("text/vtt"))
        assertEquals(TranscriptFormat.SRT, transcriptFormatOf("application/srt"))
        assertEquals(TranscriptFormat.SRT, transcriptFormatOf("application/x-subrip"))
        assertEquals(TranscriptFormat.JSON, transcriptFormatOf("application/json"))
        assertEquals(TranscriptFormat.HTML, transcriptFormatOf("text/html"))
        assertEquals(TranscriptFormat.PLAIN, transcriptFormatOf("text/plain"))
    }

    @Test
    fun fallsBackToUrlExtensionWhenTypeMissing() {
        assertEquals(TranscriptFormat.VTT, transcriptFormatOf(null, "https://x.com/a.vtt"))
        assertEquals(TranscriptFormat.SRT, transcriptFormatOf(null, "https://x.com/a.srt"))
        assertEquals(TranscriptFormat.JSON, transcriptFormatOf(null, "https://x.com/a.json"))
        assertEquals(TranscriptFormat.PLAIN, transcriptFormatOf(null, "https://x.com/a.unknown"))
    }

    @Test
    fun prefersVttOverHtmlWhenBothPresent() {
        val refs = listOf(
            TranscriptRef(url = "a.html", type = "text/html"),
            TranscriptRef(url = "b.vtt", type = "text/vtt"),
            TranscriptRef(url = "c.srt", type = "application/srt")
        )

        assertEquals("b.vtt", selectBestTranscript(refs)?.url)
    }

    @Test
    fun returnsNullForNoCandidates() {
        assertNull(selectBestTranscript(emptyList()))
    }
}
