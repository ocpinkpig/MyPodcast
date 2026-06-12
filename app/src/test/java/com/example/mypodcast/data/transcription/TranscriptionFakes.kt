package com.example.mypodcast.data.transcription

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * Scripted PCM source: emits [chunks] without real-time pacing (tests must not
 * sleep). Each entry is (byteCount, endPositionMs).
 */
class FakePcmSource(private val chunks: List<Pair<Int, Long>>) : PcmSource {
    override fun stream(startMs: Long): Flow<PcmChunk> = flow {
        chunks.forEach { (size, endMs) -> emit(PcmChunk(ByteArray(size), endMs)) }
    }
}

/**
 * Scripted engine: emits a queued text after the session has been fed at least
 * the paired number of bytes. Completion happens when finish() is called.
 */
class FakeSpeechEngine(
    private val script: List<Pair<Long, String>>, // fedBytesAtLeast -> segment text
    var availability: EngineAvailability = EngineAvailability.AVAILABLE,
    var failOnFeed: Boolean = false
) : SpeechTranscriptionEngine {
    var modelDownloadRequests = 0
        private set
    val openedSessions = mutableListOf<FakeSpeechSession>()
    val openedLocales = mutableListOf<Locale>()

    override suspend fun checkAvailability(): EngineAvailability = availability
    override suspend fun requestModelDownload() { modelDownloadRequests++ }
    override fun openSession(locale: Locale): SpeechSession {
        openedLocales.add(locale)
        return FakeSpeechSession(script, failOnFeed).also { openedSessions.add(it) }
    }
}

class FakeSpeechSession(
    script: List<Pair<Long, String>>,
    private val failOnFeed: Boolean
) : SpeechSession {
    private val pending = script.toMutableList()
    private val channel = Channel<String>(Channel.UNLIMITED)
    private var fedBytes = 0L
    var closed = false
        private set

    override val results: Flow<String> = channel.consumeAsFlow()

    override suspend fun feed(chunk: ByteArray) {
        if (failOnFeed) throw IllegalStateException("engine exploded")
        fedBytes += chunk.size
        while (pending.isNotEmpty() && fedBytes >= pending.first().first) {
            channel.send(pending.removeAt(0).second)
        }
    }

    override suspend fun finish() {
        while (pending.isNotEmpty()) channel.send(pending.removeAt(0).second)
        channel.close()
    }

    override fun close() {
        closed = true
        channel.close()
    }
}

// --- Session-manager fakes -------------------------------------------------

class FakePlayerRepository : com.example.mypodcast.domain.repository.PlayerRepository {
    val state = kotlinx.coroutines.flow.MutableStateFlow(com.example.mypodcast.domain.model.PlayerState())
    override val playerState: kotlinx.coroutines.flow.StateFlow<com.example.mypodcast.domain.model.PlayerState>
        get() = state

    override fun play(episode: com.example.mypodcast.domain.model.Episode) = Unit
    override fun prepare(episode: com.example.mypodcast.domain.model.Episode) = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun skipForward(seconds: Int) = Unit
    override fun skipBack(seconds: Int) = Unit
    override fun setPlaybackSpeed(speed: Float) = Unit
    override fun setSleepTimer(minutes: Int) = Unit
    override fun cancelSleepTimer() = Unit
    override fun setFavorite(guid: String, isFavorite: Boolean) = Unit
    override fun enqueue(episode: com.example.mypodcast.domain.model.Episode) = Unit
    override fun enqueueNext(episode: com.example.mypodcast.domain.model.Episode) = Unit
    override fun removeFromQueue(guid: String) = Unit
    override fun clearQueue() = Unit
    override fun skipToQueueItem(guid: String) = Unit
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) = Unit
    override fun release() = Unit
}

/** Minimal LibraryRepository fake: only the members the session manager touches. */
class FakeTranscriptionLibraryRepository(
    private val pathByGuid: Map<String, String> = emptyMap(),
    private val languageByPodcastId: Map<Long, String> = emptyMap()
) : com.example.mypodcast.domain.repository.LibraryRepository {
    val statusUpdates = mutableListOf<Pair<String, com.example.mypodcast.domain.model.TranscriptStatus>>()

    override suspend fun getDownloadedFilePath(episodeGuid: String): String? = pathByGuid[episodeGuid]
    override suspend fun getPodcastLanguage(podcastId: Long): String? = languageByPodcastId[podcastId]
    override suspend fun setTranscriptStatus(
        episodeGuid: String,
        status: com.example.mypodcast.domain.model.TranscriptStatus
    ) {
        statusUpdates.add(episodeGuid to status)
    }
    override fun observeTranscriptStatuses() =
        kotlinx.coroutines.flow.flowOf(emptyMap<String, com.example.mypodcast.domain.model.TranscriptStatus>())

    // Members unused by the session manager:
    override suspend fun subscribe(podcastId: Long) = Unit
    override suspend fun unsubscribe(podcastId: Long) = Unit
    override fun observeSubscriptions() =
        kotlinx.coroutines.flow.flowOf(emptyList<com.example.mypodcast.domain.model.Podcast>())
    override fun observeIsSubscribed(podcastId: Long) = kotlinx.coroutines.flow.flowOf(false)
    override fun observeDownloadedEpisodes() =
        kotlinx.coroutines.flow.flowOf(emptyList<com.example.mypodcast.domain.model.Episode>())
    override fun observeIsDownloaded(episodeGuid: String) = kotlinx.coroutines.flow.flowOf(false)
    override suspend fun saveDownload(episodeGuid: String, podcastId: Long, localPath: String, sizeBytes: Long) = Unit
    override suspend fun deleteDownload(episodeGuid: String) = Unit
    override suspend fun cleanupOrphanedFiles() = Unit
}
