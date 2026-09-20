package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.ListeningProgress
import com.longerlsx.storyapp.feature.reader.tts.ReaderTextStartLocation
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSessionState

internal object ReaderTtsResumeLocationResolver {
    fun resolve(
        bookId: String,
        runtime: ReaderTtsRuntimeState,
        saved: ListeningProgress?,
    ): ReaderTextStartLocation? {
        if (runtime.currentBookId == bookId) {
            if (runtime.playbackState == ReaderTtsSessionState.STOPPED_AT_BOOK_END) return null
            runtime.playbackSnapshot.currentSegment?.let { segment ->
                return ReaderTextStartLocation(
                    segment.chapterIndex,
                    (runtime.playbackSnapshot.nextRecoverableCharOffset ?: segment.startCharOffset)
                        .coerceIn(segment.startCharOffset, segment.endCharOffset),
                )
            }
            runtime.listeningProgress?.takeIf { it.bookId == bookId && !it.completed }?.let {
                return ReaderTextStartLocation(it.chapterIndex, it.charOffset)
            }
        }
        return saved?.takeIf { it.bookId == bookId && !it.completed }?.let {
            ReaderTextStartLocation(it.chapterIndex, it.charOffset)
        }
    }
}
