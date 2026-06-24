package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsActiveVisualRange
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import com.longerlsx.storyapp.feature.reader.tts.activeVisualRangeOrNull

object ReaderTtsActiveVisualRangeResolver {
    fun resolveLiveRange(
        runtimeState: ReaderTtsRuntimeState,
        currentBookSession: ReaderTtsCurrentBookSession,
    ): ReaderTtsActiveVisualRange? {
        if (!currentBookSession.isSpeaking) {
            return null
        }
        return runtimeState.playbackSnapshot.activeVisualRangeOrNull()
    }

    fun resolveActiveRange(
        pendingRange: ReaderTtsActiveVisualRange?,
        liveRange: ReaderTtsActiveVisualRange?,
    ): ReaderTtsActiveVisualRange? {
        return pendingRange ?: liveRange
    }
}
