package com.longerlsx.storyapp.feature.reader.tts

data class ReaderTtsCharacterRange(
    val startCharOffset: Int,
    val endCharOffset: Int,
)

data class ReaderTtsActiveVisualRange(
    val chapterIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int,
)

data class ReaderTtsPlaybackSnapshot(
    val currentSegment: ReaderTtsSegment? = null,
    val lastConfirmedSpokenRange: ReaderTtsCharacterRange? = null,
    val nextRecoverableCharOffset: Int? = null,
    val nextRecoverableRange: ReaderTtsCharacterRange? = null,
    val activePauseReason: ReaderTtsPauseReason? = null,
)

fun ReaderTtsPlaybackSnapshot.activeVisualRangeOrNull(): ReaderTtsActiveVisualRange? {
    val segment = currentSegment ?: return null
    val confirmedRange = lastConfirmedSpokenRange
        ?.takeIf { it.endCharOffset > it.startCharOffset }
    val resolvedRange = confirmedRange ?: ReaderTtsCharacterRange(
        startCharOffset = segment.startCharOffset,
        endCharOffset = segment.endCharOffset,
    )
    val clampedStart = resolvedRange.startCharOffset
        .coerceIn(segment.startCharOffset, segment.endCharOffset)
    val clampedEnd = resolvedRange.endCharOffset
        .coerceIn(clampedStart, segment.endCharOffset)
    return ReaderTtsActiveVisualRange(
        chapterIndex = segment.chapterIndex,
        startCharOffset = clampedStart,
        endCharOffset = clampedEnd,
    )
}

fun ReaderTtsPlaybackSnapshot.activeFollowCharOffsetOrNull(): Int? {
    val segment = currentSegment ?: return null
    return nextRecoverableCharOffset
        ?.coerceIn(segment.startCharOffset, segment.endCharOffset)
        ?: lastConfirmedSpokenRange
            ?.startCharOffset
            ?.coerceIn(segment.startCharOffset, segment.endCharOffset)
        ?: segment.startCharOffset
}
