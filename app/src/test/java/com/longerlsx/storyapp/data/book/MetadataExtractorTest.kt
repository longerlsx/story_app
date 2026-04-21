package com.longerlsx.storyapp.data.book

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataExtractorTest {

    @Test
    fun extractsTitleAndAuthorFromHeaderAndFileName() {
        val previewText = loadFixture("fixtures/sample_book_header.txt")

        val metadata = MetadataExtractor.extract(
            fileName = "《小猫咪在星际监狱也会手慢无？》作者：桃花朋.txt",
            previewText = previewText,
        )

        assertEquals("小猫咪在星际监狱也会手慢无？", metadata.title)
        assertEquals("桃花朋", metadata.author)
    }

    private fun loadFixture(path: String): String {
        return requireNotNull(javaClass.classLoader?.getResource(path)) {
            "Missing fixture: $path"
        }.readText()
    }
}
