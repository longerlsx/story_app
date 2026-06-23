package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTtsTransientMessageResolverTest {

    @Test
    fun prefersCurrentBookErrorOverRestartFeedbackAndStatus() {
        val message = ReaderTtsTransientMessageResolver.resolve(
            currentBookId = "current-book",
            runtimeState = ReaderTtsRuntimeState(
                currentBookId = "current-book",
                localErrorMessage = "朗读启动失败",
                localStatusMessage = "已停止朗读",
            ),
            localRestartFeedbackMessage = "从这里重新朗读",
        )

        assertEquals("朗读启动失败", message)
    }

    @Test
    fun prefersRestartFeedbackOverCurrentBookStatus() {
        val message = ReaderTtsTransientMessageResolver.resolve(
            currentBookId = "current-book",
            runtimeState = ReaderTtsRuntimeState(
                currentBookId = "current-book",
                localStatusMessage = "已停止朗读",
            ),
            localRestartFeedbackMessage = "从这里重新朗读",
        )

        assertEquals("从这里重新朗读", message)
    }

    @Test
    fun ignoresRuntimeMessagesFromOtherBooksButKeepsRestartFeedback() {
        val message = ReaderTtsTransientMessageResolver.resolve(
            currentBookId = "current-book",
            runtimeState = ReaderTtsRuntimeState(
                currentBookId = "other-book",
                localErrorMessage = "另一本文的错误",
                localStatusMessage = "另一本文的状态",
            ),
            localRestartFeedbackMessage = "从这里重新朗读",
        )

        assertEquals("从这里重新朗读", message)
    }

    @Test
    fun returnsNullWhenNoDisplayableMessageExists() {
        val message = ReaderTtsTransientMessageResolver.resolve(
            currentBookId = "current-book",
            runtimeState = ReaderTtsRuntimeState(
                currentBookId = "other-book",
                localErrorMessage = "另一本文的错误",
                localStatusMessage = "另一本文的状态",
            ),
            localRestartFeedbackMessage = null,
        )

        assertNull(message)
    }
}
