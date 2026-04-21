package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress

object ReadingAnchorReducer {
    fun reduce(
        current: ReadingProgress,
        nextAnchor: ReadingAnchor,
        nextMode: ReadingMode,
        updatedAt: Long,
    ): ReadingProgress {
        if (updatedAt < current.updatedAt) {
            return current
        }

        return current.copy(
            anchor = nextAnchor,
            readingMode = nextMode,
            updatedAt = updatedAt,
        )
    }
}
