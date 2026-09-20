package com.longerlsx.storyapp.feature.reader.tts

import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderTtsAudioPreparationTest {
    private val next = ReaderTtsSegment(2, 40, 44, "下一句话")
    private val settings = ReaderTtsSettings(voiceName = "male-a", speechRate = 1f)

    @Test
    fun secondUnitIsReadyBeforeTheFirstIsConsumedAndOnlyTwoArePreparedInOrder() = runTest {
        val firstReady = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        val second = next.copy(spokenText = "再下一句话")
        val third = next.copy(spokenText = "还没到这句")
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, _ ->
            started += segment.spokenText
            if (segment.spokenText == next.spokenText) firstReady.await()
            ReaderSpeechAudio(floatArrayOf(if (segment.spokenText == next.spokenText) 0.25f else 0.5f), 24_000)
        }

        preparation.prepareUpcoming(listOf(next, second, third), settings)
        runCurrent()
        assertEquals(listOf("下一句话"), started)
        firstReady.complete(Unit)
        runCurrent()
        assertEquals(listOf("下一句话", "再下一句话"), started)

        assertArrayEquals(floatArrayOf(0.25f), preparation.take(next, settings).samples, 0f)
        assertArrayEquals(floatArrayOf(0.5f), preparation.take(second, settings).samples, 0f)
        assertEquals(listOf("下一句话", "再下一句话"), started)
    }

    @Test
    fun advancingTheWindowKeepsTheSecondOccurrenceEvenWhenTheTextRepeats() = runTest {
        val repeated = next.copy(chapterIndex = 3, startCharOffset = 0, endCharOffset = 4)
        val later = next.copy(chapterIndex = 3, startCharOffset = 4, endCharOffset = 8, spokenText = "后面的句子")
        val generatedAt = mutableListOf<Pair<Int, Int>>()
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, _ ->
            generatedAt += segment.chapterIndex to segment.startCharOffset
            ReaderSpeechAudio(floatArrayOf(generatedAt.size / 4f), 24_000)
        }

        preparation.prepareUpcoming(listOf(next, repeated), settings)
        runCurrent()
        assertEquals(listOf(2 to 40, 3 to 0), generatedAt)
        assertArrayEquals(floatArrayOf(0.25f), preparation.take(next, settings).samples, 0f)

        preparation.prepareUpcoming(listOf(repeated, later), settings)
        runCurrent()

        assertArrayEquals(floatArrayOf(0.5f), preparation.take(repeated, settings).samples, 0f)
        assertArrayEquals(floatArrayOf(0.75f), preparation.take(later, settings).samples, 0f)
        assertEquals(listOf(2 to 40, 3 to 0, 3 to 4), generatedAt)
    }

    @Test
    fun replacingAndClearingFutureUnitsDoesNotCancelAudioAlreadyTaken() = runTest {
        val ready = CompletableDeferred<Unit>()
        var takenCancelled = false
        val started = mutableListOf<String>()
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, _ ->
            started += segment.spokenText
            if (segment.spokenText == next.spokenText) {
                try {
                    ready.await()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    takenCancelled = true
                    throw cancelled
                }
            }
            ReaderSpeechAudio(floatArrayOf(0.25f), 24_000)
        }
        preparation.prepareUpcoming(listOf(next, next.copy(spokenText = "已撤回的后句")), settings)
        runCurrent()
        val consuming = async { preparation.take(next, settings) }
        runCurrent()

        preparation.prepareUpcoming(listOf(next.copy(spokenText = "新的后句")), settings)
        preparation.clear()
        runCurrent()
        assertFalse(takenCancelled)
        assertFalse(consuming.isCompleted)

        ready.complete(Unit)
        assertArrayEquals(floatArrayOf(0.25f), consuming.await().samples, 0f)
        runCurrent()
        assertEquals(listOf("下一句话"), started)
    }

    @Test
    fun matchingPendingAudioIsGeneratedOnceDespiteDifferentLocationPitchAndTimer() = runTest {
        val ready = CompletableDeferred<Unit>()
        var calls = 0
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { _, _ ->
            calls++
            ready.await()
            ReaderSpeechAudio(floatArrayOf(0.25f, -0.5f), 24_000)
        }
        preparation.prepareNext(next, settings)
        preparation.prepareNext(next.copy(chapterIndex = 3, startCharOffset = 0, endCharOffset = 4), settings)
        runCurrent()
        assertEquals(1, calls)

        val consuming = async {
            preparation.take(next, settings.copy(pitch = 0.9f, timerPreset = ReaderTtsTimerPreset.Countdown(15)))
        }
        runCurrent()
        assertFalse(consuming.isCompleted)
        ready.complete(Unit)
        val audio = consuming.await()

        assertArrayEquals(floatArrayOf(0.25f, -0.5f), audio.samples, 0f)
        assertEquals(24_000, audio.sampleRate)
        assertEquals(1, calls)
    }

    @Test
    fun differentVoiceOrRateCancelsOldPreparationAndReturnsTheRequestedSound() = runTest {
        for ((selected, expected) in listOf(
            settings.copy(voiceName = "male-b") to 0.75f,
            settings.copy(speechRate = 1.5f) to 0.125f,
        )) {
            var oldCancelled = false
            val obsoleteStarted = mutableListOf<String>()
            val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, chosen ->
                if (chosen == settings) {
                    obsoleteStarted += segment.spokenText
                    try {
                        awaitCancellation()
                    } finally {
                        oldCancelled = true
                    }
                }
                ReaderSpeechAudio(floatArrayOf(if (chosen.voiceName == "male-b") 0.75f else 0.125f), 24_000)
            }
            preparation.prepareUpcoming(listOf(next, next.copy(spokenText = "旧声音后句")), settings)
            runCurrent()
            val audio = preparation.take(next, selected)
            runCurrent()

            assertTrue(oldCancelled)
            assertEquals(listOf("下一句话"), obsoleteStarted)
            assertArrayEquals(floatArrayOf(expected), audio.samples, 0f)
        }
    }

    @Test
    fun differentTextIsNeverReplacedByAlreadyPreparedAudio() = runTest {
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, _ ->
            ReaderSpeechAudio(floatArrayOf(if (segment.spokenText == "另一句话") 0.5f else -0.5f), 24_000)
        }
        preparation.prepareNext(next, settings)
        runCurrent()

        val audio = preparation.take(next.copy(spokenText = "另一句话"), settings)

        assertArrayEquals(floatArrayOf(0.5f), audio.samples, 0f)
    }

    @Test
    fun replacingOrClearingNextAudioCancelsTheSupersededWork() = runTest {
        val started = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, _ ->
            started += segment.spokenText
            try {
                awaitCancellation()
            } finally {
                cancelled += segment.spokenText
            }
        }
        preparation.prepareNext(next, settings)
        runCurrent()
        preparation.prepareNext(next.copy(spokenText = "替换的话"), settings)
        runCurrent()
        assertEquals(listOf("下一句话", "替换的话"), started)
        assertEquals(listOf("下一句话"), cancelled)

        preparation.clear()
        runCurrent()
        assertEquals(listOf("下一句话", "替换的话"), cancelled)
    }

    @Test
    fun cancellingTheConsumerStopsGenerationEvenAfterItTookThePendingTask() = runTest {
        var cancelled = false
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { _, _ ->
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        preparation.prepareNext(next, settings)
        runCurrent()
        val consuming = launch { preparation.take(next, settings) }
        runCurrent()

        consuming.cancelAndJoin()
        runCurrent()

        assertTrue(cancelled)
    }

    @Test
    fun cancellingTheConsumerAlsoStopsAnUnpreparedCurrentSentence() = runTest {
        var cancelled = false
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { _, _ ->
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val consuming = launch { preparation.take(next, settings) }
        runCurrent()

        consuming.cancelAndJoin()

        assertTrue(cancelled)
    }

    @Test
    fun failedPrefetchIsReportedOnlyWhenThatSoundIsActuallyRequested() = runTest {
        val failure = IllegalStateException("local synthesis failed")
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { _, _ -> throw failure }
        preparation.prepareNext(next, settings)
        runCurrent()

        val result = runCatching { preparation.take(next, settings) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("local synthesis failed", result.exceptionOrNull()?.message)
    }
}
