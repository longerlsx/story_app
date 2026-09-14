package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.longerlsx.storyapp.core.model.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPageContentIntegrityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mixedParagraphsAndBlankLinesKeepChapterTailAtLargestFont() {
        val content = buildString {
            append("\n\n　　开篇唯一标记。\n\n")
            repeat(24) { index ->
                append("段落${index.toString().padStart(3, '0')}：")
                if (index % 3 == 0) {
                    repeat(16) { append("风吹过河岸，远处的灯依次亮起。") }
                } else {
                    append("短段正文。")
                }
                append("\n \t\n\n")
            }
            append("　　章尾唯一终点。\n\n")
        }

        assertEveryPagePreservesAndDisplaysContent(content, fontSizeSp = 32, paragraphSpacingEm = 1.8f)
    }

    @Test
    fun veryLongSingleParagraphPreservesEveryCharacterAcrossPages() {
        val content = buildString {
            repeat(1_200) { index ->
                append("连续段${index.toString().padStart(4, '0')}：甲乙丙丁戊己庚辛壬癸。")
            }
            append("章尾唯一终点。")
        }
        assertTrue("The fixture must exercise an unusually long single paragraph", content.length > 20_000)

        assertEveryPagePreservesAndDisplaysContent(content, fontSizeSp = 18, paragraphSpacingEm = 0.9f)
    }

    @Test
    fun manyUnequalParagraphsKeepTheirOrderWithLargeParagraphSpacing() {
        val content = buildString {
            repeat(180) { index ->
                append("\t段${index.toString().padStart(3, '0')}：")
                repeat(if (index % 4 == 0) 18 else 1) {
                    append("正文顺序不能被分页改变，Alpha${index}也必须保留。")
                }
                append(if (index % 2 == 0) "\n\n\n" else "\n")
            }
            append("章尾唯一终点。")
        }

        assertEveryPagePreservesAndDisplaysContent(content, fontSizeSp = 18, paragraphSpacingEm = 1.8f)
    }

    private fun assertEveryPagePreservesAndDisplaysContent(
        content: String,
        fontSizeSp: Int,
        paragraphSpacingEm: Float,
    ) {
        val settings = ReaderSettings(fontSizeSp = fontSizeSp, paragraphSpacingEm = paragraphSpacingEm)
        val palette = ReaderThemeResolver.resolveActivePalette(settings)
        val displayedPage = mutableIntStateOf(0)
        var measuredPages = emptyList<ReaderPageSlice>()
        var horizontalPaddingPx = 0f
        var topPaddingPx = 0f
        var bottomPaddingPx = 0f

        composeRule.setContent {
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val fontSize = fontSizeSp.sp
            val lineHeight = (fontSizeSp * settings.lineHeightMultiplier).sp
            val paragraphSpacing = (fontSizeSp * paragraphSpacingEm).coerceIn(8f, 36f).dp
            val textStyle = TextStyle(
                color = palette.content,
                fontSize = fontSize,
                lineHeight = lineHeight,
                textAlign = TextAlign.Start,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )
            val widthPx = with(density) { (PageWidth - HorizontalPadding * 2).roundToPx() }
            val heightPx = with(density) { (PageHeight - TopPadding - BottomPadding).roundToPx() }
            val spacingPx = with(density) { paragraphSpacing.toPx() }
            val pages = remember(content, widthPx, heightPx, spacingPx, textStyle, textMeasurer) {
                // Call the exact production paginator, including its real page-fit remeasurement.
                paginatePageSlices(content, widthPx, heightPx, spacingPx, textMeasurer, textStyle)
            }
            SideEffect {
                measuredPages = pages
                horizontalPaddingPx = with(density) { HorizontalPadding.toPx() }
                topPaddingPx = with(density) { TopPadding.toPx() }
                bottomPaddingPx = with(density) { BottomPadding.toPx() }
            }
            Box(Modifier.requiredSize(PageWidth, PageHeight).testTag(PageViewportTag)) {
                ReaderPageSurface(
                    page = pages[displayedPage.intValue],
                    themePalette = palette,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    paragraphSpacing = paragraphSpacing,
                    pageTopPadding = TopPadding,
                    pageBottomPadding = BottomPadding,
                    highlightRange = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
        val pages = composeRule.runOnIdle { measuredPages }
        assertTrue("The input must span several actual measured pages", pages.size > 2)
        assertPageRangesPreserveSource(content, pages)

        val renderedCharacters = StringBuilder()
        pages.forEachIndexed { pageIndex, page ->
            composeRule.runOnIdle { displayedPage.intValue = pageIndex }
            composeRule.waitForIdle()
            val viewport = composeRule.onNodeWithTag(PageViewportTag).fetchSemanticsNode().boundsInRoot
            val textNodes = composeRule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().sortedBy { it.positionInRoot.y }
            assertTrue("Page $pageIndex must render actual text nodes", textNodes.isNotEmpty())
            val pageCharacters = StringBuilder()
            composeRule.runOnIdle {
                textNodes.forEach { node ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    val action = node.config[SemanticsActions.GetTextLayoutResult].action
                    assertTrue("Page $pageIndex must expose its actual text layout", action?.invoke(layouts) == true)
                    assertEquals(1, layouts.size)
                    val layout = layouts.single()
                    val text = layout.layoutInput.text.text
                    assertFalse("Page $pageIndex text must not overflow its own layout", layout.hasVisualOverflow)
                    assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = false))
                    repeat(layout.lineCount) { line ->
                        val top = node.positionInRoot.y + layout.getLineTop(line)
                        val bottom = node.positionInRoot.y + layout.getLineBottom(line)
                        val left = node.positionInRoot.x + layout.getLineLeft(line)
                        val right = node.positionInRoot.x + layout.getLineRight(line)
                        assertTrue("Page $pageIndex line $line crosses top padding", top >= viewport.top + topPaddingPx - 1f)
                        assertTrue("Page $pageIndex line $line is clipped at the bottom", bottom <= viewport.bottom - bottomPaddingPx + 1f)
                        assertTrue("Page $pageIndex line $line crosses left padding", left >= viewport.left + horizontalPaddingPx - 1f)
                        assertTrue("Page $pageIndex line $line crosses right padding", right <= viewport.right - horizontalPaddingPx + 1f)
                    }
                    pageCharacters.append(text.filterNot(Char::isWhitespace))
                }
            }
            assertEquals(
                "Page $pageIndex must render its source characters in order, not just an equal count",
                page.rawText.filterNot(Char::isWhitespace),
                pageCharacters.toString(),
            )
            renderedCharacters.append(pageCharacters)
        }
        assertEquals(content.filterNot(Char::isWhitespace), renderedCharacters.toString())
        assertTrue("The last rendered page must retain the chapter ending", renderedCharacters.endsWith("章尾唯一终点。"))
    }

    private fun assertPageRangesPreserveSource(content: String, pages: List<ReaderPageSlice>) {
        var previousEnd = 0
        pages.forEachIndexed { index, page ->
            assertTrue("Page $index must have a nonempty in-range source interval", page.startCharOffset in 0 until page.endCharOffset)
            assertTrue("Page $index ends beyond the source", page.endCharOffset <= content.length)
            assertTrue("Page $index duplicates earlier source content", page.startCharOffset >= previousEnd)
            // Only formatting whitespace may disappear between rendered slices; story characters may not.
            assertTrue(
                "Page $index skips non-whitespace source characters",
                content.substring(previousEnd, page.startCharOffset).all(Char::isWhitespace),
            )
            assertEquals(content.substring(page.startCharOffset, page.endCharOffset), page.rawText)
            assertTrue(page.visibleStartCharOffset in page.startCharOffset..page.endCharOffset)
            assertTrue(page.visibleEndCharOffset in page.visibleStartCharOffset..page.endCharOffset)
            previousEnd = page.endCharOffset
        }
        assertTrue("Pagination drops the chapter ending", content.substring(previousEnd).all(Char::isWhitespace))
    }

    private companion object {
        const val PageViewportTag = "page-content-integrity-viewport"
        val PageWidth = 320.dp
        val PageHeight = 480.dp
        val HorizontalPadding = 24.dp
        val TopPadding = 52.dp
        val BottomPadding = 56.dp
    }
}
