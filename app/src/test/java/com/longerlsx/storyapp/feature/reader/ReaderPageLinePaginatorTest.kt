package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

class ReaderPageLinePaginatorTest {

    @Test
    fun distinguishesContinuationPagesFromIndentedNaturalParagraphStartsWithoutChangingSource() {
        val content = "　　甲乙丙丁\n\t戊己"
        val lines = listOf(
            ReaderPageLine(2, 4, 0f, 24f),
            ReaderPageLine(4, 6, 24f, 48f),
            ReaderPageLine(8, 10, 48f, 72f),
        )

        val pages = ReaderPageLinePaginator.paginate(content, lines, availableHeightPx = 24f)

        assertEquals(listOf(false, true, false), pages.map { it.firstParagraphContinues })
        assertEquals(listOf(2, 4, 8), pages.map { it.startCharOffset })
        assertEquals(listOf("甲乙", "丙丁", "戊己"), pages.map { it.rawText })
    }

    @Test
    fun cancellationDuringPageFitStopsBeforeComputingTheRemainingPages() {
        val job = Job()
        val content = "甲乙丙丁戊己庚辛"
        val lines = content.indices.map { index ->
            ReaderPageLine(index, index + 1, index * 24f, (index + 1) * 24f)
        }
        var checkedPages = 0

        assertThrows(CancellationException::class.java) {
            ReaderPageLinePaginator.paginate(
                content = content,
                lines = lines,
                availableHeightPx = 48f,
                pageFitsViewport = {
                    checkedPages += 1
                    job.cancel()
                    true
                },
                cancellationContext = job,
            )
        }
        assertEquals("取消后不应继续计算后面的页，也不能返回部分正文", 1, checkedPages)
    }

    @Test
    fun paginatesByVisibleLinesWithinPageHeight() {
        val content = "第一页第一行\n第一页第二行\n第一页第三行\n第二页第一行\n第二页第二行\n第二页第三行"
        val lines = listOf(
            ReaderPageLine(0, 6, topPx = 0f, bottomPx = 24f),
            ReaderPageLine(7, 13, topPx = 24f, bottomPx = 48f),
            ReaderPageLine(14, 20, topPx = 48f, bottomPx = 72f),
            ReaderPageLine(21, 27, topPx = 72f, bottomPx = 96f),
            ReaderPageLine(28, 34, topPx = 96f, bottomPx = 120f),
            ReaderPageLine(35, content.length, topPx = 120f, bottomPx = 144f),
        )

        val pages = ReaderPageLinePaginator.paginate(
            content = content,
            lines = lines,
            availableHeightPx = 72f,
        )

        assertEquals(2, pages.size)
        assertEquals("第一页第一行\n第一页第二行\n第一页第三行", pages[0].text)
        assertEquals("第二页第一行\n第二页第二行\n第二页第三行", pages[1].text)
        assertEquals(0, pages[0].startCharOffset)
        assertEquals(21, pages[1].startCharOffset)
    }

    @Test
    fun keepsAtLeastOneLinePerPageWhenHeightIsVerySmall() {
        val content = "第一行\n第二行\n第三行"
        val lines = listOf(
            ReaderPageLine(0, 3, topPx = 0f, bottomPx = 24f),
            ReaderPageLine(4, 7, topPx = 24f, bottomPx = 48f),
            ReaderPageLine(8, content.length, topPx = 48f, bottomPx = 72f),
        )

        val pages = ReaderPageLinePaginator.paginate(
            content = content,
            lines = lines,
            availableHeightPx = 10f,
        )

        assertEquals(3, pages.size)
        assertEquals("第一行", pages[0].text)
        assertEquals("第二行", pages[1].text)
        assertEquals("第三行", pages[2].text)
    }

    @Test
    fun keepsRawSliceTextSoStandalonePagesCanReflowNaturally() {
        val content = "ABCDEF123456"
        val lines = listOf(
            ReaderPageLine(0, 3, topPx = 0f, bottomPx = 24f),
            ReaderPageLine(3, 6, topPx = 24f, bottomPx = 48f),
            ReaderPageLine(6, 9, topPx = 48f, bottomPx = 72f),
            ReaderPageLine(9, 12, topPx = 72f, bottomPx = 96f),
        )

        val pages = ReaderPageLinePaginator.paginate(
            content = content,
            lines = lines,
            availableHeightPx = 48f,
        )

        assertEquals(2, pages.size)
        assertEquals("ABCDEF", pages[0].text)
        assertEquals("123456", pages[1].text)
    }

    @Test
    fun backsOffLastVisibleLineWhenCandidatePageDoesNotFitAfterReflow() {
        val content = "第一行\n第二行\n第三行\n第四行"
        val lines = listOf(
            ReaderPageLine(0, 3, topPx = 0f, bottomPx = 24f),
            ReaderPageLine(4, 7, topPx = 24f, bottomPx = 48f),
            ReaderPageLine(8, 11, topPx = 48f, bottomPx = 72f),
            ReaderPageLine(12, content.length, topPx = 72f, bottomPx = 96f),
        )

        val pages = ReaderPageLinePaginator.paginate(
            content = content,
            lines = lines,
            availableHeightPx = 72f,
            pageFitsViewport = { page -> !page.text.contains("第三行") },
        )

        assertEquals(3, pages.size)
        assertEquals("第一行\n第二行", pages[0].text)
        assertEquals("第三行", pages[1].text)
        assertEquals("第四行", pages[2].text)
    }

    @Test
    fun recordsVisibleOffsetsWhenPageStartsWithLeadingNewlines() {
        val content = "\n\n正文第一行\n正文第二行"
        val lines = listOf(
            ReaderPageLine(0, 1, topPx = 0f, bottomPx = 12f),
            ReaderPageLine(1, 2, topPx = 12f, bottomPx = 24f),
            ReaderPageLine(2, 7, topPx = 24f, bottomPx = 48f),
            ReaderPageLine(8, content.length, topPx = 48f, bottomPx = 72f),
        )

        val pages = ReaderPageLinePaginator.paginate(
            content = content,
            lines = lines,
            availableHeightPx = 48f,
        )

        assertEquals(2, pages.first().visibleStartCharOffset)
        assertEquals(content.substring(0, 7), pages.first().rawText)
        assertEquals("正文第一行", pages.first().text)
    }

    @Test
    fun returnsPlaceholderWhenContentIsBlank() {
        val pages = ReaderPageLinePaginator.paginate(
            content = "",
            lines = emptyList(),
            availableHeightPx = 100f,
        )

        assertEquals(1, pages.size)
        assertEquals("当前章节暂无正文。", pages.single().text)
    }
}
