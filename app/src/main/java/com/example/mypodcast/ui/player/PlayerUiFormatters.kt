package com.example.mypodcast.ui.player

import java.util.Locale
import kotlin.math.roundToInt

internal fun formatPlaybackTime(ms: Long): String {
    val totalSecs = (ms.coerceAtLeast(0L) / 1000).toInt()
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

internal fun formatProgressFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun formatSpeedLabel(speed: Float): String {
    val tenths = (speed * 10f).roundToInt() / 10f
    return "%.1fx".format(Locale.ROOT, tenths)
}

internal fun formatSleepTimerLabel(remainingMs: Long): String {
    if (remainingMs <= 0L) return "Timer off"
    return "${formatPlaybackTime(remainingMs)} left"
}
