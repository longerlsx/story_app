package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettingsTabResolverTest {

    @Test
    fun defaultsToTtsTabWhenCurrentBookHasOngoingPlayback() {
        assertEquals(
            ReaderSettingsTab.TTS,
            ReaderSettingsTabResolver.resolveOnOpen(
                currentTab = null,
                isCurrentBookTtsOngoing = true,
            ),
        )
    }

    @Test
    fun defaultsToReadingTabWhenCurrentBookIsNotSpeaking() {
        assertEquals(
            ReaderSettingsTab.READING,
            ReaderSettingsTabResolver.resolveOnOpen(
                currentTab = null,
                isCurrentBookTtsOngoing = false,
            ),
        )
    }

    @Test
    fun keepsExistingTabAfterUserHasSelectedOne() {
        assertEquals(
            ReaderSettingsTab.READING,
            ReaderSettingsTabResolver.resolveOnOpen(
                currentTab = ReaderSettingsTab.READING,
                isCurrentBookTtsOngoing = true,
            ),
        )
        assertEquals(
            ReaderSettingsTab.TTS,
            ReaderSettingsTabResolver.resolveOnOpen(
                currentTab = ReaderSettingsTab.TTS,
                isCurrentBookTtsOngoing = false,
            ),
        )
    }
}
