package com.example.mypodcast.data.transcription

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class RecognizerLocaleTest {

    private val fallback = Locale.US

    @Test
    fun `null or blank feed language falls back to default`() {
        assertEquals(fallback, recognizerLocale(null, fallback))
        assertEquals(fallback, recognizerLocale("  ", fallback))
    }

    @Test
    fun `simplified chinese variants map to cmn-Hans-CN`() {
        // ML Kit lists Mandarin as cmn-Hans-CN, not zh-CN.
        for (tag in listOf("zh", "zh-cn", "zh-CN", "zh_CN", "zh-Hans", "zh-SG")) {
            assertEquals(
                "mapping $tag",
                Locale.forLanguageTag("cmn-Hans-CN"),
                recognizerLocale(tag, fallback)
            )
        }
    }

    @Test
    fun `traditional chinese variants map to cmn-Hant-TW`() {
        for (tag in listOf("zh-TW", "zh-tw", "zh-Hant", "zh-HK")) {
            assertEquals(
                "mapping $tag",
                Locale.forLanguageTag("cmn-Hant-TW"),
                recognizerLocale(tag, fallback)
            )
        }
    }

    @Test
    fun `regular language tags pass through with underscores normalized`() {
        assertEquals(Locale.forLanguageTag("ja-JP"), recognizerLocale("ja_JP", fallback))
        assertEquals(Locale.forLanguageTag("de-DE"), recognizerLocale("de-DE", fallback))
        assertEquals(Locale.forLanguageTag("en"), recognizerLocale("en", fallback))
    }

    @Test
    fun `garbage tags fall back to default`() {
        assertEquals(fallback, recognizerLocale("!!not-a-language!!", fallback))
    }
}
