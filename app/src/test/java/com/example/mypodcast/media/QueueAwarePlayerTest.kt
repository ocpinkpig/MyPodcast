package com.example.mypodcast.media

import androidx.media3.common.Player
import androidx.media3.test.utils.StubPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueAwarePlayerTest {

    private class FakeUnderlying : StubPlayer() {
        var seekToCalls = 0
        override fun seekTo(
            mediaItemIndex: Int,
            positionMs: Long,
            seekCommand: Int,
            isRepeatingCurrentItem: Boolean
        ) {
            seekToCalls++
        }

        override fun getAvailableCommands(): Player.Commands =
            Player.Commands.Builder().addAllCommands().build()
    }

    @Test
    fun `seekToNextMediaItem invokes onNext callback instead of underlying`() {
        var nextInvocations = 0
        val underlying = FakeUnderlying()
        val player = QueueAwarePlayer(
            wrapped = underlying,
            hasQueueItems = { true },
            onNext = { nextInvocations++ }
        )

        player.seekToNextMediaItem()

        assertEquals(1, nextInvocations)
        assertEquals(0, underlying.seekToCalls)
    }
}
