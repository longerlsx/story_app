package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRestorePolicyTest {

    @Test
    fun waitsWhenContentIsNotLoadedYet() {
        assertTrue(
            ReaderRestorePolicy.shouldWaitForRestore(
                content = null,
                savedCharOffset = 120,
                maxScrollValue = 0,
            ),
        )
    }

    @Test
    fun waitsWhenSavedOffsetExistsButScrollMetricsAreNotReady() {
        assertTrue(
            ReaderRestorePolicy.shouldWaitForRestore(
                content = "正文内容",
                savedCharOffset = 120,
                maxScrollValue = 0,
            ),
        )
    }

    @Test
    fun allowsRestoreWhenSavedOffsetExistsAndScrollMetricsAreReady() {
        assertFalse(
            ReaderRestorePolicy.shouldWaitForRestore(
                content = "正文内容",
                savedCharOffset = 120,
                maxScrollValue = 400,
            ),
        )
    }

    @Test
    fun onlyBootstrapsProgressWhenNoSavedOffsetExists() {
        assertTrue(ReaderRestorePolicy.shouldBootstrapProgress(savedCharOffset = 0))
        assertFalse(ReaderRestorePolicy.shouldBootstrapProgress(savedCharOffset = 42))
    }
}
