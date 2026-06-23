package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTtsNotificationActionResolverTest {

    @Test
    fun startingAndPlayingSessionsExposePauseAction() {
        assertEquals(
            ReaderTtsNotificationPrimaryAction.PAUSE,
            ReaderTtsNotificationActionResolver.resolvePrimaryAction(ReaderTtsSessionState.STARTING),
        )
        assertEquals(
            ReaderTtsNotificationPrimaryAction.PAUSE,
            ReaderTtsNotificationActionResolver.resolvePrimaryAction(ReaderTtsSessionState.PLAYING),
        )
    }

    @Test
    fun pausedSessionsExposeResumeAction() {
        assertEquals(
            ReaderTtsNotificationPrimaryAction.RESUME,
            ReaderTtsNotificationActionResolver.resolvePrimaryAction(ReaderTtsSessionState.PAUSED_BY_USER),
        )
        assertEquals(
            ReaderTtsNotificationPrimaryAction.RESUME,
            ReaderTtsNotificationActionResolver.resolvePrimaryAction(ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS),
        )
    }

    @Test
    fun nonOngoingSessionsExposePauseFallbackForDefensiveCompatibility() {
        assertEquals(
            ReaderTtsNotificationPrimaryAction.PAUSE,
            ReaderTtsNotificationActionResolver.resolvePrimaryAction(ReaderTtsSessionState.OFF),
        )
        assertEquals(
            ReaderTtsNotificationPrimaryAction.PAUSE,
            ReaderTtsNotificationActionResolver.resolvePrimaryAction(ReaderTtsSessionState.STOPPED_BY_TIMER),
        )
    }
}
