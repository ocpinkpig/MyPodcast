package com.example.mypodcast.data.config

import com.example.mypodcast.domain.FeatureFlags
import javax.inject.Inject

/**
 * Compile-time feature flags. Replace the Hilt binding with a settings-backed
 * implementation to make these user-toggleable.
 */
class DefaultFeatureFlags @Inject constructor() : FeatureFlags {
    override val onDeviceTranscriptionEnabled: Boolean = ON_DEVICE_TRANSCRIPTION_ENABLED

    private companion object {
        // Disabled: the alpha on-device speech model is not yet good enough
        // (esp. for non-English). Flip to true, or replace the binding with a
        // settings-backed impl, to re-enable.
        const val ON_DEVICE_TRANSCRIPTION_ENABLED = false
    }
}
