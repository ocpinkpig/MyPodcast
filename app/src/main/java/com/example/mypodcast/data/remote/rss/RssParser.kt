package com.example.mypodcast.data.remote.rss

import android.util.Xml
import com.example.mypodcast.data.remote.rss.model.RssFeed
import com.example.mypodcast.data.remote.rss.model.RssEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class RssParser @Inject constructor(private val okHttpClient: OkHttpClient) {

    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)
    )

    suspend fun parse(feedUrl: String): RssFeed = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(feedUrl).build()
        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            response.body?.string() ?: ""
        }

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(responseBody.reader())

        var feedTitle = ""
        var feedImage: String? = null
        val episodes = mutableListOf<RssEpisode>()

        var inItem = false
        var inImage = false
        var currentTag: String? = null

        var guid = ""
        var title = ""
        var description: String? = null
        var audioUrl = ""
        var artworkUrl: String? = null
        var publishedAt = 0L
        var durationSeconds = 0
        var fileSizeBytes = 0L

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    currentTag = tag
                    when {
                        tag == "item" -> {
                            inItem = true
                            guid = ""; title = ""; description = null
                            audioUrl = ""; artworkUrl = null
                            publishedAt = 0L; durationSeconds = 0; fileSizeBytes = 0L
                        }
                        tag == "image" && !inItem -> inImage = true
                        tag == "enclosure" && inItem -> {
                            audioUrl = parser.getAttributeValue(null, "url") ?: ""
                            fileSizeBytes = parser.getAttributeValue(null, "length")?.toLongOrNull() ?: 0L
                        }
                        tag == "itunes:image" -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (href != null) {
                                if (inItem) artworkUrl = href else feedImage = href
                            }
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    if (parser.isWhitespace) { /* skip */ }
                    else {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isEmpty()) {
                            // skip
                        } else when (currentTag) {
                            "title" -> when {
                                inItem -> if (title.isEmpty()) title = text
                                inImage -> { /* skip image title */ }
                                else -> if (feedTitle.isEmpty()) feedTitle = text
                            }
                            "guid" -> if (inItem) guid = text
                            "description" -> if (inItem && description == null) description = text
                            "itunes:summary" -> if (inItem && description == null) description = text
                            "pubDate" -> if (inItem) publishedAt = parseDate(text)
                            "itunes:duration" -> if (inItem) durationSeconds = parseDuration(text)
                            "url" -> if (inImage) feedImage = text
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name
                    when {
                        tag == "item" -> {
                            if (audioUrl.isNotBlank()) {
                                episodes.add(
                                    RssEpisode(
                                        guid = guid.ifBlank { audioUrl },
                                        title = title,
                                        description = description,
                                        audioUrl = audioUrl,
                                        artworkUrl = artworkUrl,
                                        publishedAt = publishedAt,
                                        durationSeconds = durationSeconds,
                                        fileSizeBytes = fileSizeBytes
                                    )
                                )
                            }
                            inItem = false
                        }
                        tag == "image" && !inItem -> inImage = false
                    }
                    currentTag = null
                }
            }

            eventType = parser.next()
        }

        RssFeed(title = feedTitle, imageUrl = feedImage, episodes = episodes)
    }

    private fun parseDate(raw: String): Long {
        for (fmt in dateFormats) {
            try { return fmt.parse(raw)?.time ?: 0L } catch (_: Exception) {}
        }
        return 0L
    }

    private fun parseDuration(raw: String): Int {
        val parts = raw.split(":").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }
}
