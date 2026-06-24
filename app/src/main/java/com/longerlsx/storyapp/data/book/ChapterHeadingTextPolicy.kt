package com.longerlsx.storyapp.data.book

object ChapterHeadingTextPolicy {
    const val DEFAULT_MAX_HEADING_LENGTH = 40
    const val CHINESE_NUMBERED_MAX_HEADING_LENGTH = 48

    private val sentenceEndings = setOf('。', '！', '？', '；', '，', ',', '.', '!', '?')
    private val punctuationOnlyTailRegex = Regex("""^[\s　]*[…。！？；，,.!?]+[\s　]*$""")
    private val chapterSuffixes = setOf('章', '节', '回', '话', '集')
    private val numberedSubtitleMarkerRegex = Regex("""^[\s　]+[0-9０-９]{2,4}[\s　]+\S.*$""")
    private val dialogueLikeSubtitlePrefixRegex =
        Regex("""^[\s　]*[一-龥]{1,6}(?:说|问|想|道|喊|叫|答|笑|骂|叹|念|觉得)[\s　]*$""")

    fun isLikelyHeading(
        title: String,
        maxHeadingLength: Int = DEFAULT_MAX_HEADING_LENGTH,
        allowSubtitlePunctuation: Boolean = false,
    ): Boolean {
        if (title.isBlank() || title.length > maxHeadingLength) {
            return false
        }
        if (title.lastOrNull() !in sentenceEndings) {
            return true
        }
        return allowSubtitlePunctuation &&
            (hasExplicitSubtitleSeparator(title) || hasNumberedSubtitleMarker(title))
    }

    fun isLikelyChineseNumberedHeading(
        suffix: String?,
        tail: String,
        title: String,
    ): Boolean {
        if (suffix == "回" && tail.startsWith("合")) {
            return false
        }
        if (suffix == "回" && punctuationOnlyTailRegex.matches(tail)) {
            return false
        }
        if (title.lastOrNull() in sentenceEndings && hasDialogueLikeSubtitlePrefix(tail)) {
            return false
        }
        return isLikelyHeading(
            title = title,
            maxHeadingLength = CHINESE_NUMBERED_MAX_HEADING_LENGTH,
            allowSubtitlePunctuation = true,
        )
    }

    private fun hasExplicitSubtitleSeparator(title: String): Boolean {
        val suffixIndex = title.indexOfFirst { it in chapterSuffixes }
        if (suffixIndex < 0) {
            return false
        }
        return title.indexOf(':', startIndex = suffixIndex + 1) > suffixIndex ||
            title.indexOf('：', startIndex = suffixIndex + 1) > suffixIndex
    }

    private fun hasNumberedSubtitleMarker(title: String): Boolean {
        val suffixIndex = title.indexOfFirst { it in chapterSuffixes }
        if (suffixIndex < 0) {
            return false
        }
        val suffixTail = title.substring(suffixIndex + 1)
        return numberedSubtitleMarkerRegex.matches(suffixTail)
    }

    private fun hasDialogueLikeSubtitlePrefix(tail: String): Boolean {
        val separatorIndex = listOf(tail.indexOf(':'), tail.indexOf('：'))
            .filter { it >= 0 }
            .minOrNull()
            ?: return false
        return dialogueLikeSubtitlePrefixRegex.matches(tail.substring(0, separatorIndex))
    }
}
