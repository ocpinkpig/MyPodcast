package com.example.mypodcast.data.transcription

import android.os.Build
import android.os.ParcelFileDescriptor
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.Locale
import javax.inject.Inject

/**
 * Production [SpeechTranscriptionEngine] backed by ML Kit GenAI Speech
 * Recognition (alpha). Audio is handed to the recognizer through a pipe
 * ParcelFileDescriptor: [SpeechSession.feed] writes paced PCM into the write
 * end, the recognizer streams responses for the read end.
 */
class MlKitSpeechEngine @Inject constructor() : SpeechTranscriptionEngine {

    /** Best recognition mode for a locale, resolved during [checkAvailability]. */
    private val modeByLocale = mutableMapOf<String, Int>()

    override suspend fun checkAvailability(locale: Locale): EngineAvailability {
        if (Build.VERSION.SDK_INT < MIN_API) return EngineAvailability.UNAVAILABLE
        // Prefer the advanced (Gemini Nano) model — far better quality, and the
        // only tier where languages like Mandarin are beyond beta. Fall back to
        // basic where advanced isn't offered for this locale/device.
        val advanced = statusFor(locale, MODE_ADVANCED)
        val (mode, status) = if (advanced != EngineAvailability.UNAVAILABLE) {
            MODE_ADVANCED to advanced
        } else {
            MODE_BASIC to statusFor(locale, MODE_BASIC)
        }
        modeByLocale[locale.toLanguageTag()] = mode
        return status
    }

    private suspend fun statusFor(locale: Locale, mode: Int): EngineAvailability = runCatching {
        newRecognizer(locale, mode).use { recognizer ->
            when (recognizer.checkStatus()) {
                FeatureStatus.AVAILABLE -> EngineAvailability.AVAILABLE
                FeatureStatus.DOWNLOADABLE -> EngineAvailability.DOWNLOADABLE
                FeatureStatus.DOWNLOADING -> EngineAvailability.DOWNLOADING
                else -> EngineAvailability.UNAVAILABLE
            }
        }
    }.getOrDefault(EngineAvailability.UNAVAILABLE)

    override suspend fun requestModelDownload(locale: Locale) {
        if (Build.VERSION.SDK_INT < MIN_API) return
        runCatching {
            newRecognizer(locale, modeFor(locale)).use { recognizer ->
                // Collecting drives the download; AICore continues it even if
                // this collection is cancelled with the session.
                recognizer.download().collect()
            }
        }
    }

    override fun openSession(locale: Locale): SpeechSession =
        MlKitSpeechSession(locale, modeFor(locale))

    /** The mode resolved for [locale] (advanced if [checkAvailability] hasn't run). */
    private fun modeFor(locale: Locale): Int =
        modeByLocale[locale.toLanguageTag()] ?: MODE_ADVANCED

    private companion object {
        const val MIN_API = 31
        val MODE_BASIC = SpeechRecognizerOptions.Mode.MODE_BASIC
        val MODE_ADVANCED = SpeechRecognizerOptions.Mode.MODE_ADVANCED

        fun newRecognizer(locale: Locale? = null, mode: Int = MODE_ADVANCED): SpeechRecognizer =
            SpeechRecognition.getClient(
                SpeechRecognizerOptions.Builder()
                    .apply {
                        if (locale != null) this.locale = locale
                        preferredMode = mode
                    }
                    .build()
            )
    }

    private class MlKitSpeechSession(locale: Locale, mode: Int) : SpeechSession {

        private val recognizer: SpeechRecognizer = newRecognizer(locale, mode)
        private val pipe: Array<ParcelFileDescriptor> = ParcelFileDescriptor.createPipe()
        private val readSide: ParcelFileDescriptor = pipe[0]
        private val writeStream: OutputStream =
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])

        override val results: Flow<String> = flow {
            val request = SpeechRecognizerRequest.Builder()
                .apply { audioSource = AudioSource.fromPfd(readSide) }
                .build()
            emitAll(recognizer.startRecognition(request))
        }.transform { response ->
            when (response) {
                is SpeechRecognizerResponse.FinalTextResponse -> emit(response.text)
                is SpeechRecognizerResponse.ErrorResponse -> throw response.e
                else -> Unit // partials are superseded; CompletedResponse ends the flow
            }
        }

        override suspend fun feed(chunk: ByteArray) = withContext(Dispatchers.IO) {
            writeStream.write(chunk)
        }

        override suspend fun finish() = withContext(Dispatchers.IO) {
            // Closing the write end signals EOF; the recognizer flushes its
            // last segment and completes the response flow.
            writeStream.close()
        }

        override fun close() {
            runCatching { writeStream.close() }
            runCatching { readSide.close() }
            runCatching { recognizer.close() }
        }
    }
}
