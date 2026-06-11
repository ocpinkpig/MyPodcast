package com.example.mypodcast.data.transcription

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmMathTest {

    @Test
    fun `downmix mono is identity`() {
        val input = shortArrayOf(1, -2, 3)
        assertArrayEquals(input, downmixToMono(input, channels = 1))
    }

    @Test
    fun `downmix stereo averages channel pairs`() {
        // Frames: (100, 200), (-100, 100), (32767, 32767)
        val input = shortArrayOf(100, 200, -100, 100, 32767, 32767)
        assertArrayEquals(shortArrayOf(150, 0, 32767), downmixToMono(input, channels = 2))
    }

    @Test
    fun `resample same rate is identity`() {
        val input = shortArrayOf(0, 100, 200, 300)
        assertArrayEquals(input, resampleLinear(input, fromRate = 16000, toRate = 16000))
    }

    @Test
    fun `resample halves sample count from 32kHz to 16kHz`() {
        val input = ShortArray(32) { (it * 10).toShort() }
        val output = resampleLinear(input, fromRate = 32000, toRate = 16000)
        assertEquals(16, output.size)
        // First output sample maps exactly onto input[0].
        assertEquals(0, output[0].toInt())
    }

    @Test
    fun `resample interpolates between input samples`() {
        // 2 -> 4 samples: positions 0, 0.5, 1.0, 1.5 over input [0, 100]
        val output = resampleLinear(shortArrayOf(0, 100), fromRate = 8000, toRate = 16000)
        assertEquals(4, output.size)
        assertEquals(0, output[0].toInt())
        assertEquals(50, output[1].toInt())
        assertEquals(100, output[2].toInt())
        assertEquals(100, output[3].toInt()) // clamped at the last input sample
    }

    @Test
    fun `byte clock converts 16-bit mono 16kHz bytes to ms`() {
        // 32_000 bytes/sec => 32 bytes/ms
        assertEquals(0L, pcm16BytesToMs(0))
        assertEquals(1_000L, pcm16BytesToMs(32_000))
        assertEquals(12_345L, pcm16BytesToMs(12_345L * 32))
    }
}
