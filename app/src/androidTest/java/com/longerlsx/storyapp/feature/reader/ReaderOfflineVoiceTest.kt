package com.longerlsx.storyapp.feature.reader

import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsEngine
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSegment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the shipped engine and AudioTrack; host recording supplies the separate output evidence. */
@RunWith(AndroidJUnit4::class)
class ReaderOfflineVoiceTest {
    @Test
    fun preparedNaturalRateAudioPlaysAtNewDoubleSpeedWithoutResynthesis(): Unit = runBlocking {
        val application = ApplicationProvider.getApplicationContext<StoryApplication>()
        application.setReaderTtsEngineFactoryForTests(null)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val audioManager = application.getSystemService(AudioManager::class.java)
        val text = "他推开窗，看见远处的山林。今天是九月二十日，约好的时间是下午三点半。"
        val firstId = "natural-rate-first"
        val secondId = "prepared-rate-second"
        val starts = listOf(firstId, secondId).associateWith { CompletableDeferred<Long>() }
        val completions = listOf(firstId, secondId).associateWith { CompletableDeferred<Long>() }
        val callback = object : ReaderTtsEngine.Callback {
            override fun onUtteranceStarted(utteranceId: String) {
                starts.getValue(utteranceId).complete(SystemClock.elapsedRealtime())
            }

            override fun onUtteranceRangeStart(utteranceId: String, start: Int, end: Int) = Unit

            override fun onUtteranceCompleted(utteranceId: String) {
                completions.getValue(utteranceId).complete(SystemClock.elapsedRealtime())
            }

            override fun onUtteranceError(utteranceId: String, message: String) {
                starts.getValue(utteranceId).completeExceptionally(AssertionError(message))
                completions.getValue(utteranceId).completeExceptionally(AssertionError(message))
            }
        }
        val marker = "preparedRateChangeBegin=${SystemClock.elapsedRealtime()}"
        fun events(): String {
            val logs = device.executeShellCommand(
                "logcat -d -v brief --pid=${android.os.Process.myPid()} -s OfflineReaderTts:I ReaderOfflineVoiceTest:I",
            )
            assertTrue("The test marker must remain available", logs.contains(marker))
            return logs.substringAfter(marker)
        }
        val engine = withContext(Dispatchers.Main) { application.createReaderTtsEngine(callback) }
        try {
            withTimeout(60_000) { engine.initialize().getOrThrow() }
            Log.i("ReaderOfflineVoiceTest", marker)
            val first = ReaderTtsSegment(0, 0, text.length, text)
            val next = ReaderTtsSegment(0, text.length, text.length * 2, text)
            withContext(Dispatchers.Main) {
                engine.applySettings(ReaderTtsSettings(speechRate = 1f))
                assertTrue(engine.speak(firstId, first))
            }
            val firstStartedAt = withTimeout(60_000) { starts.getValue(firstId).await() }
            withContext(Dispatchers.Main) { engine.prepareNext(next) }
            var firstAudioActive = false
            val firstCompletedAt = withTimeout(60_000) {
                while (!completions.getValue(firstId).isCompleted) {
                    firstAudioActive = firstAudioActive || audioManager.isMusicActive
                    kotlinx.coroutines.delay(20)
                }
                completions.getValue(firstId).await()
            }
            assertTrue("The first sentence must use the actual audio route", firstAudioActive)

            // Await native synthesis completion, not an assumed amount of prefetch time.
            val audioDurations = withTimeout(60_000) {
                var matches = emptyList<MatchResult>()
                while (matches.size < 2) {
                    matches = Regex("synthesisMs=\\d+ audioMs=(\\d+) ").findAll(events()).toList()
                    if (matches.size < 2) kotlinx.coroutines.delay(200)
                }
                matches.map { it.groupValues[1].toLong() }
            }
            assertEquals("Only the playing and prepared sentences should have been synthesized", 2, audioDurations.size)
            val secondAcceptedAt = SystemClock.elapsedRealtime()
            withContext(Dispatchers.Main) {
                engine.applySettings(ReaderTtsSettings(speechRate = 2f))
                assertTrue(engine.speak(secondId, next))
            }
            val secondStartedAt = withTimeout(60_000) { starts.getValue(secondId).await() }
            var secondAudioActive = false
            val secondCompletedAt = withTimeout(60_000) {
                while (!completions.getValue(secondId).isCompleted) {
                    secondAudioActive = secondAudioActive || audioManager.isMusicActive
                    kotlinx.coroutines.delay(20)
                }
                completions.getValue(secondId).await()
            }
            assertTrue("The prepared sentence must still use the actual audio route", secondAudioActive)
            assertEquals("Changing playback speed must consume the prepared PCM without synthesizing it again", 2,
                events().lineSequence().count { it.contains("synthesisStart ") })
            val firstPlaybackMs = firstCompletedAt - firstStartedAt
            val secondPlaybackMs = secondCompletedAt - secondStartedAt
            assertTrue("Both callbacks must span real playback", firstPlaybackMs > 1_000 && secondPlaybackMs > 500)
            // Generative output duration can vary slightly even for identical text. Compare
            // elapsed playback per natural PCM millisecond, instead of assuming identical WAVs.
            val playbackRatio = secondPlaybackMs.toDouble() / firstPlaybackMs
            val normalizedRatio = playbackRatio * audioDurations[0] / audioDurations[1]
            assertTrue("Prepared 2× audio must take approximately half the 1× playback time; ratio=$normalizedRatio",
                normalizedRatio in 0.4..0.6)
            Log.i("ReaderOfflineVoiceTest", "preparedRateChange=passed firstPlaybackMs=$firstPlaybackMs secondPlaybackMs=$secondPlaybackMs " +
                "naturalAudioMs=$audioDurations playbackRatio=$playbackRatio normalizedRatio=$normalizedRatio " +
                "cachedFirstPlaybackMs=${secondStartedAt - secondAcceptedAt} synthesisCount=2")
            Unit
        } finally {
            withContext(Dispatchers.Main) { engine.shutdown() }
        }
    }

    @Test
    fun bundledVoiceGeneratesAndPlaysNewChineseText(): Unit = runBlocking {
        val application = ApplicationProvider.getApplicationContext<StoryApplication>()
        val voiceName = InstrumentationRegistry.getArguments().getString("voiceName", "kokoro:59")
        val speechRate = InstrumentationRegistry.getArguments().getString("speechRate", "1").toFloat()
        application.setReaderTtsEngineFactoryForTests(null)
        val completed = CompletableDeferred<Unit>()
        var playbackStartedAt = 0L
        var wasAudioActive = false
        val audioManager = application.getSystemService(AudioManager::class.java)
        val callback = object : ReaderTtsEngine.Callback {
            override fun onUtteranceStarted(utteranceId: String) {
                playbackStartedAt = SystemClock.elapsedRealtime()
            }

            override fun onUtteranceRangeStart(utteranceId: String, start: Int, end: Int) = Unit

            override fun onUtteranceCompleted(utteranceId: String) {
                completed.complete(Unit)
            }

            override fun onUtteranceError(utteranceId: String, message: String) {
                completed.completeExceptionally(AssertionError(message))
            }
        }
        val engine = withContext(Dispatchers.Main) { application.createReaderTtsEngine(callback) }
        try {
            val voices = withTimeout(60_000) { engine.initialize().getOrThrow() }
            assertEquals("Only the bundled reference voice is selectable", listOf("zipvoice:leijun"), voices.map { it.name })
            // The old persisted Kokoro value must still synthesize through the new single voice.
            val text = InstrumentationRegistry.getArguments().getString("text")
                ?: "他推开窗，看见远处的山林。今天是九月二十日，约好的时间是下午三点半。"
            val acceptedAt = SystemClock.elapsedRealtime()
            val processCpuStartedAt = android.os.Process.getElapsedCpuTime()
            withContext(Dispatchers.Main) {
                engine.applySettings(ReaderTtsSettings(voiceName = voiceName, speechRate = speechRate))
                assertTrue(engine.speak("offline-real-output", ReaderTtsSegment(0, 0, text.length, text)))
            }
            withTimeout(60_000) {
                while (!completed.isCompleted) {
                    wasAudioActive = wasAudioActive || audioManager.isMusicActive
                    kotlinx.coroutines.delay(20)
                }
                completed.await()
            }
            assertTrue("Real audio route must become active", wasAudioActive)
            assertTrue("Completion must follow actual playback, not just generation", playbackStartedAt > 0 && SystemClock.elapsedRealtime() - playbackStartedAt > 1_000)
            // Includes all app threads during synthesis and playback; not a power measurement.
            Log.i("ReaderOfflineVoiceTest", "rate=$speechRate voice=$voiceName firstPlaybackMs=${playbackStartedAt - acceptedAt} playbackMs=${SystemClock.elapsedRealtime() - playbackStartedAt} totalMs=${SystemClock.elapsedRealtime() - acceptedAt} processCpuMs=${android.os.Process.getElapsedCpuTime() - processCpuStartedAt}")
            Unit
        } finally {
            withContext(Dispatchers.Main) { engine.shutdown() }
        }
    }
}
