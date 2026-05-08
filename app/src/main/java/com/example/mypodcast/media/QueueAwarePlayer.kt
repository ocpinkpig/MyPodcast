package com.example.mypodcast.media

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

class QueueAwarePlayer(
    wrapped: Player,
    private val hasQueueItems: () -> Boolean,
    private val onNext: () -> Unit
) : ForwardingPlayer(wrapped) {

    override fun seekToNextMediaItem() {
        onNext()
    }

    override fun seekToNext() {
        onNext()
    }

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        return if (hasQueueItems()) {
            Player.Commands.Builder()
                .addAll(base)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .build()
        } else {
            Player.Commands.Builder()
                .addAll(base)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .build()
        }
    }

    override fun hasNextMediaItem(): Boolean = hasQueueItems()
}
