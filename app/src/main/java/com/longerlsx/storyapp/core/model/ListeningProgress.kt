package com.longerlsx.storyapp.core.model

data class ListeningProgress(
    val bookId: String,
    val bookTitle: String,
    val chapterIndex: Int,
    val charOffset: Int,
    val chapterTitle: String,
    val updatedAt: Long,
    val completed: Boolean = false,
)
