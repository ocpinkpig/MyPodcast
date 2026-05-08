package com.example.mypodcast.media

import androidx.media3.common.Player
import androidx.media3.test.utils.StubPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test
    fun `availableCommands omits next when queue empty`() {
        val player = QueueAwarePlayer(
            wrapped = FakeUnderlying(),
            hasQueueItems = { false },
            onNext = { }
        )

        val commands = player.availableCommands

        assertTrue(!commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(!commands.contains(Player.COMMAND_SEEK_TO_NEXT))
    }

    @Test
    fun `availableCommands includes next when queue non-empty`() {
        val player = QueueAwarePlayer(
            wrapped = FakeUnderlying(),
            hasQueueItems = { true },
            onNext = { }
        )

        val commands = player.availableCommands

        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT))
    }
}
