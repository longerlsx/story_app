package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsTransientMessageClearancePolicyTest {

    @Test
    fun clearsMatchingErrorForCurrentBook() {
        val clearance = ReaderTtsTransientMessageClearancePolicy.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                currentBookId = "book-1",
                localErrorMessage = "朗读启动失败",
            ),
            displayedMessage = "朗读启动失败",
        )

        assertTrue(clearance.clearLocalErrorMessage)
        assertFalse(clearance.clearLocalStatusMessage)
    }

    @Test
    fun clearsMatchingStatusForCurrentBook() {
        val clearance = ReaderTtsTransientMessageClearancePolicy.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                currentBookId = "book-1",
                localStatusMessage = "已停止朗读",
            ),
            displayedMessage = "已停止朗读",
        )

        assertFalse(clearance.clearLocalErrorMessage)
        assertTrue(clearance.clearLocalStatusMessage)
    }

    @Test
    fun doesNotClearMatchingMessageFromAnotherBook() {
        val clearance = ReaderTtsTransientMessageClearancePolicy.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                currentBookId = "book-2",
                localErrorMessage = "朗读启动失败",
                localStatusMessage = "朗读启动失败",
            ),
            displayedMessage = "朗读启动失败",
        )

        assertFalse(clearance.clearLocalErrorMessage)
        assertFalse(clearance.clearLocalStatusMessage)
    }

    @Test
    fun doesNotClearWhenDisplayedMessageIsMissing() {
        val clearance = ReaderTtsTransientMessageClearancePolicy.resolve(
            currentBookId = "book-1",
            runtimeState = ReaderTtsRuntimeState(
                currentBookId = "book-1",
                localErrorMessage = "朗读启动失败",
                localStatusMessage = "朗读启动失败",
            ),
            displayedMessage = null,
        )

        assertFalse(clearance.clearLocalErrorMessage)
        assertFalse(clearance.clearLocalStatusMessage)
    }
}
