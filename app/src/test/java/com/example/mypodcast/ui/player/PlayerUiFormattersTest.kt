package com.example.mypodcast.ui.player

import junit.framework.TestCase.assertEquals
import org.junit.Test

class PlayerUiFormattersTest {
    @Test
    fun formatPlaybackTime_formatsMinutesAndSeconds() {
        assertEquals("0:00", formatPlaybackTime(0L))
        assertEquals("1:05", formatPlaybackTime(65_000L))
        assertEquals("59:59", formatPlaybackTime(3_599_000L))
    }

    @Test
    fun formatPlaybackTime_formatsHours() {
        assertEquals("1:00:00", formatPlaybackTime(3_600_000L))
        assertEquals("2:03:04", formatPlaybackTime(7_384_000L))
    }

    @Test
    fun formatProgressFraction_handlesUnknownDuration() {
        assertEquals(0f, formatProgressFraction(positionMs = 5_000L, durationMs = 0L))
        assertEquals(0.5f, formatProgressFraction(positionMs = 5_000L, durationMs = 10_000L))
        assertEquals(1f, formatProgressFraction(positionMs = 12_000L, durationMs = 10_000L))
        assertEquals(0f, formatProgressFraction(positionMs = -1_000L, durationMs = 10_000L))
    }

    @Test
    fun formatSpeedLabel_usesSingleDecimal() {
        assertEquals("1.0x", formatSpeedLabel(1.0f))
        assertEquals("1.5x", formatSpeedLabel(1.5f))
        assertEquals("3.0x", formatSpeedLabel(3.0f))
    }

    @Test
    fun formatSleepTimerLabel_handlesInactiveAndActiveTimer() {
        assertEquals("Timer off", formatSleepTimerLabel(0L))
        assertEquals("14:59 left", formatSleepTimerLabel(899_000L))
        assertEquals("1:00:00 left", formatSleepTimerLabel(3_600_000L))
    }
}
