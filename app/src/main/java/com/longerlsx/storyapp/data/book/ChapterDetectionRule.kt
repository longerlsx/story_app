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
                ChapterHeadingTextPolicy.isLikelyChineseNumberedHeading(
                    suffix = it.groups[2]?.value,
                    tail = it.groups[3]?.value.orEmpty(),
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
                ChapterHeadingTextPolicy.isLikelyChineseNumberedHeading(
                    suffix = it.groups[2]?.value,
                    tail = it.groups[3]?.value.orEmpty(),
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
            ?.takeIf { ChapterHeadingTextPolicy.isLikelyHeading(line.trimmed) }
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
            ?.takeIf { ChapterHeadingTextPolicy.isLikelyHeading(line.trimmed) }
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
            ?.takeIf { ChapterHeadingTextPolicy.isLikelyHeading(line.trimmed) }
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
