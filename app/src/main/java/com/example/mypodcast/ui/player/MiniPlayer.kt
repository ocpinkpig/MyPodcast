package com.example.mypodcast.ui.player

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState

@Composable
fun MiniPlayer(
    state: PlayerState,
    onTap: () -> Unit,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val episode = state.episode ?: return
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f

    val backdrop = LocalBackdropLayer.current
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurLayer = rememberGraphicsLayer()
    val blurEffect = remember(supportsBlur) {
        if (supportsBlur) {
            RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        } else null
    }
    blurLayer.renderEffect = blurEffect
    val tintColor = MaterialTheme.colorScheme.surfaceVariant.copy(
        alpha = if (supportsBlur) 0.55f else 0.85f
    )
    var position by remember { mutableStateOf<Offset?>(null) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { position = it.positionInParent() }
            .drawBehind {
                val pos = position
                if (backdrop != null && pos != null) {
                    blurLayer.record(
                        size = IntSize(size.width.toInt(), size.height.toInt())
                    ) {
                        translate(left = -pos.x, top = -pos.y) {
                            drawLayer(backdrop)
                        }
                    }
                    drawLayer(blurLayer)
                }
                drawRect(tintColor)
            },
        shadowElevation = 8.dp,
        color = Color.Transparent
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTap)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = episode.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play"
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MiniPlayerPreview() {
    val sampleEpisode = Episode(
        guid = "preview",
        podcastId = 0L,
        title = "Sample episode title that overflows the available width",
        description = null,
        audioUrl = "",
        artworkUrl = null,
        publishedAt = 0L,
        durationSeconds = 1800,
        fileSizeBytes = 0L
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Color.Red, Color.Blue)))
    ) {
        MiniPlayer(
            state = PlayerState(
                episode = sampleEpisode,
                isPlaying = true,
                positionMs = 600_000L,
                durationMs = 1_800_000L
            ),
            onTap = {},
            onPlayPauseClick = {}
        )
    }
}
