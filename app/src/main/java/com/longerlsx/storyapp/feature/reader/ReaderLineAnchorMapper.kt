package com.longerlsx.storyapp.feature.reader

/** Offsets refer to the source text; vertical coordinates come from rendered lines. */
internal object ReaderLineAnchorMapper {
    fun charOffsetAtTop(lines: List<ReaderPageLine>, topPx: Float): Int =
        (lines.firstOrNull { it.bottomPx > topPx } ?: lines.lastOrNull())?.startCharOffset ?: 0

    fun topForCharOffset(lines: List<ReaderPageLine>, charOffset: Int): Float =
        (lines.firstOrNull { it.endCharOffset > charOffset } ?: lines.lastOrNull())?.topPx ?: 0f
}
