package com.longerlsx.storyapp.data.book

object MetadataExtractor {
    data class Metadata(
        val title: String,
        val author: String?,
    )

    private val titlePattern = Regex("""《\s*(.+?)\s*》""")
    private val authorPattern = Regex("""^作者[:：]\s*(.+)$""")
    private val authorInNamePattern = Regex("""作者[:：]\s*(.+)$""")
    private val noisePatterns = listOf(
        Regex("""^(本站|站点).*(收藏|域名|网址).*$"""),
        Regex("""^.*(最新网址|请收藏).*$"""),
        Regex("""^.*(小说下载|必备网址|www\.).*$"""),
        Regex("""^.*(每天更新|去看看).*$"""),
        Regex("""^.*(阅读指南|内容标签|一句话简介|立意).*$"""),
    )

    fun extract(
        fileName: String,
        previewText: String,
    ): Metadata {
        val normalizedPreview = TxtNormalizer.normalize(previewText)
        val meaningfulLines = normalizedPreview.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot(::isNoiseLine)
            .toList()

        val title = meaningfulLines.firstNotNullOfOrNull(::extractTitleFromLine)
            ?: extractTitleFromFileName(fileName)
            ?: sanitizeFileName(fileName)

        val author = meaningfulLines.firstNotNullOfOrNull(::extractAuthorFromLine)
            ?: extractAuthorFromFileName(fileName)

        return Metadata(
            title = title,
            author = author,
        )
    }

    private fun extractTitleFromLine(line: String): String? {
        return titlePattern.find(line)?.groupValues?.get(1)
            ?: if (!authorPattern.matches(line) && !isNoiseLine(line) && line.length <= 40) {
                line
            } else {
                null
            }
    }

    private fun extractAuthorFromLine(line: String): String? {
        return authorPattern.find(line)?.groupValues?.get(1)?.trim()
    }

    private fun extractTitleFromFileName(fileName: String): String? {
        val sanitized = sanitizeFileName(fileName)
        return titlePattern.find(sanitized)?.groupValues?.get(1)
    }

    private fun extractAuthorFromFileName(fileName: String): String? {
        val sanitized = sanitizeFileName(fileName)
        return authorInNamePattern.find(sanitized)?.groupValues?.get(1)?.trim()
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName.substringBeforeLast('.').trim()
    }

    private fun isNoiseLine(line: String): Boolean {
        return noisePatterns.any { it.matches(line) }
    }
}
