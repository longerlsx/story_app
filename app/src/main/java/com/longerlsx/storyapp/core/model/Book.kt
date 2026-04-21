package com.longerlsx.storyapp.core.model

data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val importSourceType: ImportSourceType,
    val importFileName: String,
    val storedPath: String,
    val charset: String,
    val fileHash: String,
    val wordCount: Int,
    val chapterCount: Int,
    val importedAt: Long,
    val lastReadAt: Long,
)
