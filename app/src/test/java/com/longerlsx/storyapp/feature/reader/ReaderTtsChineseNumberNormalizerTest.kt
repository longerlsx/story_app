package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsChineseNumberNormalizer
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsCharacterRange
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsSegmenter
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
    fun validClockTimesReadAsHoursAndMinutesWhileInvalidOnesKeepTheirSeparatorMeaning() {
        assertEquals(
            "现在二十三点五十九分，凌晨零点零五分，会议十点整开始；错误时刻二四冒号七零保持原样。",
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
    fun mixedLetterNumberTokensReadIdentifierDigitsAndLeaveLettersForTheLetterFrontend() {
        val examples = listOf(
            "At 23:59, wait 48 hours. Chance 5%, version 3.5." to
                "At 二十三点五十九分, wait 四十八 hours. Chance 百分之五, version 三点五.",
            "A48型设备显示v3.5，速度2x。" to "A四八型设备显示v三点五，速度二x。",
            "编号X-100和AB12-C03。" to "编号X杠一零零和AB一二杠C零三。",
            "山风吹过石桥，天色渐渐暗了。" to "山风吹过石桥，天色渐渐暗了。",
        )
        examples.forEach { (text, expected) ->
            assertEquals(text, expected, ReaderTtsChineseNumberNormalizer.normalize(text))
        }
    }

    @Test
    fun digitOnlySegmentsAndNegativeQuantitiesAreStillSpoken() {
        val examples = listOf(
            "123" to "一百二十三",
            "-12.5" to "负十二点五",
            "温度-.5度。" to "温度负零点五度。",
            "气温－3度，变化-0.25%。" to "气温负三度，变化负百分之零点二五。",
            "0007" to "零零零七",
        )
        examples.forEach { (text, expected) ->
            assertEquals(text, expected, ReaderTtsChineseNumberNormalizer.normalize(text))
        }
    }

    @Test
    fun calendarDatesAreRecognizedBeforeHyphensBecomeModelSeparators() {
        val examples = listOf(
            "日期2026-10-22。" to "日期二零二六年十月二十二日。",
            "2026/01/02" to "二零二六年一月二日",
            "2026.10.22" to "二零二六年十月二十二日",
            "2026-13-40" to "二零二六杠一三杠四零",
        )
        examples.forEach { (text, expected) ->
            assertEquals(text, expected, ReaderTtsChineseNumberNormalizer.normalize(text))
        }
    }

    @Test
    fun unclassifiedSeparatedNumbersReadEveryDigitAndKeepEachSeparatorMeaning() {
        val examples = listOf(
            "编号12/34。" to "编号一二斜杠三四。",
            "编号12//34。" to "编号一二斜杠斜杠三四。",
            "范围12--34。" to "范围一二杠杠三四。",
            "版本1.2.3。" to "版本一点二点三。",
            "范围12-34。" to "范围一二杠三四。",
            "编号12,34。" to "编号一二逗号三四。",
        )
        examples.forEach { (text, expected) ->
            assertEquals(text, expected, ReaderTtsChineseNumberNormalizer.normalize(text))
        }
    }

    @Test
    fun validThousandsGroupsKeepTheQuantityReading() {
        assertEquals("共有一千二百三十四人。", ReaderTtsChineseNumberNormalizer.normalize("共有1,234人。"))
    }

    @Test
    fun fullWidthNumbersModelsAndSignsUseTheSameReadingRules() {
        assertEquals(
            "型号X杠一零零，温度负十二点五度。",
            ReaderTtsChineseNumberNormalizer.normalize("型号Ｘ－１００，温度－１２．５度。"),
        )
        assertEquals("一百二十三", ReaderTtsChineseNumberNormalizer.normalize("１２３"))
    }

    @Test
    fun restartingWithinAModelKeepsItsDigitReadingWithoutRepeatingThePrefix() {
        val segment = ReaderTtsSegmenter.segment(0, "前X-123后", startCharOffset = 3).single()

        assertEquals("一二三后", ReaderTtsChineseNumberNormalizer.normalize(segment))
        assertEquals("123后", segment.spokenText)
        assertEquals(3, segment.startCharOffset)
        assertEquals(7, segment.endCharOffset)
        assertEquals(ReaderTtsCharacterRange(3, 6), segment.sourceRangeForSpokenRange(0, 3))
        assertEquals("一百二十三后", ReaderTtsChineseNumberNormalizer.normalize("123后"))
    }

    @Test
    fun aModelLongerThanEightyCharactersKeepsItsTailAsDigits() {
        val segments = ReaderTtsSegmenter.segment(0, "X-" + "12".repeat(40) + "3")

        assertEquals(listOf("X杠" + "一二".repeat(39), "一二三"),
            segments.map { ReaderTtsChineseNumberNormalizer.normalize(it) })
        assertEquals("123", segments.last().spokenText)
        assertEquals(80, segments.last().startCharOffset)
        assertEquals(83, segments.last().endCharOffset)
    }

    @Test
    fun aOneCharacterLimitRetainsYearModelAndNegativeDecimalContext() {
        val segments = ReaderTtsSegmenter.segment(0, "2026年，X-12，-3.5", maxChunkChars = 1)

        assertEquals("二零二六年，X杠一二，负三点五",
            segments.joinToString("") { ReaderTtsChineseNumberNormalizer.normalize(it) })
    }

    @Test
    fun aYearEndingAtTheChunkLimitDoesNotBecomeAnOrdinaryQuantity() {
        val segments = ReaderTtsSegmenter.segment(0, "2026年", maxChunkChars = 4)

        assertEquals(listOf("二零二六", "年"), segments.map { ReaderTtsChineseNumberNormalizer.normalize(it) })
    }

    @Test
    fun restartingInsideCalendarAndClockFieldsDoesNotRereadEarlierFields() {
        val examples = listOf(
            Triple("2026-10-22", 5, "十月二十二日"),
            Triple("2026-10-22", 6, "零月二十二日"),
            Triple("09:05", 3, "零五分"),
            Triple("09:05", 4, "五分"),
        )
        examples.forEach { (text, offset, expected) ->
            val segment = ReaderTtsSegmenter.segment(0, text, startCharOffset = offset).single()
            assertEquals(text, expected, ReaderTtsChineseNumberNormalizer.normalize(segment))
            assertEquals(offset, segment.startCharOffset)
        }
    }

    @Test
    fun calendarValidationDoesNotInventADateForAnInvalidLeapDay() {
        assertEquals("二零二四年二月二十九日", ReaderTtsChineseNumberNormalizer.normalize("2024-02-29"))
        assertEquals("二零二六杠零二杠二九", ReaderTtsChineseNumberNormalizer.normalize("2026-02-29"))
    }

    @Test
    fun explicitTelephoneAndNumberLabelsReadDigitsInsteadOfLargeQuantities() {
        val examples = listOf(
            "电话13800138000。" to "电话一三八零零一三八零零零。",
            "手机号是13800138000。" to "手机号是一三八零零一三八零零零。",
            "号码：10086。" to "号码：一零零八六。",
            "编号为２０２６。" to "编号为二零二六。",
            "10000000000" to "一百亿",
            "号码增加到10000000000。" to "号码增加到一百亿。",
        )
        examples.forEach { (text, expected) ->
            assertEquals(text, expected, ReaderTtsChineseNumberNormalizer.normalize(text))
        }
    }

    @Test
    fun aPhoneNumberKeepsItsContextWhenTheLabelStaysBeforeTheEightyCharacterBoundary() {
        val examples = listOf(75 to 77, 78 to 80)
        examples.forEach { (padding, numberStart) ->
            val prefix = "甲".repeat(padding) + "电话"
            val segments = ReaderTtsSegmenter.segment(0, prefix + "13800138000。")

            assertEquals(listOf(prefix, "13800138000。"), segments.map { it.spokenText })
            assertEquals(listOf(prefix, "一三八零零一三八零零零。"),
                segments.map { ReaderTtsChineseNumberNormalizer.normalize(it) })
            assertEquals(numberStart, segments.last().startCharOffset)
            assertEquals(ReaderTtsCharacterRange(numberStart, numberStart + 11),
                segments.last().sourceRangeForSpokenRange(0, 11))
        }
    }

    @Test
    fun restartingAtOrInsideAPhoneNumberKeepsDigitsWithoutRereadingItsLabel() {
        val source = "电话13800138000。"
        val completeNumber = ReaderTtsSegmenter.segment(0, source, startCharOffset = 2).single()
        val insideNumber = ReaderTtsSegmenter.segment(0, source, startCharOffset = 5).single()

        assertEquals("一三八零零一三八零零零。", ReaderTtsChineseNumberNormalizer.normalize(completeNumber))
        assertEquals("零零一三八零零零。", ReaderTtsChineseNumberNormalizer.normalize(insideNumber))
        assertEquals(2, completeNumber.startCharOffset)
        assertEquals(5, insideNumber.startCharOffset)
        assertEquals(ReaderTtsCharacterRange(5, 13), insideNumber.sourceRangeForSpokenRange(0, 8))
    }

    @Test
    fun calendarDatesKeepAnExplicitDaySuffixWithoutSayingItTwice() {
        val examples = listOf(
            "2026-10-22日出发。" to "二零二六年十月二十二日出发。",
            "2026-10-22号出发。" to "二零二六年十月二十二号出发。",
        )
        examples.forEach { (text, expected) ->
            assertEquals(text, expected, ReaderTtsChineseNumberNormalizer.normalize(text))
        }
    }

    @Test
    fun anExplicitDaySuffixAfterTheEightyCharacterBoundaryIsOnlyReadFromItsOwnSourceRange() {
        val prefix = "甲".repeat(70)
        for (suffix in listOf("日", "号")) {
            val segments = ReaderTtsSegmenter.segment(0, prefix + "2026-10-22" + suffix)

            assertEquals(listOf(prefix + "2026-10-22", suffix), segments.map { it.spokenText })
            assertEquals(listOf(prefix + "二零二六年十月二十二", suffix),
                segments.map { ReaderTtsChineseNumberNormalizer.normalize(it) })
            assertEquals(listOf(0 to 80, 80 to 81),
                segments.map { it.startCharOffset to it.endCharOffset })
        }
    }
}
