package com.longerlsx.storyapp.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
fun rememberStoryAppState(
    initialBookId: String? = null,
): StoryAppState {
    var currentScreen by rememberSaveable {
        mutableStateOf(
            if (initialBookId == null) AppScreen.BOOKSHELF else AppScreen.READER,
        )
    }
    var selectedBookId by rememberSaveable {
        mutableStateOf(initialBookId)
    }

    return remember(currentScreen, selectedBookId) {
        StoryAppState(
            currentScreen = currentScreen,
            selectedBookId = selectedBookId,
            onOpenSourceEntry = { currentScreen = AppScreen.SOURCE_ENTRY },
            onOpenBookshelf = { currentScreen = AppScreen.BOOKSHELF },
            onOpenReader = { bookId ->
                selectedBookId = bookId
                currentScreen = AppScreen.READER
            },
        )
    }
}

@Stable
class StoryAppState(
    val currentScreen: AppScreen,
    val selectedBookId: String?,
    private val onOpenSourceEntry: () -> Unit,
    private val onOpenBookshelf: () -> Unit,
    private val onOpenReader: (String) -> Unit,
) {
    fun openSourceEntry() = onOpenSourceEntry()

    fun openBookshelf() = onOpenBookshelf()

    fun openReader(bookId: String) = onOpenReader(bookId)
}
