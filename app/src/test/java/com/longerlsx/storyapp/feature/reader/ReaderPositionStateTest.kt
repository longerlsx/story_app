package com.longerlsx.storyapp.feature.reader

import com.longerlsx.storyapp.core.model.ReadingAnchor
import org.junit.Assert.*
import org.junit.Test

class ReaderPositionStateTest {
    @Test
    fun appendsTurnsToPendingTargetButDirectoryReplacesThem() {
        val state = ReaderPositionState(ReadingAnchor(0, 8))
        val layout = ReaderPageLayoutKey(400, 700, 1f, 1f, 20, 1.6f, 12f)
        state.turn(1, layout)
        state.turn(1, layout)
        state.turn(-1, layout)
        assertEquals(listOf(1, 1, -1), state.request!!.turns.map { it.direction })
        state.jump(ReadingAnchor(5, 20))
        state.turn(1, layout)
        assertEquals(ReadingAnchor(5, 20), state.request!!.anchor)
        assertEquals(listOf(1), state.request!!.turns.map { it.direction })
    }

    @Test
    fun staleCompletionAndFailureNeverOverwriteConfirmedProgress() {
        val state = ReaderPositionState(ReadingAnchor(1, 42))
        val initial = state.request!!.id
        assertTrue(state.confirm(initial, ReadingAnchor(1, 42)))
        state.jump(ReadingAnchor(2, 0))
        val old = state.request!!.id
        state.jump(ReadingAnchor(3, 12))
        assertFalse(state.confirm(old, ReadingAnchor(2, 0)))
        assertEquals(ReadingAnchor(1, 42), state.confirmedAnchor)
        state.fail(state.request!!.id, "正文读取失败")
        assertEquals(ReadingAnchor(1, 42), state.confirmedAnchor)
        assertNull(state.request)
        assertEquals("正文读取失败", state.error)
        assertFalse(state.recordViewport(ReadingAnchor(3, 0)))
        assertEquals(ReadingAnchor(1, 42), state.confirmedAnchor)
    }

    @Test
    fun reflowRetainsUnresolvedMovesAndTheirOriginalLayout() {
        val state = ReaderPositionState(ReadingAnchor(0, 10))
        val layout = ReaderPageLayoutKey(400, 700, 1f, 1f, 20, 1.6f, 12f)
        state.turn(1, layout)
        val old = state.request!!.id
        state.reflow()
        assertEquals(ReadingAnchor(0, 10), state.request!!.anchor)
        assertEquals(layout, state.request!!.turns.single().layout)
        assertNotEquals(old, state.request!!.id)
    }
}
