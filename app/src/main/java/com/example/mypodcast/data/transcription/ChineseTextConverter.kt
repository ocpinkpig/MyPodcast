package com.example.mypodcast.data.transcription

import com.github.houbb.opencc4j.util.ZhConverterUtil

/**
 * Traditional → Simplified Chinese conversion.
 *
 * The advanced ML Kit recognizer emits Traditional characters even for a
 * Simplified (`zh-CN` / `cmn-Hans-CN`) feed, so we normalize its output to the
 * script the feed declares. Conversion is phrase-aware (via opencc4j) and a
 * no-op for text with no Traditional characters.
 */
internal fun toSimplifiedChinese(text: String): String =
    if (text.isBlank()) text else ZhConverterUtil.toSimple(text)

/** True when [localeTag] denotes Simplified Han (so recognizer output should be simplified). */
internal fun shouldSimplifyForLocale(localeTag: String): Boolean {
    val lower = localeTag.lowercase()
    if (!lower.startsWith("zh") && !lower.startsWith("cmn")) return false
    if (lower.contains("hant")) return false
    // Hans, or a bare zh/cmn-CN style tag, is treated as Simplified.
    return lower.contains("hans") || lower.contains("-cn") || lower == "zh" || lower == "cmn"
}

/**
 * A text transform for recognizer output: simplifies Traditional characters for
 * Simplified-Han locales, and is the identity (same instance) otherwise.
 */
internal fun simplifyTransformForLocale(localeTag: String): (String) -> String =
    if (shouldSimplifyForLocale(localeTag)) ::toSimplifiedChinese else { text -> text }
