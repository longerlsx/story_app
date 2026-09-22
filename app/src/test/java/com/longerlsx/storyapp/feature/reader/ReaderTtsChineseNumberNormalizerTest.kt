package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsChineseNumberNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies speech-only number forms without rewriting the stored novel or its offsets. */
class ReaderTtsChineseNumberNormalizerTest {
    @Test
    fun chineseQuantitiesAndChapterNumbersPreserveTheirSurroundingText() {
        assertEquals(
            "“在四十八小时内读完第二章。”他带了一千零五元。\n剩余零人。",
            ReaderTtsChineseNumberNormalizer.normalize("“在48小时内读完第2章。”他带了1005元。\n剩余0人。"),
        )
    }

    @Test
    fun decimalPercentAndYearHaveTheirOwnChineseReadingForms() {
        val examples = listOf(
            "重量3.5千克，概率5%，增加0.25%。" to "重量三点五千克，概率百分之五，增加百分之零点二五。",
            "2026年9月22日，回忆2010年。" to "二零二六年九月二十二日，回忆二零一零年。",
        )
        examples.forEach { (text, expected) ->
            assertEquals(text, expected, ReaderTtsChineseNumberNormalizer.normalize(text))
        }
    }

    @Test
    fun validClockTimesReadAsHoursAndMinutesWhileInvalidOnesStayUntouched() {
        assertEquals(
            "现在二十三点五十九分，凌晨零点零五分，会议十点整开始；错误时刻24:70保持原样。",
            ReaderTtsChineseNumberNormalizer.normalize("现在23:59，凌晨00:05，会议10：00开始；错误时刻24:70保持原样。"),
        )
    }

    @Test
    fun leadingZeroAndOverflowRunsReadDigitByDigitWithoutLosingDigits() {
        assertEquals(
            "读到零零零七，随后是九二二三三七二零三六八五四七七五八零八。",
            ReaderTtsChineseNumberNormalizer.normalize("读到0007，随后是9223372036854775808。"),
        )
    }

    @Test
    fun fourDigitGroupBoundariesKeepNecessaryZerosAndTens() {
        val examples = listOf(
            "共有10000人。" to "共有一万人。",
            "共有10001人。" to "共有一万零一人。",
            "共有10010人。" to "共有一万零一十人。",
            "共有100000001人。" to "共有一亿零一人。",
            "共有100001000人。" to "共有一亿零一千人。",
        )
        examples.forEach { (text, expected) ->
            assertEquals(text, expected, ReaderTtsChineseNumberNormalizer.normalize(text))
        }
    }

    @Test
    fun englishAndMixedLetterNumberTokensAreNotReinterpretedAsChineseQuantities() {
        val examples = listOf(
            "At 23:59, wait 48 hours. Chance 5%, version 3.5.",
            "A48型设备显示v3.5，速度2x。",
            "山风吹过石桥，天色渐渐暗了。",
        )
        examples.forEach { text ->
            assertEquals(text, text, ReaderTtsChineseNumberNormalizer.normalize(text))
        }
    }
}
