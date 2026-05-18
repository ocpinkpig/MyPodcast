package com.example.mypodcast.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object PodcastCardGridDefaults {
    const val ColumnCount = 3
    val ContentPadding = 8.dp
    val Spacing = 8.dp

    private val HorizontalSpace = 32.dp

    fun cardWidthFor(maxWidth: Dp): Dp = (maxWidth - HorizontalSpace) / ColumnCount.toFloat()
}
