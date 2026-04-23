package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsPlaybackSnapshot
import com.longerlsx.storyapp.feature.reader.tts.activeFollowCharOffsetOrNull

fun resolvePageFollowTargetCharOffset(
    playbackSnapshot: ReaderTtsPlaybackSnapshot?,
    selectedChapterIndex: Int,
    isFollowSuppressed: Boolean,
): Int? {
    if (isFollowSuppressed) {
        return null
    }
    val currentSegment = playbackSnapshot?.currentSegment ?: return null
    if (currentSegment.chapterIndex != selectedChapterIndex) {
        return null
    }
    return playbackSnapshot.activeFollowCharOffsetOrNull()
}
