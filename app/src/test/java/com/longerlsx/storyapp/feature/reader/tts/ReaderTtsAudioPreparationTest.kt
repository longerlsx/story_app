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
    fun identicalDigitsFromAModelAndAQuantityNeverReuseDifferentReadings() = runTest {
        val modelTail = ReaderTtsSegmenter.segment(0, "X-100", startCharOffset = 2).single()
        val quantity = ReaderTtsSegmenter.segment(1, "100").single()
        assertEquals("100", modelTail.spokenText)
        assertEquals("100", quantity.spokenText)
        val readings = mutableListOf<String>()
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, _ ->
            val reading = ReaderTtsChineseNumberNormalizer.normalize(segment)
            readings += reading
            ReaderSpeechAudio(floatArrayOf(if (reading == "一零零") 1f else 2f), 22_050)
        }
        preparation.prepareNext(modelTail, settings)
        runCurrent()
        assertEquals(2f, preparation.take(quantity, settings).samples.single(), 0f)
        assertEquals(listOf("一零零", "一百"), readings)
    }

    @Test
    fun shortInitialAudioWaitsForLongNextAudioWithoutConsumingOrGeneratingItTwice() = runTest {
        val first = next.copy(spokenText = "天亮了。")
        val following = next.copy(chapterIndex = 3, spokenText = "他推开窗，看见远处的山林和清晨的薄雾。")
        val followingReady = CompletableDeferred<Unit>()
        val generated = mutableListOf<String>()
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, _ ->
            generated += segment.spokenText
            if (segment == following) followingReady.await()
            ReaderSpeechAudio(FloatArray(if (segment == first) 950 else 8_000), 1_000)
        }
        val starting = async { preparation.takeForPlayback(first, settings, 1f) { following } }
        runCurrent()
        assertFalse("A sub-second head must not play before its much longer successor is ready", starting.isCompleted)
        assertEquals(listOf(first.spokenText, following.spokenText), generated)

        followingReady.complete(Unit)
        runCurrent()
        assertEquals(950, starting.await().samples.size)
        assertEquals(8_000, preparation.take(following, settings).samples.size)
        assertEquals("Waiting must leave the following PCM available for its normal consumer", 2, generated.size)
    }

    @Test
    fun ordinaryInitialAudioDoesNotEvenLoadTheFollowingChapter() = runTest {
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { _, _ ->
            ReaderSpeechAudio(FloatArray(8_000), 1_000)
        }
        val audio = preparation.takeForPlayback(next, settings, 2f) {
            error("A four-second playback window must not request extra chapter IO")
        }
        assertEquals(8_000, audio.samples.size)
    }

    @Test
    fun startupBufferKeepsAnAlreadyMatchingTwoUnitWindow() = runTest {
        val following = next.copy(spokenText = "这是一句比开头长得多的后续正文。")
        val later = next.copy(spokenText = "再往后的下一段。")
        val followingReady = CompletableDeferred<Unit>()
        val generated = mutableListOf<String>()
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, _ ->
            generated += segment.spokenText
            if (segment == following) followingReady.await()
            ReaderSpeechAudio(FloatArray(950), 1_000)
        }
        val starting = async {
            preparation.takeForPlayback(next, settings, 1f) {
                preparation.prepareUpcoming(listOf(following, later), settings)
                following
            }
        }
        runCurrent()
        followingReady.complete(Unit)
        runCurrent()
        starting.await()

        assertEquals(listOf(next.spokenText, following.spokenText, later.spokenText), generated)
        preparation.take(following, settings)
        preparation.take(later, settings)
        assertEquals(3, generated.size)
    }

    @Test
    fun startupBufferUsesPlaybackRateAndOnlyWaitsForAMuchLongerSuccessor() = runTest {
        val following = next.copy(spokenText = "这是一句比开头长得多的后续正文。")
        val ready = CompletableDeferred<Unit>()
        val generated = mutableListOf<String>()
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, _ ->
            generated += segment.spokenText
            if (segment == following) ready.await()
            ReaderSpeechAudio(FloatArray(3_000), 1_000)
        }
        val faster = async { preparation.takeForPlayback(next, settings, 2f) { following } }
        runCurrent()
        assertFalse("Three seconds of PCM only gives 1.5 seconds of lead at 2×", faster.isCompleted)
        ready.complete(Unit)
        faster.await()
        preparation.clear()

        val similar = next.copy(spokenText = "再读下一句。")
        preparation.takeForPlayback(next, settings, 2f) { similar }
        assertFalse("Similar short sentences must retain immediate startup", similar.spokenText in generated)
    }

    @Test
    fun stoppingDuringStartupBufferCancelsPendingAudioAndNeverReleasesTheHead() = runTest {
        val following = next.copy(spokenText = "这是一句比开头长得多的后续正文。")
        var followingCancelled = false
        var headReleased = false
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, _ ->
            if (segment == following) {
                try { awaitCancellation() } finally { followingCancelled = true }
            }
            ReaderSpeechAudio(FloatArray(950), 1_000)
        }
        val starting = launch {
            preparation.takeForPlayback(next, settings, 1f) { following }
            headReleased = true
        }
        runCurrent()
        starting.cancelAndJoin()
        preparation.clear()
        runCurrent()

        assertTrue(followingCancelled)
        assertFalse(headReleased)
    }

    @Test
    fun failedStartupPrefetchStillPlaysTheHeadAndReportsFailureWhenFollowingAudioIsTaken() = runTest {
        val following = next.copy(spokenText = "这是一句比开头长得多的后续正文。")
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, _ ->
            if (segment == following) error("following synthesis failed")
            ReaderSpeechAudio(FloatArray(950), 1_000)
        }
        assertEquals(950, preparation.takeForPlayback(next, settings, 1f) { following }.samples.size)
        assertEquals("following synthesis failed", runCatching { preparation.take(following, settings) }.exceptionOrNull()?.message)
    }

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
            settings.copy(speechRate = 2f) to 0.125f,
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
    fun doubleSpeedNeverUsesReadyNormalSpeedAudioAcrossTheChapterBoundary() = runTest {
        val nextChapter = ReaderTtsSegment(3, 0, 4, "新的章节")
        val generated = mutableListOf<Pair<Int, Float>>()
        val preparation = ReaderTtsAudioPreparation(backgroundScope) { segment, selected ->
            generated += segment.chapterIndex to selected.speechRate
            ReaderSpeechAudio(floatArrayOf(selected.speechRate / 4f), 24_000)
        }
        preparation.prepareUpcoming(listOf(next, nextChapter), settings)
        runCurrent()
        assertEquals(listOf(2 to 1f, 3 to 1f), generated)

        val faster = settings.copy(speechRate = 2f)
        assertArrayEquals(floatArrayOf(0.5f), preparation.take(next, faster).samples, 0f)
        preparation.prepareUpcoming(listOf(nextChapter), faster)
        runCurrent()
        assertArrayEquals(floatArrayOf(0.5f), preparation.take(nextChapter, faster).samples, 0f)
        assertEquals(listOf(2 to 1f, 3 to 1f, 2 to 2f, 3 to 2f), generated)
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
