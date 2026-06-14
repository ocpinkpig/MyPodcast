package com.example.mypodcast.data.transcription

import android.util.Log
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.TranscriptStatus
import com.example.mypodcast.domain.repository.LibraryRepository
import com.example.mypodcast.domain.repository.PlayerRepository
import com.example.mypodcast.domain.transcription.LiveTranscription
import com.example.mypodcast.domain.transcription.TranscriptionMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches playback and runs on-device transcript generation for downloaded,
 * transcript-less episodes while they play ("Variant 2" of the design spec:
 * an independent paced decode anchored to the playback session, not a tap of
 * the playback audio).
 *
 * Hosted by PlaybackService: [start] in onCreate, [stop] in onDestroy.
 */
@Singleton
class TranscriptionSessionManager @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val libraryRepository: LibraryRepository,
    private val store: GeneratedTranscriptStore,
    private val engine: SpeechTranscriptionEngine,
    private val pcmSourceFactory: PcmSourceFactory
) : TranscriptionMonitor {

    private val _live = MutableStateFlow<LiveTranscription?>(null)
    override val live: StateFlow<LiveTranscription?> = _live.asStateFlow()

    private val refreshTicks = MutableStateFlow(0)
    private var watchJob: Job? = null
    private val availabilityByLocale = mutableMapOf<String, EngineAvailability>()

    fun start(scope: CoroutineScope) {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            combine(
                playerRepository.playerState.map { state ->
                    state.episode?.takeIf {
                        state.isPlaying && it.transcriptUrl.isNullOrBlank()
                    }
                },
                refreshTicks
            ) { episode, tick -> episode to tick }
                .distinctUntilChangedBy { (episode, tick) -> episode?.guid to tick }
                .collectLatest { (episode, _) ->
                    // collectLatest cancels the running session on pause/stop/switch.
                    if (episode != null) runSession(episode)
                }
        }
    }

    override fun refresh() {
        refreshTicks.value++
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
    }

    private suspend fun runSession(episode: Episode) {
        val filePath = libraryRepository.getDownloadedFilePath(episode.guid)
        if (filePath == null) {
            Log.d(TAG, "skip ${episode.guid}: not downloaded")
            return
        }
        val locale = recognizerLocale(
            feedLanguage = libraryRepository.getPodcastLanguage(episode.podcastId),
            fallback = Locale.getDefault()
        )
        val localeTag = locale.toLanguageTag()
        if (!engineReady(locale)) {
            Log.d(TAG, "skip ${episode.guid}: engine not ready for $localeTag")
            return
        }
        val stored = store.read(episode.guid)
        // Progress is only reusable when produced by the same recognizer locale
        // AND engine version. A different locale, or an older engine/mode (e.g. a
        // basic-mode transcript from before the advanced-mode switch), is stale —
        // regenerate it even if it was marked complete.
        val matchesCurrent = stored != null &&
            stored.locale == localeTag &&
            stored.engineVersion == SpeechTranscriptionEngine.VERSION
        if (matchesCurrent && stored!!.isComplete) return
        val resumed = stored?.takeIf { matchesCurrent }
        if (stored != null && resumed == null) {
            Log.d(
                TAG,
                "discarding ${episode.guid} progress " +
                    "(locale ${stored.locale}->$localeTag, version ${stored.engineVersion}, " +
                    "wasComplete=${stored.isComplete})"
            )
        }
        Log.d(TAG, "session start ${episode.guid} from ${resumed?.transcribedUpToMs ?: 0}ms locale=$localeTag")

        var cues = resumed?.cues.orEmpty()
        var upToMs = resumed?.transcribedUpToMs ?: 0L
        var unsavedCues = 0
        var lastPersistedMs = upToMs

        libraryRepository.setTranscriptStatus(episode.guid, TranscriptStatus.IN_PROGRESS)
        _live.value = LiveTranscription(episode.guid, cues, upToMs, isComplete = false)

        fun persist(isComplete: Boolean) {
            store.write(
                episode.guid,
                GeneratedTranscript(
                    cues = cues,
                    transcribedUpToMs = upToMs,
                    isComplete = isComplete,
                    engineVersion = SpeechTranscriptionEngine.VERSION,
                    locale = localeTag
                )
            )
            unsavedCues = 0
            lastPersistedMs = upToMs
        }

        try {
            EpisodeTranscriber(engine, textTransform = simplifyTransformForLocale(localeTag))
                .transcribe(pcmSourceFactory.create(filePath), startMs = upToMs, locale = locale)
                .collect { event ->
                    when (event) {
                        is TranscriberEvent.Cue -> {
                            cues = cues + event.cue
                            upToMs = maxOf(upToMs, event.cue.endMs)
                            unsavedCues++
                            _live.value =
                                LiveTranscription(episode.guid, cues, upToMs, isComplete = false)
                        }

                        is TranscriberEvent.Progress -> {
                            upToMs = maxOf(upToMs, event.upToMs)
                            _live.value =
                                LiveTranscription(episode.guid, cues, upToMs, isComplete = false)
                        }

                        TranscriberEvent.Completed -> {
                            persist(isComplete = true)
                            libraryRepository.setTranscriptStatus(
                                episode.guid, TranscriptStatus.COMPLETE
                            )
                            _live.value =
                                LiveTranscription(episode.guid, cues, upToMs, isComplete = true)
                        }
                    }
                    if (unsavedCues >= PERSIST_EVERY_CUES ||
                        upToMs - lastPersistedMs >= PERSIST_EVERY_MS
                    ) {
                        persist(isComplete = false)
                    }
                }
        } catch (e: CancellationException) {
            persist(isComplete = false)
            throw e
        } catch (e: Exception) {
            // Engine/decoder failure: keep what we have; retry next playback
            // session. Silent in the UI by design, but never silent in logcat.
            Log.w(TAG, "transcription failed for ${episode.guid} at ${upToMs}ms", e)
            persist(isComplete = false)
        }
    }

    /**
     * AVAILABLE -> proceed. DOWNLOADABLE -> request the model once, skip for
     * now (re-check on next session). DOWNLOADING/UNAVAILABLE -> skip silently.
     * Availability is per locale: each language has its own model pack.
     */
    private suspend fun engineReady(locale: Locale): Boolean {
        val tag = locale.toLanguageTag()
        val status = availabilityByLocale[tag] ?: engine.checkAvailability(locale)
        Log.d(TAG, "engine status for $tag: $status")
        return when (status) {
            EngineAvailability.AVAILABLE -> {
                availabilityByLocale[tag] = EngineAvailability.AVAILABLE
                true
            }
            EngineAvailability.DOWNLOADABLE -> {
                // Suspends until AICore finishes (or fails) the pack download;
                // if it lands while this episode is still playing, transcribe
                // right away instead of waiting for the next play event.
                val downloaded = runCatching { engine.requestModelDownload(locale) }.isSuccess
                val after = if (downloaded) engine.checkAvailability(locale) else status
                Log.d(TAG, "model download for $tag finished, status now $after")
                availabilityByLocale.remove(tag)
                if (after == EngineAvailability.AVAILABLE) {
                    availabilityByLocale[tag] = after
                    true
                } else {
                    false
                }
            }
            EngineAvailability.DOWNLOADING -> {
                availabilityByLocale.remove(tag)
                false
            }
            EngineAvailability.UNAVAILABLE -> {
                availabilityByLocale[tag] = EngineAvailability.UNAVAILABLE
                false
            }
        }
    }

    private companion object {
        const val TAG = "TranscriptionSession"
        const val PERSIST_EVERY_CUES = 15
        const val PERSIST_EVERY_MS = 30_000L
    }
}
