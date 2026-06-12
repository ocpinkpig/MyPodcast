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

    override suspend fun checkAvailability(locale: Locale): EngineAvailability {
        if (Build.VERSION.SDK_INT < MIN_API) return EngineAvailability.UNAVAILABLE
        val status = runCatching {
            newRecognizer(locale).use { recognizer ->
                when (recognizer.checkStatus()) {
                    FeatureStatus.AVAILABLE -> EngineAvailability.AVAILABLE
                    FeatureStatus.DOWNLOADABLE -> EngineAvailability.DOWNLOADABLE
                    FeatureStatus.DOWNLOADING -> EngineAvailability.DOWNLOADING
                    else -> EngineAvailability.UNAVAILABLE
                }
            }
        }.getOrDefault(EngineAvailability.UNAVAILABLE)
        return status
    }

    override suspend fun requestModelDownload(locale: Locale) {
        if (Build.VERSION.SDK_INT < MIN_API) return
        runCatching {
            newRecognizer(locale).use { recognizer ->
                // Collecting drives the download; AICore continues it even if
                // this collection is cancelled with the session.
                recognizer.download().collect()
            }
        }
    }

    override fun openSession(locale: Locale): SpeechSession = MlKitSpeechSession(locale)

    private companion object {
        const val MIN_API = 31

        fun newRecognizer(locale: Locale? = null): SpeechRecognizer =
            SpeechRecognition.getClient(
                SpeechRecognizerOptions.Builder()
                    .apply { if (locale != null) this.locale = locale }
                    .build()
            )
    }

    private class MlKitSpeechSession(locale: Locale) : SpeechSession {

        private val recognizer: SpeechRecognizer = newRecognizer(locale)
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
