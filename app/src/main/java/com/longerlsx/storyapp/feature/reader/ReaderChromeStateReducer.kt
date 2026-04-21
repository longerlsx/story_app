package com.longerlsx.storyapp.feature.reader

object ReaderChromeStateReducer {
    fun onCenterTap(current: ReaderChromeMode): ReaderChromeMode {
        return when (current) {
            ReaderChromeMode.READING_ONLY -> ReaderChromeMode.CHROME_VISIBLE
            ReaderChromeMode.CHROME_VISIBLE,
            ReaderChromeMode.SETTINGS_EXPANDED,
            ReaderChromeMode.DIRECTORY_OPEN,
            -> ReaderChromeMode.READING_ONLY
        }
    }

    fun onOpenSettings(current: ReaderChromeMode): ReaderChromeMode {
        return if (current == ReaderChromeMode.SETTINGS_EXPANDED) {
            ReaderChromeMode.CHROME_VISIBLE
        } else {
            ReaderChromeMode.SETTINGS_EXPANDED
        }
    }

    fun onOpenDirectory(current: ReaderChromeMode): ReaderChromeMode {
        return if (current == ReaderChromeMode.DIRECTORY_OPEN) {
            ReaderChromeMode.CHROME_VISIBLE
        } else {
            ReaderChromeMode.DIRECTORY_OPEN
        }
    }

    fun onDirectoryChapterSelected(): ReaderChromeMode = ReaderChromeMode.READING_ONLY

    fun onChapterStep(current: ReaderChromeMode): ReaderChromeMode {
        return if (current == ReaderChromeMode.READING_ONLY) {
            ReaderChromeMode.READING_ONLY
        } else {
            ReaderChromeMode.CHROME_VISIBLE
        }
    }
}
