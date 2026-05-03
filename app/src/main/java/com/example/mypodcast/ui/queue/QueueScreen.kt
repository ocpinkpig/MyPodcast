package com.example.mypodcast.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun QueueScreen(viewModel: QueueViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Queue") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
                QueueTab.entries.forEach { tab ->
                    Tab(selected = state.selectedTab == tab, onClick = { viewModel.selectTab(tab) }, text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            when (state.selectedTab) {
                QueueTab.QUEUE -> QueueTabContent(state, viewModel)
                QueueTab.FAVORITES -> EpisodeSimpleList(state.favorites, emptyLabel = "No favorites yet")
                QueueTab.HISTORY -> EpisodeSimpleList(state.history, emptyLabel = "No history yet")
            }
        }
    }
}

@Composable
private fun QueueTabContent(state: QueueUiState, viewModel: QueueViewModel) {
    val current = state.playerState.episode
    val queue = state.playerState.queue
    if (current == null && queue.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Your queue is empty", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        current?.let { episode ->
            item {
                Text("Now Playing", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                QueueRow(title = episode.title, trailing = { Text("Playing", modifier = Modifier.padding(end = 16.dp)) })
                HorizontalDivider()
                Text("Up Next", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
            }
        }
        itemsIndexed(queue, key = { _, it -> it.guid }) { index, episode ->
            QueueRow(
                title = episode.title,
                onPlay = { viewModel.skipToQueueItem(episode.guid) },
                trailing = {
                    IconButton(onClick = { viewModel.moveQueueItem(index, (index - 1).coerceAtLeast(0)) }) { Icon(Icons.Default.ArrowUpward, "Move up") }
                    IconButton(onClick = { viewModel.moveQueueItem(index, (index + 1).coerceAtMost(queue.lastIndex)) }) { Icon(Icons.Default.ArrowDownward, "Move down") }
                    IconButton(onClick = { viewModel.removeFromQueue(episode.guid) }) { Icon(Icons.Default.Delete, "Remove from queue") }
                }
            )
            HorizontalDivider()
        }
        if (queue.isNotEmpty()) item { Button(onClick = viewModel::clearQueue, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Clear queue") } }
    }
}

@Composable
private fun EpisodeSimpleList(items: List<com.example.mypodcast.domain.model.Episode>, emptyLabel: String) {
    if (items.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text(emptyLabel) }
        return
    }
    LazyColumn { itemsIndexed(items, key = { _, it -> it.guid }) { _, episode -> Text(episode.title, modifier = Modifier.fillMaxWidth().padding(16.dp)); HorizontalDivider() } }
}

@Composable
private fun QueueRow(title: String, onPlay: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, modifier = Modifier.weight(1f).padding(end = 12.dp), maxLines = 1)
        if (onPlay != null) Button(onClick = onPlay, modifier = Modifier.padding(end = 8.dp)) { Text("Play") }
        Row { trailing() }
    }
}
