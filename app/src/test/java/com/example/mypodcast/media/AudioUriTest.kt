package com.example.mypodcast.media

import androidx.media3.common.util.Util
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Downloaded episodes carry a filesystem path in `Episode.audioUrl`, and the
 * path is derived from the feed's `<guid>` — arbitrary text. These cover the
 * guid shapes that broke playback: a bare `Uri.parse` reads the ':' in a
 * URL-style guid as a scheme separator (routing ExoPlayer to HTTP) and
 * truncates at '?' or '#'.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioUriTest {

    private val episodesDir = "/data/user/0/com.example.mypodcast/files/episodes"

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
