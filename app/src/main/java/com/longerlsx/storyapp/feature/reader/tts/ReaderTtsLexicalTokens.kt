package com.longerlsx.storyapp.feature.reader.tts

/** Shared lexical boundaries keep speech sanitizing and number readings in agreement. */
internal object ReaderTtsLexicalTokens {
    enum class Kind { WORD, QUANTITY, YEAR, DATE, CLOCK, IDENTIFIER, NUMBER_ID, SEPARATED }

    data class Token(
        val start: Int,
        val end: Int,
        val kind: Kind,
        val negative: Boolean = false,
        val explicitDaySuffix: Boolean = false,
    )

    data class Reading(
        val start: Int,
        val end: Int,
        val kind: ReaderTtsNumberReading,
        val suffix: ReaderTtsNumberSuffix = ReaderTtsNumberSuffix.NONE,
    )

    private val quantity = Regex("(?:(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?|\\.[0-9]+)")
    private val date = Regex("([0-9]{4})([-/.])([0-9]{1,2})\\2([0-9]{1,2})")
    private val clock = Regex("([0-9]{1,2})[:：]([0-9]{2})")
    private val numberLabel = Regex("""(?:电话|手机号|号码|编号)[\s:：=“"「『（(]*(?:是|为)?[\s:：=“"「『（(]*$""")

    /** Each conversion occupies exactly one UTF-16 code unit, preserving chapter positions. */
    fun foldWidth(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            append(when (ch) {
                in '０'..'９', in 'Ａ'..'Ｚ', in 'ａ'..'ｚ' -> (ch.code - 0xfee0).toChar()
                '－', '−' -> '-'
                '＋' -> '+'
                '．' -> '.'
                '／' -> '/'
                '＿' -> '_'
                else -> ch
            })
        }
    }

    fun scan(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var cursor = 0
        while (cursor < text.length) {
            val signedStart = text[cursor] in "+-" && (text.getOrNull(cursor + 1).isAsciiDigit() ||
                text.getOrNull(cursor + 1) == '.' && text.getOrNull(cursor + 2).isAsciiDigit())
            val fractionalStart = text[cursor] == '.' && text.getOrNull(cursor + 1).isAsciiDigit()
            if (!text[cursor].isAsciiLetterOrDigit() && !signedStart && !fractionalStart) {
                cursor++
                continue
            }
            val start = cursor++
            if (signedStart && text[cursor] == '.') cursor++
            while (cursor < text.length) {
                if (text[cursor].isAsciiLetterOrDigit()) cursor++ else {
                    val separatorEnd = innerSeparatorEnd(text, cursor)
                    if (separatorEnd == cursor) break
                    cursor = separatorEnd
                }
            }
            if ((text.getOrNull(cursor) == '%' || text.getOrNull(cursor) == '％') &&
                text[cursor - 1].isAsciiDigit()) cursor++
            val value = text.substring(start, cursor)
            val after = text.getOrNull(cursor)
            val kind = classify(value, after, hasNumberLabel(text, start))
            tokens += Token(start, cursor, kind,
                negative = value.startsWith('-') && kind != Kind.IDENTIFIER,
                explicitDaySuffix = kind == Kind.DATE && (after == '日' || after == '号'))
        }
        return tokens
    }

    fun needsContext(token: Token, start: Int, end: Int): Boolean = token.kind != Kind.WORD &&
        (token.kind == Kind.YEAR || token.kind == Kind.NUMBER_ID || token.explicitDaySuffix ||
            start > token.start || end < token.end)

    /** Only finite reading categories and the visible ranges survive into a segment. */
    fun readings(text: String, token: Token, start: Int, end: Int): List<Reading> {
        val visibleStart = maxOf(start, token.start)
        val visibleEnd = minOf(end, token.end)
        if (visibleStart >= visibleEnd) return emptyList()
        val parts = when (token.kind) {
            Kind.DATE -> {
                val separators = (token.start until token.end).filter { text[it] in "-/." }
                val first = separators[0]
                val second = separators[1]
                listOf(
                    Reading(token.start, first, ReaderTtsNumberReading.DIGITS),
                    Reading(first, first + 1, ReaderTtsNumberReading.YEAR_SEPARATOR),
                    Reading(first + 1, second, ReaderTtsNumberReading.QUANTITY),
                    Reading(second, second + 1, ReaderTtsNumberReading.MONTH_SEPARATOR),
                    Reading(second + 1, token.end, ReaderTtsNumberReading.QUANTITY,
                        if (token.explicitDaySuffix) ReaderTtsNumberSuffix.NONE else ReaderTtsNumberSuffix.DAY),
                )
            }
            Kind.CLOCK -> {
                val separator = (token.start until token.end).first { text[it] in ":：" }
                listOf(
                    Reading(token.start, separator, ReaderTtsNumberReading.QUANTITY),
                    Reading(separator, separator + 1, ReaderTtsNumberReading.HOUR_SEPARATOR),
                    Reading(separator + 1, token.end, ReaderTtsNumberReading.CLOCK_MINUTES),
                )
            }
            else -> listOf(Reading(token.start, token.end,
                if (token.negative && visibleStart == token.start) ReaderTtsNumberReading.NEGATIVE_DIGITS
                else ReaderTtsNumberReading.DIGITS))
        }
        return parts.mapNotNull { part ->
            val from = maxOf(part.start, visibleStart)
            val to = minOf(part.end, visibleEnd)
            if (from >= to) null else {
                val partialNumber = (from > part.start || to < part.end) &&
                    part.kind in listOf(ReaderTtsNumberReading.QUANTITY, ReaderTtsNumberReading.CLOCK_MINUTES)
                val suffix = when {
                    to < part.end -> ReaderTtsNumberSuffix.NONE
                    partialNumber && part.kind == ReaderTtsNumberReading.CLOCK_MINUTES -> ReaderTtsNumberSuffix.MINUTE
                    else -> part.suffix
                }
                part.copy(start = from, end = to,
                    kind = if (partialNumber) ReaderTtsNumberReading.DIGITS else part.kind,
                    suffix = suffix)
            }
        }
    }

    private fun classify(value: String, after: Char?, labeledNumber: Boolean): Kind {
        if (value.none { it.isAsciiDigit() }) return Kind.WORD
        if (value.any { it in 'a'..'z' || it in 'A'..'Z' }) return Kind.IDENTIFIER
        if (labeledNumber && value.all { it.isAsciiDigit() }) return Kind.NUMBER_ID
        val calendar = date.matchEntire(value)
        if (calendar != null && isValidDate(calendar)) return Kind.DATE
        val time = clock.matchEntire(value)
        if (time != null && time.groupValues[1].toInt() in 0..23 && time.groupValues[2].toInt() in 0..59) {
            return Kind.CLOCK
        }
        if (value.length == 4 && value.all { it.isAsciiDigit() } && after == '年') return Kind.YEAR
        val number = value.removePrefix("-").removePrefix("+").removeSuffix("%").removeSuffix("％")
        return if (quantity.matches(number)) Kind.QUANTITY else Kind.SEPARATED
    }

    private fun hasNumberLabel(text: String, numberStart: Int): Boolean {
        // An immediate label and its optional colon/copula supply context; digit count does not.
        val context = text.substring(maxOf(0, numberStart - 24), numberStart)
        return numberLabel.containsMatchIn(context)
    }

    private fun isValidDate(match: MatchResult): Boolean {
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[3].toInt()
        val day = match.groupValues[4].toInt()
        if (year == 0 || month !in 1..12) return false
        val days = when (month) {
            2 -> if (year % 400 == 0 || year % 4 == 0 && year % 100 != 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day in 1..days
    }

    private fun innerSeparatorEnd(text: String, index: Int): Int {
        val before = text.getOrNull(index - 1)
        if (before == null || !before.isAsciiLetterOrDigit()) return index
        var end = index
        var identifierSeparator = false
        while (end < text.length && text[end] in "-_/+.,:：") {
            if (text[end] in "-_/+") identifierSeparator = true
            end++
        }
        val after = text.getOrNull(end) ?: return index
        return if (end > index && after.isAsciiLetterOrDigit() &&
            (identifierSeparator || before.isAsciiDigit() && after.isAsciiDigit())) end else index
    }

    private fun Char?.isAsciiDigit(): Boolean = this != null && this in '0'..'9'
    private fun Char.isAsciiLetterOrDigit(): Boolean = this in '0'..'9' || this in 'a'..'z' || this in 'A'..'Z'
}
