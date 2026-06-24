package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageSettledPolicyTest {

    @Test
    fun firstSettledPageDoesNotInterruptFollow() {
        val decision = ReaderPageSettledPolicy.resolve(
            previousSettledPage = null,
            pendingProgrammaticSettledPage = null,
            settledPage = 0,
        )

        assertFalse(decision.shouldInterruptFollow)
        assertEquals(null, decision.nextPendingProgrammaticSettledPage)
        assertEquals(0, decision.nextLastSettledPage)
    }

    @Test
    fun programmaticSettledPageClearsPendingWithoutInterruptingFollow() {
        val decision = ReaderPageSettledPolicy.resolve(
            previousSettledPage = 0,
            pendingProgrammaticSettledPage = 2,
            settledPage = 2,
        )

        assertFalse(decision.shouldInterruptFollow)
        assertEquals(null, decision.nextPendingProgrammaticSettledPage)
        assertEquals(2, decision.nextLastSettledPage)
    }

    @Test
    fun manualPageChangeInterruptsFollow() {
        val decision = ReaderPageSettledPolicy.resolve(
            previousSettledPage = 0,
            pendingProgrammaticSettledPage = null,
            settledPage = 1,
        )

        assertTrue(decision.shouldInterruptFollow)
        assertEquals(null, decision.nextPendingProgrammaticSettledPage)
        assertEquals(1, decision.nextLastSettledPage)
    }

    @Test
    fun unchangedSettledPageDoesNotInterruptFollow() {
        val decision = ReaderPageSettledPolicy.resolve(
            previousSettledPage = 1,
            pendingProgrammaticSettledPage = null,
            settledPage = 1,
        )

        assertFalse(decision.shouldInterruptFollow)
        assertEquals(null, decision.nextPendingProgrammaticSettledPage)
        assertEquals(1, decision.nextLastSettledPage)
    }

    @Test
    fun nonTargetSettledPageKeepsPendingProgrammaticPage() {
        val decision = ReaderPageSettledPolicy.resolve(
            previousSettledPage = 0,
            pendingProgrammaticSettledPage = 2,
            settledPage = 1,
        )

        assertTrue(decision.shouldInterruptFollow)
        assertEquals(2, decision.nextPendingProgrammaticSettledPage)
        assertEquals(1, decision.nextLastSettledPage)
    }
}
