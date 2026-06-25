package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageSurfaceInteractionPolicyTest {

    @Test
    fun textTapFollowsExpandedChromeDismissState() {
        assertTrue(
            ReaderPageSurfaceInteractionPolicy.resolve(
                pageIndex = 0,
                currentPage = 0,
                enableTapToDismissExpandedChrome = true,
                enableTtsRestartGesture = false,
            ).enableTextTap,
        )
        assertFalse(
            ReaderPageSurfaceInteractionPolicy.resolve(
                pageIndex = 0,
                currentPage = 0,
                enableTapToDismissExpandedChrome = false,
                enableTtsRestartGesture = true,
            ).enableTextTap,
        )
    }

    @Test
    fun longPressRestartRequiresTtsAndCurrentPage() {
        assertTrue(
            ReaderPageSurfaceInteractionPolicy.resolve(
                pageIndex = 2,
                currentPage = 2,
                enableTapToDismissExpandedChrome = false,
                enableTtsRestartGesture = true,
            ).enableLongPressRestart,
        )
        assertFalse(
            ReaderPageSurfaceInteractionPolicy.resolve(
                pageIndex = 1,
                currentPage = 2,
                enableTapToDismissExpandedChrome = false,
                enableTtsRestartGesture = true,
            ).enableLongPressRestart,
        )
        assertFalse(
            ReaderPageSurfaceInteractionPolicy.resolve(
                pageIndex = 2,
                currentPage = 2,
                enableTapToDismissExpandedChrome = false,
                enableTtsRestartGesture = false,
            ).enableLongPressRestart,
        )
    }
}
