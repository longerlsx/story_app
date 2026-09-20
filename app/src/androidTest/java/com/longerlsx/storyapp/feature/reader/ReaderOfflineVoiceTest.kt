package com.longerlsx.storyapp.feature.reader

import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the shipped engine and AudioTrack; host recording supplies the separate output evidence. */
@RunWith(AndroidJUnit4::class)
class ReaderOfflineVoiceTest {
    @Test
    fun bundledVoiceGeneratesAndPlaysNewChineseText(): Unit = runBlocking {
        val application = ApplicationProvider.getApplicationContext<StoryApplication>()
        val voiceName = InstrumentationRegistry.getArguments().getString("voiceName", "kokoro:59")
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
            assertTrue("The selected bundled voice must work without a system voice", voices.any { it.name == voiceName })
            val text = "他推开窗，看见远处的山林。今天是九月二十日，约好的时间是下午三点半。"
            val acceptedAt = SystemClock.elapsedRealtime()
            withContext(Dispatchers.Main) {
                engine.applySettings(ReaderTtsSettings(voiceName = voiceName))
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
            Log.i("ReaderOfflineVoiceTest", "firstPlaybackMs=${playbackStartedAt - acceptedAt} totalMs=${SystemClock.elapsedRealtime() - acceptedAt}")
            Unit
        } finally {
            withContext(Dispatchers.Main) { engine.shutdown() }
        }
    }
}
