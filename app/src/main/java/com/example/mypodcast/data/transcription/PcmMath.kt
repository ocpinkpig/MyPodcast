package com.example.mypodcast.data.transcription

/** Bytes per second of the recognizer's required input: 16-bit mono @ 16 kHz. */
internal const val TARGET_SAMPLE_RATE = 16_000
internal const val TARGET_BYTES_PER_SECOND = TARGET_SAMPLE_RATE * 2

/** Averages interleaved [channels]-channel 16-bit PCM frames down to mono. */
internal fun downmixToMono(input: ShortArray, channels: Int): ShortArray {
    require(channels >= 1) { "channels must be >= 1" }
    if (channels == 1) return input
    val frames = input.size / channels
    val out = ShortArray(frames)
    for (frame in 0 until frames) {
        var sum = 0
        val base = frame * channels
        for (ch in 0 until channels) sum += input[base + ch]
        out[frame] = (sum / channels).toShort()
    }
    return out
}

/** Linear-interpolation resample of mono 16-bit PCM from [fromRate] to [toRate]. */
internal fun resampleLinear(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
    require(fromRate > 0 && toRate > 0) { "rates must be positive" }
    if (fromRate == toRate || input.isEmpty()) return input
    val outSize = (input.size.toLong() * toRate / fromRate).toInt()
    val out = ShortArray(outSize)
    val step = fromRate.toDouble() / toRate
    for (i in 0 until outSize) {
        val srcPos = i * step
        val idx = srcPos.toInt()
        val frac = srcPos - idx
        val a = input[idx.coerceAtMost(input.lastIndex)].toInt()
        val b = input[(idx + 1).coerceAtMost(input.lastIndex)].toInt()
        out[i] = (a + (b - a) * frac).toInt().toShort()
    }
    return out
}

/** Position in milliseconds represented by [byteCount] bytes of target-format PCM. */
internal fun pcm16BytesToMs(byteCount: Long): Long =
    byteCount * 1000 / TARGET_BYTES_PER_SECOND
