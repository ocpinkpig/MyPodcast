package com.example.mypodcast.domain

/**
 * Build/runtime feature toggles. Implementations may be constant (compile-time)
 * or, in the future, backed by a settings store so the user can flip them.
 */
interface FeatureFlags {
    /**
     * On-device transcript generation for downloaded episodes. Off by default
     * while the underlying ML Kit GenAI speech model is alpha-quality; a future
     * Settings item will let the user enable it.
     */
    val onDeviceTranscriptionEnabled: Boolean
}
