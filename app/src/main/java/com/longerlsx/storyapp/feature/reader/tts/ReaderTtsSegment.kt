package com.longerlsx.storyapp.feature.reader.tts

data class ReaderTtsSegment(
    val chapterIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val spokenText: String,
    val spokenCharSourceRanges: List<ReaderTtsCharacterRange> = emptyList(),
) {
    /** Callback offsets are UTF-16 indices, just like Android and the source chapter. */
    fun sourceRangeForSpokenRange(start: Int, end: Int): ReaderTtsCharacterRange {
        val safeStart = start.coerceIn(0, spokenText.length)
        val safeEnd = end.coerceIn(safeStart, spokenText.length)
        if (spokenCharSourceRanges.size == spokenText.length && spokenCharSourceRanges.isNotEmpty()) {
            val sourceStart = spokenCharSourceRanges.getOrNull(safeStart)?.startCharOffset
                ?: spokenCharSourceRanges.last().endCharOffset
            val sourceEnd = if (safeEnd == safeStart) sourceStart
            else spokenCharSourceRanges[safeEnd - 1].endCharOffset
            return ReaderTtsCharacterRange(sourceStart, sourceEnd)
        }
        return ReaderTtsCharacterRange(
            (startCharOffset + safeStart).coerceIn(startCharOffset, endCharOffset),
            (startCharOffset + safeEnd).coerceIn(startCharOffset, endCharOffset),
        )
    }
}
