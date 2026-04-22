package com.longerlsx.storyapp.feature.reader.tts

data class ReaderTtsSegment(
    val chapterIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val spokenText: String,
)
