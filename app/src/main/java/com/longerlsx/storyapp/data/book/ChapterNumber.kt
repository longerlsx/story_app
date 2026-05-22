package com.longerlsx.storyapp.data.book

data class ChapterNumber(
    val raw: String,
    val value: Int?,
) {
    companion object {
        fun parse(raw: String): ChapterNumber {
            val normalized = normalizeDigits(raw.trim())
            val arabic = normalized.toIntOrNull()
            if (arabic != null) {
                return ChapterNumber(raw = raw, value = arabic)
            }

            return ChapterNumber(raw = raw, value = parseChineseNumber(normalized))
        }

        private fun normalizeDigits(value: String): String {
            return buildString {
                value.forEach { char ->
                    append(
                        when (char) {
                            in '０'..'９' -> '0' + (char - '０')
                            else -> char
                        },
                    )
                }
            }
        }

        private fun parseChineseNumber(value: String): Int? {
            if (value.isBlank()) {
                return null
            }

            var result = 0
            var section = 0
            var number = 0
            value.forEach { char ->
                val digit = CHINESE_DIGITS[char]
                val unit = CHINESE_UNITS[char]
                when {
                    digit != null -> number = digit
                    unit != null -> {
                        val multiplier = if (number == 0) 1 else number
                        section += multiplier * unit
                        number = 0
                    }
                    char == '零' || char == '〇' -> number = 0
                    else -> return null
                }
            }

            return result + section + number
        }

        private val CHINESE_DIGITS = mapOf(
            '一' to 1,
            '二' to 2,
            '两' to 2,
            '三' to 3,
            '四' to 4,
            '五' to 5,
            '六' to 6,
            '七' to 7,
            '八' to 8,
            '九' to 9,
        )

        private val CHINESE_UNITS = mapOf(
            '十' to 10,
            '百' to 100,
            '千' to 1_000,
        )
    }
}
