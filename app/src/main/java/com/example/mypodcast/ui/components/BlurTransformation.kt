package com.example.mypodcast.ui.components

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Cheap, GPU-free blur via downsample + bilinear upsample. The output bitmap
 * goes into Coil's memory/disk cache, so the blur work runs once per image
 * — never per frame. This replaces `Modifier.blur(...)` on the hero artwork,
 * which would otherwise apply a `RenderEffect` blur on every frame the hero
 * was on-screen during scroll.
 *
 * `downsampleSize` controls blur strength: smaller = blurrier. 24px gives a
 * look close to a ~30dp Gaussian blur on common podcast artwork.
 */
class BlurTransformation(
    private val downsampleSize: Int = 24
) : Transformation() {

    override val cacheKey: String = "${BlurTransformation::class.java.name}-$downsampleSize"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w <= downsampleSize || h <= downsampleSize) return input
        // Preserve aspect ratio.
        val ratio = w.toFloat() / h
        val smallW: Int
        val smallH: Int
        if (ratio >= 1f) {
            smallW = downsampleSize
            smallH = (downsampleSize / ratio).toInt().coerceAtLeast(1)
        } else {
            smallH = downsampleSize
            smallW = (downsampleSize * ratio).toInt().coerceAtLeast(1)
        }
        val small = Bitmap.createScaledBitmap(input, smallW, smallH, true)
        val out = Bitmap.createScaledBitmap(small, w, h, true)
        if (small !== out) small.recycle()
        return out
    }
}
