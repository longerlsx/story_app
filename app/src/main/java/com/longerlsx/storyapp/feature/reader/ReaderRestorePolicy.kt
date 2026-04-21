package com.longerlsx.storyapp.feature.reader

object ReaderRestorePolicy {
    fun shouldWaitForRestore(
        content: String?,
        savedCharOffset: Int,
        maxScrollValue: Int,
    ): Boolean {
        if (content == null) {
            return true
        }

        return savedCharOffset > 0 && content.isNotBlank() && maxScrollValue == 0
    }

    fun shouldBootstrapProgress(savedCharOffset: Int): Boolean {
        return savedCharOffset == 0
    }
}
