package com.longerlsx.storyapp.feature.reader.tts

/** Speech-only number forms; callers keep the original novel text and character offsets. */
internal object ReaderTtsChineseNumberNormalizer {
    private val numberToken = Regex("[0-9]+(?:[.：:,/][0-9]+)*[%％]?")
    private val clockTime = Regex("([0-9]{1,2})[:：]([0-9]{2})")
    private val decimalNumber = Regex("[0-9]+(?:\\.[0-9]+)?")
    private const val spokenDigits = "零一二三四五六七八九"
    private val groupUnits = arrayOf("", "万", "亿")
    private val groupDivisors = longArrayOf(1L, 10_000L, 100_000_000L)
    private val placeDivisors = intArrayOf(1_000, 100, 10, 1)
    private val placeUnits = arrayOf("千", "百", "十", "")

    fun normalize(text: String): String {
        if (text.none { it in '0'..'9' } ||
            !text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }) return text

        return numberToken.replace(text) { match ->
            val original = match.value
            val before = text.getOrNull(match.range.first - 1)
            val after = text.getOrNull(match.range.last + 1)
            if (before.isIdentifierBoundary() || after.isIdentifierBoundary()) return@replace original

            if (original.contains(':') || original.contains('：')) {
                val time = clockTime.matchEntire(original) ?: return@replace original
                val hour = time.groupValues[1].toInt()
                val minute = time.groupValues[2].toInt()
                if (hour !in 0..23 || minute !in 0..59) return@replace original
                return@replace integer(hour.toString()) + "点" + when {
                    minute == 0 -> "整"
                    minute < 10 -> "零" + integer(minute.toString()) + "分"
                    else -> integer(minute.toString()) + "分"
                }
            }

            val percent = original.endsWith('%') || original.endsWith('％')
            val number = if (percent) original.dropLast(1) else original
            // Unknown grouped numbers, dates and multi-dot expressions remain untouched.
            if (!decimalNumber.matches(number)) return@replace original
            val spoken = when {
                '.' in number -> integer(number.substringBefore('.')) + "点" + digitByDigit(number.substringAfter('.'))
                !percent && number.length == 4 && after == '年' -> digitByDigit(number)
                else -> integer(number)
            }
            if (percent) "百分之" + spoken else spoken
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

    /** Avoid reinterpreting adjacent English/model identifiers or partial numeric expressions. */
    private fun Char?.isIdentifierBoundary(): Boolean = this != null &&
        (this in 'a'..'z' || this in 'A'..'Z' || this in "_./+-")
}
