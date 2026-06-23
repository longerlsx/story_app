package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsVoiceOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTtsVoiceSelectionResolverTest {

    @Test
    fun selectsPersistedVoiceWhenItIsAvailable() {
        val state = ReaderTtsVoiceSelectionResolver.resolve(
            persistedVoiceName = "voice-b",
            availableVoices = listOf(
                ReaderTtsVoiceOption(name = "voice-a", displayName = "系统女声"),
                ReaderTtsVoiceOption(name = "voice-b", displayName = "机械男声"),
            ),
            availableVoicesLoaded = true,
        )

        assertEquals("voice-b", state.selectedVoiceName)
        assertNull(state.systemDefaultVoiceStatus)
    }

    @Test
    fun showsSystemDefaultStatusWhenPersistedVoiceIsMissingAfterCatalogLoaded() {
        val state = ReaderTtsVoiceSelectionResolver.resolve(
            persistedVoiceName = "voice-c",
            availableVoices = listOf(
                ReaderTtsVoiceOption(name = "voice-a", displayName = "系统女声"),
            ),
            availableVoicesLoaded = true,
        )

        assertNull(state.selectedVoiceName)
        assertEquals("当前使用系统默认音色", state.systemDefaultVoiceStatus)
    }

    @Test
    fun suppressesSystemDefaultStatusBeforeCatalogHasLoaded() {
        val state = ReaderTtsVoiceSelectionResolver.resolve(
            persistedVoiceName = "voice-c",
            availableVoices = emptyList(),
            availableVoicesLoaded = false,
        )

        assertNull(state.selectedVoiceName)
        assertNull(state.systemDefaultVoiceStatus)
    }

    @Test
    fun keepsSystemDefaultStatusHiddenWhenNoVoiceWasPersisted() {
        val state = ReaderTtsVoiceSelectionResolver.resolve(
            persistedVoiceName = null,
            availableVoices = emptyList(),
            availableVoicesLoaded = true,
        )

        assertNull(state.selectedVoiceName)
        assertNull(state.systemDefaultVoiceStatus)
    }
}
