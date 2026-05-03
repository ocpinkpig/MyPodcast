package com.example.mypodcast.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypodcast.ui.main.MainScreenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModel: MainScreenViewModel = hiltViewModel()) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val current = playerState.episode
    val queue = playerState.queue

    Scaffold(topBar = { TopAppBar(title = { Text("Queue") }) }) { padding ->
        if (current == null && queue.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Your queue is empty", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            current?.let { episode ->
                item {
                    Text("Now Playing", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                    QueueRow(title = episode.title, trailing = { Text("Playing", modifier = Modifier.padding(end = 16.dp)) })
                    HorizontalDivider()
                    Text("Up Next", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                }
            }
            items(queue, key = { it.guid }) { episode ->
                QueueRow(
                    title = episode.title,
                    onPlay = { viewModel.skipToQueueItem(episode.guid) },
                    trailing = {
                        IconButton(onClick = { viewModel.removeFromQueue(episode.guid) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove from queue")
                        }
                    }
                )
                HorizontalDivider()
            }
            if (queue.isNotEmpty()) {
                item {
                    Button(onClick = viewModel::clearQueue, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Clear queue")
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(title: String, onPlay: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, modifier = Modifier.weight(1f).padding(end = 12.dp), maxLines = 1)
        if (onPlay != null) Button(onClick = onPlay, modifier = Modifier.padding(end = 8.dp)) { Text("Play") }
        trailing()
    }
}
