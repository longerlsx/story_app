package com.longerlsx.storyapp.data.book

enum class ChapterTitleKind {
    CHAPTER,
    SPECIAL,
    NUMERIC,
    STRUCTURAL,
    VOLUME,
}

enum class ChapterTitleConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

data class ChapterDetectionRule(
    val id: String,
    val kind: ChapterTitleKind,
    val confidence: ChapterTitleConfidence,
    val priority: Int,
    private val matcher: (ChapterLine) -> ChapterCandidate?,
) {
    fun match(line: ChapterLine): ChapterCandidate? = matcher(line)
}

object ChapterDetectionRules {
    private const val DEFAULT_MAX_HEADING_LENGTH = 40
    private const val CHINESE_NUMBERED_MAX_HEADING_LENGTH = 48
    private val sentenceEndings = setOf('。', '！', '？', '；', '，', ',', '.', '!', '?')
    private val punctuationOnlyTailRegex = Regex("""^[\s　]*[…。！？；，,.!?]+[\s　]*$""")
    private val chapterSuffixes = setOf('章', '节', '回', '话', '集')
    private val numberedSubtitleMarkerRegex = Regex("""^[\s　]+[0-9０-９]{2,4}[\s　]+\S.*$""")
    private val dialogueLikeSubtitlePrefixRegex =
        Regex("""^[\s　]*[一-龥]{1,6}(?:说|问|想|道|喊|叫|答|笑|骂|叹|念|觉得)[\s　]*$""")
    private val numberToken = """([0-9０-９一二三四五六七八九十百千零〇两]+)"""
    private val compactChapterRegex = Regex("""^第$numberToken([章节回话集])\s*(.*)$""")
    private val spacedChapterRegex = Regex("""^第[\s　]+$numberToken[\s　]*([章节回话集])\s*(.*)$""")
    private val specialRegex = Regex("""^(序章|楔子|后记|尾声|番外[0-9０-９一二三四五六七八九十百千零〇两]*\s*.*)$""")
    private val numericArabicRegex = Regex("""^([0-9０-９]{1,4})(?:[.、]\s*|\s+)(\S.*)$""")
    private val numericChineseRegex = Regex("""^([一二三四五六七八九十百千零〇两]{1,6})、\s*(\S.*)$""")
    private val structuralRegex = Regex("""^(前言|正文)$""")
    private val volumeRegex = Regex("""^第[\s　]*$numberToken[\s　]*([卷部篇])\s*(.*)$""")

    val compactChineseNumbered = ChapterDetectionRule(
        id = "chinese-numbered-compact",
        kind = ChapterTitleKind.CHAPTER,
        confidence = ChapterTitleConfidence.HIGH,
        priority = 90,
    ) { line ->
        compactChapterRegex.matchEntire(line.trimmed)
            ?.takeIf {
                isLikelyChineseNumberedHeading(
                    match = it,
                    title = line.trimmed,
                )
            }
            ?.toCandidate(line, "chinese-numbered-compact", ChapterTitleKind.CHAPTER, ChapterTitleConfidence.HIGH, 1)
    }

    val spacedChineseNumbered = ChapterDetectionRule(
        id = "chinese-numbered-spaced",
        kind = ChapterTitleKind.CHAPTER,
        confidence = ChapterTitleConfidence.HIGH,
        priority = 100,
    ) { line ->
        spacedChapterRegex.matchEntire(line.trimmed)
            ?.takeIf {
                isLikelyChineseNumberedHeading(
                    match = it,
                    title = line.trimmed,
                )
            }
            ?.toCandidate(line, "chinese-numbered-spaced", ChapterTitleKind.CHAPTER, ChapterTitleConfidence.HIGH, 1)
    }

    val special = ChapterDetectionRule(
        id = "special-marker",
        kind = ChapterTitleKind.SPECIAL,
        confidence = ChapterTitleConfidence.MEDIUM,
        priority = 70,
    ) { line ->
        specialRegex.matchEntire(line.trimmed)
            ?.takeIf { isLikelyHeading(line.trimmed) }
            ?.toCandidate(line, "special-marker", ChapterTitleKind.SPECIAL, ChapterTitleConfidence.MEDIUM, 1)
    }

    val numeric = ChapterDetectionRule(
        id = "numeric-title",
        kind = ChapterTitleKind.NUMERIC,
        confidence = ChapterTitleConfidence.MEDIUM,
        priority = 60,
    ) { line ->
        val match = numericArabicRegex.matchEntire(line.trimmed)
            ?: numericChineseRegex.matchEntire(line.trimmed)
        match
            ?.takeIf { isLikelyHeading(line.trimmed) }
            ?.toCandidate(line, "numeric-title", ChapterTitleKind.NUMERIC, ChapterTitleConfidence.MEDIUM, 1)
    }

    val structural = ChapterDetectionRule(
        id = "structural-marker",
        kind = ChapterTitleKind.STRUCTURAL,
        confidence = ChapterTitleConfidence.LOW,
        priority = 10,
    ) { line ->
        structuralRegex.matchEntire(line.trimmed)
            ?.toCandidate(line, "structural-marker", ChapterTitleKind.STRUCTURAL, ChapterTitleConfidence.LOW, null)
    }

    val volume = ChapterDetectionRule(
        id = "volume-marker",
        kind = ChapterTitleKind.VOLUME,
        confidence = ChapterTitleConfidence.LOW,
        priority = 20,
    ) { line ->
        volumeRegex.matchEntire(line.trimmed)
            ?.takeIf { isLikelyHeading(line.trimmed) }
            ?.toCandidate(line, "volume-marker", ChapterTitleKind.VOLUME, ChapterTitleConfidence.LOW, 1)
    }

    val all = listOf(
        spacedChineseNumbered,
        compactChineseNumbered,
        special,
        numeric,
        structural,
        volume,
    )

    val primaryRules = listOf(
        spacedChineseNumbered,
        compactChineseNumbered,
        numeric,
    )

    val defaultAuxiliaryRules = listOf(
        special,
        volume,
    )

    fun matchAll(line: ChapterLine, rules: List<ChapterDetectionRule> = all): List<ChapterCandidate> {
        return rules.mapNotNull { rule -> rule.match(line) }
    }

    private fun isLikelyHeading(
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

    private fun isLikelyChineseNumberedHeading(match: MatchResult, title: String): Boolean {
        val suffix = match.groups[2]?.value
        val tail = match.groups[3]?.value.orEmpty()
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

    private fun MatchResult.toCandidate(
        line: ChapterLine,
        ruleId: String,
        kind: ChapterTitleKind,
        confidence: ChapterTitleConfidence,
        numberGroup: Int?,
    ): ChapterCandidate {
        val number = numberGroup
            ?.let { groups[it]?.value }
            ?.takeIf(String::isNotBlank)
            ?.let(ChapterNumber::parse)

        return ChapterCandidate(
            title = line.trimmed,
            number = number,
            kind = kind,
            confidence = confidence,
            ruleId = ruleId,
            lineIndex = line.lineIndex,
            headingStartOffset = line.trimmedStartOffset,
            headingEndOffset = line.trimmedEndOffsetExclusive,
            bodyStartOffset = line.nextLineStartOffset,
        )
    }
}
