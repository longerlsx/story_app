package com.longerlsx.storyapp.feature.reader

data class ReaderPageSettledDecision(
    val shouldInterruptFollow: Boolean,
    val nextPendingProgrammaticSettledPage: Int?,
    val nextLastSettledPage: Int,
)

object ReaderPageSettledPolicy {
    fun resolve(
        previousSettledPage: Int?,
        pendingProgrammaticSettledPage: Int?,
        settledPage: Int,
    ): ReaderPageSettledDecision {
        val shouldInterruptFollow = previousSettledPage != null &&
            settledPage != previousSettledPage &&
            pendingProgrammaticSettledPage != settledPage
        val nextPendingProgrammaticSettledPage = if (pendingProgrammaticSettledPage == settledPage) {
            null
        } else {
            pendingProgrammaticSettledPage
        }

        return ReaderPageSettledDecision(
            shouldInterruptFollow = shouldInterruptFollow,
            nextPendingProgrammaticSettledPage = nextPendingProgrammaticSettledPage,
            nextLastSettledPage = settledPage,
        )
    }
}
