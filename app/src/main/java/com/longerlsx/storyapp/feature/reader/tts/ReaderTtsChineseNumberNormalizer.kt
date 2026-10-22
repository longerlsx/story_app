package com.longerlsx.storyapp.feature.reader.tts

/** Speech-only number forms; callers keep the original novel text and character offsets. */
internal object ReaderTtsChineseNumberNormalizer {
    private const val spokenDigits = "零一二三四五六七八九"
    private val groupUnits = arrayOf("", "万", "亿")
    private val groupDivisors = longArrayOf(1L, 10_000L, 100_000_000L)
    private val placeDivisors = intArrayOf(1_000, 100, 10, 1)
    private val placeUnits = arrayOf("千", "百", "十", "")

    fun normalize(text: String): String {
        val folded = ReaderTtsLexicalTokens.foldWidth(text)
        val tokens = ReaderTtsLexicalTokens.scan(folded)
        return buildString {
            var cursor = 0
            tokens.forEach { token ->
                append(folded, cursor, token.start)
                val value = folded.substring(token.start, token.end)
                append(when (token.kind) {
                    ReaderTtsLexicalTokens.Kind.WORD -> value
                    ReaderTtsLexicalTokens.Kind.QUANTITY -> quantity(value)
                    ReaderTtsLexicalTokens.Kind.DATE, ReaderTtsLexicalTokens.Kind.CLOCK,
                    ReaderTtsLexicalTokens.Kind.RANGE ->
                        ReaderTtsLexicalTokens.readings(folded, token, token.start, token.end).joinToString("") {
                            read(folded.substring(it.start, it.end), it.kind, it.suffix)
                        }
                    else -> readDigits(value, negative = token.negative)
                })
                cursor = token.end
            }
            append(folded, cursor, folded.length)
        }
    }

    fun normalize(segment: ReaderTtsSegment): String {
        if (segment.numberReadingHints.isEmpty()) return normalize(segment.spokenText)
        val text = ReaderTtsLexicalTokens.foldWidth(segment.spokenText)
        return buildString {
            var cursor = 0
            segment.numberReadingHints.forEach { hint ->
                require(hint.startSpokenOffset >= cursor && hint.endSpokenOffset > hint.startSpokenOffset &&
                    hint.endSpokenOffset <= text.length) { "数字读法提示必须对应当前朗读片段" }
                append(normalize(text.substring(cursor, hint.startSpokenOffset)))
                append(read(text.substring(hint.startSpokenOffset, hint.endSpokenOffset), hint.reading, hint.suffix))
                cursor = hint.endSpokenOffset
            }
            append(normalize(text.substring(cursor)))
        }
    }

    private fun quantity(value: String): String {
        val sign = when (value.first()) { '-' -> "负"; '+' -> "正"; else -> "" }
        val unsigned = value.removePrefix("-").removePrefix("+")
        val percent = unsigned.endsWith('%') || unsigned.endsWith('％')
        val number = unsigned.removeSuffix("%").removeSuffix("％").replace(",", "")
        val spoken = if ('.' in number) {
            integer(number.substringBefore('.').ifEmpty { "0" }) + "点" + digitByDigit(number.substringAfter('.'))
        } else integer(number)
        return sign + (if (percent) "百分之" else "") + spoken
    }

    private fun read(text: String, reading: ReaderTtsNumberReading, suffix: ReaderTtsNumberSuffix): String {
        val spoken = when (reading) {
            ReaderTtsNumberReading.DIGITS -> readDigits(text)
            ReaderTtsNumberReading.NEGATIVE_DIGITS -> readDigits(text.trimStart(), negative = true)
            ReaderTtsNumberReading.QUANTITY -> integer(text.trimStart('0').ifEmpty { "0" })
            ReaderTtsNumberReading.RANGE_ENDPOINT -> quantity(text.trim())
            ReaderTtsNumberReading.RANGE_SEPARATOR -> "到"
            ReaderTtsNumberReading.CLOCK_MINUTES -> if (text.toInt() == 0) "整" else integer(text) + "分"
            ReaderTtsNumberReading.YEAR_SEPARATOR -> "年"
            ReaderTtsNumberReading.MONTH_SEPARATOR -> "月"
            ReaderTtsNumberReading.HOUR_SEPARATOR -> "点"
        }
        return spoken + when (suffix) {
            ReaderTtsNumberSuffix.NONE -> ""
            ReaderTtsNumberSuffix.DAY -> "日"
            ReaderTtsNumberSuffix.MINUTE -> "分"
        }
    }

    private fun readDigits(text: String, negative: Boolean = false): String = buildString {
        text.forEachIndexed { index, ch ->
            append(when (ch) {
                in '0'..'9' -> spokenDigits[ch - '0'].toString()
                '-' -> if (negative && index == 0) "负" else "杠"
                '+' -> "加"
                '.' -> "点"
                '/' -> "斜杠"
                ':', '：' -> "冒号"
                ',' -> "逗号"
                '_' -> "下划线"
                '~' -> "波浪号"
                '%', '％' -> "百分号"
                else -> ch.toString()
            })
        }
    }

    private fun integer(digits: String): String {
        if (digits.length > 1 && digits.startsWith('0')) return digitByDigit(digits)
        val value = digits.toLongOrNull() ?: return digitByDigit(digits)
        // Three four-digit groups cover everyday quantities through 9,999 亿.
        // Larger values remain lossless digit readings instead of inventing huge-number units.
        if (value > 999_999_999_999L) return digitByDigit(digits)
        if (value == 0L) return "零"
        return buildString {
            var emptyGroup = false
            for (index in groupUnits.indices.reversed()) {
                val group = ((value / groupDivisors[index]) % 10_000).toInt()
                if (group == 0) {
                    if (isNotEmpty()) emptyGroup = true
                    continue
                }
                if (isNotEmpty() && (emptyGroup || group < 1_000)) append('零')
                append(fourDigits(group, omitLeadingOne = isEmpty()))
                append(groupUnits[index])
                emptyGroup = false
            }
        }
    }

    private fun fourDigits(number: Int, omitLeadingOne: Boolean): String = buildString {
        var pendingZero = false
        placeDivisors.forEachIndexed { index, divisor ->
            val digit = number / divisor % 10
            if (digit == 0) {
                if (isNotEmpty()) pendingZero = true
            } else {
                if (pendingZero) append('零')
                // 10 is 十, but the low group in 10,010 is 一万零一十.
                if (!(omitLeadingOne && isEmpty() && divisor == 10 && digit == 1)) append(spokenDigits[digit])
                append(placeUnits[index])
                pendingZero = false
            }
        }
    }

    private fun digitByDigit(digits: String): String = buildString(digits.length) {
        digits.forEach { append(spokenDigits[it - '0']) }
    }

}
