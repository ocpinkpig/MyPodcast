package com.example.mypodcast.media

import androidx.media3.common.util.Util
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A downloaded episode should play from its file when that file exists, and
 * stream otherwise. The file path is derived from the feed's `<guid>` —
 * arbitrary text — so these also cover the guid shapes that broke playback: a
 * bare `Uri.parse` reads the ':' in a URL-style guid as a scheme separator
 * (routing ExoPlayer to HTTP) and truncates at '?' or '#'.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioUriTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val episodesDir = "/data/user/0/com.example.mypodcast/files/episodes"
    private val feedUrl = "https://traffic.megaphone.fm/ABC1234567890.mp3"

    @Test
    fun `downloaded episode plays from its file`() {
        val file = tempFolder.newFile("episode.mp3")

        assertEquals(file.absolutePath, playbackSource(feedUrl, downloadedPath = file.absolutePath))
    }

    @Test
    fun `download whose file is missing streams from the feed url`() {
        val missing = "${tempFolder.root.absolutePath}/deleted.mp3"

        assertEquals(feedUrl, playbackSource(feedUrl, downloadedPath = missing))
    }

    @Test
    fun `episode that is not downloaded streams from the feed url`() {
        assertEquals(feedUrl, playbackSource(feedUrl, downloadedPath = null))
    }

    @Test
    fun `local path from url-style guid resolves as a local file`() {
        val path = "$episodesDir/https:/traffic.megaphone.fm/ABC1234567890.mp3.mp3"

        val uri = audioUri(path)

        assertTrue("must reach FileDataSource, not the HTTP source", Util.isLocalFileUri(uri))
        assertEquals(path, uri.path)
    }

    @Test
    fun `local path from tag-style guid resolves as a local file`() {
        val path = "$episodesDir/tag:soundcloud,2010:tracks/123456789.mp3"

        val uri = audioUri(path)

        assertTrue(Util.isLocalFileUri(uri))
        assertEquals(path, uri.path)
    }

    @Test
    fun `local path containing query and fragment characters is not truncated`() {
        val path = "$episodesDir/episode?id=42#part2.mp3"

        val uri = audioUri(path)

        assertTrue(Util.isLocalFileUri(uri))
        assertEquals(path, uri.path)
    }

    @Test
    fun `plain local path still resolves as a local file`() {
        val path = "$episodesDir/plain-uuid-4f2a1b.mp3"

        val uri = audioUri(path)

        assertTrue(Util.isLocalFileUri(uri))
        assertEquals(path, uri.path)
    }

    @Test
    fun `remote url is left untouched for streaming`() {
        val url = "https://traffic.megaphone.fm/ABC1234567890.mp3?updated=1700000000"

        val uri = audioUri(url)

        assertFalse(Util.isLocalFileUri(uri))
        assertEquals("https", uri.scheme)
        assertEquals("traffic.megaphone.fm", uri.host)
        assertEquals("/ABC1234567890.mp3", uri.path)
        assertEquals("updated=1700000000", uri.query)
    }
}
