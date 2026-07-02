package com.example.mypodcast.data.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseTextConverterTest {

    @Test
    fun `simplifies traditional characters`() {
        // The exact garble advanced mode produced for a zh-CN feed.
        assertEquals("欢迎收听", toSimplifiedChinese("歡迎收聽"))
        assertEquals("行业新变化和创业新机会", toSimplifiedChinese("行業新變化和創業新機會"))
    }

    @Test
    fun `leaves already-simplified text unchanged`() {
        assertEquals("我们关注技术", toSimplifiedChinese("我们关注技术"))
    }

    @Test
    fun `leaves latin and mixed text intact`() {
        assertEquals("AI 与 LAKE FOREST College", toSimplifiedChinese("AI 与 LAKE FOREST College"))
    }

    @Test
    fun `shouldSimplify true only for simplified han locales`() {
        assertTrue(shouldSimplifyForLocale("cmn-Hans-CN"))
        assertTrue(shouldSimplifyForLocale("zh-CN"))
        assertTrue(shouldSimplifyForLocale("zh-Hans"))
        assertFalse(shouldSimplifyForLocale("cmn-Hant-TW"))
        assertFalse(shouldSimplifyForLocale("zh-TW"))
        assertFalse(shouldSimplifyForLocale("en-US"))
        assertFalse(shouldSimplifyForLocale("ja-JP"))
    }

    @Test
    fun `transform is identity for non-simplified locales`() {
        val transform = simplifyTransformForLocale("cmn-Hant-TW")
        val text = "歡迎收聽"
        assertSame(text, transform(text)) // identity returns the same instance
    }

    @Test
    fun `transform simplifies for simplified locale`() {
        val transform = simplifyTransformForLocale("cmn-Hans-CN")
        assertEquals("欢迎收听", transform("歡迎收聽"))
    }
}
