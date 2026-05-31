package com.example.mypodcast.data.remote.transcript

import com.example.mypodcast.domain.model.TranscriptCue

/**
 * Returns the index of the cue active at [positionMs] — the last cue whose
 * start time is at or before the position — or -1 when the position precedes
 * the first cue. Cues must be sorted by [TranscriptCue.startMs]. Uses binary
 * search so it is cheap to call on every playback tick.
 */
fun cueIndexAt(cues: List<TranscriptCue>, positionMs: Long): Int {
    var low = 0
    var high = cues.size - 1
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (cues[mid].startMs <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}
