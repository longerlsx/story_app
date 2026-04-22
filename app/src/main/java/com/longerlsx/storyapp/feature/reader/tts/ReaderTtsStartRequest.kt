package com.longerlsx.storyapp.feature.reader.tts

data class ReaderTtsStartRequest(
    val bookId: String,
    val bookTitle: String,
    val chapterIndex: Int,
    val charOffset: Int,
    val chapterTitleOrSummary: String,
    val activeStateLabel: String,
)
