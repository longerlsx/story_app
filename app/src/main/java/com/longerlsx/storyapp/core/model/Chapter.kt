package com.longerlsx.storyapp.core.model

data class Chapter(
    val bookId: String,
    val chapterIndex: Int,
    val title: String,
    val startOffset: Int,
    val endOffset: Int,
    val wordCount: Int,
)
