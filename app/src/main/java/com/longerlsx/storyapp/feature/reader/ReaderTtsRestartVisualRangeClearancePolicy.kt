package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsActiveVisualRange

object ReaderTtsRestartVisualRangeClearancePolicy {
    fun shouldClearPendingRange(
        pendingRange: ReaderTtsActiveVisualRange,
        liveRange: ReaderTtsActiveVisualRange?,
        currentBookSession: ReaderTtsCurrentBookSession,
    ): Boolean {
        if (!currentBookSession.isOngoing) {
            return true
        }
        val activeLiveRange = liveRange ?: return false
        return activeLiveRange.chapterIndex == pendingRange.chapterIndex &&
            activeLiveRange.startCharOffset <= pendingRange.startCharOffset &&
            activeLiveRange.endCharOffset >= pendingRange.startCharOffset
    }
}
