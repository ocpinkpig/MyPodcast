package com.example.mypodcast.ui.main

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val LibraryShelfIcon: ImageVector
    get() {
        if (_libraryShelfIcon != null) return _libraryShelfIcon!!

        _libraryShelfIcon = ImageVector.Builder(
            name = "LibraryShelf",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.5f, 19.5f)
                lineTo(20.5f, 19.5f)

                moveTo(5.2f, 6.2f)
                lineTo(8.8f, 6.2f)
                lineTo(8.8f, 18.8f)
                lineTo(5.2f, 18.8f)
                close()

                moveTo(10.4f, 5.4f)
                lineTo(13.8f, 4.7f)
                lineTo(16.4f, 18.3f)
                lineTo(13.0f, 19.0f)
                close()

                moveTo(16.8f, 7.2f)
                lineTo(19.6f, 7.2f)
                lineTo(19.6f, 18.8f)
                lineTo(16.8f, 18.8f)
                close()

                moveTo(6.45f, 8.8f)
                lineTo(7.55f, 8.8f)

                moveTo(6.45f, 16.2f)
                lineTo(7.55f, 16.2f)

                moveTo(12.2f, 8.0f)
                lineTo(14.1f, 17.3f)
            }
        }.build()

        return _libraryShelfIcon!!
    }

private var _libraryShelfIcon: ImageVector? = null
