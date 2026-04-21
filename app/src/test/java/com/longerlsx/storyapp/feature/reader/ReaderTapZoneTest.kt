package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapZoneTest {

    @Test
    fun resolvesLeftCenterAndRightTapTargets() {
        assertEquals(ReaderTapZone.PREVIOUS, ReaderTapZone.resolve(x = 50f, width = 300f))
        assertEquals(ReaderTapZone.TOGGLE_CHROME, ReaderTapZone.resolve(x = 150f, width = 300f))
        assertEquals(ReaderTapZone.NEXT, ReaderTapZone.resolve(x = 260f, width = 300f))
    }
}
