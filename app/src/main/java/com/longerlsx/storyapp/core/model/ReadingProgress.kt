package com.longerlsx.storyapp.core.model

data class ReadingProgress(
    val bookId: String,
    val anchor: ReadingAnchor,
    val readingMode: ReadingMode,
    val updatedAt: Long,
)
