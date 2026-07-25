package com.example.mypodcast.media

import android.net.Uri
import java.io.File

/**
 * Resolves an [com.example.mypodcast.domain.model.Episode.audioUrl] to a URI
 * ExoPlayer can open. Downloaded episodes carry an absolute filesystem path
 * there instead of a URL (see `LibraryRepositoryImpl.observeDownloadedEpisodes`).
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
