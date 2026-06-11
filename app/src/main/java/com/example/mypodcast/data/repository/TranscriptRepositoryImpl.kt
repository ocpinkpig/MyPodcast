package com.example.mypodcast.data.repository

import androidx.core.text.HtmlCompat
import com.example.mypodcast.data.remote.transcript.PlainTextTranscriptParser
import com.example.mypodcast.data.remote.transcript.PodcastJsonParser
import com.example.mypodcast.data.remote.transcript.SrtParser
import com.example.mypodcast.data.remote.transcript.TranscriptFormat
import com.example.mypodcast.data.remote.transcript.VttParser
import com.example.mypodcast.data.remote.transcript.transcriptFormatOf
import com.example.mypodcast.data.transcription.GeneratedTranscriptStore
import com.example.mypodcast.di.TranscriptFilesDir
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Transcript
import com.example.mypodcast.domain.repository.TranscriptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Fetches a `<podcast:transcript>` file over HTTP, caches the raw body on disk
 * (so re-opening an episode is instant), and parses it into a [Transcript]
 * according to its format. Episodes without a publisher transcript URL fall
 * back to the on-device generated transcript store, which the playback-time
 * transcriber fills in (possibly partially — see [Transcript.transcribedUpToMs]).
 */
class TranscriptRepositoryImpl @Inject constructor(
    @TranscriptFilesDir private val filesDir: File,
    private val okHttpClient: OkHttpClient,
    private val generatedTranscriptStore: GeneratedTranscriptStore
) : TranscriptRepository {

    override suspend fun getTranscript(episode: Episode): Result<Transcript> =
        withContext(Dispatchers.IO) {
            val url = episode.transcriptUrl?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.success(generatedTranscript(episode.guid))
            runCatching {
                val raw = cachedBody(episode.guid) ?: downloadBody(episode.guid, url)
                parse(raw, transcriptFormatOf(episode.transcriptType, url))
            }
        }

    /** On-device generated transcript, or the empty placeholder when absent. */
    private fun generatedTranscript(guid: String): Transcript {
        val generated = generatedTranscriptStore.read(guid)
            ?: return Transcript(emptyList(), isSynced = false)
        return Transcript(
            cues = generated.cues,
            isSynced = true,
            transcribedUpToMs = generated.transcribedUpToMs.takeIf { !generated.isComplete }
        )
    }

    private fun cacheFile(guid: String): File =
        File(filesDir, "transcripts/$guid").also { it.parentFile?.mkdirs() }

    private fun cachedBody(guid: String): String? =
        cacheFile(guid).takeIf { it.exists() && it.length() > 0 }?.readText()

    private fun downloadBody(guid: String, url: String): String {
        val request = Request.Builder().url(url).build()
        val body = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Transcript fetch failed: HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty transcript response")
        }
        val file = cacheFile(guid)
        val tempFile = File(file.parentFile, "${file.name}.part")
        tempFile.writeText(body)
        if (file.exists() && !file.delete()) throw IOException("Could not replace cached transcript")
        if (!tempFile.renameTo(file)) throw IOException("Could not finalize cached transcript")
        return body
    }

    private fun parse(raw: String, format: TranscriptFormat): Transcript = when (format) {
        TranscriptFormat.VTT -> VttParser.parse(raw)
        TranscriptFormat.SRT -> SrtParser.parse(raw)
        TranscriptFormat.JSON -> PodcastJsonParser.parse(raw)
        TranscriptFormat.HTML -> PlainTextTranscriptParser.parse(htmlToText(raw))
        TranscriptFormat.PLAIN -> PlainTextTranscriptParser.parse(raw)
    }

    private fun htmlToText(raw: String): String =
        HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
}
