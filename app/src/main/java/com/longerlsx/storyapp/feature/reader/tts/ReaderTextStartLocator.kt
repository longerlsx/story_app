package com.longerlsx.storyapp.feature.reader.tts

import com.longerlsx.storyapp.feature.reader.ReaderPageAnchorMapper
import com.longerlsx.storyapp.feature.reader.ReaderPageSlice
import com.longerlsx.storyapp.feature.reader.ReaderScrollFeedAnchorMapper
import com.longerlsx.storyapp.feature.reader.ReaderVisibleChapterItem

data class ReaderTextStartLocation(
    val chapterIndex: Int,
    val charOffset: Int,
)

object ReaderTextStartLocator {
    fun resolveScrollTopLocation(
        visibleItems: List<ReaderVisibleChapterItem>,
        chapterTextByIndex: Map<Int, String>,
        viewportTopPx: Int = 0,
    ): ReaderTextStartLocation? {
        val activeItem = visibleItems
            .sortedBy { it.offsetPx }
            .firstOrNull { item ->
                item.offsetPx + item.sizePx > viewportTopPx
            }
            ?: return null

        val contentLength = chapterTextByIndex[activeItem.chapterIndex]?.length ?: return null
        return ReaderTextStartLocation(
            chapterIndex = activeItem.chapterIndex,
            charOffset = ReaderScrollFeedAnchorMapper.toCharOffset(
                contentLength = contentLength,
                itemOffsetPx = activeItem.offsetPx - viewportTopPx,
                itemHeightPx = activeItem.sizePx,
            ),
        )
    }

    fun resolvePageTopLocation(
        chapterIndex: Int,
        pages: List<ReaderPageSlice>,
        currentPageIndex: Int,
    ): ReaderTextStartLocation {
        return ReaderTextStartLocation(
            chapterIndex = chapterIndex,
            charOffset = ReaderPageAnchorMapper.anchorForPageIndex(
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
        for (index in startOffset..text.lastIndex) {
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
