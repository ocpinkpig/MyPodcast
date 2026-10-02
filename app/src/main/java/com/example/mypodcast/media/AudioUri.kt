package com.example.mypodcast.media

import android.net.Uri
import java.io.File

/**
 * Picks what to play for an episode: its downloaded file when one is recorded
 * and still on disk, otherwise the feed's [audioUrl] for streaming.
 *
 * This is the only place the downloads table decides playback. Every
 * `Episode` the UI hands the player carries the feed URL — whether it came
 * from the Downloads tab, a podcast's episode list, history, the queue or the
 * episode restored at launch — so a downloaded episode plays from disk no
 * matter where it was started, and a download whose file has gone missing
 * streams instead of failing with "Source error".
 */
internal fun playbackSource(
    audioUrl: String,
    downloadedPath: String?,
    isFile: (String) -> Boolean = { File(it).isFile }
): String = downloadedPath?.takeIf(isFile) ?: audioUrl

/**
 * Resolves a [playbackSource] to a URI ExoPlayer can open. A downloaded
 * episode's source is an absolute filesystem path rather than a URL.
 *
 * Such a path must not go through `Uri.parse`: the path is built from the feed's
 * `<guid>`, so it routinely contains ':' (URL- and tag-style guids), which
 * `Uri.parse` reads as a scheme separator. The resulting bogus scheme makes
 * Media3's `DefaultDataSource` skip `FileDataSource` and hand the URI to its
 * HTTP source, which fails with a `TYPE_SOURCE` error — surfaced to the user as
 * "Source error". Guids containing '?' or '#' get truncated instead, pointing at
 * a file that doesn't exist. [Uri.fromFile] makes the `file` scheme explicit and
 * encodes the path, so the whole path survives.
 */
internal fun audioUri(audioUrl: String): Uri =
    if (audioUrl.startsWith("/")) Uri.fromFile(File(audioUrl)) else Uri.parse(audioUrl)
