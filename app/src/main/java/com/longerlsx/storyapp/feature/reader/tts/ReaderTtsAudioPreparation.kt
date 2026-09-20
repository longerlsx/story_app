package com.longerlsx.storyapp.feature.reader.tts

import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ReaderSpeechAudio(val samples: FloatArray, val sampleRate: Int)

internal class ReaderTtsAudioPreparation(
    private val scope: CoroutineScope,
    private val generate: suspend (ReaderTtsSegment, ReaderTtsSettings) -> ReaderSpeechAudio,
) {
    private val lock = Any()
    private var pending = emptyList<PendingAudio>()

    fun prepareUpcoming(segments: List<ReaderTtsSegment>, settings: ReaderTtsSettings) {
        val requested = segments.take(2)
        val keys = requested.map { synthesisKey(it, settings) }
        val (replaced, added) = synchronized(lock) {
            var retained = 0
            while (retained < pending.size && retained < keys.size && pending[retained].key == keys[retained]) {
                retained++
            }
            val next = pending.take(retained).toMutableList()
            val replaced = pending.drop(retained)
            for (index in retained until requested.size) {
                val segment = requested[index]
                val preceding = next.lastOrNull()?.audio
                // LAZY prevents even an immediate dispatcher from generating under the lock.
                val audio = scope.async(start = CoroutineStart.LAZY) {
                    // Finish the first unit before generating the second, without waiting for playback.
                    preceding?.join()
                    currentCoroutineContext().ensureActive()
                    try {
                        Result.success(generate(segment, settings))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        // Preparing a later sentence must not fail the currently playing one.
                        Result.failure(failure)
                    }
                }
                next += PendingAudio(keys[index], audio)
            }
            pending = next
            replaced to next.drop(retained)
        }
        replaced.forEach { it.audio.cancel() }
        added.forEach { it.audio.start() }
    }

    fun prepareNext(segment: ReaderTtsSegment, settings: ReaderTtsSettings) {
        prepareUpcoming(listOf(segment), settings)
    }

    suspend fun take(segment: ReaderTtsSegment, settings: ReaderTtsSettings): ReaderSpeechAudio {
        val key = synthesisKey(segment, settings)
        val (prepared, obsolete) = synchronized(lock) {
            val first = pending.firstOrNull()
            if (first?.key == key) {
                pending = pending.drop(1)
                first to emptyList()
            } else {
                val obsolete = pending
                pending = emptyList()
                null to obsolete
            }
        }
        obsolete.forEach { it.audio.cancel() }
        if (prepared == null) {
            // Current-sentence generation belongs to the caller and inherits its cancellation.
            return generate(segment, settings)
        }
        return try {
            prepared.audio.await().getOrThrow()
        } catch (cancelled: CancellationException) {
            // Ownership passed to this consumer; clear() must not cancel audio already taken.
            prepared.audio.cancel()
            throw cancelled
        }
    }

    fun clear() {
        val previous = synchronized(lock) { pending.also { pending = emptyList() } }
        previous.forEach { it.audio.cancel() }
    }

    private fun synthesisKey(segment: ReaderTtsSegment, settings: ReaderTtsSettings) = SynthesisKey(
        spokenText = segment.spokenText,
        voiceName = settings.voiceName,
        speechRate = settings.speechRate,
    )

    private data class SynthesisKey(val spokenText: String, val voiceName: String?, val speechRate: Float)
    private data class PendingAudio(val key: SynthesisKey, val audio: Deferred<Result<ReaderSpeechAudio>>)
}
