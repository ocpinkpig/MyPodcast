package com.example.mypodcast.data.transcription

import java.util.Locale

/**
 * Maps an RSS feed `<language>` value to the recognizer locale, falling back
 * to [fallback] (typically the device locale) when absent or unparseable.
 *
 * Chinese needs special handling: feeds use BCP-47 `zh` variants, but ML Kit
 * lists Mandarin as `cmn-Hans-CN` / `cmn-Hant-TW`.
 */
internal fun recognizerLocale(feedLanguage: String?, fallback: Locale): Locale {
    val tag = feedLanguage?.trim()?.replace('_', '-')?.takeIf { it.isNotEmpty() }
        ?: return fallback

    val lower = tag.lowercase(Locale.ROOT)
    if (lower == "zh" || lower.startsWith("zh-")) {
        val traditional = lower.contains("hant") ||
            lower.endsWith("-tw") || lower.contains("-tw-") ||
            lower.endsWith("-hk") || lower.contains("-hk-") ||
            lower.endsWith("-mo") || lower.contains("-mo-")
        // Verified on device: AICore accepts cmn-Hans-CN / cmn-Hant-TW in both
        // basic and advanced modes, while plain zh tags are rejected by basic
        // mode. Use the cmn forms ML Kit's docs list.
        return if (traditional) {
            Locale.forLanguageTag("cmn-Hant-TW")
        } else {
            Locale.forLanguageTag("cmn-Hans-CN")
        }
    }

    val locale = Locale.forLanguageTag(tag)
    // forLanguageTag returns an empty ("und") locale for garbage input.
    return if (locale.language.isEmpty() || locale.language == "und") fallback else locale
}
