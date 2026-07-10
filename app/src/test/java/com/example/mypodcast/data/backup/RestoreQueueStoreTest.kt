package com.example.mypodcast.data.backup

import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestoreQueueStoreTest {
    private lateinit var store: RestoreQueueStore

    @Before
    fun setUp() {
        store = RestoreQueueStore(RuntimeEnvironment.getApplication())
        store.clear()
    }

    @Test
    fun replace_thenSnapshot_returnsQueue() {
        store.replace(listOf("a", "b", "c"))

        val queue = store.snapshot()

        assertEquals(3, queue.total)
        assertEquals(listOf("a", "b", "c"), queue.pending)
    }

    @Test
    fun remove_keepsTotalButShrinksPending() {
        store.replace(listOf("a", "b"))

        store.remove("a")

        val queue = store.snapshot()
        assertEquals(2, queue.total)
        assertEquals(listOf("b"), queue.pending)
    }

    @Test
    fun snapshot_isEmptyWhenNothingStored() {
        val queue = store.snapshot()

        assertEquals(0, queue.total)
        assertEquals(emptyList<String>(), queue.pending)
    }

    @Test
    fun snapshot_toleratesCorruptFile() {
        File(RuntimeEnvironment.getApplication().filesDir, "restore_downloads.json")
            .writeText("{{{corrupt")

        val queue = store.snapshot()

        assertEquals(0, queue.total)
        assertEquals(emptyList<String>(), queue.pending)
    }

    @Test
    fun clear_removesQueue() {
        store.replace(listOf("a"))

        store.clear()

        assertEquals(0, store.snapshot().total)
    }
}
