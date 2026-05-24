package com.example.mypodcast.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Shared GraphicsLayer that records the Scaffold body so the MiniPlayer
 * can sample it for a backdrop blur. `null` when no recorder is in scope.
 */
val LocalBackdropLayer = staticCompositionLocalOf<GraphicsLayer?> { null }

/**
 * Height the MiniPlayer occupies at the bottom of the content area.
 * Scrollable screens add this to their bottom contentPadding so the
 * last item is reachable when an episode is loaded.
 */
val LocalMiniPlayerInset = compositionLocalOf { 0.dp }

/**
 * Wraps content in a GraphicsLayer recorder. Each frame, the content
 * is recorded into [LocalBackdropLayer] and drawn crisply in place.
 * The MiniPlayer reads the layer and redraws (a clipped portion of) it
 * with a RenderEffect blur applied.
 */
@Composable
fun BackdropRecorder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val layer = rememberGraphicsLayer()
    CompositionLocalProvider(LocalBackdropLayer provides layer) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
        ) {
            content()
        }
    }
}
