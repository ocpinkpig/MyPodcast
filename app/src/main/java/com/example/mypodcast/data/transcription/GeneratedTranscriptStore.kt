package com.example.mypodcast.data.transcription

import com.example.mypodcast.domain.model.TranscriptCue
import com.google.gson.Gson
import java.io.File

/** On-disk state of a (possibly partial) on-device generated transcript. */
data class GeneratedTranscript(
    val cues: List<TranscriptCue>,
    val transcribedUpToMs: Long,
    val isComplete: Boolean,
    val engineVersion: String,
    /** BCP-47 tag of the recognizer locale that produced [cues]; null on legacy files. */
    val locale: String? = null
)

/**
 * Persists generated transcripts as one JSON file per episode under
 * `<baseDir>/transcripts/generated/<guid>.json`. Writes are atomic
 * (`.part` + rename), matching the idiom used elsewhere in the app.
 */
class GeneratedTranscriptStore(private val baseDir: File) {

    private val gson = Gson()

    fun read(episodeGuid: String): GeneratedTranscript? {
        val file = fileFor(episodeGuid)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching { gson.fromJson(file.readText(), GeneratedTranscript::class.java) }
            .getOrNull()
            ?.takeIf { it.cues != null } // Gson can yield null fields on shape mismatch
    }

    fun write(episodeGuid: String, transcript: GeneratedTranscript) {
        val file = fileFor(episodeGuid)
        val tempFile = File(file.parentFile, "${file.name}.part")
        tempFile.writeText(gson.toJson(transcript))
        if (file.exists() && !file.delete()) error("Could not replace generated transcript")
        if (!tempFile.renameTo(file)) error("Could not finalize generated transcript")
    }

    fun delete(episodeGuid: String) {
        fileFor(episodeGuid).delete()
    }

    private fun fileFor(guid: String): File =
        File(baseDir, "transcripts/generated/$guid.json").also { it.parentFile?.mkdirs() }
}
