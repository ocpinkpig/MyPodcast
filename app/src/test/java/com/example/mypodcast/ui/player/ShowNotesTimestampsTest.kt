package com.example.mypodcast.ui.player

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class ShowNotesTimestampsTest {

    @Test
    fun detectsMinuteSecond() {
        val text = "Intro 12:34 next"
        val links = findTimestampLinks(text)
        assertEquals(1, links.size)
        val link = links[0]
        assertEquals("12:34", text.substring(link.start, link.endExclusive))
        assertEquals((12 * 60 + 34) * 1000L, link.positionMs)
    }

    @Test
    fun detectsHourMinuteSecond() {
        val text = "Deep dive at 1:02:03 here"
        val links = findTimestampLinks(text)
        assertEquals(1, links.size)
        assertEquals("1:02:03", text.substring(links[0].start, links[0].endExclusive))
        assertEquals((1 * 3600 + 2 * 60 + 3) * 1000L, links[0].positionMs)
    }

    @Test
    fun detectsZeroPaddedHour() {
        val links = findTimestampLinks("01:02:03")
        assertEquals(1, links.size)
        assertEquals((3600 + 2 * 60 + 3) * 1000L, links[0].positionMs)
    }

    @Test
    fun allowsLargeMinutesInMinuteSecondForm() {
        val links = findTimestampLinks("Marathon 90:00 mark")
        assertEquals(1, links.size)
        assertEquals(90 * 60 * 1000L, links[0].positionMs)
    }

    @Test
    fun excludesSurroundingParensAndBrackets() {
        val text = "Topic (12:34) and [1:00] done"
        val links = findTimestampLinks(text)
        assertEquals(2, links.size)
        assertEquals("12:34", text.substring(links[0].start, links[0].endExclusive))
        assertEquals("1:00", text.substring(links[1].start, links[1].endExclusive))
    }

    @Test
    fun rejectsInvalidSeconds() {
        assertTrue(findTimestampLinks("bad 12:99 value").isEmpty())
    }

    @Test
    fun rejectsTimestampGluedToLargerDigits() {
        // The colon is anchored to a longer digit run, so no valid minute field
        // can be isolated without a preceding digit (rejected by the lookbehind).
        assertTrue(findTimestampLinks("1234:56").isEmpty())
        assertTrue(findTimestampLinks("ref 12:345 here").isEmpty())
    }

    @Test
    fun returnsMultipleInOrder() {
        val links = findTimestampLinks("0:30 intro, 5:00 middle, 1:00:00 end")
        assertEquals(3, links.size)
        assertEquals(30 * 1000L, links[0].positionMs)
        assertEquals(5 * 60 * 1000L, links[1].positionMs)
        assertEquals(3600 * 1000L, links[2].positionMs)
    }
}
