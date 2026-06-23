package com.longerlsx.storyapp.feature.reader.tts

import com.longerlsx.storyapp.feature.reader.ReaderPageAnchorMapper
import com.longerlsx.storyapp.feature.reader.ReaderPageSlice
import com.longerlsx.storyapp.feature.reader.ReaderScrollFeedAnchorMapper
import com.longerlsx.storyapp.feature.reader.ReaderVisibleChapterItem

data class ReaderTextStartLocation(
    val chapterIndex: Int,
    val charOffset: Int,
)

private const val RESTART_VISUAL_RANGE_CHAR_COUNT = 8

internal fun ReaderTextStartLocation.restartVisualRangeOrNull(
    chapterText: String,
): ReaderTtsActiveVisualRange? {
    if (chapterText.isEmpty()) {
        return null
    }
    val startCharOffset = charOffset.coerceIn(0, chapterText.lastIndex)
    val endCharOffset = (startCharOffset + RESTART_VISUAL_RANGE_CHAR_COUNT)
        .coerceAtMost(chapterText.length)
        .coerceAtLeast(startCharOffset + 1)
    return ReaderTtsActiveVisualRange(
        chapterIndex = chapterIndex,
        startCharOffset = startCharOffset,
        endCharOffset = endCharOffset,
    )
}

object ReaderTextStartLocator {
    fun resolveScrollTopLocation(
        visibleItems: List<ReaderVisibleChapterItem>,
        chapterTextByIndex: Map<Int, String>,
        viewportTopPx: Int = 0,
    ): ReaderTextStartLocation? {
        val activeItem = visibleItems
            .sortedBy { it.bodyOffsetPx }
            .firstOrNull { item ->
                item.bodyOffsetPx + item.bodyHeightPx > viewportTopPx
            }
            ?: return null

        val contentLength = chapterTextByIndex[activeItem.chapterIndex]?.length ?: return null
        return ReaderTextStartLocation(
            chapterIndex = activeItem.chapterIndex,
            charOffset = ReaderScrollFeedAnchorMapper.toCharOffset(
                contentLength = contentLength,
                bodyOffsetPx = activeItem.bodyOffsetPx - viewportTopPx,
                bodyHeightPx = activeItem.bodyHeightPx,
            ),
        )
    }

    fun resolvePageTopLocation(
        chapterIndex: Int,
        pages: List<ReaderPageSlice>,
        currentPageIndex: Int,
    ): ReaderTextStartLocation {
        val page = pages.getOrNull(currentPageIndex.coerceIn(0, pages.lastIndex))
        return ReaderTextStartLocation(
            chapterIndex = chapterIndex,
            charOffset = page?.visibleStartCharOffset
                ?: ReaderPageAnchorMapper.anchorForPageIndex(
                    pages = pages,
                    pageIndex = currentPageIndex,
                ),
        )
    }

    fun resolveRestartLocation(
        chapterIndex: Int,
        text: String,
        pressedCharOffset: Int,
        nonBodyRanges: List<IntRange>,
    ): ReaderTextStartLocation? {
        if (text.isEmpty()) {
            return null
        }

        val startOffset = pressedCharOffset.coerceIn(0, text.lastIndex)
        val paragraphStart = text.lastIndexOf('\n', startOffset)
            .let { if (it < 0) 0 else it + 1 }
        val paragraphEndExclusive = text.indexOf('\n', startOffset)
            .let { if (it < 0) text.length else it }

        for (index in startOffset.coerceAtMost(paragraphEndExclusive - 1) downTo paragraphStart) {
            if (nonBodyRanges.any { index in it }) {
                continue
            }
            if (text[index].isReadableBodyCharacter()) {
                return ReaderTextStartLocation(
                    chapterIndex = chapterIndex,
                    charOffset = index,
                )
            }
        }

        for (index in startOffset until paragraphEndExclusive) {
            if (nonBodyRanges.any { index in it }) {
                continue
            }
            if (text[index].isReadableBodyCharacter()) {
                return ReaderTextStartLocation(
                    chapterIndex = chapterIndex,
                    charOffset = index,
                )
            }
        }
        for (index in paragraphEndExclusive..text.lastIndex) {
            if (nonBodyRanges.any { index in it }) {
                continue
            }
            if (text[index].isReadableBodyCharacter()) {
                return ReaderTextStartLocation(
                    chapterIndex = chapterIndex,
                    charOffset = index,
                )
            }
        }
        return null
    }

    private fun Char.isReadableBodyCharacter(): Boolean {
        if (isWhitespace()) {
            return false
        }
        return !PUNCTUATION_CHARS.contains(this)
    }

    private val PUNCTUATION_CHARS = setOf(
        '，',
        '。',
        '！',
        '？',
        '、',
        '；',
        '：',
        '（',
        '）',
        '【',
        '】',
        '《',
        '》',
        '〈',
        '〉',
        '“',
        '”',
        '‘',
        '’',
        '—',
        '…',
        ',',
        '.',
        '!',
        '?',
        ';',
        ':',
        '(',
        ')',
        '[',
        ']',
        '"',
        '\'',
        '-',
    )
}
